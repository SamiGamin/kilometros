package co.samidev.kilometrix.presentation.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.samidev.kilometrix.domain.model.WorkPlatform
import co.samidev.kilometrix.ui.theme.*
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

private val currencyFormatter: NumberFormat = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-CO"))
    .also { (it as? DecimalFormat)?.applyPattern("#,###") }

private val CITIES_COLOMBIA = listOf(
    "Bogotá",
    "Medellín",
    "Cali",
    "Barranquilla",
    "Bucaramanga",
    "Cartagena",
    "Manizales",
    "Pereira",
    "Cúcuta",
    "Santa Marta",
    "Villavicencio",
    "Ibagué",
    "Pasto",
    "Armenia",
    "Montería",
    "Neiva",
    "Popayán",
    "Tunja",
    "Otra"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditProfileBottomSheet(
    initialName: String,
    initialCity: String,
    initialPlatforms: List<String>,
    initialMaintenanceReservePercent: Int,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, city: String, platforms: List<String>, maintenanceReservePercent: Int) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var city by remember(initialCity) { mutableStateOf(if (initialCity.isBlank()) "Bogotá" else initialCity) }
    var selectedPlatforms by remember(initialPlatforms) { 
        mutableStateOf(
            if (initialPlatforms.isEmpty()) listOf(WorkPlatform.UBER) 
            else initialPlatforms.map { WorkPlatform.fromId(it) }.distinct()
        ) 
    }
    var reservePercent by remember(initialMaintenanceReservePercent) { 
        mutableFloatStateOf(initialMaintenanceReservePercent.coerceIn(5, 25).toFloat()) 
    }

    var cityDropdownExpanded by remember { mutableStateOf(false) }

    val isNameValid = name.trim().isNotBlank()
    val isPlatformsValid = selectedPlatforms.isNotEmpty()
    val canSave = isNameValid && isPlatformsValid && !isSaving

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Editar Perfil del Conductor",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = OnSurface
                    )
                    Text(
                        text = "Personaliza tus datos y configuración de trabajo",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = OnSurfaceVariant)
                }
            }

            // Driver Name
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Nombre del Conductor",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = OnSurface
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Ej. Juan Pérez") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Primary) },
                    singleLine = true,
                    isError = !isNameValid,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = OutlineVariant,
                        errorBorderColor = Error
                    )
                )
                if (!isNameValid) {
                    Text(
                        text = "El nombre no puede estar vacío",
                        style = MaterialTheme.typography.bodySmall,
                        color = Error
                    )
                }
            }

            // City Selection Dropdown
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Ciudad Principal de Operación",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = OnSurface
                )
                ExposedDropdownMenuBox(
                    expanded = cityDropdownExpanded,
                    onExpandedChange = { cityDropdownExpanded = !cityDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = city,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = OutlineVariant
                        )
                    )

                    ExposedDropdownMenu(
                        expanded = cityDropdownExpanded,
                        onDismissRequest = { cityDropdownExpanded = false }
                    ) {
                        CITIES_COLOMBIA.forEach { cityName ->
                            DropdownMenuItem(
                                text = { Text(cityName) },
                                onClick = {
                                    city = cityName
                                    cityDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                Text(
                    text = "Afecta el cálculo automático de Pico y Placa",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant
                )
            }

            // Active Platforms Selection Chips
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Plataformas de Trabajo Activas",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = OnSurface
                )
                Text(
                    text = "Selecciona las aplicaciones en las que trabajas habitualmente",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WorkPlatform.entries.forEach { platform ->
                        val isSelected = selectedPlatforms.contains(platform)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedPlatforms = if (isSelected) {
                                    selectedPlatforms - platform
                                } else {
                                    selectedPlatforms + platform
                                }
                            },
                            label = {
                                Text("${platform.iconEmoji} ${platform.displayName}")
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Primary.copy(alpha = 0.2f),
                                selectedLabelColor = Primary
                            )
                        )
                    }
                }

                if (!isPlatformsValid) {
                    Text(
                        text = "Selecciona al menos una plataforma activa",
                        style = MaterialTheme.typography.bodySmall,
                        color = Error
                    )
                }
            }

            // Maintenance Reserve Slider & Dynamic Preview Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceContainerHigh.copy(alpha = 0.6f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🔧", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Reserva de Mantenimiento",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = OnSurface
                        )
                    }
                    Text(
                        text = "${reservePercent.roundToInt()}%",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = Tertiary
                    )
                }

                Slider(
                    value = reservePercent,
                    onValueChange = { reservePercent = it },
                    valueRange = 5f..25f,
                    steps = 19, // Step of 1%
                    colors = SliderDefaults.colors(
                        thumbColor = Tertiary,
                        activeTrackColor = Tertiary,
                        inactiveTrackColor = OutlineVariant.copy(alpha = 0.3f)
                    )
                )

                val sampleGross = 100000.0
                val calculatedReserve = (sampleGross * reservePercent.roundToInt() / 100.0).toLong()
                val formattedAmount = currencyFormatter.format(calculatedReserve)

                Text(
                    text = "💡 Ahorras $$formattedAmount COP por cada $100.000 COP brutos generados para imprevistos y repuestos.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = OnSurfaceVariant
                )
            }

            // Save Button
            Button(
                onClick = {
                    onSave(
                        name.trim(),
                        city.trim(),
                        selectedPlatforms.map { it.id },
                        reservePercent.roundToInt()
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = OnPrimary,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Text(
                        text = "Guardar Cambios",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = OnPrimary
                    )
                }
            }
        }
    }
}
