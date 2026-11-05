package co.samidev.kilometrix.presentation.profile

import co.samidev.kilometrix.data.sync.KipuWalletSummary
import co.samidev.kilometrix.domain.model.DriverStats
import co.samidev.kilometrix.domain.model.UserProfile

data class ProfileScreenUiState(
    val profile: UserProfile? = null,
    val stats: DriverStats = DriverStats(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isEditSheetOpen: Boolean = false,
    val kipuWallets: List<KipuWalletSummary> = emptyList(),
    val selectedKipuWalletId: String? = null,
    val isRefreshingWallets: Boolean = false,
    // ── Fondo de Mantenimiento histórico (P24b) ──
    val maintenanceReserveProvisionedCop: Double = 0.0,
    val maintenanceReserveUsedCop: Double = 0.0,
    val maintenanceReserveBalanceCop: Double = 0.0,
    val maintenanceReserveHasData: Boolean = false,
    val maintenanceReserveVehicleName: String = ""
)
