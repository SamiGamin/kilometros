package co.samidev.kilometrix.presentation.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.samidev.kilometrix.core.util.ApkInstaller
import co.samidev.kilometrix.domain.model.AppUpdateInfo
import co.samidev.kilometrix.domain.usecase.CheckAppUpdateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface AppUpdateUiState {
    object Idle : AppUpdateUiState
    object Checking : AppUpdateUiState
    data class UpdateAvailable(val updateInfo: AppUpdateInfo) : AppUpdateUiState
    data class Downloading(val progress: Float, val updateInfo: AppUpdateInfo) : AppUpdateUiState
    data class ReadyToInstall(val apkFile: File) : AppUpdateUiState
    data class Error(val message: String, val updateInfo: AppUpdateInfo? = null) : AppUpdateUiState
}

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val checkAppUpdateUseCase: CheckAppUpdateUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<AppUpdateUiState>(AppUpdateUiState.Idle)
    val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

    private var hasCheckedForUpdate = false

    fun checkForUpdates(currentVersionName: String) {
        if (hasCheckedForUpdate) return
        hasCheckedForUpdate = true

        viewModelScope.launch {
            _uiState.value = AppUpdateUiState.Checking
            val result = checkAppUpdateUseCase(currentVersionName)

            result.onSuccess { info ->
                if (info.isUpdateAvailable) {
                    _uiState.value = AppUpdateUiState.UpdateAvailable(info)
                } else {
                    _uiState.value = AppUpdateUiState.Idle
                }
            }.onFailure {
                // Silently ignore check errors or leave Idle so it doesn't disturb user
                _uiState.value = AppUpdateUiState.Idle
            }
        }
    }

    fun startDownloadAndInstall(context: Context, updateInfo: AppUpdateInfo) {
        viewModelScope.launch {
            _uiState.value = AppUpdateUiState.Downloading(progress = 0f, updateInfo = updateInfo)

            val downloadResult = ApkInstaller.downloadApk(
                context = context,
                apkUrl = updateInfo.apkDownloadUrl,
                onProgress = { progress ->
                    _uiState.value = AppUpdateUiState.Downloading(progress = progress, updateInfo = updateInfo)
                }
            )

            downloadResult.onSuccess { apkFile ->
                _uiState.value = AppUpdateUiState.ReadyToInstall(apkFile)
                val installResult = ApkInstaller.installApk(context, apkFile)
                if (installResult.isFailure) {
                    val msg = installResult.exceptionOrNull()?.message ?: "Error al abrir instalador"
                    _uiState.value = AppUpdateUiState.Error(msg, updateInfo)
                }
            }.onFailure { error ->
                val errorMsg = error.message ?: "Falló la descarga del APK"
                _uiState.value = AppUpdateUiState.Error(errorMsg, updateInfo)
            }
        }
    }

    fun dismissDialog() {
        _uiState.value = AppUpdateUiState.Idle
    }
}
