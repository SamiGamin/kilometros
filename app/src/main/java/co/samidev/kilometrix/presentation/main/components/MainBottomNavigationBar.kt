package co.samidev.kilometrix.presentation.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.samidev.kilometrix.ui.theme.OnSurfaceVariant
import co.samidev.kilometrix.ui.theme.OutlineVariant
import co.samidev.kilometrix.ui.theme.Secondary
import co.samidev.kilometrix.ui.theme.SurfaceContainerLowest

@Composable
fun MainBottomNavigationBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceContainerLowest)
    ) {
        HorizontalDivider(color = OutlineVariant.copy(alpha = 0.4f), thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainTab.entries.forEach { tab ->
                val isSelected = tab == selectedTab
                AnimatedNavItem(
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
private fun AnimatedNavItem(
    emoji: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "iconScale"
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.5f,
        animationSpec = tween(200),
        label = "labelAlpha"
    )

    TextButton(
        onClick = onClick,
        modifier = Modifier.width(68.dp),
        contentPadding = PaddingValues(vertical = 6.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                TabPill(isSelected = isSelected)
                Text(
                    text = emoji,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.scale(iconScale)
                )
            }

            Spacer(Modifier.height(2.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) Secondary else OnSurfaceVariant.copy(alpha = labelAlpha)
            )
        }
    }
}

@Composable
private fun TabPill(isSelected: Boolean) {
    AnimatedVisibility(
        visible = isSelected,
        enter = scaleIn(
            spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
        ) + fadeIn(tween(150)),
        exit = scaleOut(tween(150)) + fadeOut(tween(100))
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Secondary.copy(alpha = 0.18f))
        )
    }
}
