package co.samidev.kilometrix.domain.usecase

import co.samidev.kilometrix.core.util.Resource
import co.samidev.kilometrix.domain.model.CityData
import co.samidev.kilometrix.domain.model.PicoPlacaResponse
import co.samidev.kilometrix.domain.model.PicoPlacaStatus
import co.samidev.kilometrix.domain.model.Restriction
import co.samidev.kilometrix.domain.model.Vehicle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

class CalculatePicoYPlacaUseCase @Inject constructor() {
    operator fun invoke(
        userCity: String?,
        activeVehicle: Vehicle?,
        picoResource: Resource<PicoPlacaResponse>
    ): PicoPlacaStatus {
        if (userCity.isNullOrBlank()) {
            return PicoPlacaStatus(
                statusText = "Sin ciudad registrada",
                subtext = "Completa tu perfil para ver restricciones",
                isRestrictedNow = false,
                hasData = false
            )
        }
        if (activeVehicle == null) {
            return PicoPlacaStatus(
                statusText = "Sin vehículo registrado",
                subtext = "Agrega un vehículo para calcular Pico y Placa",
                isRestrictedNow = false,
                hasData = false
            )
        }
        if (picoResource !is Resource.Success) {
            return PicoPlacaStatus(
                statusText = "Cargando Pico y Placa...",
                subtext = "Actualizando restricciones vigentes",
                isRestrictedNow = false,
                hasData = false
            )
        }

        val data = picoResource.data
        val cities = data.cities
        val matchedCity = cities.find {
            it.name.contains(userCity, ignoreCase = true) ||
            userCity.contains(it.name, ignoreCase = true) ||
            it.id.equals(userCity, ignoreCase = true)
        }

        if (matchedCity == null) {
            return PicoPlacaStatus(
                statusText = "Ciudad no soportada",
                subtext = "Pico y Placa no está configurado para $userCity",
                isRestrictedNow = false,
                hasData = false
            )
        }

        val vehicleType = activeVehicle.type
        val plate = activeVehicle.plate
        if (matchedCity.id == "bogota" && (vehicleType == "MOTO" || vehicleType == "TAXI")) {
            if (vehicleType == "MOTO") {
                return PicoPlacaStatus(
                    statusText = "¡Puedes circular libremente!",
                    subtext = "Las motos no tienen restricción en Bogotá D.C.",
                    isRestrictedNow = false,
                    hasData = true
                )
            }
        }

        val activeRestriction = matchedCity.restrictions.firstOrNull {
            it.vehicleType.equals(vehicleType, ignoreCase = true)
        }

        if (activeRestriction == null) {
            return PicoPlacaStatus(
                statusText = "Sin restricciones hoy para la placa ${plate.uppercase()}",
                subtext = "Tu vehículo no tiene restricciones en ${matchedCity.name} hoy",
                isRestrictedNow = false,
                hasData = true
            )
        }

        val todayCal = Calendar.getInstance()
        val dateString = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(todayCal.time)
        val isHoliday = data.holidays.contains(dateString)
        val dayOfWeek = todayCal.get(Calendar.DAY_OF_WEEK)

        if (isHoliday) {
            return PicoPlacaStatus(
                statusText = "Sin restricciones hoy para la placa ${plate.uppercase()}",
                subtext = "No aplica Pico y Placa por día festivo en ${matchedCity.name}",
                isRestrictedNow = false,
                hasData = true
            )
        }

        if (dayOfWeek == Calendar.SUNDAY) {
            return PicoPlacaStatus(
                statusText = "Sin restricciones hoy para la placa ${plate.uppercase()}",
                subtext = "No aplica Pico y Placa los domingos",
                isRestrictedNow = false,
                hasData = true
            )
        }

        val isRestrictedDay = checkIfRestricted(todayCal, plate, vehicleType, matchedCity, activeRestriction)
        if (!isRestrictedDay) {
            val endStr = if (matchedCity.id == "medellin" && vehicleType == "MOTO") "inicia" else "termina"
            val digit = if (matchedCity.id == "medellin" && vehicleType == "MOTO") getFirstDigitOfPlate(plate) else getLastDigitOfPlate(plate)
            return PicoPlacaStatus(
                statusText = "Sin restricciones hoy para la placa ${plate.uppercase()}",
                subtext = "Tu placa $endStr en $digit y no tiene restricción hoy en ${matchedCity.name}",
                isRestrictedNow = false,
                hasData = true
            )
        }

        val currentHour = todayCal.get(Calendar.HOUR_OF_DAY)
        val currentMinute = todayCal.get(Calendar.MINUTE)
        val currentMinutesSinceMidnight = currentHour * 60 + currentMinute

        val inRestrictionTime = checkRestrictionTime(currentMinutesSinceMidnight, activeRestriction.schedule)
        val formattedSchedule = activeRestriction.schedule.split("y")
            .map { convert24hRangeTo12h(it.trim()) }.joinToString(" y ")

        return if (inRestrictionTime) {
            PicoPlacaStatus(
                statusText = "⚠️ Actualmente restringido",
                subtext = "Aplica hoy en ${matchedCity.name} de $formattedSchedule",
                isRestrictedNow = true,
                hasData = true
            )
        } else {
            PicoPlacaStatus(
                statusText = "Fuera de horario de restricción",
                subtext = "Hoy aplica de $formattedSchedule. ¡Puedes circular libremente ahora!",
                isRestrictedNow = false,
                hasData = true
            )
        }
    }

