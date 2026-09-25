package co.samidev.kilometrix.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.samidev.kilometrix.data.sync.KipuWalletResolver
import co.samidev.kilometrix.domain.repository.UserRepository
import co.samidev.kilometrix.domain.usecase.GetDriverStatsUseCase
import co.samidev.kilometrix.domain.usecase.UpdateUserProfileUseCase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import co.samidev.kilometrix.data.sync.KipuWalletSummary
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

private data class ProfileInternalState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isEditSheetOpen: Boolean = false,
    val isRefreshingWallets: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    userRepository: UserRepository,
    getDriverStatsUseCase: GetDriverStatsUseCase,
    private val updateUserProfileUseCase: UpdateUserProfileUseCase,
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val kipuWalletResolver: KipuWalletResolver
) : ViewModel() {

    private val _internalState = MutableStateFlow(ProfileInternalState())
    private val _selectedKipuWalletId = MutableStateFlow(kipuWalletResolver.getSelectedWalletId())
    private val _walletsList = MutableStateFlow<List<KipuWalletSummary>>(emptyList())
    private var isObservingWallets = false

    private val _eventChannel = Channel<ProfileUiEvent>(Channel.BUFFERED)
    val uiEvent: Flow<ProfileUiEvent> = _eventChannel.receiveAsFlow()

    init {
        val uid = auth.currentUser?.uid
        if (!uid.isNullOrBlank()) {
            startObservingWallets(uid)
            refreshKipuWallets()
        }
    }

    private fun startObservingWallets(userId: String) {
        if (isObservingWallets) return
        isObservingWallets = true
        viewModelScope.launch {
            kipuWalletResolver.observeWallets(userId).collect { list ->
                if (list.isNotEmpty() || _walletsList.value.isEmpty()) {
                    _walletsList.value = list
                }
            }
        }
    }

    val uiState: StateFlow<ProfileScreenUiState> = combine(
        userRepository.getUserProfile(),
        getDriverStatsUseCase(),
        _internalState,
        _walletsList,
        _selectedKipuWalletId
    ) { profile, stats, internal, wallets, selectedWalletId ->
        val effectiveSelected = selectedWalletId ?: wallets.firstOrNull {
            val name = it.name.lowercase()
            name.contains("trabajo") || name.contains("kilometrix")
        }?.id ?: wallets.firstOrNull()?.id

        ProfileScreenUiState(
            profile = profile,
            stats = stats,
            isLoading = internal.isLoading,
            isSaving = internal.isSaving,
            isEditSheetOpen = internal.isEditSheetOpen,
            kipuWallets = wallets,
            selectedKipuWalletId = effectiveSelected,
            isRefreshingWallets = internal.isRefreshingWallets
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProfileScreenUiState()
    )

    fun refreshKipuWallets() {
        val userId = auth.currentUser?.uid ?: return
        startObservingWallets(userId)
        if (_internalState.value.isRefreshingWallets) return
        viewModelScope.launch {
            _internalState.update { it.copy(isRefreshingWallets = true) }
            try {
                val currentSelected = kipuWalletResolver.getSelectedWalletId()
                if (currentSelected != null) {
                    _selectedKipuWalletId.value = currentSelected
                }
                val result = kipuWalletResolver.recalculateAndUpdateWalletBalances(userId)
                if (result.isSuccess) {
                    val updated = result.getOrThrow()
                    if (updated.isNotEmpty()) {
                        _walletsList.value = updated
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "Error al actualizar saldos: ${e.message}", e)
            } finally {
                _internalState.update { it.copy(isRefreshingWallets = false) }
            }
        }
    }

    fun selectKipuWallet(walletId: String) {
        kipuWalletResolver.setSelectedWalletId(walletId)
        _selectedKipuWalletId.value = walletId
    }

    fun createKipuWallet(name: String, initialBalance: Long = 0L) {
        val userId = auth.currentUser?.uid ?: return
        if (name.isBlank()) {
            viewModelScope.launch {
                _eventChannel.send(ProfileUiEvent.ShowSnackbar("El nombre del monedero no puede estar vacío"))
            }
            return
        }
        viewModelScope.launch {
            _internalState.update { it.copy(isSaving = true) }
            val result = kipuWalletResolver.createWallet(userId, name, initialBalance)
            _internalState.update { it.copy(isSaving = false) }
            if (result.isSuccess) {
                val newId = result.getOrThrow()
                _selectedKipuWalletId.value = newId
                refreshKipuWallets()
                _eventChannel.send(ProfileUiEvent.ShowSnackbar("Monedero \"$name\" creado con éxito"))
            } else {
                _eventChannel.send(
                    ProfileUiEvent.ShowSnackbar(
                        result.exceptionOrNull()?.message ?: "Error al crear monedero"
                    )
                )
            }
        }
    }

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
