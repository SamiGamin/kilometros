# Kilometrix

Kilometrix es una aplicación para Android diseñada para ayudar a los conductores a llevar un control preciso de los gastos de sus vehículos, con un enfoque avanzado en la eficiencia del combustible.

## Características Principales

*   **Gestión de Vehículos**: Permite registrar y administrar múltiples vehículos (carros, motos, vans).
*   **Registro de Gastos**: Seguimiento detallado de gastos como combustible, mantenimiento, peajes, seguros y parqueaderos.
*   **Modelo Multifase de Eficiencia de Combustible**:
    Un algoritmo inteligente para calcular el rendimiento real y proyectar la autonomía del vehículo mediante un "Tanque Virtual".
    *   **Fase 1 (Flujo de Caja)**: Registra el dinero gastado y los galones comprados antes de tener suficientes datos para calibrar.
    *   **Fase 2 (Calibración)**: Utiliza hitos de tanqueo (Reserva o Tanque Lleno) para establecer ciclos precisos y calcular el promedio de kilómetros por galón (R_prom) ponderado.
    *   **Fase 3 (Tanque Virtual)**: Proyecta los galones restantes en el tanque basándose en el consumo calibrado y la distancia recorrida desde el último tanqueo. Calcula la autonomía restante en kilómetros y en turnos de trabajo.
*   **Soporte Multimedida**: Permite registrar ingresos en galones, litros, kilovatios (kWh) o metros cúbicos (GNV), realizando las conversiones internamente.
*   **Pico y Placa Inteligente**: (Dependiendo de la ciudad) Evalúa en tiempo real si el vehículo tiene restricción de circulación según su placa, la hora del dispositivo y reglas específicas de exención (ej. vehículos eléctricos).
*   **Sincronización en la Nube**: Utiliza Firebase (Auth, Firestore) para guardar de forma segura los datos del usuario en la nube y soportar uso offline con datos locales de respaldo.

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
