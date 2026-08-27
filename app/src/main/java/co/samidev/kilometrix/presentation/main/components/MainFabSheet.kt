package co.samidev.kilometrix.presentation.main.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import co.samidev.kilometrix.domain.model.ExpenseType
import co.samidev.kilometrix.domain.model.Vehicle
import co.samidev.kilometrix.presentation.components.AddEarningSheetContent
import co.samidev.kilometrix.presentation.transactions.AddExpenseSheetContent
import co.samidev.kilometrix.presentation.transactions.TransactionsViewModel
import co.samidev.kilometrix.ui.theme.Primary
import co.samidev.kilometrix.ui.theme.SurfaceContainerLow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainFabSheet(
    selectedVehicle: Vehicle?,
    userPlatforms: List<String>,
    transactionsViewModel: TransactionsViewModel,
    onDismiss: () -> Unit
) {
    var fabSheetTab by remember { mutableStateOf(0) } // 0=Gastos, 1=Ganancias
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceContainerLow
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
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
                        userPlatforms = userPlatforms,
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
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}
