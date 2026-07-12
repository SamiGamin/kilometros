package co.samidev.kilometrix.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
                AuthUiState.Success("¡Usuario registrado exitosamente!")
            } else {
                val rawError = result.exceptionOrNull()?.message
                val translatedError = when {
                    rawError == null -> "Error al registrar el usuario."
                    rawError.contains("already in use", ignoreCase = true) ->
                        "Este correo electrónico ya está registrado con otra cuenta."
                    rawError.contains("badly formatted", ignoreCase = true) || rawError.contains("invalid email", ignoreCase = true) ->
                        "El correo electrónico ingresado no es válido."
                    rawError.contains("weak password", ignoreCase = true) ->
                        "La contraseña ingresada es demasiado débil."
                    rawError.contains("network error", ignoreCase = true) || rawError.contains("network-request-failed", ignoreCase = true) ->
                        "Error de red. Por favor verifica tu conexión a internet."
                    else -> rawError
                }
                AuthUiState.Error(translatedError)
            }

            // Opcional: Puedes pasar el userId o el email al siguiente paso
            onResult(if (result.isSuccess) result.getOrNull() else null)
        }
    }

    // Función para verificar si el correo ya fue confirmado en el link
    fun checkEmailVerification(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repository.checkEmailVerification()

            if (result.isSuccess) {
                val isVerified = result.getOrNull() ?: false
                if (isVerified) {
                    _uiState.value = AuthUiState.Success("¡Correo verificado con éxito!")
                    onResult(true)
                } else {
                    _uiState.value = AuthUiState.Error("El correo aún no ha sido verificado. Por favor revisa tu bandeja de entrada.")
                    onResult(false)
                }
            } else {
                _uiState.value = AuthUiState.Error(result.exceptionOrNull()?.message ?: "Error al verificar")
                onResult(false)
            }
        }
    }

    // Función para reenviar correo de confirmación
    fun resendVerificationEmail(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repository.resendVerificationEmail()

            if (result.isSuccess) {
                _uiState.value = AuthUiState.Success("¡Enlace reenviado! Revisa tu correo.")
                onResult(true)
            } else {
                _uiState.value = AuthUiState.Error(result.exceptionOrNull()?.message ?: "Error al reenviar")
                onResult(false)
            }
        }
    }

    // Función para iniciar sesión
    fun loginUser(email: String, password: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repository.loginUser(email, password)

            _uiState.value = if (result.isSuccess) {
                AuthUiState.Success("Sesión iniciada exitosamente")
            } else {
                val rawError = result.exceptionOrNull()?.message
                val translatedError = when {
                    rawError == null -> "Error al iniciar sesión."
                    rawError.contains("badly formatted", ignoreCase = true) || rawError.contains("invalid email", ignoreCase = true) || rawError.contains("invalid-email", ignoreCase = true) ->
                        "El formato del correo electrónico es inválido."
                    rawError.contains("credential", ignoreCase = true) || rawError.contains("incorrect", ignoreCase = true) || rawError.contains("wrong", ignoreCase = true) || rawError.contains("no user record", ignoreCase = true) || rawError.contains("user-not-found", ignoreCase = true) || rawError.contains("invalid", ignoreCase = true) ->
                        "Correo electrónico o contraseña incorrectos."
                    rawError.contains("network error", ignoreCase = true) || rawError.contains("network-request-failed", ignoreCase = true) ->
                        "Error de red. Verifica tu conexión a internet."
                    else -> rawError
                }
                AuthUiState.Error(translatedError)
            }

            onResult(if (result.isSuccess) result.getOrNull() else null)
        }
    }

    // Función para recuperación de contraseña
    fun sendPasswordResetEmail(email: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repository.sendPasswordResetEmail(email)

            _uiState.value = if (result.isSuccess) {
                AuthUiState.Success("Correo de recuperación enviado")
            } else {
                val rawError = result.exceptionOrNull()?.message
                val translatedError = when {
                    rawError == null -> "Error al enviar el correo de recuperación."
                    rawError.contains("badly formatted", ignoreCase = true) || rawError.contains("invalid email", ignoreCase = true) || rawError.contains("invalid-email", ignoreCase = true) ->
                        "El correo electrónico ingresado no es válido."
                    rawError.contains("no user record", ignoreCase = true) || rawError.contains("user-not-found", ignoreCase = true) || rawError.contains("credential", ignoreCase = true) || rawError.contains("incorrect", ignoreCase = true) || rawError.contains("wrong", ignoreCase = true) || rawError.contains("invalid", ignoreCase = true) ->
                        "No existe ningún usuario registrado con este correo electrónico."
                    rawError.contains("network error", ignoreCase = true) || rawError.contains("network-request-failed", ignoreCase = true) ->
                        "Error de red. Verifica tu conexión a internet."
                    else -> rawError
                }
                AuthUiState.Error(translatedError)
            }

            onResult(result.isSuccess)
        }
    }
}
