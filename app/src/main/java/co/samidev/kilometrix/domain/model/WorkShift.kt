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
    val pausedDurationMs: Long = 0L,        // Tiempo total acumulado en pausa (ms)
    val status: ShiftStatus = ShiftStatus.ACTIVE,
    val pauseStartTime: Long? = null,       // Tiempo en que se pausó la última vez
    val earnings: List<ShiftEarning> = emptyList(),
    val type: ShiftType = ShiftType.WORK
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
