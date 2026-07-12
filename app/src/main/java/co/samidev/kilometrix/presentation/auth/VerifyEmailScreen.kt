package co.samidev.kilometrix.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.samidev.kilometrix.R
import co.samidev.kilometrix.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun VerifyEmailScreen(
    email: String,
    onVerifySuccess: () -> Unit
) {
    val viewModel: AuthViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Handle UI state changes for snackbar alerts
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is AuthUiState.Error -> {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = state.message,
                        duration = SnackbarDuration.Short
                    )
                }
            }
            is AuthUiState.Success -> {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = state.message,
                        duration = SnackbarDuration.Short
                    )
                }
            }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = Background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Email icon
                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .background(SurfaceContainerLow, RoundedCornerShape(56.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✉️", style = MaterialTheme.typography.displayLarge)
                }
                Spacer(Modifier.height(32.dp))

                Text(
                    text = stringResource(R.string.verify_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = OnSurface
                )
                Spacer(Modifier.height(16.dp))
                
                Text(
                    text = stringResource(R.string.verify_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                
                Text(
                    text = email,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                    color = Primary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(32.dp))

                // Card with instructions
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.verify_expiry),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(20.dp)
                    )
                }
                
                Spacer(Modifier.height(40.dp))

                // Check verification button
                Button(
                    onClick = {
                        viewModel.checkEmailVerification { isVerified ->
                            if (isVerified) {
                                onVerifySuccess() // Navega a la pantalla principal
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryContainer),
                    shape = RoundedCornerShape(16.dp),
                    enabled = uiState !is AuthUiState.Loading
                ) {
                    if (uiState is AuthUiState.Loading) {
                        CircularProgressIndicator(
                            color = OnPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.verify_button),
                            style = MaterialTheme.typography.titleMedium,
                            color = OnPrimaryContainer
                        )
                    }
                }
                
                Spacer(Modifier.height(16.dp))

                // Resend email link
                TextButton(
                    onClick = {
                        viewModel.resendVerificationEmail { success ->
                            if (success) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Correo de verificación reenviado.")
                                }
                            }
                        }
                    },
                    enabled = uiState !is AuthUiState.Loading
                ) {
                    Text(
                        text = stringResource(R.string.verify_resend),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                        color = Primary
                    )
                }
            }
        }
    }
}
