# Kilometrix

Kilometrix es una aplicación para Android diseñada para ayudar a los conductores a llevar un control preciso de los gastos de sus vehículos, con un enfoque avanzado en la eficiencia del combustible.

## Características Principales

*   **Gestión de Vehículos**: Permite registrar y administrar múltiples vehículos (carros, motos, vans).
*   **Registro de Gastos**: Seguimiento detallado de gastos como combustible, mantenimiento, peajes, seguros y parqueaderos.
*   **Telemetría OBD-II BLE en Vivo y Calibración Continua**:
    *   Conexión con adaptadores OBD-II BLE (ELM327 / V03H4) durante el turno.
    *   Cálculo físico de combustible por Speed-Density con régimen urbano (ralentí con factor VE=0.68 y corte DFCO a 0 L/h en desaceleración).
    *   Calibración estadística continua con EWMA ponderado por kilómetros para `k_fuel` con bandas de confianza y detección de inestabilidad.
    *   Calibración de distancia `k_dist` con mediana robusta por turno.
    *   Regla de cobertura estricta (≥90%, max gap 60 s, conexión al inicio y cierre).
*   **Modelo Multifase de Eficiencia de Combustible**:
    Un algoritmo inteligente para calcular el rendimiento real y proyectar la autonomía del vehículo mediante un "Tanque Virtual".
    *   **Fase 1 (Flujo de Caja)**: Registra el dinero gastado y los galones comprados antes de tener suficientes datos para calibrar.
    *   **Fase 2 (Calibración Homóloga)**: Ciclos exclusivos (Lleno-a-Lleno y Reserva-a-Reserva) para calcular el rendimiento real ($R_{prom}$) ponderado.
    *   **Fase 3 (Tanque Virtual)**: Proyecta el nivel del tanque y la autonomía restante en kilómetros y turnos de trabajo.
*   **Análisis y Observabilidad Honesta**:
    *   Distinción clara entre **Flujo de Caja Neto** (ingresos − compras de combustible − gastos registrados) y **Ganancia Operativa Real** basada en telemetría OBD.
    *   Nueva sección de **Observabilidad OBD** con 4 tarjetas de control: Consumo real vs. semilla nominal, Calidad de la medición (turnos válidos y cobertura), Estado de calibración (`k_fuel` y banda de confianza) y Reconciliación física (litros comprados vs. quemados).
*   **Soporte Multimedida**: Permite registrar ingresos en galones, litros, kilovatios (kWh) o metros cúbicos (GNV), realizando las conversiones internamente.
*   **Pico y Placa Inteligente**: (Dependiendo de la ciudad) Evalúa en tiempo real si el vehículo tiene restricción de circulación según su placa, la hora del dispositivo y reglas específicas de exención (ej. vehículos eléctricos).
*   **Sincronización en la Nube**: Utiliza Firebase (Auth, Firestore) para guardar de forma segura los datos del usuario en la nube y soportar uso offline con datos locales de respaldo.
*   **CI/CD Automatizado**: Integración con GitHub Actions para compilación, firma y generación automática de APKs en GitHub Releases al actualizar la versión.

## Arquitectura

El proyecto sigue los principios de **Clean Architecture** y **MVVM**:

*   **`domain`**: Modelos de negocio (`VehicleExpense`, `FuelEfficiencySummary`) y casos de uso (`CalculateFuelEfficiencyUseCase`).
*   **`data`**: Implementaciones de repositorios para interactuar con Firebase Firestore.
*   **`presentation`**: Pantallas construidas con Jetpack Compose y ViewModels usando StateFlow.

## Tecnologías

*   Kotlin
*   Jetpack Compose
*   Material 3
*   Coroutines & Flow
*   Hilt (Inyección de Dependencias)
*   Firebase (Firestore, Auth)

## Testing

El cálculo crítico de la eficiencia del combustible (`CalculateFuelEfficiencyUseCase`) está respaldado por pruebas unitarias completas que validan las diferentes fases del ciclo, la calibración y el tanque virtual utilizando datos de escenarios reales.
