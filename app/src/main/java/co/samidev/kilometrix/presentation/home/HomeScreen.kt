package co.samidev.kilometrix.presentation.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.samidev.kilometrix.R
import co.samidev.kilometrix.ui.theme.*
import kotlinx.coroutines.delay

// ── Staggered entrance animation helper ───────────────────────────────────────
@Composable
private fun staggeredEntrance(index: Int): Pair<Float, Float> {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 80L) // 80ms stagger per card
        visible = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "cardAlpha"
    )
    val translationY by animateFloatAsState(
        targetValue = if (visible) 0f else 40f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "cardTranslation"
    )
    return alpha to translationY
}

@Composable
fun HomeScreen(onNavigateToPicoPlaca: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(48.dp))

        // ── Header (card 0) ─────────────────────────────────────────────────
        val (h0Alpha, h0Ty) = staggeredEntrance(0)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(h0Alpha)
                .offset(y = h0Ty.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.home_greeting, "Salomon"),
                    style = MaterialTheme.typography.titleLarge,
                    color = OnSurface
                )
                Text(
                    text = "Sábado, 11 de julio",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(PrimaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "S",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        }

        // ── Earnings card (card 1) ────────────────────────────────────────
        val (c1Alpha, c1Ty) = staggeredEntrance(1)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(c1Alpha)
                .offset(y = c1Ty.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceContainerLow)
                .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .matchParentSize()
                    .clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                    .background(PrimaryContainer)
            )
            Column(
                modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_earnings_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant
                )
                Text(
                    text = "$ 0",
                    style = MaterialTheme.typography.displayLarge,
                    color = OnSurface
                )
                Text(
                    text = stringResource(R.string.home_earnings_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
            }
        }

        // ── Start day card (card 2) ───────────────────────────────────────
        val (c2Alpha, c2Ty) = staggeredEntrance(2)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(c2Alpha)
                .offset(y = c2Ty.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceContainerLow)
                .border(1.dp, Secondary.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("🚀", style = MaterialTheme.typography.displayLarge)
            Text(
                text = stringResource(R.string.home_start_day_title),
                style = MaterialTheme.typography.headlineSmall,
                color = OnSurface
            )
            Text(
                text = stringResource(R.string.home_start_day_body),
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant
            )
            // Animated start button with press scale
            StartDayButton()
        }

        // ── AI analysis card (card 3) ─────────────────────────────────────
        val (c3Alpha, c3Ty) = staggeredEntrance(3)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(c3Alpha)
                .offset(y = c3Ty.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceContainerLow)
                .border(1.dp, Tertiary.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    Text("🤖", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = stringResource(R.string.home_ai_analysis_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = OnSurface
                    )
                }
                Surface(shape = RoundedCornerShape(50), color = Tertiary.copy(alpha = 0.15f)) {
                    Text(
                        text = "✨ IA",
                        style = MaterialTheme.typography.labelSmall,
                        color = Tertiary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Text(
                text = stringResource(R.string.home_ai_analysis_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant
            )
        }

        // ── Pico y placa card (card 4) ────────────────────────────────────
        val (c4Alpha, c4Ty) = staggeredEntrance(4)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(c4Alpha)
                .offset(y = c4Ty.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceContainerLow)
                .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                .clickable { onNavigateToPicoPlaca() }
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Pulsing green dot
                PulsingDot()
                Column {
                    Text(
                        text = "Pico y Placa",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = OnSurface
                    )
                    Text(
                        text = stringResource(R.string.home_pico_exempt),
                        style = MaterialTheme.typography.labelSmall,
                        color = Secondary
                    )
                    Text(
                        text = stringResource(R.string.home_pico_placa_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(PrimaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text("+", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ── Animated start day button ─────────────────────────────────────────────────
@Composable
private fun StartDayButton() {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "startBtnScale"
    )
    Button(
        onClick = { /* TODO */ },
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .scale(scale),
        colors = ButtonDefaults.buttonColors(containerColor = Secondary),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = stringResource(R.string.home_start_day_button),
            style = MaterialTheme.typography.titleMedium,
            color = Color.Black
        )
    }
}

// ── Pulsing status dot ────────────────────────────────────────────────────────
@Composable
private fun PulsingDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    Box(contentAlignment = Alignment.Center) {
        // Outer pulse ring
        Box(
            modifier = Modifier
                .size(16.dp)
                .scale(pulseScale)
                .alpha(pulseAlpha)
                .clip(CircleShape)
                .background(Secondary)
        )
        // Inner solid dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Secondary)
        )
    }
}
