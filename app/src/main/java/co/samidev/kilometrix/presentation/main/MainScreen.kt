package co.samidev.kilometrix.presentation.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.samidev.kilometrix.R
import co.samidev.kilometrix.presentation.analytics.AnalyticsScreen
import co.samidev.kilometrix.presentation.home.HomeScreen
import co.samidev.kilometrix.presentation.profile.ProfileScreen
import co.samidev.kilometrix.presentation.transactions.TransactionsScreen
import co.samidev.kilometrix.presentation.vehicle.VehicleScreen
import co.samidev.kilometrix.ui.theme.*

private enum class Tab(
    val labelRes: Int,
    val emoji: String
) {
    HOME(R.string.nav_home, "🏠"),
    TRANSACTIONS(R.string.nav_transactions, "💵"),
    VEHICLE(R.string.nav_vehicle, "🚗"),
    ANALYTICS(R.string.nav_analytics, "📊"),
    PROFILE(R.string.nav_profile, "👤"),
}

@Composable
fun MainScreen() {
    var selectedTab by remember { mutableStateOf(Tab.HOME) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Content area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp)
        ) {
            AnimatedVisibility(
                visible = selectedTab == Tab.HOME,
                enter = fadeIn(tween(200)), exit = fadeOut(tween(200))
            ) { HomeScreen() }
            AnimatedVisibility(
                visible = selectedTab == Tab.TRANSACTIONS,
                enter = fadeIn(tween(200)), exit = fadeOut(tween(200))
            ) { TransactionsScreen() }
            AnimatedVisibility(
                visible = selectedTab == Tab.VEHICLE,
                enter = fadeIn(tween(200)), exit = fadeOut(tween(200))
            ) { VehicleScreen() }
            AnimatedVisibility(
                visible = selectedTab == Tab.ANALYTICS,
                enter = fadeIn(tween(200)), exit = fadeOut(tween(200))
            ) { AnalyticsScreen() }
            AnimatedVisibility(
                visible = selectedTab == Tab.PROFILE,
                enter = fadeIn(tween(200)), exit = fadeOut(tween(200))
            ) { ProfileScreen() }
        }

        // FAB
        FloatingActionButton(
            onClick = { /* TODO: open add transaction bottom sheet */ },
            containerColor = PrimaryContainer,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 96.dp)
                .size(56.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
        }

        // Bottom Navigation
        BottomNavigationBar(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun BottomNavigationBar(
    selectedTab: Tab,
    onTabSelected: (Tab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceContainerLowest)
    ) {
        HorizontalDivider(color = OutlineVariant.copy(alpha = 0.5f), thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Tab.entries.forEach { tab ->
                val isSelected = tab == selectedTab
                NavItem(
                    emoji = tab.emoji,
                    label = stringResource(tab.labelRes),
                    isSelected = isSelected,
                    onClick = { onTabSelected(tab) }
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    emoji: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.width(64.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = emoji,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) Secondary else OnSurfaceVariant
            )
            if (isSelected) {
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(Secondary)
                )
            }
        }
    }
}
