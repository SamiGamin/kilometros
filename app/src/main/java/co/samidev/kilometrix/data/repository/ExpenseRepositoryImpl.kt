package co.samidev.kilometrix.data.repository

import co.samidev.kilometrix.domain.model.ExpenseType
import co.samidev.kilometrix.domain.model.FuelDetails
import co.samidev.kilometrix.domain.model.FuelUnit
import co.samidev.kilometrix.domain.model.VehicleExpense
import co.samidev.kilometrix.domain.repository.ExpenseRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
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
                // NOTA: Se eliminó el filtro kmTraveled > 0 para que todos los tanqueos
                // (incluyendo parciales y reservas) lleguen al UseCase Multifase.
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
            val docRef = getExpensesCollection(userId).document()
            val data = expense.toFirestoreMap()
            docRef.set(data).await()

            // Si es combustible → actualizar odómetro del vehículo
            val details = expense.fuelDetails
            if (expense.type == ExpenseType.FUEL && details != null && details.odometerAtRefuel > 0) {
                getVehicleDoc(userId, expense.vehicleId)
                    .update("odometer", details.odometerAtRefuel)
                    .await()
            }

            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteExpense(expenseId: String): Result<Unit> {
        val userId = auth.currentUser?.uid
            ?: return Result.failure(Exception("No hay sesión activa"))
        return try {
            getExpensesCollection(userId).document(expenseId).delete().await()
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
            val chunks = expenses.chunked(450)
            var count = 0
            for (chunk in chunks) {
                val batch = db.batch()
                for (exp in chunk) {
                    val docId = if (exp.id.isNotBlank()) exp.id else collection.document().id
                    val docRef = collection.document(docId)
                    batch.set(docRef, exp.copy(vehicleId = vehicleId).toFirestoreMap())
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
