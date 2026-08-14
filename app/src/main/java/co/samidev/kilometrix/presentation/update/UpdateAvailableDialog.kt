package co.samidev.kilometrix.presentation.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.samidev.kilometrix.domain.model.AppUpdateInfo
import co.samidev.kilometrix.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun UpdateAvailableDialog(
    uiState: AppUpdateUiState,
    onConfirmUpdate: (AppUpdateInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val updateInfo = when (uiState) {
        is AppUpdateUiState.UpdateAvailable -> uiState.updateInfo
        is AppUpdateUiState.Downloading -> uiState.updateInfo
        is AppUpdateUiState.Error -> uiState.updateInfo
        else -> null
    } ?: return

    val isDownloading = uiState is AppUpdateUiState.Downloading
    val downloadProgress = (uiState as? AppUpdateUiState.Downloading)?.progress ?: 0f
    val errorMessage = (uiState as? AppUpdateUiState.Error)?.message

    AlertDialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        containerColor = SurfaceContainerLow,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🚀", style = MaterialTheme.typography.titleLarge)
                }
                Column {
                    Text(
                        text = "¡Nueva Versión Disponible!",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = OnSurface
                    )
                    Text(
                        text = "Actualización oficial de KiloMetrix",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Version badge box
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceContainerHigh.copy(alpha = 0.6f))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Versión actual", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        Text(
                            text = "v${updateInfo.currentVersion}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = OnSurfaceVariant
                        )
                    }

                    Text("➔", style = MaterialTheme.typography.titleMedium, color = Primary)

                    Column(horizontalAlignment = Alignment.End) {
                        Text("Nueva versión", style = MaterialTheme.typography.labelSmall, color = Primary)
                        Text(
                            text = "v${updateInfo.latestVersion}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = Primary
                        )
                    }
                }

                // Release notes formatted
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Novedades de esta versión:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = OnSurface
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceContainerHigh.copy(alpha = 0.4f))
                            .padding(14.dp)
                    ) {
                        FormattedMarkdownText(updateInfo.releaseNotes)
                    }
                }

                // Progress Indicator during download
                if (isDownloading) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Descargando actualización...",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = Primary
                            )
                            Text(
                                text = "${(downloadProgress * 100).roundToInt()}%",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = Primary
                            )
                        }

                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Primary,
                            trackColor = OutlineVariant.copy(alpha = 0.3f)
                        )
                    }
                }

                // Error message if failed
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = Error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmUpdate(updateInfo) },
                enabled = !isDownloading,
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = if (isDownloading) "Descargando..." else "Actualizar e Instalar",
                    fontWeight = FontWeight.Bold,
                    color = OnPrimary
                )
            }
        },
        dismissButton = {
            if (!isDownloading) {
                TextButton(onClick = onDismiss) {
                    Text("Ahora no", color = OnSurfaceVariant)
                }
            }
        }
    )
}

@Composable
private fun FormattedMarkdownText(markdownText: String) {
    val lines = remember(markdownText) { markdownText.lines() }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        lines.forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank()) return@forEach

            when {
                trimmed.startsWith("#") -> {
                    val cleanHeader = trimmed.replace("^#+\\s*".toRegex(), "")
                    Text(
                        text = parseMarkdownAnnotated(cleanHeader),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Primary
                    )
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    val cleanItem = trimmed.substring(2)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("•", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Primary)
                        Text(
                            text = parseMarkdownAnnotated(cleanItem),
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurface
                        )
                    }
                }
                else -> {
                    Text(
                        text = parseMarkdownAnnotated(trimmed),
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurface
                    )
                }
            }
        }
    }
}

private fun parseMarkdownAnnotated(input: String): AnnotatedString {
    val clean = input.replace("`", "")
    val parts = clean.split("**")
    return buildAnnotatedString {
        parts.forEachIndexed { index, part ->
            if (index % 2 == 1) {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(part)
                }
            } else {
                append(part)
            }
        }
    }
}
