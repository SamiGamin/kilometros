package co.samidev.kilometrix.data.repository

import co.samidev.kilometrix.domain.model.UserProfile
import co.samidev.kilometrix.domain.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) : UserRepository {

    override suspend fun saveSetupData(city: String, platforms: List<String>): Result<Unit> {
        val userId = auth.currentUser?.uid ?: return Result.failure(Exception("No active user session"))
        return try {
            val updates = mapOf(
                "city" to city,
                "platforms" to platforms,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(userId)
                .set(updates, com.google.firebase.firestore.SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateUserProfile(
        name: String,
        city: String,
        platforms: List<String>,
        maintenanceReservePercent: Int
    ): Result<Unit> {
        val userId = auth.currentUser?.uid ?: return Result.failure(Exception("No active user session"))
        return try {
            val updates = mapOf(
                "name" to name,
                "city" to city,
                "platforms" to platforms,
                "maintenanceReservePercent" to maintenanceReservePercent,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(userId)
                .set(updates, com.google.firebase.firestore.SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getUserProfile(): Flow<UserProfile?> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val listener = db.collection("users").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val profile = UserProfile(
                        id = snapshot.id,
                        name = snapshot.getString("name") ?: "",
                        email = snapshot.getString("email") ?: "",
                        city = snapshot.getString("city") ?: "",
                        platforms = (snapshot.get("platforms") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
                        maintenanceReservePercent = (snapshot.getLong("maintenanceReservePercent") ?: 10L).toInt(),
                        isVerified = snapshot.getBoolean("isVerified") ?: false
                    )
                    trySend(profile)
                } else {
                    trySend(null)
                }
            }

        awaitClose { listener.remove() }
    }
}
