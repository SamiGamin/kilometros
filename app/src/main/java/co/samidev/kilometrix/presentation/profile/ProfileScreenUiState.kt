package co.samidev.kilometrix.presentation.profile

import co.samidev.kilometrix.domain.model.DriverStats
import co.samidev.kilometrix.domain.model.UserProfile

data class ProfileScreenUiState(
    val profile: UserProfile? = null,
    val stats: DriverStats = DriverStats(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isEditSheetOpen: Boolean = false
)
