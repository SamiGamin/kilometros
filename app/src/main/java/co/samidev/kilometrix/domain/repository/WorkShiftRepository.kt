package co.samidev.kilometrix.domain.repository

import co.samidev.kilometrix.domain.model.ShiftEarning
import co.samidev.kilometrix.domain.model.WorkShift
import kotlinx.coroutines.flow.Flow

import co.samidev.kilometrix.domain.model.ShiftType

interface WorkShiftRepository {
    /** Escucha en tiempo real el turno activo (ACTIVE o PAUSED) del vehículo. */
    fun getActiveShift(vehicleId: String): Flow<WorkShift?>

    /** Escucha si hay algún turno activo globalmente sin importar el vehículo. */
    fun getAnyActiveShift(): Flow<WorkShift?>

    /** Recupera todos los turnos finalizados o activos para un vehículo específico, ordenados por fecha descendente. */
    fun getShiftsForVehicle(vehicleId: String): Flow<List<WorkShift>>

    /** Crea un nuevo turno en Firestore y retorna el ID generado. */
    suspend fun startShift(vehicleId: String, initialOdometer: Int, type: ShiftType = ShiftType.WORK): Result<String>

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
    
    /** Registra una ganancia aislada creando un mini-turno de 0 duración. */
    suspend fun addStandaloneEarning(vehicleId: String, earning: ShiftEarning): Result<Unit>

    /** Elimina un turno de Firestore por su ID. */
    suspend fun deleteShift(shiftId: String): Result<Unit>

    /** Elimina una ganancia específica de un turno (o el mini-turno entero si es aislada). */
    suspend fun deleteEarning(shiftId: String, earningId: String): Result<Unit>
}
