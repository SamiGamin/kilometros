package co.samidev.kilometrix.domain.model

import com.google.firebase.firestore.PropertyName

data class PicoPlacaResponse(
    @get:PropertyName("schema_version") @set:PropertyName("schema_version") var schemaVersion: String = "",
    @get:PropertyName("last_updated") @set:PropertyName("last_updated") var lastUpdated: String = "",
    @get:PropertyName("holidays") @set:PropertyName("holidays") var holidays: List<String> = emptyList(),
    @get:PropertyName("cities") @set:PropertyName("cities") var cities: List<CityData> = emptyList()
)

data class CityData(
    @get:PropertyName("id") @set:PropertyName("id") var id: String = "",
    @get:PropertyName("name") @set:PropertyName("name") var name: String = "",
    @get:PropertyName("state") @set:PropertyName("state") var state: String = "",
    @get:PropertyName("source_url") @set:PropertyName("source_url") var sourceUrl: String = "",
    @get:PropertyName("restrictions") @set:PropertyName("restrictions") var restrictions: List<Restriction> = emptyList()
)

data class Restriction(
    @get:PropertyName("vehicle_type") @set:PropertyName("vehicle_type") var vehicleType: String = "",
    @get:PropertyName("algorithm") @set:PropertyName("algorithm") var algorithm: String = "",
    @get:PropertyName("schedule") @set:PropertyName("schedule") var schedule: String = "",
    @get:PropertyName("description") @set:PropertyName("description") var description: String = "",
    @get:PropertyName("days") @set:PropertyName("days") var days: List<String> = emptyList(),
    @get:PropertyName("weekday_rules") @set:PropertyName("weekday_rules") var weekdayRules: Map<String, List<Int>>? = null
)
