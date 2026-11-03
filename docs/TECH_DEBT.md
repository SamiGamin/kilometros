# Deuda técnica conocida — OBD Calibration v1.0

Generada durante la auditoría del arco P1–P18 (18 fixes de desalineación).
Ninguno bloquea el uso del Polo Track. Todos son mejoras futuras.

## DT-1: `previousOdometer` inconsistente en `RecalculateFuelChainUseCase`
- `previousOdometer` apunta al tanqueo inmediatamente anterior, no al hito A del ciclo
- `kmTraveled` sí refleja el ciclo completo (B − A)
- Impacto: exportaciones CSV y reportes por registro pueden mostrar odómetros que no cuadran
- Fix sugerido: exponer `idxA` en `cycleClosureByIdx` y usar `previousOdometer = normalized[idxA].od`

## DT-2: `recalculateFuelChainUseCase(all)` innecesario en `vehicleDataTriple`
- `allExpenses` solo se usa para filtrar `it.type != ExpenseType.FUEL` (no necesita chain)
- Impacto: CPU quemandose en cada actualización del flow
- Fix sugerido: usar `allExpenses` sin recálculo, o filtrar antes de recalcular

## DT-3: `ShiftFuelCostResolver` no extraído a `:domain`
- La jerarquía de 4 niveles (k_fuel calibrado → semilla → R_prom → cash-only) está inline en `HomeViewModel` y duplicada en `ShiftCard`
- Impacto: tests de la jerarquía requieren montar el VM completo; cambios futuros deben hacerse en 2 sitios
- Fix sugerido: crear `:domain/usecase/ResolveShiftFuelCostUseCase` que retorne un `ShiftFuelCost { costCop, sourceLabel, confidence }`

## DT-4: `FuelUnit.LITER` fully-qualified en `RecalculateFuelChainUseCase`
- Línea ~45: `co.samidev.kilometrix.domain.model.FuelUnit.LITER` cuando el archivo ya importa `FuelUnit`
- Impacto: cosmético, nada
- Fix sugerido: cambiar a `FuelUnit.LITER`

## DT-5: `if (deltaKm <= 0) continue` redundante en `CalculateObdCalibrationUseCase`
- Ya cubierto por la guarda `if (odA <= 0 || odB <= 0 || odB <= odA) continue` de P14
- Impacto: dead code, nada
- Fix sugerido: eliminar la línea

## DT-6: Sin test de integración `HomeViewModel` ↔ Firestore emulator
- `ObdCalibrationAndTelemetryTest` y los tests de use cases puros cubren la lógica aislada
- `P16` (fallback de `isFuelCalibrated` desde `Vehicle`) no tiene test end-to-end
- Impacto: regresiones en la cadena `Vehicle persistido → HomeViewModel.uiState` pasan desapercibidas
- Fix sugerido: agregar `HomeViewModelCalibrationTest` con `Firestore emulator` y `FakeVehicleRepository`

## DT-7: Caso borde de P16 — badge 🟢 cuando solo kDist está calibrado
- `hasPersistedCalibration = persistedKFuel != 1.0 || persistedKDist != 1.0`
- Si solo `kDist` está calibrado (turnos válidos sin ciclos cerrados), el badge dice "🟢 calibrado" aunque `kFuel = 1.0` (semilla)
- Impacto: semántica del badge ambigua en un caso borde
- Fix sugerido: usar solo `persistedKFuel != 1.0` para el flag, o introducir un `hasPersistedFuelCalibration` separado

## DT-13: `AnalyticsViewModel` conserva nomenclatura y cálculo de cash-flow para `netProfit` y `earningsPerKm`
- `AnalyticsUiState.netProfit` y `earningsPerKm` calculan ingresos menos gastos registrados (cash-flow), no ganancia operativa real
- Aunque el copy de `AnalyticsScreen` fue transparentado a "Flujo de Caja Neto" y "Flujo / Km", el estado y ViewModel aún usan nombres de ganancia neta
- Impacto: discrepancia entre la capa de presentación visual y el modelo de estado; ambigüedad para futuros desarrollos en analítica
- Fix sugerido: renombrar en `AnalyticsUiState` y `AnalyticsViewModel` a `cashFlow` y `cashFlowPerKm`, o introducir modelos separados cuando se integre OBD

## DT-14: Discrepancia conceptual entre Analytics (flujo de caja por compras) y Home/Transactions (costo operativo OBD)
- `HomeViewModel` y `TransactionsScreen` calculan ganancia descontando combustible quemado estimado por telemetría OBD (jerarquía §5), mientras `AnalyticsViewModel` solo resta compras de combustible del período
- En períodos con turnos pero sin tanqueos (`totalExpenses == 0.0 && totalEarnings > 0.0`), el flujo de caja no refleja el costo del combustible consumido
- Impacto: el usuario visualiza dos métricas con significados económicos diferentes para los mismos turnos según la pantalla consultada
- Fix sugerido: integrar agregación de combustible quemado por turnos en `AnalyticsViewModel` (DT-11) para permitir alternar entre vista de "Flujo de Caja" y vista de "Ganancia Operativa"

## DT-15: Analytics empty state (C4) no se activa si hay gastos no-combustible

- En `AnalyticsScreen.kt` P19 introdujo `if (uiState.totalExpenses == 0.0 && uiState.totalEarnings > 0.0)` para mostrar un hint cuando no hay tanqueos
- `AnalyticsUiState.totalExpenses` suma TODOS los gastos (FUEL + MAINTENANCE + TOLL + INSURANCE + PARKING + OTHER), no solo combustible
- Caso que falla: 1 turno de $50.000 + 1 mantenimiento de $80.000, 0 tanqueos → `totalExpenses = 80.000`, `totalEarnings = 50.000` → condición false → empty state NO se muestra aunque la causa real es "sin tanqueos"
- Impacto: el hint se activa exactamente cuando NO se necesita y se oculta cuando sí
- Fix sugerido: agregar `fuelExpensesSum: Double` a `AnalyticsUiState` calculado como `filteredExpenses.filter { it.type == ExpenseType.FUEL }.sumOf { it.amount }`, y cambiar la condición a `uiState.fuelExpensesSum == 0.0 && uiState.totalEarnings > 0.0`
- Bloqueante: ninguno. Requiere tocar `AnalyticsViewModel.kt`, fuera del scope de P19
- Prioridad: baja (cosmético, no rompe cálculos)

## DT-16: Cilindrada y capacidad de tanque son constantes globales, no campos por vehículo

- `VehicleProfile.DEFAULT_ENGINE_DISPLACEMENT_LITERS = 1.598` (Polo Track)
- `CalculateFuelEfficiencyUseCase.DEFAULT_TANK_CAPACITY_LITERS = 52.0` (Polo Track)
- Funcionan correctos mientras solo haya un Polo Track en la app
- Si algún día se agrega una moto (150cc = 0.150 L, tanque 12 L) o un segundo vehículo, las constantes dan valores incorrectos
- Impacto: latente, no bloquea. Solo se manifiesta con >1 vehículo de distinta cilindrada
- Fix sugerido: agregar `engineDisplacementLiters: Double = 1.598` y `tankCapacityLiters: Double = 52.0` a `Vehicle`, persistir en Firestore junto al resto del `Vehicle`, y pasarlos a `VehicleProfile`/`CalculateFuelEfficiencyUseCase` como parámetros
- Prioridad: baja (solo cuando se agregue otro vehículo)
