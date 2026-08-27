package co.samidev.kilometrix.domain.model

enum class OilStatus {
    OK,
    SOON,
    EXPIRED
}

data class Vehicle(
    val id: String = "",
    val type: String = "PARTICULAR", // PARTICULAR, MOTO, VAN, TAXI
    val nickname: String = "",
    val brand: String = "",
    val model: String = "",
    val year: Int = 0,
    val plate: String = "",
    val fuel: String = "GASOLINE", // GASOLINE, DIESEL, GLP, GNV, ELECTRIC
    val odometer: Int = 0,
    val soatExpiry: String? = null,
    val tecnomecExpiry: String? = null,
    val seguroExpiry: String? = null,
    val extinguisherExpiry: String? = null,
    val soatEnabled: Boolean = false,
    val tecnomecEnabled: Boolean = false,
    val seguroEnabled: Boolean = false,
    val extinguisherEnabled: Boolean = false,
    val lastOilChangeKm: Int? = null,
    val oilIntervalKm: Int = 10000
) {
    val nextOilChangeKm: Int
        get() = (lastOilChangeKm ?: 0) + oilIntervalKm

    val remainingOilKm: Int
        get() = if (lastOilChangeKm != null) nextOilChangeKm - odometer else oilIntervalKm

    val oilStatus: OilStatus
        get() = when {
            lastOilChangeKm == null -> OilStatus.OK
            remainingOilKm < 0 -> OilStatus.EXPIRED
            remainingOilKm <= 1000 -> OilStatus.SOON
            else -> OilStatus.OK
        }
}
