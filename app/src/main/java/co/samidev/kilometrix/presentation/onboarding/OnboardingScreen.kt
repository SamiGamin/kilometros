package co.samidev.kilometrix.presentation.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.samidev.kilometrix.R
import co.samidev.kilometrix.ui.theme.*
import kotlinx.coroutines.launch

// ── Data model for each onboarding slide ──────────────────────────────────────
private data class OnboardingPage(
    val titleRes: Int,
    val bodyRes: Int,
    val emoji: String,
    val accentColor: Color
)

private val pages = listOf(
    OnboardingPage(
        titleRes = R.string.onboarding_slide1_title,
        bodyRes = R.string.onboarding_slide1_body,
        emoji = "📊",
        accentColor = Secondary      // Vibrant Green
    ),
    OnboardingPage(
        titleRes = R.string.onboarding_slide2_title,
        bodyRes = R.string.onboarding_slide2_body,
        emoji = "⚖️",
        accentColor = Primary        // Electric Blue
    ),
    OnboardingPage(
        titleRes = R.string.onboarding_slide3_title,
        bodyRes = R.string.onboarding_slide3_body,
        emoji = "🔧",
        accentColor = Tertiary       // Warning Amber
    ),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val currentPage = pagerState.currentPage
    val currentAccent = pages[currentPage].accentColor

    val animatedButtonColor by animateColorAsState(
        targetValue = currentAccent,
        animationSpec = tween(durationMillis = 400),
        label = "buttonColor"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Skip button
        TextButton(
            onClick = onNavigateToLogin,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 20.dp)
        ) {
            Text(
                text = stringResource(R.string.onboarding_skip),
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { index ->
                OnboardingPage(page = pages[index])
            }

            // Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Pagination dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    pages.forEachIndexed { index, page ->
                        val isSelected = index == currentPage
                        val width by animateDpAsState(
                            targetValue = if (isSelected) 28.dp else 8.dp,
                            animationSpec = tween(300),
                            label = "dotWidth"
                        )
                        val dotColor by animateColorAsState(
                            targetValue = if (isSelected) page.accentColor else OnSurfaceVariant.copy(alpha = 0.3f),
                            animationSpec = tween(300),
                            label = "dotColor"
                        )
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }
                }

                // Main action button — with spring press animation
                val isLastPage = currentPage == pages.lastIndex
                val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val btnScale by animateFloatAsState(
                    targetValue = if (isPressed) 0.96f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
                    label = "onboardingBtnScale"
                )
                Button(
                    onClick = {
                        if (currentPage < pages.lastIndex) {
                            scope.launch { pagerState.animateScrollToPage(currentPage + 1) }
                        } else {
                            onNavigateToRegister()
                        }
                    },
                    interactionSource = interactionSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .scale(btnScale),
                    colors = ButtonDefaults.buttonColors(containerColor = animatedButtonColor),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    AnimatedContent(
                        targetState = isLastPage,
                        transitionSpec = {
                            fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.85f) togetherWith
                            fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.85f)
                        },
                        label = "btnText"
                    ) { isLast ->
                        Text(
                            text = if (isLast) stringResource(R.string.onboarding_start) else stringResource(R.string.onboarding_next),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }
                }

                // Login link
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_have_account),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = onNavigateToLogin, contentPadding = PaddingValues(0.dp)) {
                        Text(
                            text = stringResource(R.string.onboarding_login_link),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                            color = Primary
                        )
                    }
                }

                // Home indicator
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(SurfaceContainerHigh)
                )
            }
        }
    }
}

@Composable
private fun OnboardingPage(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon container with scale pop animation per page
        val iconScale by animateFloatAsState(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            label = "iconScale"
        )
        Box(
            modifier = Modifier
                .size(128.dp)
                .scale(iconScale)
                .clip(CircleShape)
                .background(SurfaceContainerLow),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = page.emoji,
                style = MaterialTheme.typography.displayLarge
            )
        }

        Spacer(Modifier.height(48.dp))

        Text(
            text = stringResource(page.titleRes),
            style = MaterialTheme.typography.headlineMedium,
            color = OnSurface,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(page.bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            color = OnSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
