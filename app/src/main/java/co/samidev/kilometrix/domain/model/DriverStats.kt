package co.samidev.kilometrix.domain.model

data class DriverStats(
    val totalShifts: Int = 0,
    val totalGrossEarnings: Double = 0.0,
    val activeVehicleName: String = "Sin vehículo"
)
