package co.samidev.kilometrix.presentation.vehicle

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import co.samidev.kilometrix.domain.model.Vehicle
import co.samidev.kilometrix.presentation.vehicle.components.*
import co.samidev.kilometrix.ui.theme.Background

@Composable
fun VehicleScreen() {
    val viewModel: VehicleViewModel = hiltViewModel()
    val vehicles by viewModel.vehicles.collectAsState()
    val activeVehicleId by viewModel.activeVehicleId.collectAsState()
    val activeVehicle by viewModel.activeVehicle.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val picoPlacaState by viewModel.picoPlacaState.collectAsState()
    val hasActiveShift by viewModel.hasActiveShift.collectAsState()
    val context = LocalContext.current

    var selectedVehicle by remember { mutableStateOf<Vehicle?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showQuickOilDialog by remember { mutableStateOf(false) }

    // Sync selected vehicle with active vehicle or first item
    LaunchedEffect(activeVehicle, vehicles) {
        if (selectedVehicle == null && activeVehicle != null) {
            selectedVehicle = activeVehicle
        } else if (selectedVehicle != null) {
            selectedVehicle = vehicles.find { it.id == selectedVehicle?.id } ?: activeVehicle ?: vehicles.firstOrNull()
        } else if (vehicles.isNotEmpty()) {
            selectedVehicle = vehicles.first()
        }
    }

    val showBlockedChangeToast = {
        Toast.makeText(
            context,
            "Debes finalizar el recorrido actual antes de cambiar de vehículo.",
            Toast.LENGTH_SHORT
        ).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        // Header
        VehicleHeader(
            vehicleCount = vehicles.size,
            onAddClick = { showAddDialog = true }
        )

        if (vehicles.isEmpty()) {
            // Empty state
            VehicleEmptyStateCard(
                onAddClick = { showAddDialog = true }
            )
        } else {
            // Horizontal switch for multiple vehicles
            VehicleSelectorRow(
                vehicles = vehicles,
                selectedVehicleId = selectedVehicle?.id,
                activeVehicleId = activeVehicleId,
                hasActiveShift = hasActiveShift,
                onSelectVehicle = { vehicle ->
                    selectedVehicle = vehicle
                    viewModel.setActiveVehicle(vehicle.id)
                },
                onBlockedChange = showBlockedChangeToast
            )

            selectedVehicle?.let { vehicle ->
                val isAppActive = vehicle.id == activeVehicleId || (activeVehicleId == null && vehicle.id == vehicles.firstOrNull()?.id)
                val picoPlacaStatus = viewModel.getPicoPlacaStatus(vehicle, userProfile?.city, picoPlacaState)

                // Selected vehicle details card
                VehicleDetailsCard(
                    vehicle = vehicle,
                    isAppActive = isAppActive,
                    picoPlacaStatus = picoPlacaStatus,
                    hasActiveShift = hasActiveShift,
                    onActivate = { viewModel.setActiveVehicle(vehicle.id) },
                    onEdit = { showEditDialog = true },
                    onRegisterOilChange = { showQuickOilDialog = true },
                    onUpdateVehicle = { updated ->
                        viewModel.updateVehicle(updated)
                        selectedVehicle = updated
                    },
                    onBlockedChange = showBlockedChangeToast
                )
            }
        }

        Spacer(Modifier.height(80.dp))
    }

    if (showAddDialog) {
        VehicleFormDialog(
            title = "Agregar vehículo",
            vehicle = null,
            onDismiss = { showAddDialog = false },
            onSave = { newVehicle ->
                viewModel.addVehicle(newVehicle)
                showAddDialog = false
            }
        )
    }

    if (showEditDialog && selectedVehicle != null) {
        VehicleFormDialog(
            title = "Editar vehículo",
            vehicle = selectedVehicle,
            onDismiss = { showEditDialog = false },
            onSave = { updatedVehicle ->
                viewModel.updateVehicle(updatedVehicle)
                selectedVehicle = updatedVehicle
                showEditDialog = false
            }
        )
    }

    if (showQuickOilDialog && selectedVehicle != null) {
        QuickOilChangeDialog(
            vehicle = selectedVehicle!!,
            onDismiss = { showQuickOilDialog = false },
            onConfirm = { km ->
                val current = selectedVehicle!!
                val updated = current.copy(
                    lastOilChangeKm = km,
                    odometer = maxOf(current.odometer, km)
                )
                viewModel.updateVehicle(updated)
                selectedVehicle = updated
                showQuickOilDialog = false
            }
        )
    }
}
