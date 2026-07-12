package co.samidev.kilometrix.presentation.auth

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await

private const val TAG = "AuthRepository"

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // Registra al usuario, envía correo de verificación y guarda en Firestore
    suspend fun registerUser(name: String, email: String, password: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Iniciando registro de usuario para: $email")
                val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                val user = authResult.user
                val userId = user?.uid ?: ""
                Log.d(TAG, "Usuario creado en Auth exitosamente. UID: $userId")

                // Enviar correo de verificación nativo
                Log.d(TAG, "Enviando correo de verificación nativo...")
                user?.sendEmailVerification()?.await()
                Log.d(TAG, "Correo de verificación nativo enviado con éxito.")

                // Guardar en Firestore
                Log.d(TAG, "Guardando datos del usuario en Firestore...")
                val userData = mapOf(
                    "name" to name,
                    "email" to email,
                    "isVerified" to false
                )

                db.collection("users").document(userId).set(userData).await()
                Log.d(TAG, "Datos guardados en Firestore correctamente.")

                Result.success(userId)
            } catch (e: Exception) {
                Log.e(TAG, "Error durante el registro de usuario: ${e.message}", e)
                Result.failure(e)
            }
        }

    // Verifica si el correo ha sido confirmado
    suspend fun checkEmailVerification(): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Iniciando comprobación de verificación de correo...")
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    Log.d(TAG, "Usuario actual encontrado: ${currentUser.email}. Recargando perfil...")
                    currentUser.reload().await() // Recargar datos desde Firebase Auth
                    val isVerified = currentUser.isEmailVerified
                    Log.d(TAG, "Estado de verificación de correo: $isVerified")
                    
                    if (isVerified) {
                        Log.d(TAG, "El correo está verificado. Actualizando Firestore a isVerified=true...")
                        db.collection("users").document(currentUser.uid).update("isVerified", true).await()
                        Log.d(TAG, "Firestore actualizado exitosamente.")
                        Result.success(true)
                    } else {
                        Log.d(TAG, "El usuario aún no ha verificado su correo mediante el link.")
                        Result.success(false)
                    }
                } else {
                    Log.e(TAG, "No se pudo comprobar la verificación: No hay sesión activa.")
                    Result.failure(Exception("No active user session"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al comprobar verificación de correo: ${e.message}", e)
                Result.failure(e)
            }
        }

    // Reenvía el correo de verificación
    suspend fun resendVerificationEmail(): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Iniciando reenvío de correo de verificación...")
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    currentUser.sendEmailVerification().await()
                    Log.d(TAG, "Correo de verificación reenviado exitosamente a ${currentUser.email}")
                    Result.success(true)
                } else {
                    Log.e(TAG, "No se pudo reenviar: No hay sesión activa.")
                    Result.failure(Exception("No active user session"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al reenviar correo de verificación: ${e.message}", e)
                Result.failure(e)
            }
        }

    // Inicia sesión con correo y contraseña
    suspend fun loginUser(email: String, password: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Iniciando sesión para: $email")
                val authResult = auth.signInWithEmailAndPassword(email, password).await()
                val user = authResult.user
                val userId = user?.uid ?: ""
                Log.d(TAG, "Inicio de sesión exitoso. UID: $userId")
                Result.success(userId)
            } catch (e: Exception) {
                Log.e(TAG, "Error al iniciar sesión: ${e.message}", e)
                Result.failure(e)
            }
        }

    // Envía correo de recuperación de contraseña
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Verificando si el correo existe en Firestore: $email")
                val querySnapshot = db.collection("users")
                    .whereEqualTo("email", email)
                    .get()
                    .await()

                if (querySnapshot.isEmpty) {
                    Log.d(TAG, "El correo no está registrado en Firestore.")
                    return@withContext Result.failure(Exception("user-not-found"))
                }

                Log.d(TAG, "Enviando correo de recuperación de contraseña a: $email")
                auth.sendPasswordResetEmail(email).await()
                Log.d(TAG, "Correo de recuperación enviado con éxito.")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Error al enviar correo de recuperación: ${e.message}", e)
                Result.failure(e)
            }
        }
}


