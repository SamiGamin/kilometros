package co.samidev.kilometrix.domain.model

data class AppUpdateInfo(
    val isUpdateAvailable: Boolean = false,
    val currentVersion: String = "",
    val latestVersion: String = "",
    val releaseNotes: String = "",
    val apkDownloadUrl: String = ""
)
