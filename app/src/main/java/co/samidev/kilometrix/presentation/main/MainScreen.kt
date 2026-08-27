package co.samidev.kilometrix.presentation.main

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import co.samidev.kilometrix.presentation.analytics.AnalyticsScreen
import co.samidev.kilometrix.presentation.home.HomeScreen
import co.samidev.kilometrix.presentation.main.components.MainBottomNavigationBar
import co.samidev.kilometrix.presentation.main.components.MainFabSheet
import co.samidev.kilometrix.presentation.main.components.MainFloatingActionButton
import co.samidev.kilometrix.presentation.main.components.MainTab
import co.samidev.kilometrix.presentation.profile.ProfileScreen
import co.samidev.kilometrix.presentation.transactions.ActionState
import co.samidev.kilometrix.presentation.transactions.TransactionsScreen
import co.samidev.kilometrix.presentation.transactions.TransactionsViewModel
import co.samidev.kilometrix.presentation.update.AppUpdateUiState
import co.samidev.kilometrix.presentation.update.AppUpdateViewModel
import co.samidev.kilometrix.presentation.update.UpdateAvailableDialog
import co.samidev.kilometrix.presentation.vehicle.VehicleScreen
import co.samidev.kilometrix.ui.theme.Background

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onLogout: () -> Unit,
    onNavigateToPicoPlaca: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(MainTab.HOME) }
    var showFabSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Auto-update ViewModel
    val appUpdateViewModel: AppUpdateViewModel = hiltViewModel()
    val appUpdateUiState by appUpdateViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        val currentVersionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
        appUpdateViewModel.checkForUpdates(currentVersionName)
    }

    // ViewModel compartido — instancia única para el sheet global
    val transactionsViewModel: TransactionsViewModel = hiltViewModel()
    val selectedVehicle by transactionsViewModel.selectedVehicle.collectAsState()
    val actionState by transactionsViewModel.actionState.collectAsState()
    val userPlatforms by transactionsViewModel.userPlatforms.collectAsState()

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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Background)
                .padding(innerPadding)
                .nestedScroll(nestedScrollConnection)
        ) {
            // ── Content with AnimatedContent slide transition ──────────────────
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val tabList = MainTab.entries
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
                    MainTab.HOME -> HomeScreen(
                        onNavigateToPicoPlaca = onNavigateToPicoPlaca,
                        onNavigateToGastos = {
                            selectedTab = MainTab.TRANSACTIONS
                            transactionsViewModel.setSelectedTab(1)
                        }
                    )
                    MainTab.TRANSACTIONS -> TransactionsScreen(viewModel = transactionsViewModel)
                    MainTab.VEHICLE -> VehicleScreen()
                    MainTab.ANALYTICS -> AnalyticsScreen()
                    MainTab.PROFILE -> ProfileScreen(onLogout = onLogout)
                }
            }

            // ── Unified FAB Sheet: Gastos | Ganancias ──────────────────
            if (showFabSheet) {
                MainFabSheet(
                    selectedVehicle = selectedVehicle,
                    userPlatforms = userPlatforms,
                    transactionsViewModel = transactionsViewModel,
                    onDismiss = { showFabSheet = false }
                )
            }

            // ── FAB with entrance animation ────────────────────────────────────
            MainFloatingActionButton(
                visible = isFabVisible && selectedTab != MainTab.PROFILE && selectedTab != MainTab.VEHICLE,
                onClick = { showFabSheet = true },
                modifier = Modifier.align(Alignment.BottomEnd)
            )

            // ── Bottom navigation bar ──────────────────────────────────────────
            MainBottomNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            // ── Update Available Dialog ─────────────────────────────────────────
            if (appUpdateUiState !is AppUpdateUiState.Idle && appUpdateUiState !is AppUpdateUiState.Checking) {
                UpdateAvailableDialog(
                    uiState = appUpdateUiState,
                    onConfirmUpdate = { info ->
                        appUpdateViewModel.startDownloadAndInstall(context, info)
                    },
                    onDismiss = {
                        appUpdateViewModel.dismissDialog()
                    }
                )
            }
        } // end Box
    } // end Scaffold
}
