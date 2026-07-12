package co.samidev.kilometrix.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import co.samidev.kilometrix.R
import co.samidev.kilometrix.domain.model.UserProfile
import co.samidev.kilometrix.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(onLogout: () -> Unit) {
    val viewModel: ProfileViewModel = hiltViewModel()
    val profile by viewModel.userProfile.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showDeleteConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        if (uiState is ProfileUiState.Error) {
            val errorState = uiState as ProfileUiState.Error
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = errorState.message,
                    duration = SnackbarDuration.Long
                )
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
            Spacer(Modifier.height(24.dp))

            // Avatar card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceContainerLow)
                    .padding(vertical = 28.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Get initials
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
                    style = MaterialTheme.typography.headlineSmall,
                    color = OnSurface
                )
                Text(
                    text = profile?.email?.ifEmpty { "Cargando..." } ?: "Cargando...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                
                OutlinedButton(
                    onClick = {},
                    shape = RoundedCornerShape(50),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    Text(stringResource(R.string.profile_plan_free), style = MaterialTheme.typography.labelMedium, color = Primary)
                }
            }

            // Driver data card
            ProfileSection(title = stringResource(R.string.profile_driver_data_label)) {
                ProfileRow("👤", stringResource(R.string.profile_name_label), profile?.name ?: "—")
                HorizontalDivider(color = OutlineVariant.copy(alpha = 0.2f))
                ProfileRow("✉️", stringResource(R.string.profile_email_label), profile?.email ?: "—")
                HorizontalDivider(color = OutlineVariant.copy(alpha = 0.2f))
                ProfileRow("🏙️", "Ciudad", profile?.city?.ifEmpty { "No registrada" } ?: "—")
                HorizontalDivider(color = OutlineVariant.copy(alpha = 0.2f))
                ProfileRow("📍", stringResource(R.string.profile_country_label), "Colombia")
                HorizontalDivider(color = OutlineVariant.copy(alpha = 0.2f))
                ProfileRow("💵", stringResource(R.string.profile_currency_label), "COP")
            }

            // Income split card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceContainerLow)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.profile_income_split_label), style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                Text(stringResource(R.string.profile_income_split_desc), style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceContainerHigh.copy(alpha = 0.5f))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Tertiary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🔧", style = MaterialTheme.typography.headlineSmall)
                        }
                        Column {
                            Text(stringResource(R.string.profile_maintenance_label), style = MaterialTheme.typography.titleMedium, color = OnSurface)
                            Text("10%", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = Tertiary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Action Buttons (Logout & Delete Account)
            Button(
                onClick = {
                    viewModel.signOut(onLogout)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHigh),
                shape = RoundedCornerShape(14.dp),
                enabled = uiState !is ProfileUiState.Loading
            ) {
                Text("Cerrar sesión", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            }

            TextButton(
                onClick = { showDeleteConfirmation = true },
                enabled = uiState !is ProfileUiState.Loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Eliminar mi cuenta", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Error)
            }

            Spacer(Modifier.height(48.dp))
        }
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
                    Text("Esta acción es irreversible y borrará permanentemente tu perfil, vehículos e historial de ganancias de KiloMetrix.")
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
        Text(title, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
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
