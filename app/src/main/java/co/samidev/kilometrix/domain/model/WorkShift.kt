package co.samidev.kilometrix.domain.model

import co.samidev.kilometrix.R

/**
 * Representa un turno de conducción activo o histórico.
 * Un turno empieza cuando el conductor sube al coche (odómetro inicial)
 * y termina cuando ingresa el odómetro final.
 */
data class WorkShift(
    val id: String = "",
    val vehicleId: String = "",
    val startTime: Long = 0L,
    val endTime: Long? = null,
    val initialOdometer: Int = 0,
    val finalOdometer: Int? = null,
    val initialOdometerPrecise: Double? = null,
    val finalOdometerPrecise: Double? = null,
    val pausedDurationMs: Long = 0L,        // Tiempo total acumulado en pausa (ms)
    val status: ShiftStatus = ShiftStatus.ACTIVE,
    val pauseStartTime: Long? = null,       // Tiempo en que se pausó la última vez
    val earnings: List<ShiftEarning> = emptyList(),
    val type: ShiftType = ShiftType.WORK,
    // ── Telemetría y Cobertura OBD-II en Litros/Km canónicos (Puntos 2, 5, 6, 10) ──
    val obdDistanceKm: Double = 0.0,        // Distancia medida por integración OBD-II (010D)
    val obdFuelLitersRaw: Double = 0.0,     // Litros crudos medidos por Speed-Density/MAF OBD-II
    val obdCoveragePct: Double = 0.0,       // % del tiempo activo del turno cubierto sin gaps (>90% requerido)
    val obdMaxGapSec: Double = 0.0,         // Mayor gap sin muestras durante el turno (<=60s requerido)
    val obdGapCount: Int = 0,               // Número de interrupciones (dt > 2.0s)
    val obdValid: Boolean = false,          // true si cumple regla estricta de cobertura
    val obdConnectedAtStart: Boolean = false,
    val obdConnectedAtEnd: Boolean = false,
    val obdSampleCount: Int = 0,            // Total de muestras integradas en el turno
    val obdMeanDtMs: Double = 0.0,          // Intervalo promedio entre muestras (ms)
    val obdIdleSec: Double = 0.0,           // Segundos acumulados en régimen de ralentí
    val obdCutoffSec: Double = 0.0,         // Segundos acumulados en corte de inyección (DFCO)
    val startAltitudeMeters: Double? = null,
    val netElevationChangeMeters: Int? = null  // Δaltura cierre − inicio. null = sin datos
)

enum class ShiftStatus { ACTIVE, PAUSED, ENDED }

enum class ShiftType { WORK, PERSONAL }

/**
 * Registro de ganancia por app de transporte durante un turno.
 */
data class ShiftEarning(
    val id: String = java.util.UUID.randomUUID().toString(),
    val appName: String = "",
    val appEmoji: String = "💰",
    val amount: Double = 0.0,
    val registeredAt: Long = System.currentTimeMillis()
)

/**
 * Apps de transporte disponibles para registrar ganancias.
 */
data class TransportApp(
    val name: String,
    val emoji: String,
    val colorHex: String,  // e.g. "#000000"
    val drawableRes: Int? = null
)

val TRANSPORT_APPS = listOf(
    TransportApp("Uber",     "🖤", "#000000", R.drawable.uber),
    TransportApp("Didi",     "🟠", "#FF6900", R.drawable.didi),
    TransportApp("InDrive",  "🟢", "#00B050", R.drawable.indrive),
    TransportApp("Cabify",   "🟣", "#7C3AED", R.drawable.cabify),
    TransportApp("Rappi",    "🔴", "#FF441B", R.drawable.rappi),
    TransportApp("Yango",    "🟡", "#FFD600", R.drawable.yangopro),
    TransportApp("Taxi",     "🚖", "#FFD600", R.drawable.taxi),
    TransportApp("Efectivo", "💵", "#16A34A"),
    TransportApp("Otro",     "💳", "#6B7280")
)
