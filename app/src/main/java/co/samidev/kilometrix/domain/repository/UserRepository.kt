package co.samidev.kilometrix.domain.repository

import co.samidev.kilometrix.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    suspend fun saveSetupData(city: String, platforms: List<String>): Result<Unit>
    fun getUserProfile(): Flow<UserProfile?>
}
