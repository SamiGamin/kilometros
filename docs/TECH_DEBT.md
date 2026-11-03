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
