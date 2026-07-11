package co.samidev.kilometrix.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import co.samidev.kilometrix.R
import co.samidev.kilometrix.ui.theme.*

@Composable
fun RegisterScreen(
    onNavigateBack: () -> Unit,
    onNavigateToVerify: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var termsAccepted by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(48.dp))

            // Back button
            TextButton(
                onClick = onNavigateBack,
                contentPadding = PaddingValues(0.dp)
            ) {
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
            Spacer(Modifier.height(24.dp))

            // Title
            Text(
                text = stringResource(R.string.register_title),
                style = MaterialTheme.typography.headlineMedium,
                color = OnSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.register_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = OnSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))

            // Name field
            AppTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.register_name_label),
                placeholder = stringResource(R.string.register_name_placeholder),
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = OnSurfaceVariant) }
            )
            Spacer(Modifier.height(16.dp))

            // Email field
            AppTextField(
                value = email,
                onValueChange = { email = it },
                label = stringResource(R.string.register_email_label),
                placeholder = stringResource(R.string.register_email_placeholder),
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = OnSurfaceVariant) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            Spacer(Modifier.height(16.dp))

            // Password field
            AppTextField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.register_password_label),
                placeholder = stringResource(R.string.register_password_placeholder),
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = OnSurfaceVariant) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = OnSurfaceVariant
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            Spacer(Modifier.height(20.dp))

            // Terms and conditions
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Checkbox(
                    checked = termsAccepted,
                    onCheckedChange = { termsAccepted = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = PrimaryContainer,
                        uncheckedColor = OutlineVariant,
                        checkmarkColor = Color.White
                    ),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = buildAnnotatedString {
                        append(stringResource(R.string.register_terms))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
            }
            Spacer(Modifier.height(24.dp))

            // Register button
            Button(
                onClick = { onNavigateToVerify(email) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryContainer),
                shape = RoundedCornerShape(16.dp),
                enabled = name.isNotBlank() && email.isNotBlank() && password.length >= 8 && termsAccepted
            ) {
                Text(
                    text = stringResource(R.string.register_button),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
            Spacer(Modifier.height(20.dp))

            // Login link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.register_have_account),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onNavigateBack, contentPadding = PaddingValues(0.dp)) {
                    Text(
                        text = stringResource(R.string.register_login_link),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                        color = Primary
                    )
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}
