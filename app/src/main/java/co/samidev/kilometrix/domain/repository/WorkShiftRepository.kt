package co.samidev.kilometrix.domain.repository

import co.samidev.kilometrix.domain.model.ShiftEarning
import co.samidev.kilometrix.domain.model.WorkShift
import kotlinx.coroutines.flow.Flow

interface WorkShiftRepository {
    /** Escucha en tiempo real el turno activo (ACTIVE o PAUSED) del vehículo. */
    fun getActiveShift(vehicleId: String): Flow<WorkShift?>

    /** Escucha si hay algún turno activo globalmente sin importar el vehículo. */
    fun getAnyActiveShift(): Flow<WorkShift?>

    /** Crea un nuevo turno en Firestore y retorna el ID generado. */
    suspend fun startShift(vehicleId: String, initialOdometer: Int): Result<String>

    /** Pausa el turno: guarda el timestamp de pausa. */
    suspend fun pauseShift(shiftId: String): Result<Unit>

    /**
     * Reanuda el turno: acumula el tiempo pausado en [WorkShift.pausedDurationMs]
     * y borra [WorkShift.pauseStartTime].
     */
    suspend fun resumeShift(shiftId: String): Result<Unit>

    /**
     * Finaliza el turno con el odómetro final.
     * Registra [endTime] y cambia el estado a ENDED.
     */
    suspend fun endShift(shiftId: String, finalOdometer: Int): Result<Unit>

    /** Agrega una ganancia al array `earnings` del turno (arrayUnion). */
    suspend fun addEarning(shiftId: String, earning: ShiftEarning): Result<Unit>
}
