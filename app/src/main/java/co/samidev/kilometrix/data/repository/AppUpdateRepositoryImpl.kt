package co.samidev.kilometrix.data.repository

import co.samidev.kilometrix.domain.model.AppUpdateInfo
import co.samidev.kilometrix.domain.repository.AppUpdateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateRepositoryImpl @Inject constructor() : AppUpdateRepository {

    override suspend fun checkForUpdate(currentVersionName: String): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/SamiGamin/kilometros/releases/latest")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "KiloMetrix-Android-App")
                connectTimeout = 10000
                readTimeout = 10000
            }

            if (connection.responseCode != 200) {
                return@withContext Result.failure(Exception("GitHub API devolvió código HTTP ${connection.responseCode}"))
            }

            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)

            val rawTagName = json.optString("tag_name", "")
            val latestVersion = rawTagName.removePrefix("v").removePrefix("V").trim()
            val cleanCurrentVersion = currentVersionName.removePrefix("v").removePrefix("V").trim()

            val releaseNotes = json.optString("body", "Sin detalles del cambio.").trim()

            var apkUrl = ""
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val downloadUrl = asset.optString("browser_download_url", "")
                    val assetName = asset.optString("name", "")
                    if (downloadUrl.endsWith(".apk", ignoreCase = true) || assetName.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = downloadUrl
                        break
                    }
                }
            }

            val isNewer = isVersionHigher(cleanCurrentVersion, rawTagName.removePrefix("v").removePrefix("V").trim())

            android.util.Log.d("AppUpdate", "Current version: '$cleanCurrentVersion', Latest tag: '$rawTagName', IsNewer: $isNewer, ApkUrl: '$apkUrl'")

            Result.success(
                AppUpdateInfo(
                    isUpdateAvailable = isNewer && apkUrl.isNotBlank(),
                    currentVersion = cleanCurrentVersion,
                    latestVersion = latestVersion,
                    releaseNotes = releaseNotes,
                    apkDownloadUrl = apkUrl
                )
            )
        } catch (e: Exception) {
            android.util.Log.e("AppUpdate", "Error checking update", e)
            Result.failure(e)
        }
    }

    private fun isVersionHigher(current: String, latest: String): Boolean {
        if (current.isBlank() || latest.isBlank()) return false

        val currentBase = current.split("-").first().trim()
        val latestBase = latest.split("-").first().trim()

        val currentParts = currentBase.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = latestBase.split(".").mapNotNull { it.toIntOrNull() }

        val maxLength = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until maxLength) {
            val c = currentParts.getOrElse(i) { 0 }
            val l = latestParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (c > l) return false
        }

        // If base version numbers are equal (e.g. 1.0.2 == 1.0.2), check build numbers (e.g. build7 vs build0)
        val buildRegex = "build(\\d+)".toRegex(RegexOption.IGNORE_CASE)
        val currentBuild = buildRegex.find(current)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val latestBuild = buildRegex.find(latest)?.groupValues?.get(1)?.toIntOrNull() ?: 0

        if (latestBuild > currentBuild) return true

        return false
    }
}
