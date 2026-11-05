package co.samidev.kilometrix.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AltitudeProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Devuelve altitud en metros, o null si:
     * - No hay permiso ACCESS_FINE_LOCATION
     * - No hay GPS habilitado
     * - El último fix es > 5 min viejo (evita usar caché obsoleto)
     */
    fun readAltitudeMeters(): Double? {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return null

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) return null

        val lastGps = try {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } catch (_: SecurityException) {
            return null
        } ?: return null

        val ageMs = System.currentTimeMillis() - lastGps.time
        if (ageMs > 5 * 60 * 1000L) return null

        return lastGps.altitude.takeIf { it.isFinite() && it > -500.0 && it < 9000.0 }
    }
}
