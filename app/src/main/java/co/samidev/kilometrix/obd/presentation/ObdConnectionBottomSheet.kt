package co.samidev.kilometrix.obd.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import co.samidev.kilometrix.obd.domain.ConnectionState
import co.samidev.kilometrix.obd.domain.EcuDiagnosticInfo
import co.samidev.kilometrix.obd.domain.FuelSensorMode
import co.samidev.kilometrix.obd.domain.ObdDevice
import co.samidev.kilometrix.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObdConnectionBottomSheet(
    connectionState: ConnectionState,
    diagnosticInfo: EcuDiagnosticInfo,
    scannedDevices: List<ObdDevice>,
    isScanning: Boolean,
    disconnectionAlertMessage: String? = null,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onSelectDevice: (ObdDevice) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val requiredPermissions = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    var permissionsGranted by remember {
        mutableStateOf(
            requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val btGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            result[Manifest.permission.BLUETOOTH_SCAN] == true &&
                result[Manifest.permission.BLUETOOTH_CONNECT] == true
        } else {
            result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        }
        permissionsGranted = btGranted
        if (btGranted) {
            onStartScan()
        }
    }

    val isConnected = connectionState is ConnectionState.Connected && diagnosticInfo.ecuVerified
    val isBusy = connectionState is ConnectionState.Connecting ||
        connectionState is ConnectionState.DiscoveringServices ||
        connectionState is ConnectionState.EnablingNotifications ||
        connectionState is ConnectionState.InitializingElm ||
        connectionState is ConnectionState.VerifyingEcu

    ModalBottomSheet(
        onDismissRequest = {
            if (isScanning) onStopScan()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = SurfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Encabezado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("🔌", fontSize = 24.sp)
                    Column {
                        Text(
                            text = "Conexión OBD-II (ECU)",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                        Text(
                            text = "Telemetría en vivo para recorridos y consumo",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariant
                        )
                    }
                }

                StatusPill(connectionState = connectionState, ecuVerified = diagnosticInfo.ecuVerified)
            }

            // Alerta de desconexión si ocurrió durante un turno
            if (!disconnectionAlertMessage.isNullOrBlank() && !isConnected) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚠️", fontSize = 18.sp)
                        Text(
                            text = disconnectionAlertMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Tarjeta de telemetría cuando está conectado
            if (isConnected) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Secondary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Secondary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "✅ ECU Verificada · ${diagnosticInfo.elmVersion.ifBlank { "ELM327" }}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Secondary
                            )
                            Text(
                                text = diagnosticInfo.protocol.ifBlank { "CAN 11b/500k" },
                                style = MaterialTheme.typography.labelSmall,
                                color = OnSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            LiveSensorItem("RPM", "${diagnosticInfo.liveRpm ?: "--"}")
                            LiveSensorItem("Velocidad", "${diagnosticInfo.liveSpeedKph ?: 0} km/h")
                            when (diagnosticInfo.fuelSensorMode) {
                                FuelSensorMode.SPEED_DENSITY_MAP ->
                                    LiveSensorItem("MAP", "${diagnosticInfo.liveMapKpa ?: "--"} kPa")
                                FuelSensorMode.MAF_DIRECT ->
                                    LiveSensorItem("MAF", "${diagnosticInfo.liveMafGramsPerSec?.let { "%.1f".format(it) } ?: "--"} g/s")
                                FuelSensorMode.UNSUPPORTED ->
                                    LiveSensorItem("Carga", "N/A")
                            }
                            LiveSensorItem(
                                label = if (diagnosticInfo.isIatDegraded) "IAT (est.)" else "IAT",
                                value = "${diagnosticInfo.liveIntakeAirTemperatureCelsius ?: 25} °C"
                            )
                            if (diagnosticInfo.liveFuelLevelPct != null) {
                                LiveSensorItem("Nivel 012F", "${"%.0f".format(diagnosticInfo.liveFuelLevelPct)}%")
                            }
                        }
                    }
                }
            } else if (connectionState is ConnectionState.Error) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFF6B35).copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, Color(0xFFFF6B35).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = connectionState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFF6B35),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Botones de acción (Escanear / Desconectar)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!isConnected) {
                    Button(
                        onClick = {
                            if (!permissionsGranted) {
                                permissionLauncher.launch(requiredPermissions.toTypedArray())
                            } else if (isScanning) {
                                onStopScan()
                            } else {
                                onStartScan()
                            }
                        },
                        enabled = !isBusy,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(statusLabel(connectionState, false), color = Color.White)
                        } else {
                            Text(
                                text = if (!permissionsGranted) "🔐 Permitir Bluetooth y Buscar"
                                else if (isScanning) "⏹ Detener búsqueda"
                                else "🔍 Buscar Adaptador OBD-II",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = onDisconnect,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Text("Desconectar OBD", color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Secondary)
                    ) {
                        Text("Listo · Continuar", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }

            // Lista de dispositivos BLE encontrados
            AnimatedVisibility(visible = !isConnected && (scannedDevices.isNotEmpty() || isScanning)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isScanning) "BUSCANDO ADAPTADORES BLE..." else "ADAPTADORES ENCONTRADOS",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )

                    if (scannedDevices.isEmpty() && isScanning) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text(
                                    text = "Asegúrate de que el adaptador V03H4 / ELM327 esté conectado al puerto OBD2 y el contacto encendido.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceVariant
                                )
                            }
                        }
                    }

                    scannedDevices.forEach { device ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceContainerHigh,
                            border = BorderStroke(1.dp, Primary.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isBusy) {
                                    onStopScan()
                                    onSelectDevice(device)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = device.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = OnSurface
                                    )
                                    Text(
                                        text = "${device.address} · Señal ${device.rssi} dBm",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OnSurfaceVariant
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Primary.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "Conectar",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Consola compacta de diagnóstico (últimas líneas)
            if (diagnosticInfo.logs.isNotEmpty()) {
                var showLogs by remember { mutableStateOf(false) }
                TextButton(
                    onClick = { showLogs = !showLogs },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = if (showLogs) "▲ Ocultar registro técnico (${diagnosticInfo.logs.size})"
                        else "▼ Ver registro técnico ELM/ECU (${diagnosticInfo.logs.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant
                    )
                }
                AnimatedVisibility(visible = showLogs) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0D1117))
                            .border(0.5.dp, CardBorder, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = diagnosticInfo.logs.takeLast(25).joinToString("\n"),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFF9CDCFE)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(connectionState: ConnectionState, ecuVerified: Boolean) {
    val isConnected = connectionState is ConnectionState.Connected && ecuVerified
    val color = when {
        isConnected -> Secondary
        connectionState is ConnectionState.Error -> Color(0xFFFF6B35)
        connectionState is ConnectionState.Disconnected -> OnSurfaceVariant
        else -> Primary
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = statusLabel(connectionState, ecuVerified),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

private fun statusLabel(state: ConnectionState, ecuVerified: Boolean): String = when {
    state is ConnectionState.Connected && ecuVerified -> "CONECTADO"
    state is ConnectionState.Connecting -> "CONECTANDO..."
    state is ConnectionState.DiscoveringServices -> "GATT..."
    state is ConnectionState.EnablingNotifications -> "CANAL RX..."
    state is ConnectionState.InitializingElm -> "INIT ELM..."
    state is ConnectionState.VerifyingEcu -> "VERIFICANDO ECU..."
    state is ConnectionState.Error -> "SIN CONEXIÓN"
    else -> "DESCONECTADO"
}

@Composable
private fun LiveSensorItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = OnSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceVariant
        )
    }
}
