package co.samidev.kilometrix.presentation.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.samidev.kilometrix.domain.model.Vehicle
import co.samidev.kilometrix.domain.model.VehicleExpense
import co.samidev.kilometrix.domain.model.WorkShift
import co.samidev.kilometrix.domain.repository.ActiveVehicleRepository
import co.samidev.kilometrix.domain.usecase.GetExpensesUseCase
import co.samidev.kilometrix.domain.usecase.GetVehiclesUseCase
import co.samidev.kilometrix.domain.usecase.GetWorkShiftsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import kotlin.math.max

enum class AnalyticsPeriod {
    DAY, WEEK, MONTH
}

data class ChartBarData(
    val label: String,
    val value: Double
)

data class AnalyticsUiState(
    val vehicles: List<Vehicle> = emptyList(),
    val selectedVehicle: Vehicle? = null,
    val period: AnalyticsPeriod = AnalyticsPeriod.MONTH,
    val dateOffset: Int = 0,
    val periodLabel: String = "",
    val totalEarnings: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val netProfit: Double = 0.0,
    val kmDriven: Int = 0,
    val earningsPerKm: Double = 0.0,
    val totalHours: Double = 0.0,
    val platformEarnings: Map<String, Double> = emptyMap(),
    val chartData: List<ChartBarData> = emptyList(),
    val isLoading: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    getVehiclesUseCase: GetVehiclesUseCase,
    private val activeVehicleRepository: ActiveVehicleRepository,
    private val getWorkShiftsUseCase: GetWorkShiftsUseCase,
    private val getExpensesUseCase: GetExpensesUseCase
) : ViewModel() {

    private val _period = MutableStateFlow(AnalyticsPeriod.MONTH)
    private val _dateOffset = MutableStateFlow(0)

    val vehicles: StateFlow<List<Vehicle>> = getVehiclesUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedVehicle: StateFlow<Vehicle?> = combine(vehicles, activeVehicleRepository.activeVehicleId) { list, id ->
        if (id != null) list.firstOrNull { it.id == id } else list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val rawShifts = selectedVehicle.flatMapLatest { vehicle ->
        if (vehicle == null) flowOf(emptyList()) else getWorkShiftsUseCase(vehicle.id)
    }

    private val rawExpenses = selectedVehicle.flatMapLatest { vehicle ->
        if (vehicle == null) flowOf(emptyList()) else getExpensesUseCase(vehicle.id)
    }

    val uiState: StateFlow<AnalyticsUiState> = combine(
        combine(vehicles, selectedVehicle, ::Pair),
        combine(_period, _dateOffset, ::Pair),
        combine(rawShifts, rawExpenses, ::Pair)
    ) { (vehiclesList, vehicle), (period, offset), (shifts, expenses) ->
        val (startTimeMs, endTimeMs, label) = getPeriodRange(period, offset)
        
        val filteredShifts = shifts.filter { it.startTime in startTimeMs..endTimeMs }
        val filteredExpenses = expenses.filter { it.date in startTimeMs..endTimeMs }

        var totalEarnings = 0.0
        val platformMap = mutableMapOf<String, Double>()
        var kmDriven = 0
        var totalMs = 0L

        filteredShifts.forEach { shift ->
            shift.earnings.forEach { earning ->
                totalEarnings += earning.amount
                platformMap[earning.appName] = (platformMap[earning.appName] ?: 0.0) + earning.amount
            }
            if (shift.initialOdometer > 0 && shift.finalOdometer != null && shift.finalOdometer > shift.initialOdometer) {
                kmDriven += (shift.finalOdometer - shift.initialOdometer)
            }
            val end = shift.endTime ?: System.currentTimeMillis()
            totalMs += max(0L, (end - shift.startTime) - shift.pausedDurationMs)
        }

        val totalExpensesAmount = filteredExpenses.sumOf { it.amount }
        val netProfit = totalEarnings - totalExpensesAmount
        val earningsPerKm = if (kmDriven > 0) netProfit / kmDriven else 0.0
        val totalHours = totalMs / (1000.0 * 60 * 60)

        // Generate Chart Data
        val chartData = generateChartData(period, startTimeMs, endTimeMs, filteredShifts)

        AnalyticsUiState(
            vehicles = vehiclesList,
            selectedVehicle = vehicle,
            period = period,
            dateOffset = offset,
            periodLabel = label,
            totalEarnings = totalEarnings,
            totalExpenses = totalExpensesAmount,
            netProfit = netProfit,
            kmDriven = kmDriven,
            earningsPerKm = earningsPerKm,
            totalHours = totalHours,
            platformEarnings = platformMap,
            chartData = chartData
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AnalyticsUiState())

    fun setPeriod(period: AnalyticsPeriod) {
        _period.value = period
        _dateOffset.value = 0 // Reset offset when changing period
    }

    fun shiftOffset(amount: Int) {
        _dateOffset.value += amount
    }
    
    fun selectVehicle(vehicleId: String) {
        activeVehicleRepository.setActiveVehicleId(vehicleId)
    }

    private fun getPeriodRange(period: AnalyticsPeriod, offset: Int): Triple<Long, Long, String> {
        val bogotaTz = java.util.TimeZone.getTimeZone("America/Bogota")
        val cal = Calendar.getInstance(bogotaTz)
        cal.firstDayOfWeek = Calendar.MONDAY
        
        val startCal = cal.clone() as Calendar
        val endCal = cal.clone() as Calendar
        var label = ""
        
        when (period) {
            AnalyticsPeriod.DAY -> {
                startCal.add(Calendar.DAY_OF_YEAR, offset)
                startCal.set(Calendar.HOUR_OF_DAY, 0)
                startCal.set(Calendar.MINUTE, 0)
                startCal.set(Calendar.SECOND, 0)
                startCal.set(Calendar.MILLISECOND, 0)
                
                endCal.timeInMillis = startCal.timeInMillis
                endCal.add(Calendar.DAY_OF_YEAR, 1)
                endCal.add(Calendar.MILLISECOND, -1)
                
                val fmt = SimpleDateFormat("dd MMM yyyy", Locale("es", "CO"))
                label = if (offset == 0) "Hoy" else if (offset == -1) "Ayer" else fmt.format(startCal.time)
            }
            AnalyticsPeriod.WEEK -> {
                startCal.add(Calendar.WEEK_OF_YEAR, offset)
                startCal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                startCal.set(Calendar.HOUR_OF_DAY, 0)
                startCal.set(Calendar.MINUTE, 0)
                startCal.set(Calendar.SECOND, 0)
                startCal.set(Calendar.MILLISECOND, 0)
                
                endCal.timeInMillis = startCal.timeInMillis
                endCal.add(Calendar.DAY_OF_YEAR, 7)
                endCal.add(Calendar.MILLISECOND, -1)
                
                val fmt = SimpleDateFormat("dd MMM", Locale("es", "CO"))
                label = "${fmt.format(startCal.time)} - ${fmt.format(endCal.time)}"
                if (offset == 0) label = "Esta semana ($label)"
            }
            AnalyticsPeriod.MONTH -> {
                startCal.add(Calendar.MONTH, offset)
                startCal.set(Calendar.DAY_OF_MONTH, 1)
                startCal.set(Calendar.HOUR_OF_DAY, 0)
                startCal.set(Calendar.MINUTE, 0)
                startCal.set(Calendar.SECOND, 0)
                startCal.set(Calendar.MILLISECOND, 0)
                
                endCal.timeInMillis = startCal.timeInMillis
                endCal.add(Calendar.MONTH, 1)
                endCal.add(Calendar.MILLISECOND, -1)
                
                val fmt = SimpleDateFormat("MMMM yyyy", Locale("es", "CO"))
                label = fmt.format(startCal.time).replaceFirstChar { it.uppercase() }
            }
        }
        return Triple(startCal.timeInMillis, endCal.timeInMillis, label)
    }

    private fun generateChartData(period: AnalyticsPeriod, start: Long, end: Long, shifts: List<WorkShift>): List<ChartBarData> {
        val bogotaTz = java.util.TimeZone.getTimeZone("America/Bogota")
        val data = mutableListOf<ChartBarData>()
        val cal = Calendar.getInstance(bogotaTz)
        cal.timeInMillis = start

        when (period) {
            AnalyticsPeriod.DAY -> {
                // Group by hours (e.g. 00:00, 04:00, 08:00, etc.)
                // For simplicity, let's group by 4-hour chunks
                val chunks = 6
                for (i in 0 until chunks) {
                    val chunkStart = cal.timeInMillis
                    cal.add(Calendar.HOUR_OF_DAY, 4)
                    val chunkEnd = cal.timeInMillis - 1
                    
                    val earnings = shifts.filter { it.startTime in chunkStart..chunkEnd }
                        .sumOf { s -> s.earnings.sumOf { e -> e.amount } }
                    
                    val hourLabel = String.format("%02d:00", i * 4)
                    data.add(ChartBarData(hourLabel, earnings))
                }
            }
            AnalyticsPeriod.WEEK -> {
                // Group by day of week
                val format = SimpleDateFormat("EEE", Locale("es", "CO"))
                for (i in 0..6) {
                    val dayStart = cal.timeInMillis
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                    val dayEnd = cal.timeInMillis - 1
                    
                    val earnings = shifts.filter { it.startTime in dayStart..dayEnd }
                        .sumOf { s -> s.earnings.sumOf { e -> e.amount } }
                        
                    val calLabel = Calendar.getInstance(bogotaTz)
                    calLabel.timeInMillis = dayStart
                    data.add(ChartBarData(format.format(calLabel.time).replaceFirstChar { it.uppercase() }, earnings))
                }
            }
            AnalyticsPeriod.MONTH -> {
                // Group by week (approx 4-5 weeks)
                var weekCounter = 1
                while (cal.timeInMillis <= end) {
                    val weekStart = cal.timeInMillis
                    cal.add(Calendar.DAY_OF_YEAR, 7)
                    var weekEnd = cal.timeInMillis - 1
                    if (weekEnd > end) weekEnd = end
                    
                    val earnings = shifts.filter { it.startTime in weekStart..weekEnd }
                        .sumOf { s -> s.earnings.sumOf { e -> e.amount } }
                        
                    data.add(ChartBarData("Sem $weekCounter", earnings))
                    weekCounter++
                }
            }
        }
        return data
    }
}