    private fun checkIfRestricted(
        calendar: Calendar,
        plate: String,
        vehicleType: String,
        city: CityData,
        restriction: Restriction
    ): Boolean {
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val calendarDay = calendar.get(Calendar.DAY_OF_MONTH)

        val dayName = when (dayOfWeek) {
            Calendar.MONDAY -> "Lunes"
            Calendar.TUESDAY -> "Martes"
            Calendar.WEDNESDAY -> "Miércoles"
            Calendar.THURSDAY -> "Jueves"
            Calendar.FRIDAY -> "Viernes"
            Calendar.SATURDAY -> "Sábado"
            Calendar.SUNDAY -> "Domingo"
            else -> ""
        }

        if (!restriction.days.contains(dayName)) {
            return false
        }

        return when (restriction.algorithm) {
            "BOGOTA_PARITY" -> {
                val lastDigit = getLastDigitOfPlate(plate)
                val isEvenDay = calendarDay % 2 == 0
                if (isEvenDay) {
                    lastDigit in 1..5
                } else {
                    lastDigit in 6..9 || lastDigit == 0
                }
            }
            "WEEKDAY_MAP" -> {
                val weekdayKey = when (dayOfWeek) {
                    Calendar.MONDAY -> "1"
                    Calendar.TUESDAY -> "2"
                    Calendar.WEDNESDAY -> "3"
                    Calendar.THURSDAY -> "4"
                    Calendar.FRIDAY -> "5"
                    Calendar.SATURDAY -> "6"
                    else -> ""
                }
                val restrictedDigits = restriction.weekdayRules?.get(weekdayKey) ?: emptyList()
                val digitToCheck = if (city.id == "medellin" && vehicleType == "MOTO") {
                    getFirstDigitOfPlate(plate)
                } else {
                    getLastDigitOfPlate(plate)
                }
                restrictedDigits.contains(digitToCheck)
            }
            else -> false
        }
    }

    private fun checkRestrictionTime(currentMinutesSinceMidnight: Int, schedule: String): Boolean {
        val parts = schedule.split("y").map { it.trim() }
        for (part in parts) {
            val range = part.split("-").map { it.trim() }
            if (range.size == 2) {
                val startMinutes = parseTimeString(range[0])
                val endMinutes = parseTimeString(range[1])
                if (currentMinutesSinceMidnight in startMinutes..endMinutes) {
                    return true
                }
            }
        }
        return false
    }

    private fun parseTimeString(timeStr: String): Int {
        val clean = timeStr.trim()
        val parts = clean.split(":")
        if (parts.size >= 2) {
            val hours = parts[0].toIntOrNull() ?: 0
            val minutes = parts[1].toIntOrNull() ?: 0
            return hours * 60 + minutes
        }
        return 0
    }

    private fun getFirstDigitOfPlate(plate: String): Int {
        val numericPart = plate.filter { it.isDigit() }
        return if (numericPart.isNotEmpty()) numericPart.first().toString().toInt() else 0
    }

    private fun getLastDigitOfPlate(plate: String): Int {
        val numericPart = plate.filter { it.isDigit() }
        return if (numericPart.isNotEmpty()) numericPart.last().toString().toInt() else 0
    }

    private fun convert24hRangeTo12h(range: String): String {
        val parts = range.split("-").map { it.trim() }
        if (parts.size == 2) {
            val start12 = formatTo12h(parts[0])
            val end12 = formatTo12h(parts[1])
            return "$start12 - $end12"
        }
        return range
    }

    private fun formatTo12h(timeStr: String): String {
        try {
            val parts = timeStr.split(":")
            if (parts.size >= 2) {
                val hours = parts[0].toIntOrNull() ?: return timeStr
                val minutes = parts[1].toIntOrNull() ?: 0
                val amPm = if (hours >= 12) " pm" else " am"
                var hours12 = hours % 12
                if (hours12 == 0) hours12 = 12
                val minutesStr = String.format("%02d", minutes)
                return "$hours12:$minutesStr$amPm"
            }
        } catch (e: Exception) {
            // Fallback
        }
        return timeStr
    }
}
