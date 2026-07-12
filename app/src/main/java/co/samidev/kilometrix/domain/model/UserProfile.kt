package co.samidev.kilometrix.domain.model

data class UserProfile(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val city: String = "",
    val platforms: List<String> = emptyList(),
    val isVerified: Boolean = false
)
