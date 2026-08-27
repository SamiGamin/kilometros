package co.samidev.kilometrix.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.samidev.kilometrix.domain.model.ShiftType
import co.samidev.kilometrix.presentation.home.HomeUiState
import co.samidev.kilometrix.ui.theme.*

@Composable
fun HomeActiveShiftBalanceCard(
    uiState: HomeUiState,
    modifier: Modifier = Modifier
) {
    val isPersonalShift = uiState.activeShift?.type == ShiftType.PERSONAL
    val netProfit = uiState.shiftNetProfit
    val totalEarnings = uiState.shiftTotalEarnings
    val totalExpenses = uiState.shiftTotalExpenses

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF131B2E))
            .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                .background(if (isPersonalShift) Primary else if (netProfit < 0) Error else Primary)
        )
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isPersonalShift) "RECORRIDO PERSONAL ACTIVO"
                           else "BALANCE NETO DEL RECORRIDO",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isPersonalShift) Primary.copy(alpha = 0.15f)
                            else if (netProfit >= 0) Secondary.copy(alpha = 0.15f)
                            else Error.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (isPersonalShift) "🏠 Personal"
                               else if (netProfit >= 0) "🟢 Ganancia"
                               else "🔴 Pérdida / Gasto",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isPersonalShift) Primary else if (netProfit >= 0) Secondary else Error,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            if (isPersonalShift) {
                Text(
                    text = if (totalExpenses > 0) "$ ${homeCurrencyFmt.format(totalExpenses.toLong())} en gastos"
                           else "~${"%.1f".format(uiState.estimatedKmTraveled)} km",
                    style = MaterialTheme.typography.displayLarge,
                    color = OnSurface
                )
                Text(
                    text = if (totalExpenses > 0) "Gastos registrados durante este trayecto personal"
                           else "Trayecto particular sin registro de ganancias",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
            } else {
                Text(
                    text = "$ ${homeCurrencyFmt.format(netProfit.toLong())}",
                    style = MaterialTheme.typography.displayLarge,
                    color = if (netProfit >= 0) OnSurface else Error
                )

                HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("🟢", fontSize = 12.sp)
                        Text("Ingresos:", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                        Text(
                            "$ ${homeCurrencyFmt.format(totalEarnings.toLong())}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("🔴", fontSize = 12.sp)
                        Text("Gastos:", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                        Text(
                            "$ ${homeCurrencyFmt.format(totalExpenses.toLong())}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Error
                        )
                    }
                }
            }
        }
    }
}
