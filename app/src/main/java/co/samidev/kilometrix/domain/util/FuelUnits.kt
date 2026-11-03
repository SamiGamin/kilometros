package co.samidev.kilometrix.domain.util

/**
 * Conversiones canónicas de unidades de combustible para Kilometrix.
 *
 * Convención arquitectónica (Punto 5):
 * - Todo el paquete `:obd` (`VehicleTelemetry`, `TripTelemetryAccumulator`, `WorkShift.obdFuelLitersRaw`,
 *   `CalculateObdCalibrationUseCase`) opera **estrictamente en Litros (L) y COP/L**.
 * - El dominio de surtidor (`FuelDetails.gallons`) y las tarjetas de UI convierten a Galones US
 *   (`1 gal = 3.785411784 L`) únicamente en la frontera de presentación o resumen.
 */
object FuelUnits {
    const val LITERS_PER_US_GALLON = 3.785411784

    fun litersToGallons(liters: Double): Double =
        if (liters.isFinite() && liters > 0.0) liters / LITERS_PER_US_GALLON else 0.0

    fun gallonsToLiters(gallons: Double): Double =
        if (gallons.isFinite() && gallons > 0.0) gallons * LITERS_PER_US_GALLON else 0.0

    fun pricePerGallonToPricePerLiter(pricePerGallonCop: Double): Double =
        if (pricePerGallonCop.isFinite() && pricePerGallonCop > 0.0) {
            pricePerGallonCop / LITERS_PER_US_GALLON
        } else 0.0

    fun pricePerLiterToPricePerGallon(pricePerLiterCop: Double): Double =
        if (pricePerLiterCop.isFinite() && pricePerLiterCop > 0.0) {
            pricePerLiterCop * LITERS_PER_US_GALLON
        } else 0.0

    fun kmPerLiterToKmPerGallon(kmPerLiter: Double): Double =
        if (kmPerLiter.isFinite() && kmPerLiter > 0.0) kmPerLiter * LITERS_PER_US_GALLON else 0.0

    fun kmPerGallonToKmPerLiter(kmPerGallon: Double): Double =
        if (kmPerGallon.isFinite() && kmPerGallon > 0.0) kmPerGallon / LITERS_PER_US_GALLON else 0.0
}
