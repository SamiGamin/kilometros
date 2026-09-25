package co.samidev.kilometrix.data.repository

import co.samidev.kilometrix.data.sync.KipuWalletResolver
import co.samidev.kilometrix.domain.model.ShiftEarning
import co.samidev.kilometrix.domain.model.ShiftStatus
import co.samidev.kilometrix.domain.model.ShiftType
import co.samidev.kilometrix.domain.model.WorkShift
import co.samidev.kilometrix.domain.repository.WorkShiftRepository
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkShiftRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val kipuWalletResolver: KipuWalletResolver
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
            val shift = doc.toWorkShift()
            val vehicleId = shift?.vehicleId ?: doc.getString("vehicleId")

            val totalEarnings = shift?.earnings?.sumOf { it.amount } ?: 0.0
            val endTimeMillis = System.currentTimeMillis()

            var vehiclePlate = ""
            if (!vehicleId.isNullOrBlank()) {
                try {
                    val vDoc = db.collection("users").document(userId)
                        .collection("vehicles").document(vehicleId).get().await()
                    vehiclePlate = vDoc.getString("plate") ?: ""
                } catch (_: Exception) {}
            }

            val desc = if (vehiclePlate.isNotBlank()) "Ganancias Turno ($vehiclePlate)" else "Ganancias Turno"

            val batch = db.batch()

            // 1. Finalizar turno
            batch.update(
                docRef,
                mapOf(
                    "status" to ShiftStatus.ENDED.name,
                    "endTime" to endTimeMillis,
                    "finalOdometer" to finalOdometer
                )
            )

            // 2. Si el turno tiene ganancias netas > 0, crear transacción en Kipu y actualizar balance del monedero
            if (totalEarnings > 0.0) {
                val targetWalletId = kipuWalletResolver.resolveTargetWalletId(userId)
                val amountMinor = totalEarnings.toLong()

                val txRef = db.collection("users").document(userId)
                    .collection("transactions").document("shift_$shiftId")

                val txData = hashMapOf(
                    "operationId" to shiftId,
                    "walletId" to targetWalletId,
                    "destinationWalletId" to null,
                    "categoryId" to "work",
                    "category" to "Trabajo",
                    "description" to desc,
                    "amount" to totalEarnings,
                    "amountMinor" to amountMinor,
                    "currency" to "COP",
                    "type" to "INCOME",
                    "source" to "KILOMETRIX",
                    "status" to "COMPLETED",
                    "date" to Timestamp(Date(endTimeMillis)),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "schemaVersion" to 1
                )
                batch.set(txRef, txData)

                if (targetWalletId.isNotBlank() && amountMinor > 0L) {
                    val walletRef = db.collection("users").document(userId)
                        .collection("wallets").document(targetWalletId)
                    batch.set(
                        walletRef,
                        mapOf(
                            "currentBalanceMinor" to FieldValue.increment(amountMinor),
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    )
                }
            }

            // 3. Odómetro máster del vehículo
            if (vehicleId != null && finalOdometer > 0) {
                val vehicleRef = db.collection("users").document(userId)
                    .collection("vehicles").document(vehicleId)
                batch.update(vehicleRef, "odometer", finalOdometer)
            }

            batch.commit().await()
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

            var vehiclePlate = ""
            if (vehicleId.isNotBlank()) {
                try {
                    val vDoc = db.collection("users").document(userId)
                        .collection("vehicles").document(vehicleId).get().await()
                    vehiclePlate = vDoc.getString("plate") ?: ""
                } catch (_: Exception) {}
            }

            val batch = db.batch()
            val docRef = shiftsCol(userId).document(shiftId)
            batch.set(docRef, shiftMap)

            if (earning.amount > 0.0) {
                val targetWalletId = kipuWalletResolver.resolveTargetWalletId(userId)
                val amountMinor = earning.amount.toLong()

                val txRef = db.collection("users").document(userId)
                    .collection("transactions").document("shift_$shiftId")

                val desc = if (earning.appName.isNotBlank()) {
                    "Ganancia ${earning.appName}${if (vehiclePlate.isNotBlank()) " ($vehiclePlate)" else ""}"
                } else {
                    "Ganancias Turno${if (vehiclePlate.isNotBlank()) " ($vehiclePlate)" else ""}"
                }

                val txData = hashMapOf(
                    "operationId" to shiftId,
                    "walletId" to targetWalletId,
                    "destinationWalletId" to null,
                    "categoryId" to "work",
                    "category" to "Trabajo",
                    "description" to desc,
                    "amount" to earning.amount,
                    "amountMinor" to amountMinor,
                    "currency" to "COP",
                    "type" to "INCOME",
                    "source" to "KILOMETRIX",
                    "status" to "COMPLETED",
                    "date" to Timestamp(Date(earning.registeredAt)),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "schemaVersion" to 1
                )
                batch.set(txRef, txData)

                if (targetWalletId.isNotBlank() && amountMinor > 0L) {
                    val walletRef = db.collection("users").document(userId)
                        .collection("wallets").document(targetWalletId)
                    batch.set(
                        walletRef,
                        mapOf(
                            "currentBalanceMinor" to FieldValue.increment(amountMinor),
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    )
                }
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun deleteShift(shiftId: String): Result<Unit> {
        val userId = auth.currentUser?.uid ?: return Result.failure(Exception("Sin sesión"))
        return try {
            val shiftRef = shiftsCol(userId).document(shiftId)
            val txRef = db.collection("users").document(userId)
                .collection("transactions").document("shift_$shiftId")

            val txSnap = txRef.get().await()
            val amountMinor = txSnap.getLong("amountMinor") ?: 0L
            val walletId = txSnap.getString("walletId") ?: kipuWalletResolver.getSelectedWalletId()

            val batch = db.batch()
            batch.delete(shiftRef)
            batch.delete(txRef)

            if (!walletId.isNullOrBlank() && amountMinor > 0L) {
                val walletRef = db.collection("users").document(userId)
                    .collection("wallets").document(walletId)
                batch.set(
                    walletRef,
                    mapOf(
                        "currentBalanceMinor" to FieldValue.increment(-amountMinor),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun deleteEarning(shiftId: String, earningId: String): Result<Unit> {
        val userId = auth.currentUser?.uid ?: return Result.failure(Exception("Sin sesión"))
        return try {
            val docSnap = shiftsCol(userId).document(shiftId).get().await()
            val shift = docSnap.toWorkShift()
            if (shift != null) {
                val deletedEarning = shift.earnings.firstOrNull { it.id == earningId }
                val updatedEarnings = shift.earnings.filter { it.id != earningId }

                if (updatedEarnings.isEmpty() && shift.initialOdometer == 0 && shift.finalOdometer == 0) {
                    return deleteShift(shiftId)
                }

                val updatedListMap = updatedEarnings.map { e ->
                    mapOf(
                        "id" to e.id,
                        "appName" to e.appName,
                        "appEmoji" to e.appEmoji,
                        "amount" to e.amount,
                        "registeredAt" to e.registeredAt
                    )
                }

                val batch = db.batch()
                batch.update(shiftsCol(userId).document(shiftId), "earnings", updatedListMap)

                // Si el turno ya había finalizado y tenía transacción contable en Kipu, sincronizar el ajuste
                if (shift.status == ShiftStatus.ENDED && deletedEarning != null && deletedEarning.amount > 0.0) {
                    val txRef = db.collection("users").document(userId)
                        .collection("transactions").document("shift_$shiftId")
                    val txSnap = txRef.get().await()
                    if (txSnap.exists()) {
                        val deletedMinor = deletedEarning.amount.toLong()
                        val currentMinor = txSnap.getLong("amountMinor") ?: 0L
                        val newMinor = (currentMinor - deletedMinor).coerceAtLeast(0L)
                        val newAmount = newMinor.toDouble()
                        val walletId = txSnap.getString("walletId") ?: kipuWalletResolver.getSelectedWalletId()

                        if (newMinor == 0L) {
                            batch.delete(txRef)
                        } else {
                            batch.update(
                                txRef,
                                mapOf(
                                    "amount" to newAmount,
                                    "amountMinor" to newMinor
                                )
                            )
                        }

                        if (!walletId.isNullOrBlank() && deletedMinor > 0L) {
                            val walletRef = db.collection("users").document(userId)
                                .collection("wallets").document(walletId)
                            batch.set(
                                walletRef,
                                mapOf(
                                    "currentBalanceMinor" to FieldValue.increment(-deletedMinor),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                ),
                                SetOptions.merge()
                            )
                        }
                    }
                }

                batch.commit().await()
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
