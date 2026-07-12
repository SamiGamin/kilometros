package co.samidev.kilometrix.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.samidev.kilometrix.domain.model.UserProfile
import co.samidev.kilometrix.domain.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

sealed interface ProfileUiState {
    object Idle : ProfileUiState
    object Loading : ProfileUiState
    object SuccessSignOut : ProfileUiState
    object SuccessDeleteAccount : ProfileUiState
    data class Error(val message: String) : ProfileUiState
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    userRepository: UserRepository,
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) : ViewModel() {

    val userProfile: StateFlow<UserProfile?> = userRepository.getUserProfile()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Idle)
    val uiState: StateFlow<ProfileUiState> = _uiState

    fun signOut(onComplete: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            try {
                auth.signOut()
                _uiState.value = ProfileUiState.SuccessSignOut
                onComplete()
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error(e.message ?: "Error al cerrar sesión")
            }
        }
    }

    fun deleteAccount(onComplete: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            try {
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    val uid = currentUser.uid
                    // 1. Eliminar datos de Firestore (Vehículos en subcolección)
                    val vehicles = db.collection("users").document(uid).collection("vehicles").get().await()
                    for (doc in vehicles.documents) {
                        doc.reference.delete().await()
                    }
                    // 2. Eliminar documento de usuario en Firestore
                    db.collection("users").document(uid).delete().await()

                    // 3. Eliminar usuario en Firebase Auth
                    currentUser.delete().await()

                    _uiState.value = ProfileUiState.SuccessDeleteAccount
                    onComplete()
                } else {
                    _uiState.value = ProfileUiState.Error("No hay sesión activa para eliminar")
                }
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error(
                    "Por seguridad, eliminar tu cuenta requiere iniciar sesión de nuevo recientemente. Cierra sesión e ingresa de nuevo para reintentar."
                )
            }
        }
    }
}
