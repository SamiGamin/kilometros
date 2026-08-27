package co.samidev.kilometrix.data.repository

import co.samidev.kilometrix.domain.model.Vehicle
import co.samidev.kilometrix.domain.repository.VehicleRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VehicleRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) : VehicleRepository {

    private fun getVehiclesCollection(userId: String) =
        db.collection("users").document(userId).collection("vehicles")

    override fun getVehiclesRealtime(): Flow<List<Vehicle>> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = getVehiclesCollection(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val vehicles = snapshot.documents.mapNotNull { doc ->
                        Vehicle(
                            id = doc.id,
                            type = doc.getString("type") ?: "PARTICULAR",
                            nickname = doc.getString("nickname") ?: "",
                            brand = doc.getString("brand") ?: "",
                            model = doc.getString("model") ?: "",
                            year = doc.getLong("year")?.toInt() ?: 0,
                            plate = doc.getString("plate") ?: "",
                            fuel = doc.getString("fuel") ?: "GASOLINE",
                            odometer = doc.getLong("odometer")?.toInt() ?: 0,
                            soatExpiry = doc.getString("soatExpiry"),
                            tecnomecExpiry = doc.getString("tecnomecExpiry"),
                            seguroExpiry = doc.getString("seguroExpiry"),
                            extinguisherExpiry = doc.getString("extinguisherExpiry"),
                            soatEnabled = doc.getBoolean("soatEnabled") ?: false,
                            tecnomecEnabled = doc.getBoolean("tecnomecEnabled") ?: false,
                            seguroEnabled = doc.getBoolean("seguroEnabled") ?: false,
                            extinguisherEnabled = doc.getBoolean("extinguisherEnabled") ?: false,
                            lastOilChangeKm = doc.getLong("lastOilChangeKm")?.toInt(),
                            oilIntervalKm = doc.getLong("oilIntervalKm")?.toInt() ?: 10000
                        )
                    }
                    trySend(vehicles)
                } else {
                    trySend(emptyList())
                }
            }

        awaitClose { listener.remove() }
    }

    override suspend fun addVehicle(vehicle: Vehicle): Result<String> {
        val userId = auth.currentUser?.uid ?: return Result.failure(Exception("No active user session"))
        return try {
            val docRef = getVehiclesCollection(userId).document()
            val vehicleData = mapOf(
                "type" to vehicle.type,
                "nickname" to vehicle.nickname,
                "brand" to vehicle.brand,
                "model" to vehicle.model,
                "year" to vehicle.year,
                "plate" to vehicle.plate,
                "fuel" to vehicle.fuel,
                "odometer" to vehicle.odometer,
                "soatExpiry" to vehicle.soatExpiry,
                "tecnomecExpiry" to vehicle.tecnomecExpiry,
                "seguroExpiry" to vehicle.seguroExpiry,
                "extinguisherExpiry" to vehicle.extinguisherExpiry,
                "soatEnabled" to vehicle.soatEnabled,
                "tecnomecEnabled" to vehicle.tecnomecEnabled,
                "seguroEnabled" to vehicle.seguroEnabled,
                "extinguisherEnabled" to vehicle.extinguisherEnabled,
                "lastOilChangeKm" to vehicle.lastOilChangeKm,
                "oilIntervalKm" to vehicle.oilIntervalKm
            )
            docRef.set(vehicleData).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateVehicle(vehicle: Vehicle): Result<Unit> {
        val userId = auth.currentUser?.uid ?: return Result.failure(Exception("No active user session"))
        return try {
            val updates = mapOf(
                "type" to vehicle.type,
                "nickname" to vehicle.nickname,
                "brand" to vehicle.brand,
                "model" to vehicle.model,
                "year" to vehicle.year,
                "plate" to vehicle.plate,
                "fuel" to vehicle.fuel,
                "odometer" to vehicle.odometer,
                "soatExpiry" to vehicle.soatExpiry,
                "tecnomecExpiry" to vehicle.tecnomecExpiry,
                "seguroExpiry" to vehicle.seguroExpiry,
                "extinguisherExpiry" to vehicle.extinguisherExpiry,
                "soatEnabled" to vehicle.soatEnabled,
                "tecnomecEnabled" to vehicle.tecnomecEnabled,
                "seguroEnabled" to vehicle.seguroEnabled,
                "extinguisherEnabled" to vehicle.extinguisherEnabled,
                "lastOilChangeKm" to vehicle.lastOilChangeKm,
                "oilIntervalKm" to vehicle.oilIntervalKm
            )
            getVehiclesCollection(userId).document(vehicle.id).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteVehicle(vehicleId: String): Result<Unit> {
        val userId = auth.currentUser?.uid ?: return Result.failure(Exception("No active user session"))
        return try {
            getVehiclesCollection(userId).document(vehicleId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
