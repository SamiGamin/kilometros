package co.samidev.kilometrix.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.samidev.kilometrix.domain.repository.UserRepository
import co.samidev.kilometrix.domain.usecase.GetDriverStatsUseCase
import co.samidev.kilometrix.domain.usecase.UpdateUserProfileUseCase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

private data class ProfileInternalState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isEditSheetOpen: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    userRepository: UserRepository,
    getDriverStatsUseCase: GetDriverStatsUseCase,
    private val updateUserProfileUseCase: UpdateUserProfileUseCase,
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) : ViewModel() {

    private val _internalState = MutableStateFlow(ProfileInternalState())

    private val _eventChannel = Channel<ProfileUiEvent>(Channel.BUFFERED)
    val uiEvent: Flow<ProfileUiEvent> = _eventChannel.receiveAsFlow()

    val uiState: StateFlow<ProfileScreenUiState> = combine(
        userRepository.getUserProfile(),
        getDriverStatsUseCase(),
        _internalState
    ) { profile, stats, internal ->
        ProfileScreenUiState(
            profile = profile,
            stats = stats,
            isLoading = internal.isLoading,
            isSaving = internal.isSaving,
            isEditSheetOpen = internal.isEditSheetOpen
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProfileScreenUiState()
    )

    fun openEditSheet() {
        _internalState.update { it.copy(isEditSheetOpen = true) }
    }

    fun closeEditSheet() {
        _internalState.update { it.copy(isEditSheetOpen = false) }
    }

    fun updateProfile(
        name: String,
        city: String,
        platforms: List<String>,
        maintenanceReservePercent: Int
    ) {
        viewModelScope.launch {
            _internalState.update { it.copy(isSaving = true) }
            val result = updateUserProfileUseCase(
                name = name,
                city = city,
                platforms = platforms,
                maintenanceReservePercent = maintenanceReservePercent
            )

            _internalState.update { it.copy(isSaving = false) }

            if (result.isSuccess) {
                _internalState.update { it.copy(isEditSheetOpen = false) }
                _eventChannel.send(ProfileUiEvent.ShowSnackbar("Perfil actualizado correctamente"))
                _eventChannel.send(ProfileUiEvent.CloseEditSheet)
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Error al actualizar el perfil"
                _eventChannel.send(ProfileUiEvent.ShowSnackbar(errorMsg))
            }
        }
    }

    fun updateMaintenanceReserve(percent: Int) {
        val currentProfile = uiState.value.profile ?: return
        updateProfile(
            name = currentProfile.name,
            city = currentProfile.city,
            platforms = currentProfile.platforms,
            maintenanceReservePercent = percent
        )
    }

    fun signOut(onComplete: () -> Unit) {
        viewModelScope.launch {
            _internalState.update { it.copy(isLoading = true) }
            try {
                auth.signOut()
                _internalState.update { it.copy(isLoading = false) }
                _eventChannel.send(ProfileUiEvent.SignedOut)
                onComplete()
            } catch (e: Exception) {
                _internalState.update { it.copy(isLoading = false) }
                _eventChannel.send(ProfileUiEvent.ShowSnackbar(e.message ?: "Error al cerrar sesión"))
            }
        }
    }

    fun deleteAccount(onComplete: () -> Unit) {
        viewModelScope.launch {
            _internalState.update { it.copy(isLoading = true) }
            try {
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    val uid = currentUser.uid
                    // 1. Delete vehicles in user's subcollection
                    val vehicles = db.collection("users").document(uid).collection("vehicles").get().await()
                    for (doc in vehicles.documents) {
                        doc.reference.delete().await()
                    }
                    // 2. Delete user profile document
                    db.collection("users").document(uid).delete().await()

                    // 3. Delete Firebase Auth user
                    currentUser.delete().await()

                    _internalState.update { it.copy(isLoading = false) }
                    _eventChannel.send(ProfileUiEvent.AccountDeleted)
                    onComplete()
                } else {
                    _internalState.update { it.copy(isLoading = false) }
                    _eventChannel.send(ProfileUiEvent.ShowSnackbar("No hay sesión activa para eliminar"))
                }
            } catch (e: Exception) {
                _internalState.update { it.copy(isLoading = false) }
                _eventChannel.send(
                    ProfileUiEvent.ShowSnackbar(
                        "Por seguridad, eliminar tu cuenta requiere haber iniciado sesión recientemente. Cierra sesión e ingresa de nuevo."
                    )
                )
            }
        }
    }
}
