package co.samidev.kilometrix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.samidev.kilometrix.ui.theme.Primary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Success(val message: String) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel : ViewModel() {
    private val repository = AuthRepository()

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState = _uiState.asStateFlow()

    // Función para registrar
    fun registerUser(name: String, email: String, password: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repository.registerUser(name, email, password)
            
            _uiState.value = if (result.isSuccess) {
                AuthUiState.Success("User registered!")
            } else {
                AuthUiState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
            
            // Opcional: Puedes pasar el userId o el email al siguiente paso
            onResult(if (result.isSuccess) result.getOrNull() else null)
        }
    }

    // Función para verificar
    fun verifyOtp(email: String, otp: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repository.verifyOtp(email, otp)
            
            _uiState.value = if (result.isSuccess) {
                AuthUiState.Success("Verified!")
            } else {
                AuthUiState.Error("Invalid OTP")
            }
            
            onResult(result.getOrNull() ?: false)
        }
    }
}