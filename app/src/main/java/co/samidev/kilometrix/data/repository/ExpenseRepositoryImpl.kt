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

    // ── Mapping helpers ────────────────────────────────────────────────────────

    private fun com.google.firebase.firestore.DocumentSnapshot.toVehicleExpense(): VehicleExpense? {
        val type = try {
            ExpenseType.valueOf(getString("type") ?: return null)
        } catch (e: IllegalArgumentException) { return null }

        val fuelDetails = if (type == ExpenseType.FUEL) {
            val unit = try {
                FuelUnit.valueOf(getString("enteredUnit") ?: FuelUnit.GALLON.name)
            } catch (e: Exception) { FuelUnit.GALLON }

            FuelDetails(
                gallons = getDouble("gallons") ?: 0.0,
                liters = getDouble("liters") ?: 0.0,
                pricePerGallon = getDouble("pricePerGallon") ?: 0.0,
                pricePerLiter = getDouble("pricePerLiter") ?: 0.0,
                enteredUnit = unit,
                enteredQuantity = getDouble("enteredQuantity") ?: 0.0,
                pricePerEnteredUnit = getDouble("pricePerEnteredUnit") ?: 0.0,
                odometerAtRefuel = getLong("odometerAtRefuel")?.toInt() ?: 0,
                previousOdometer = getLong("previousOdometer")?.toInt() ?: 0,
                kmTraveled = getLong("kmTraveled")?.toInt() ?: 0,
                kmPerGallon = getDouble("kmPerGallon") ?: 0.0,
                kmPerLiter = getDouble("kmPerLiter") ?: 0.0,
                // 🆕 Campos Multifase — default false para retro-compatibilidad con
                // registros anteriores que no tenían estos campos en Firestore.
                isReserve = getBoolean("isReserve") ?: false,
                isFullTank = getBoolean("isFullTank") ?: false,
                isPartial = getBoolean("isPartial") ?: false
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
            base["gallons"] = d.gallons
            base["liters"] = d.liters
            base["pricePerGallon"] = d.pricePerGallon
            base["pricePerLiter"] = d.pricePerLiter
            base["enteredUnit"] = d.enteredUnit.name
            base["enteredQuantity"] = d.enteredQuantity
            base["pricePerEnteredUnit"] = d.pricePerEnteredUnit
            base["odometerAtRefuel"] = d.odometerAtRefuel
            base["previousOdometer"] = d.previousOdometer
            base["kmTraveled"] = d.kmTraveled
            base["kmPerGallon"] = d.kmPerGallon
            base["kmPerLiter"] = d.kmPerLiter
            // 🆕 Campos Multifase
            base["isReserve"] = d.isReserve
            base["isFullTank"] = d.isFullTank
            base["isPartial"] = d.isPartial
        }
        return base
    }
}
