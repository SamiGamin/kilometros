package co.samidev.kilometrix.domain.repository

import co.samidev.kilometrix.domain.model.AppUpdateInfo

interface AppUpdateRepository {
    suspend fun checkForUpdate(currentVersionName: String): Result<AppUpdateInfo>
}
