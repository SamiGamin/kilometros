package co.samidev.kilometrix.domain.model

data class Vehicle(
    val id: String = "",
    val type: String = "PARTICULAR", // PARTICULAR, MOTO, VAN
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
    val soatEnabled: Boolean = false,
    val tecnomecEnabled: Boolean = false,
    val seguroEnabled: Boolean = false
)
