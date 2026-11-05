package co.samidev.kilometrix.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class AltitudeProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun readAltitudeMeters(timeoutMs: Long = 4000L): Double? {
        if (!hasPermission()) return null

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) return null

        // 1. Intentar caché primero (rápido)
        val cached = try {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } catch (_: SecurityException) { null }

        if (cached != null && isFresh(cached) && cached.altitude.isValidAltitude()) {
            return cached.altitude
        }

        // 2. Solicitar fix activo con timeout
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val listener = object : android.location.LocationListener {
                    override fun onLocationChanged(location: Location) {
                        lm.removeUpdates(this)
                        val alt = location.altitude
                        cont.resume(if (alt.isValidAltitude()) alt else null)
                    }
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {
                        lm.removeUpdates(this)
                        cont.resume(null)
                    }
                    @Deprecated("Deprecated in API 29")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                }
                try {
                    lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, Looper.getMainLooper())
                } catch (_: SecurityException) {
                    cont.resume(null)
                }
                cont.invokeOnCancellation { lm.removeUpdates(listener) }
            }
        }
    }

    private fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isFresh(location: Location): Boolean {
        val ageMs = System.currentTimeMillis() - location.time
        return ageMs <= 5 * 60 * 1000L
    }

    private fun Double.isValidAltitude(): Boolean =
        this.isFinite() && this > -500.0 && this < 9000.0

    fun hasLocationPermission(): Boolean = hasPermission()
}
