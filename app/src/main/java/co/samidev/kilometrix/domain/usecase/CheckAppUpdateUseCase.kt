package co.samidev.kilometrix.domain.usecase

import co.samidev.kilometrix.domain.model.AppUpdateInfo
import co.samidev.kilometrix.domain.repository.AppUpdateRepository
import javax.inject.Inject

class CheckAppUpdateUseCase @Inject constructor(
    private val appUpdateRepository: AppUpdateRepository
) {
    suspend operator fun invoke(currentVersionName: String): Result<AppUpdateInfo> {
        return appUpdateRepository.checkForUpdate(currentVersionName)
    }
}
