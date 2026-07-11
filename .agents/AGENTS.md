# Sistema Maestro de Reglas para IA — Proyecto Android

## Objetivo
Este archivo define las reglas OBLIGATORIAS que cualquier IA, agente o asistente debe seguir al generar código, arquitectura, UI o lógica dentro del proyecto.

El objetivo es:
- Mantener coherencia técnica.
- Evitar malas prácticas y prevenir deuda técnica.
- Asegurar arquitectura limpia.
- Mantener seguridad avanzada.
- Garantizar calidad production-ready.
- Evitar inconsistencias visuales.
- Optimizar rendimiento Android.

La IA debe obedecer estas reglas estrictamente.

---

# STACK OFICIAL DEL PROYECTO

## Android
- Kotlin
- Jetpack Compose
- Material 3
- Navigation Compose
- Coroutines
- StateFlow
- Hilt
- Clean Architecture
- MVVM

## Backend (Uso EXCLUSIVO de Firebase)
- Firebase Authentication
- Firebase Realtime Database
- Firebase Firestore
- Firebase Cloud Storage
- Firebase Cloud Messaging
- Firebase Cloud Functions
- Firebase App Check
- Firebase Crashlytics
- Firebase Remote Config

*Nota: NO usar backend externo.*

---

# ARQUITECTURA OBLIGATORIA
La IA SIEMPRE debe usar:
- Clean Architecture
- MVVM
- Repository Pattern
- Use Cases
- StateFlow
- Immutable UI state
- Modularización clara

---

# ESTRUCTURA DE MÓDULOS OBLIGATORIA
- `app/`
- `core/`
- `data/`
- `domain/`
- `presentation/`
- `services/`
- `di/`
