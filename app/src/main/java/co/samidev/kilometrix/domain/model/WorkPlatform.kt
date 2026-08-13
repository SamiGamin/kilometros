package co.samidev.kilometrix.domain.model

enum class WorkPlatform(
    val id: String,
    val displayName: String,
    val iconEmoji: String,
    val colorHex: String,
    val alternateIds: List<String> = emptyList()
) {
    UBER("Uber", "Uber", "🖤", "#000000"),
    DIDI("Didi", "DiDi", "🟠", "#FF6900"),
    INDRIVE("InDrive", "InDrive", "🟢", "#00B050"),
    CABIFY("Cabify", "Cabify", "🟣", "#7C3AED"),
    RAPPI("Rappi", "Rappi", "🔴", "#FF441B"),
    YANGO("Yango", "Yango Pro", "🟡", "#FFD600", listOf("Yango Pro")),
    PICAP("Picap", "Picap", "🏍️", "#E11D48"),
    TAXI("Taxi", "Taxi", "🚖", "#FFD600"),
    PARTICULAR("Particular", "Particular", "🚘", "#6B7280");

    companion object {
        fun fromId(id: String): WorkPlatform {
            return entries.firstOrNull { platform ->
                platform.id.equals(id, ignoreCase = true) || 
                platform.displayName.equals(id, ignoreCase = true) || 
                platform.name.equals(id, ignoreCase = true) ||
                platform.alternateIds.any { it.equals(id, ignoreCase = true) }
            } ?: PARTICULAR
        }
    }
}
