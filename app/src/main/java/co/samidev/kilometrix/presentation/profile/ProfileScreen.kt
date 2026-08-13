package co.samidev.kilometrix.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import co.samidev.kilometrix.R
import co.samidev.kilometrix.domain.model.WorkPlatform
import co.samidev.kilometrix.presentation.profile.components.AdjustReserveDialog
import co.samidev.kilometrix.presentation.profile.components.EditProfileBottomSheet
import co.samidev.kilometrix.ui.theme.*
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

private val currencyFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-CO"))
    .also { (it as? DecimalFormat)?.applyPattern("#,###") }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(onLogout: () -> Unit) {
    val viewModel: ProfileViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showAdjustReserveDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is ProfileUiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        duration = SnackbarDuration.Short
                    )
                }
                is ProfileUiEvent.SignedOut -> onLogout()
                is ProfileUiEvent.AccountDeleted -> onLogout()
                is ProfileUiEvent.CloseEditSheet -> {}
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = Background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // 1. Avatar & Driver Card
            val profile = uiState.profile
            val initials = remember(profile?.name) {
                val nameParts = profile?.name?.trim()?.split("\\s+".toRegex()) ?: emptyList()
                if (nameParts.isEmpty() || nameParts.first().isEmpty()) {
                    "U"
                } else if (nameParts.size == 1) {
                    nameParts.first().take(2).uppercase()
                } else {
                    (nameParts[0].take(1) + nameParts[1].take(1)).uppercase()
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(SurfaceContainerLow)
                    .padding(vertical = 24.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(Secondary.copy(alpha = 0.15f))
                        .border(3.dp, Secondary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = Secondary
                    )
                }

                Text(
                    text = profile?.name?.ifEmpty { "Conductor" } ?: "Cargando...",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = OnSurface
                )
                Text(
                    text = profile?.email?.ifEmpty { "Cargando..." } ?: "Cargando...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Plan Gratuito", style = MaterialTheme.typography.labelMedium) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = Primary.copy(alpha = 0.12f),
                            labelColor = Primary
                        ),
                        border = null
                    )

                    OutlinedButton(
                        onClick = { viewModel.openEditSheet() },
                        shape = RoundedCornerShape(50),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Editar Perfil", style = MaterialTheme.typography.labelMedium, color = Primary)
                    }
                }
            }

            // 2. Driver Statistics Summary Card
            val stats = uiState.stats
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceContainerLow)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "ESTADÍSTICAS DEL CONDUCTOR",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = OnSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Turnos
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceContainerHigh.copy(alpha = 0.5f))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("⏱️ Turnos", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${stats.totalShifts}",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = OnSurface
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    // Ganancias Brutas
                    Column(
                        modifier = Modifier
                            .weight(1.3f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceContainerHigh.copy(alpha = 0.5f))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("💵 Ganancias", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "$${currencyFormat.format(stats.totalGrossEarnings)}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Primary
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceContainerHigh.copy(alpha = 0.5f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🚘", style = MaterialTheme.typography.titleMedium)
                        Text("Vehículo Activo:", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                    }
                    Text(
                        text = stats.activeVehicleName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = OnSurface
                    )
                }
            }

            // 3. Driver Info Card & Platforms
            ProfileSection(title = "DATOS DEL CONDUCTOR") {
                ProfileRow("👤", "Nombre", profile?.name?.ifBlank { "—" } ?: "—")
                HorizontalDivider(color = OutlineVariant.copy(alpha = 0.2f))
                ProfileRow("✉️", "Correo Electrónico", profile?.email?.ifBlank { "—" } ?: "—")
                HorizontalDivider(color = OutlineVariant.copy(alpha = 0.2f))
                ProfileRow("🏙️", "Ciudad de Operación", profile?.city?.ifBlank { "No registrada" } ?: "—")
                HorizontalDivider(color = OutlineVariant.copy(alpha = 0.2f))
                ProfileRow("📍", "País", "Colombia")
                HorizontalDivider(color = OutlineVariant.copy(alpha = 0.2f))
                ProfileRow("💵", "Moneda", "COP")
                HorizontalDivider(color = OutlineVariant.copy(alpha = 0.2f))

                // Platforms Chips
                Column(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("📲", style = MaterialTheme.typography.bodyLarge)
                        Text("Plataformas Activas", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                    }

                    val platformsList = (profile?.platforms ?: emptyList())
                        .map { WorkPlatform.fromId(it) }
                        .distinct()
                    if (platformsList.isEmpty()) {
                        Text("Ninguna plataforma configurada", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            platformsList.forEach { p ->
                                AssistChip(
                                    onClick = {},
                                    label = { Text("${p.iconEmoji} ${p.displayName}", style = MaterialTheme.typography.labelSmall) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = SurfaceContainerHigh,
                                        labelColor = OnSurface
                                    ),
                                    border = null
                                )
                            }
                        }
                    }
                }
            }

            // 4. Maintenance Reserve Card
            val reservePercent = profile?.maintenanceReservePercent ?: 10
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceContainerLow)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RESERVA DE MANTENIMIENTO",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = OnSurfaceVariant
                    )
                    TextButton(onClick = { showAdjustReserveDialog = true }) {
                        Text("Ajustar %", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Primary)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceContainerHigh.copy(alpha = 0.5f))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Tertiary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🔧", style = MaterialTheme.typography.titleLarge)
                        }
                        Column {
                            Text("Porcentaje Destinado", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                            Text("Fondo para repuestos y prevención", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                        }
                    }
                    Text(
                        text = "$reservePercent%",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = Tertiary
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // 5. Account Action Buttons
            Button(
                onClick = { viewModel.signOut(onLogout) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHigh),
                shape = RoundedCornerShape(16.dp),
                enabled = !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = OnSurface)
                } else {
                    Text("Cerrar sesión", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                }
            }

            TextButton(
                onClick = { showDeleteConfirmation = true },
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Eliminar mi cuenta",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Error
                )
            }

            Spacer(Modifier.height(48.dp))
        }
    }

    // Edit Profile Bottom Sheet
    if (uiState.isEditSheetOpen) {
        EditProfileBottomSheet(
            initialName = uiState.profile?.name ?: "",
            initialCity = uiState.profile?.city ?: "",
            initialPlatforms = uiState.profile?.platforms ?: emptyList(),
            initialMaintenanceReservePercent = uiState.profile?.maintenanceReservePercent ?: 10,
            isSaving = uiState.isSaving,
            onDismiss = { viewModel.closeEditSheet() },
            onSave = { name, city, platforms, reservePercent ->
                viewModel.updateProfile(name, city, platforms, reservePercent)
            }
        )
    }

    // Direct Maintenance Reserve Dialog
    if (showAdjustReserveDialog) {
        AdjustReserveDialog(
            initialReservePercent = uiState.profile?.maintenanceReservePercent ?: 10,
            isSaving = uiState.isSaving,
            onDismiss = { showAdjustReserveDialog = false },
            onSave = { newPercent ->
                viewModel.updateMaintenanceReserve(newPercent)
                showAdjustReserveDialog = false
            }
        )
    }

    // Delete Account Confirmation Dialog
    if (showDeleteConfirmation) {
        var confirmText by remember { mutableStateOf("") }
        val isConfirmEnabled = confirmText.trim().equals("eliminar", ignoreCase = true)

        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmation = false
                confirmText = ""
            },
            title = { Text("¿Eliminar tu cuenta?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Esta acción es irreversible y borrará permanentemente tu perfil, vehículos e historial de KiloMetrix.")
                    Text("Para confirmar, escribe la palabra \"ELIMINAR\" a continuación:", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    OutlinedTextField(
                        value = confirmText,
                        onValueChange = { confirmText = it },
                        placeholder = { Text("Escribe ELIMINAR") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Error,
                            unfocusedBorderColor = OutlineVariant,
                            cursorColor = Error
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        confirmText = ""
                        viewModel.deleteAccount(onLogout)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Error),
                    enabled = isConfirmEnabled
                ) {
                    Text("Sí, eliminar", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        confirmText = ""
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun ProfileSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceContainerLow)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = OnSurfaceVariant
        )
        content()
    }
}

@Composable
private fun ProfileRow(emoji: String, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(emoji, style = MaterialTheme.typography.bodyLarge, color = Secondary)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
        }
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = OnSurface)
    }
}
