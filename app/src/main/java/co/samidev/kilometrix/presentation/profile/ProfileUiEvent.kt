package co.samidev.kilometrix.presentation.profile

sealed interface ProfileUiEvent {
    data class ShowSnackbar(val message: String) : ProfileUiEvent
    object CloseEditSheet : ProfileUiEvent
    object SignedOut : ProfileUiEvent
    object AccountDeleted : ProfileUiEvent
}
