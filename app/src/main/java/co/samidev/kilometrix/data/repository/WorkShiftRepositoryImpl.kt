package co.samidev.kilometrix.data.repository

import co.samidev.kilometrix.domain.model.ShiftEarning
import co.samidev.kilometrix.domain.model.ShiftStatus
import co.samidev.kilometrix.domain.model.ShiftType
import co.samidev.kilometrix.domain.model.WorkShift
import co.samidev.kilometrix.domain.repository.WorkShiftRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkShiftRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) : WorkShiftRepository {

    private fun shiftsCol(userId: String) =
        db.collection("users").document(userId).collection("workShifts")

    // ── Realtime listener ──────────────────────────────────────────────────────

    override fun getActiveShift(vehicleId: String): Flow<WorkShift?> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            trySend(null)
            close()
            return@callbackFlow
        }
        // Al usar in-query sobre un solo campo evitamos requerir un índice compuesto en Firestore.
        // Filtramos en el cliente por vehicleId, lo cual es muy eficiente porque un usuario 
        // normalmente solo tiene 0 o 1 turno activo.
        val listener = shiftsCol(userId)
            .whereIn("status", listOf(ShiftStatus.ACTIVE.name, ShiftStatus.PAUSED.name))
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val shift = snapshot?.documents
                    ?.mapNotNull { it.toWorkShift() }
                    ?.firstOrNull { it.vehicleId == vehicleId }
                trySend(shift)
            }
        awaitClose { listener.remove() }
    }

    override fun getAnyActiveShift(): Flow<WorkShift?> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val listener = shiftsCol(userId)
            .whereIn("status", listOf(ShiftStatus.ACTIVE.name, ShiftStatus.PAUSED.name))
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val shift = snapshot?.documents
                    ?.mapNotNull { it.toWorkShift() }
                    ?.firstOrNull()
                trySend(shift)
            }
        awaitClose { listener.remove() }
    }

    override fun getShiftsForVehicle(vehicleId: String): Flow<List<WorkShift>> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = shiftsCol(userId)
            .whereEqualTo("vehicleId", vehicleId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val shifts = snapshot?.documents
                    ?.mapNotNull { it.toWorkShift() }
                    ?.sortedByDescending { it.startTime }
                    ?: emptyList()
                trySend(shifts)
            }
        awaitClose { listener.remove() }
    }

    // ── Write operations ───────────────────────────────────────────────────────

    override suspend fun startShift(vehicleId: String, initialOdometer: Int, type: ShiftType): Result<String> {
        val userId = auth.currentUser?.uid ?: return Result.failure(Exception("Sin sesión"))
        return try {
            val docRef = shiftsCol(userId).document()
            val map = mapOf(
                "id" to docRef.id,
                "vehicleId" to vehicleId,
                "startTime" to System.currentTimeMillis(),
                "endTime" to null,
                "initialOdometer" to initialOdometer,
                "finalOdometer" to null,
                "pausedDurationMs" to 0L,
                "status" to ShiftStatus.ACTIVE.name,
                "pauseStartTime" to null,
                "earnings" to emptyList<Any>(),
                "type" to type.name
            )
            docRef.set(map).await()

            // Actualizar odómetro del vehículo al iniciar recorrido si initialOdometer > 0
            if (initialOdometer > 0) {
                db.collection("users").document(userId)
                    .collection("vehicles").document(vehicleId)
                    .update("odometer", initialOdometer)
                    .await()
            }

            Result.success(docRef.id)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun pauseShift(shiftId: String): Result<Unit> {
        val userId = auth.currentUser?.uid ?: return Result.failure(Exception("Sin sesión"))
        return try {
            shiftsCol(userId).document(shiftId).update(
                mapOf(
                    "status" to ShiftStatus.PAUSED.name,
                    "pauseStartTime" to System.currentTimeMillis()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun resumeShift(shiftId: String): Result<Unit> {
        val userId = auth.currentUser?.uid ?: return Result.failure(Exception("Sin sesión"))
        return try {
            val doc = shiftsCol(userId).document(shiftId).get().await()
            val pauseStartTime = doc.getLong("pauseStartTime") ?: System.currentTimeMillis()
            val currentPausedMs = doc.getLong("pausedDurationMs") ?: 0L
            val addedPause = System.currentTimeMillis() - pauseStartTime
            shiftsCol(userId).document(shiftId).update(
                mapOf(
                    "status" to ShiftStatus.ACTIVE.name,
                    "pauseStartTime" to null,
                    "pausedDurationMs" to (currentPausedMs + addedPause)
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun endShift(shiftId: String, finalOdometer: Int): Result<Unit> {
        val userId = auth.currentUser?.uid ?: return Result.failure(Exception("Sin sesión"))
        return try {
            val docRef = shiftsCol(userId).document(shiftId)
            val doc = docRef.get().await()
            val vehicleId = doc.getString("vehicleId")

            docRef.update(
                mapOf(
                    "status" to ShiftStatus.ENDED.name,
                    "endTime" to System.currentTimeMillis(),
                    "finalOdometer" to finalOdometer
                )
            ).await()

            // Actualizar odómetro máster del vehículo al finalizar recorrido si finalOdometer > 0
            if (vehicleId != null && finalOdometer > 0) {
                db.collection("users").document(userId)
                    .collection("vehicles").document(vehicleId)
                    .update("odometer", finalOdometer)
                    .await()
            }

            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun addEarning(shiftId: String, earning: ShiftEarning): Result<Unit> {
        val userId = auth.currentUser?.uid ?: return Result.failure(Exception("Sin sesión"))
        return try {
            val earningMap = mapOf(
                "id" to earning.id,
                "appName" to earning.appName,
                "appEmoji" to earning.appEmoji,
                "amount" to earning.amount,
                "registeredAt" to earning.registeredAt
            )
            shiftsCol(userId).document(shiftId)
                .update("earnings", FieldValue.arrayUnion(earningMap))
                .await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }
    
    override suspend fun addStandaloneEarning(vehicleId: String, earning: ShiftEarning): Result<Unit> {
        val userId = auth.currentUser?.uid ?: return Result.failure(Exception("Sin sesión"))
        return try {
            val shiftId = java.util.UUID.randomUUID().toString()
            val shift = WorkShift(
                id = shiftId,
                vehicleId = vehicleId,
                startTime = earning.registeredAt,
                endTime = earning.registeredAt,
                initialOdometer = 0,
                finalOdometer = 0,
                status = ShiftStatus.ENDED,
                earnings = listOf(earning),
                pauseStartTime = null,
                pausedDurationMs = 0L
            )
            val shiftMap = mapOf(
                "id" to shift.id,
                "vehicleId" to shift.vehicleId,
                "startTime" to shift.startTime,
                "endTime" to shift.endTime,
                "initialOdometer" to shift.initialOdometer,
                "finalOdometer" to shift.finalOdometer,
                "status" to shift.status.name,
                "pauseStartTime" to shift.pauseStartTime,
                "pausedDurationMs" to shift.pausedDurationMs,
                "earnings" to shift.earnings.map { e ->
                    mapOf(
                        "id" to e.id,
                        "appName" to e.appName,
                        "appEmoji" to e.appEmoji,
                        "amount" to e.amount,
                        "registeredAt" to e.registeredAt
                    )
                }
            )
            shiftsCol(userId).document(shiftId).set(shiftMap).await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun deleteShift(shiftId: String): Result<Unit> {
        val userId = auth.currentUser?.uid ?: return Result.failure(Exception("Sin sesión"))
        return try {
            shiftsCol(userId).document(shiftId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun deleteEarning(shiftId: String, earningId: String): Result<Unit> {
        val userId = auth.currentUser?.uid ?: return Result.failure(Exception("Sin sesión"))
        return try {
            val docSnap = shiftsCol(userId).document(shiftId).get().await()
            val shift = docSnap.toWorkShift()
            if (shift != null) {
                val updatedEarnings = shift.earnings.filter { it.id != earningId }
                if (updatedEarnings.isEmpty() && shift.initialOdometer == 0 && shift.finalOdometer == 0) {
                    shiftsCol(userId).document(shiftId).delete().await()
                } else {
                    val updatedListMap = updatedEarnings.map { e ->
                        mapOf(
                            "id" to e.id,
                            "appName" to e.appName,
                            "appEmoji" to e.appEmoji,
                            "amount" to e.amount,
                            "registeredAt" to e.registeredAt
                        )
                    }
                    shiftsCol(userId).document(shiftId).update("earnings", updatedListMap).await()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }
}

// ── Firestore ↔ Domain mappers ────────────────────────────────────────────────

private fun DocumentSnapshot.toWorkShift(): WorkShift? {
    return try {
        @Suppress("UNCHECKED_CAST")
        val earningsList = (get("earnings") as? List<*>)?.mapNotNull { item ->
            (item as? Map<*, *>)?.let { map ->
                ShiftEarning(
                    id = map["id"] as? String ?: "",
                    appName = map["appName"] as? String ?: "",
                    appEmoji = map["appEmoji"] as? String ?: "💰",
                    amount = (map["amount"] as? Number)?.toDouble() ?: 0.0,
                    registeredAt = (map["registeredAt"] as? Number)?.toLong() ?: 0L
                )
            }
        } ?: emptyList()

        WorkShift(
            id = id,
            vehicleId = getString("vehicleId") ?: return null,
            startTime = getLong("startTime") ?: return null,
            endTime = getLong("endTime"),
            initialOdometer = getLong("initialOdometer")?.toInt() ?: 0,
            finalOdometer = getLong("finalOdometer")?.toInt(),
            pausedDurationMs = getLong("pausedDurationMs") ?: 0L,
            status = try {
                ShiftStatus.valueOf(getString("status") ?: "ACTIVE")
            } catch (_: IllegalArgumentException) { ShiftStatus.ACTIVE },
            pauseStartTime = getLong("pauseStartTime"),
            earnings = earningsList,
            type = try {
                ShiftType.valueOf(getString("type") ?: "WORK")
            } catch (_: IllegalArgumentException) { ShiftType.WORK }
        )
    } catch (_: Exception) { null }
}
