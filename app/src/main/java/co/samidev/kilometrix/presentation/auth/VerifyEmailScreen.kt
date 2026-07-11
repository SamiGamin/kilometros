package co.samidev.kilometrix.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.samidev.kilometrix.R
import co.samidev.kilometrix.ui.theme.*

@Composable
fun VerifyEmailScreen(
    email: String,
    onVerifySuccess: () -> Unit
) {
    var otpValues by remember { mutableStateOf(List(6) { "" }) }
    val focusRequesters = remember { List(6) { FocusRequester() } }

    // Auto-focus first cell on entry
    LaunchedEffect(Unit) {
        focusRequesters[0].requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Email icon
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(SurfaceContainerLow, RoundedCornerShape(48.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "📧", style = MaterialTheme.typography.displayLarge)
            }
            Spacer(Modifier.height(32.dp))

            Text(
                text = stringResource(R.string.verify_title),
                style = MaterialTheme.typography.headlineMedium,
                color = OnSurface
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.verify_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = email,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                color = Primary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(40.dp))

            // OTP input cells
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                otpValues.forEachIndexed { index, value ->
                    OtpCell(
                        value = value,
                        focusRequester = focusRequesters[index],
                        onValueChange = { newChar ->
                            val newList = otpValues.toMutableList()
                            newList[index] = newChar
                            otpValues = newList
                            if (newChar.isNotEmpty() && index < 5) {
                                focusRequesters[index + 1].requestFocus()
                            }
                        },
                        onBackspace = {
                            val newList = otpValues.toMutableList()
                            newList[index] = ""
                            otpValues = newList
                            if (index > 0) focusRequesters[index - 1].requestFocus()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(32.dp))

            // Verify button
            Button(
                onClick = onVerifySuccess,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryContainer),
                shape = RoundedCornerShape(16.dp),
                enabled = otpValues.all { it.isNotEmpty() }
            ) {
                Text(
                    text = stringResource(R.string.verify_button),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(Modifier.height(16.dp))

            // Resend link
            TextButton(onClick = { /* TODO: resend */ }) {
                Text(
                    text = stringResource(R.string.verify_resend),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                    color = Primary
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.verify_expiry),
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant
            )
        }
    }
}

@Composable
private fun OtpCell(
    value: String,
    focusRequester: FocusRequester,
    onValueChange: (String) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isFilled = value.isNotEmpty()
    Box(
        modifier = modifier
            .aspectRatio(0.75f)
            .background(SurfaceContainerLow, RoundedCornerShape(12.dp))
            .border(
                width = 2.dp,
                color = if (isFilled) Primary else OutlineVariant,
                shape = RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        BasicTextField(
            value = value,
            onValueChange = { new ->
                when {
                    new.isEmpty() -> onBackspace()
                    new.length == 1 -> onValueChange(new)
                    else -> onValueChange(new.last().toString())
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            cursorBrush = SolidColor(Primary),
            textStyle = MaterialTheme.typography.headlineMedium.copy(
                color = OnSurface,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )
    }
}
