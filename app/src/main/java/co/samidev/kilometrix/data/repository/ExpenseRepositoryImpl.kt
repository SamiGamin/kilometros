package co.samidev.kilometrix.data.repository

import co.samidev.kilometrix.data.sync.KipuWalletResolver
import co.samidev.kilometrix.domain.model.ExpenseType
import co.samidev.kilometrix.domain.model.FuelDetails
import co.samidev.kilometrix.domain.model.FuelUnit
import co.samidev.kilometrix.domain.model.VehicleExpense
import co.samidev.kilometrix.domain.repository.ExpenseRepository
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val kipuWalletResolver: KipuWalletResolver
) : ExpenseRepository {

    private fun getExpensesCollection(userId: String) =
        db.collection("users").document(userId).collection("expenses")

    private fun getVehicleDoc(userId: String, vehicleId: String) =
        db.collection("users").document(userId).collection("vehicles").document(vehicleId)

    // ── Realtime flow ──────────────────────────────────────────────────────────

    override fun getExpensesRealtime(vehicleId: String): Flow<List<VehicleExpense>> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = getExpensesCollection(userId)
            .whereEqualTo("vehicleId", vehicleId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val list = snapshot?.documents
                    ?.mapNotNull { it.toVehicleExpense() }
                    ?.sortedByDescending { it.date }
                    ?: emptyList()
                trySend(list)
            }

        awaitClose { listener.remove() }
    }

    override fun getFuelHistory(vehicleId: String): Flow<List<VehicleExpense>> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = getExpensesCollection(userId)
            .whereEqualTo("vehicleId", vehicleId)
            .whereEqualTo("type", ExpenseType.FUEL.name)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val list = snapshot?.documents
                    ?.mapNotNull { it.toVehicleExpense() }
                    ?.sortedByDescending { it.date }
                    ?: emptyList()
                trySend(list)
            }

        awaitClose { listener.remove() }
    }

    // ── Write operations ───────────────────────────────────────────────────────

    override suspend fun addExpense(expense: VehicleExpense): Result<String> {
        val userId = auth.currentUser?.uid
            ?: return Result.failure(Exception("No hay sesión activa"))
        return try {
            val docRef = if (expense.id.isNotBlank()) {
                getExpensesCollection(userId).document(expense.id)
            } else {
                getExpensesCollection(userId).document()
            }
            val expenseId = docRef.id
            val finalExpense = expense.copy(id = expenseId)

            // Resolver monedero contable de Kipu
            val targetWalletId = kipuWalletResolver.resolveTargetWalletId(userId)
            val amountMinor = expense.amount.toLong()

            val batch = db.batch()

            // 1. Guardar gasto vehicular
            batch.set(docRef, finalExpense.toFirestoreMap())

            // 2. Registrar transacción contable en el Ledger de Kipu (users/{userId}/transactions/exp_{expenseId})
            val txDocRef = db.collection("users").document(userId)
                .collection("transactions").document("exp_$expenseId")

            val description = if (expense.notes.isNotBlank()) {
                "${expense.type.label}: ${expense.notes.trim()}"
            } else {
                "Gasto Vehicular: ${expense.type.label}"
            }

            val txData = hashMapOf(
                "operationId" to expenseId,
                "walletId" to targetWalletId,
                "destinationWalletId" to null,
                "categoryId" to "vehicle",
                "category" to "Vehículo",
                "description" to description,
                "amount" to expense.amount,
                "amountMinor" to amountMinor,
                "currency" to "COP",
                "type" to "EXPENSE",
                "source" to "KILOMETRIX",
                "status" to "COMPLETED",
                "date" to Timestamp(Date(expense.date)),
                "createdAt" to FieldValue.serverTimestamp(),
                "schemaVersion" to 1
            )
            batch.set(txDocRef, txData)

            // 3. Descontar balance del monedero en Kipu (-amount.toLong())
            if (targetWalletId.isNotBlank() && amountMinor > 0L) {
                val walletRef = db.collection("users").document(userId)
                    .collection("wallets").document(targetWalletId)
                batch.set(
                    walletRef,
                    mapOf(
                        "currentBalanceMinor" to FieldValue.increment(-amountMinor),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
            }

            // 4. Si es combustible → actualizar odómetro del vehículo
            val details = expense.fuelDetails
            if (expense.type == ExpenseType.FUEL && details != null && details.odometerAtRefuel > 0) {
                batch.update(getVehicleDoc(userId, expense.vehicleId), "odometer", details.odometerAtRefuel)
            }

            batch.commit().await()
            Result.success(expenseId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteExpense(expenseId: String): Result<Unit> {
        val userId = auth.currentUser?.uid
            ?: return Result.failure(Exception("No hay sesión activa"))
        return try {
            val expenseRef = getExpensesCollection(userId).document(expenseId)
            val txRef = db.collection("users").document(userId)
                .collection("transactions").document("exp_$expenseId")

            val txSnap = txRef.get().await()
            val expSnap = if (!txSnap.exists()) expenseRef.get().await() else null

            val amountMinor = txSnap.getLong("amountMinor")
                ?: (expSnap?.getDouble("amount") ?: 0.0).toLong()
            val walletId = txSnap.getString("walletId")
                ?: kipuWalletResolver.getSelectedWalletId()

            val batch = db.batch()
            batch.delete(expenseRef)
            batch.delete(txRef)

            if (!walletId.isNullOrBlank() && amountMinor > 0L) {
                val walletRef = db.collection("users").document(userId)
                    .collection("wallets").document(walletId)
                batch.set(
                    walletRef,
                    mapOf(
                        "currentBalanceMinor" to FieldValue.increment(amountMinor),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun importExpensesBatch(vehicleId: String, expenses: List<VehicleExpense>): Result<Int> {
        val userId = auth.currentUser?.uid
            ?: return Result.failure(Exception("No hay sesión activa"))
        return try {
            val collection = getExpensesCollection(userId)
            val targetWalletId = kipuWalletResolver.resolveTargetWalletId(userId)
            // Cada item genera 2 escrituras (expense + tx), chunks de 200 items (< 500 ops por batch)
            val chunks = expenses.chunked(200)
            var count = 0
            for (chunk in chunks) {
                val batch = db.batch()
                var chunkTotalMinor = 0L

                for (exp in chunk) {
                    val docId = if (exp.id.isNotBlank()) exp.id else collection.document().id
                    val docRef = collection.document(docId)
                    val fullExp = exp.copy(id = docId, vehicleId = vehicleId)
                    batch.set(docRef, fullExp.toFirestoreMap())

                    val expMinor = exp.amount.toLong()
                    chunkTotalMinor += expMinor

                    val txRef = db.collection("users").document(userId)
                        .collection("transactions").document("exp_$docId")
                    val description = if (exp.notes.isNotBlank()) {
                        "${exp.type.label}: ${exp.notes.trim()}"
                    } else {
                        "Gasto Vehicular: ${exp.type.label}"
                    }
                    val txData = hashMapOf(
                        "operationId" to docId,
                        "walletId" to targetWalletId,
                        "destinationWalletId" to null,
                        "categoryId" to "vehicle",
                        "category" to "Vehículo",
                        "description" to description,
                        "amount" to exp.amount,
                        "amountMinor" to expMinor,
                        "currency" to "COP",
                        "type" to "EXPENSE",
                        "source" to "KILOMETRIX",
                        "status" to "COMPLETED",
                        "date" to Timestamp(Date(exp.date)),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "schemaVersion" to 1
                    )
                    batch.set(txRef, txData)
                }

                if (targetWalletId.isNotBlank() && chunkTotalMinor > 0L) {
                    val walletRef = db.collection("users").document(userId)
                        .collection("wallets").document(targetWalletId)
                    batch.set(
                        walletRef,
                        mapOf(
                            "currentBalanceMinor" to FieldValue.increment(-chunkTotalMinor),
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    )
                }

                batch.commit().await()
                count += chunk.size
            }
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Mapping helpers ────────────────────────────────────────────────────────

    private fun com.google.firebase.firestore.DocumentSnapshot.toVehicleExpense(): VehicleExpense? {
        val type = try {
            ExpenseType.valueOf(getString("type") ?: return null)
        } catch (e: IllegalArgumentException) { return null }

        val fuelDetails = if (type == ExpenseType.FUEL) {
            val fuelMap = get("fuelDetails") as? Map<*, *>

            fun getDbl(key: String): Double {
                val valFromMap = (fuelMap?.get(key) as? Number)?.toDouble()
                if (valFromMap != null) return valFromMap
                return getDouble(key) ?: 0.0
            }

            fun getIntVal(key: String): Int {
                val valFromMap = (fuelMap?.get(key) as? Number)?.toInt()
                if (valFromMap != null) return valFromMap
                return getLong(key)?.toInt() ?: 0
            }

            fun getBoolVal(key: String): Boolean {
                val valFromMap = fuelMap?.get(key) as? Boolean
                if (valFromMap != null) return valFromMap
                return getBoolean(key) ?: false
            }

            val unitStr = (fuelMap?.get("enteredUnit") as? String)
                ?: getString("enteredUnit")
                ?: FuelUnit.GALLON.name
            val unit = try { FuelUnit.valueOf(unitStr) } catch (e: Exception) { FuelUnit.GALLON }

            FuelDetails(
                gallons = getDbl("gallons"),
                liters = getDbl("liters"),
                pricePerGallon = getDbl("pricePerGallon"),
                pricePerLiter = getDbl("pricePerLiter"),
                enteredUnit = unit,
                enteredQuantity = getDbl("enteredQuantity"),
                pricePerEnteredUnit = getDbl("pricePerEnteredUnit"),
                odometerAtRefuel = getIntVal("odometerAtRefuel"),
                previousOdometer = getIntVal("previousOdometer"),
                kmTraveled = getIntVal("kmTraveled"),
                kmPerGallon = getDbl("kmPerGallon"),
                kmPerLiter = getDbl("kmPerLiter"),
                isReserve = getBoolVal("isReserve"),
                isFullTank = getBoolVal("isFullTank"),
                isPartial = getBoolVal("isPartial")
            )
        } else null

        return VehicleExpense(
            id = id,
            vehicleId = getString("vehicleId") ?: "",
            type = type,
            amount = getDouble("amount") ?: 0.0,
            date = getLong("date") ?: System.currentTimeMillis(),
            notes = getString("notes") ?: "",
            fuelDetails = fuelDetails
        )
    }

    private fun VehicleExpense.toFirestoreMap(): Map<String, Any?> {
        val base: MutableMap<String, Any?> = mutableMapOf(
            "vehicleId" to vehicleId,
            "type" to type.name,
            "amount" to amount,
            "date" to date,
            "notes" to notes
        )
        fuelDetails?.let { d ->
            val detailsMap = mapOf(
                "gallons" to d.gallons,
                "liters" to d.liters,
                "pricePerGallon" to d.pricePerGallon,
                "pricePerLiter" to d.pricePerLiter,
                "enteredUnit" to d.enteredUnit.name,
                "enteredQuantity" to d.enteredQuantity,
                "pricePerEnteredUnit" to d.pricePerEnteredUnit,
                "odometerAtRefuel" to d.odometerAtRefuel,
                "previousOdometer" to d.previousOdometer,
                "kmTraveled" to d.kmTraveled,
                "kmPerGallon" to d.kmPerGallon,
                "kmPerLiter" to d.kmPerLiter,
                "isReserve" to d.isReserve,
                "isFullTank" to d.isFullTank,
                "isPartial" to d.isPartial
            )
            base["fuelDetails"] = detailsMap
        }
        return base
    }
}
