package co.samidev.kilometrix.presentation.auth

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.samidev.kilometrix.R
import co.samidev.kilometrix.ui.theme.*

@Composable
fun ForgotPasswordScreen(
    onNavigateBack: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var emailSent by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedContent(
                targetState = emailSent,
                label = "forgotState"
            ) { sent ->
                if (!sent) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Back button
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                            TextButton(onClick = onNavigateBack, contentPadding = PaddingValues(0.dp)) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    tint = OnSurfaceVariant
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.register_back),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OnSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(32.dp))

                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .background(SurfaceContainerLow, RoundedCornerShape(48.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "🔑", style = MaterialTheme.typography.displayLarge)
                        }
                        Spacer(Modifier.height(28.dp))

                        Text(
                            text = stringResource(R.string.forgot_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = OnSurface
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.forgot_subtitle),
                            style = MaterialTheme.typography.bodyLarge,
                            color = OnSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(32.dp))

                        AppTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = stringResource(R.string.forgot_email_label),
                            placeholder = stringResource(R.string.forgot_email_placeholder),
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = OnSurfaceVariant) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                        )
                        Spacer(Modifier.height(24.dp))

                        Button(
                            onClick = { emailSent = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryContainer),
                            shape = RoundedCornerShape(16.dp),
                            enabled = email.contains("@")
                        ) {
                            Text(
                                text = stringResource(R.string.forgot_button),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }
                    }
                } else {
                    // Success state
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(112.dp)
                                .background(Secondary.copy(alpha = 0.15f), RoundedCornerShape(56.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "✅", style = MaterialTheme.typography.displayLarge)
                        }
                        Text(
                            text = stringResource(R.string.forgot_success_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = OnSurface
                        )
                        Text(
                            text = stringResource(R.string.forgot_success_body),
                            style = MaterialTheme.typography.bodyLarge,
                            color = OnSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onNavigateBack,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                        ) {
                            Text(
                                text = stringResource(R.string.forgot_back_login),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
