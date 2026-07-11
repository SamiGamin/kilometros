package co.samidev.kilometrix.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.samidev.kilometrix.R
import co.samidev.kilometrix.ui.theme.*

@Composable
fun ProfileScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(48.dp))

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
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(Secondary.copy(alpha = 0.15f))
                    .border(3.dp, Secondary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SM",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = Secondary
                )
            }
            Text("Salomon Martinez", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
            Text("salito1405@gmail.com", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
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
            ProfileRow("👤", stringResource(R.string.profile_name_label), "Salomon Martinez")
            HorizontalDivider(color = OutlineVariant.copy(alpha = 0.2f))
            ProfileRow("✉️", stringResource(R.string.profile_email_label), "salito1405@gmail.com")
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
