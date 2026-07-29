package co.samidev.kilometrix.presentation.main

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.samidev.kilometrix.R
import co.samidev.kilometrix.domain.model.ExpenseType
import co.samidev.kilometrix.presentation.analytics.AnalyticsScreen
import co.samidev.kilometrix.presentation.home.HomeScreen
import co.samidev.kilometrix.presentation.profile.ProfileScreen
import co.samidev.kilometrix.presentation.transactions.ActionState
import co.samidev.kilometrix.presentation.transactions.AddExpenseBottomSheet
import co.samidev.kilometrix.presentation.transactions.AddExpenseSheetContent
import co.samidev.kilometrix.presentation.components.AddEarningSheetContent
import co.samidev.kilometrix.presentation.transactions.RefuelCycleType
import co.samidev.kilometrix.presentation.transactions.TransactionsScreen
import co.samidev.kilometrix.presentation.transactions.TransactionsViewModel
import co.samidev.kilometrix.presentation.vehicle.VehicleScreen
import co.samidev.kilometrix.ui.theme.*
import androidx.hilt.navigation.compose.hiltViewModel

private enum class Tab(val labelRes: Int, val emoji: String) {
    HOME(R.string.nav_home, "🏠"),
    TRANSACTIONS(R.string.nav_transactions, "💵"),
    VEHICLE(R.string.nav_vehicle, "🚗"),
    ANALYTICS(R.string.nav_analytics, "📊"),
    PROFILE(R.string.nav_profile, "👤"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onLogout: () -> Unit,
    onNavigateToPicoPlaca: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(Tab.HOME) }
    var showFabSheet by remember { mutableStateOf(false) }
    var fabSheetTab by remember { mutableStateOf(0) } // 0=Gastos, 1=Ganancias

    // ViewModel compartido — instancia única para el sheet global
    val transactionsViewModel: TransactionsViewModel = hiltViewModel()
    val selectedVehicle by transactionsViewModel.selectedVehicle.collectAsState()
    val actionState by transactionsViewModel.actionState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Reacciona a éxito y error
    LaunchedEffect(actionState) {
        when (val state = actionState) {
            is ActionState.Success -> {
                showFabSheet = false
                transactionsViewModel.resetActionState()
            }
            is ActionState.Error -> {
                showFabSheet = false
                snackbarHostState.showSnackbar(state.message)
                transactionsViewModel.resetActionState()
            }
            else -> {}
        }
    }

    var isFabVisible by remember { mutableStateOf(true) }
    LaunchedEffect(selectedTab) {
        isFabVisible = true
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -10f) {
                    isFabVisible = false
                } else if (available.y > 10f) {
                    isFabVisible = true
                }
                return Offset.Zero
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Background
    ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().background(Background).padding(innerPadding).nestedScroll(nestedScrollConnection)) {

        // ── Content with AnimatedContent slide transition ──────────────────
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                val tabList = Tab.entries
                val fromIndex = tabList.indexOf(initialState)
                val toIndex = tabList.indexOf(targetState)
                val direction = if (toIndex > fromIndex) 1 else -1

                (slideInHorizontally(
                    initialOffsetX = { it * direction },
                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(200))) togetherWith
                (slideOutHorizontally(
                    targetOffsetX = { -it * direction },
                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(150)))
            },
            label = "tabContent",
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 72.dp)
        ) { tab ->
            when (tab) {
                Tab.HOME -> HomeScreen(
                    onNavigateToPicoPlaca = onNavigateToPicoPlaca,
                    onNavigateToGastos = {
                        selectedTab = Tab.TRANSACTIONS
                        transactionsViewModel.setSelectedTab(1)
                    }
                )
                Tab.TRANSACTIONS -> TransactionsScreen(viewModel = transactionsViewModel)
                Tab.VEHICLE -> VehicleScreen()
                Tab.ANALYTICS -> AnalyticsScreen()
                Tab.PROFILE -> ProfileScreen(onLogout = onLogout)
            }
        }

        // ── Unified FAB Sheet: Gastos | Ganancias ──────────────────
        if (showFabSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showFabSheet = false },
                sheetState = sheetState,
                containerColor = SurfaceContainerLow
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Tabs
                    PrimaryTabRow(
                        selectedTabIndex = fabSheetTab,
                        containerColor = SurfaceContainerLow,
                        contentColor = Primary
                    ) {
                        Tab(
                            selected = fabSheetTab == 0,
                            onClick = { fabSheetTab = 0 },
                            text = { Text("💸 Gastos", fontWeight = if (fabSheetTab == 0) FontWeight.Bold else FontWeight.Normal) }
                        )
                        Tab(
                            selected = fabSheetTab == 1,
                            onClick = { fabSheetTab = 1 },
                            text = { Text("💰 Ganancias", fontWeight = if (fabSheetTab == 1) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }

                    when (fabSheetTab) {
                        0 -> {
                            // ── Contenido de Gastos ────────────────────────
                            AddExpenseSheetContent(
                                vehicle = selectedVehicle,
                                previousOdometer = selectedVehicle?.odometer ?: 0,
                                onSave = { expense, fuelUnit, quantity, pricePerUnit, odometerNow, cycleType, dateMs ->
                                    val vehicleOdometer = selectedVehicle?.odometer ?: 0
                                    val finalExpense = if (expense.type == ExpenseType.FUEL) {
                                        val fuelDetails = transactionsViewModel.buildFuelDetails(
                                            enteredQuantity = quantity,
                                            enteredUnit = fuelUnit,
                                            pricePerUnit = pricePerUnit,
                                            odometerAtRefuel = odometerNow,
                                            previousOdometer = vehicleOdometer,
                                            isReserve = cycleType.isReserve,
                                            isFullTank = cycleType.isFullTank,
                                            isPartial = cycleType.isPartial
                                        )
                                        expense.copy(fuelDetails = fuelDetails)
                                    } else expense
                                    transactionsViewModel.addExpense(finalExpense)
                                }
                            )
                        }
                        1 -> {
                            // ── Contenido de Ganancias ─────────────────────
                            AddEarningSheetContent(
                                vehicle = selectedVehicle,
                                onSave = { appName, appEmoji, amount, isBonus, date ->
                                    transactionsViewModel.addStandaloneEarning(
                                        vehicleId = selectedVehicle?.id,
                                        appName = appName,
                                        appEmoji = appEmoji,
                                        amount = amount,
                                        isBonus = isBonus,
                                        date = date
                                    )
                                },
                                onDismiss = { showFabSheet = false }
                            )
                        }
                    }
                }
            }
        }

        // ── FAB with entrance animation ────────────────────────────────────
        AnimatedVisibility(
            visible = isFabVisible && selectedTab != Tab.PROFILE && selectedTab != Tab.VEHICLE,
            enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut(tween(150)) + fadeOut(tween(100)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 88.dp)
        ) {
            FloatingActionButton(
                onClick = { showFabSheet = true },
                containerColor = PrimaryContainer,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }

        // ── Bottom navigation bar ──────────────────────────────────────────
        BottomNavigationBar(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    } // end Box
    } // end Scaffold
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
        HorizontalDivider(color = OutlineVariant.copy(alpha = 0.4f), thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Tab.entries.forEach { tab ->
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

@Composable
private fun AnimatedNavItem(
    emoji: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // Spring-based scale for selected icon
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
            // Animated background pill for selected tab
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
