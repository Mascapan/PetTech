# Evidencia — Verificación parcial del código Android (incremento 1)

- Fecha: 2026-11-01T13:01:16-03:00
- **Alcance real de esta verificación:** el entorno de desarrollo asistido no tiene Android SDK ni acceso a los
  repositorios Maven de Google, por lo que **la app completa NO fue compilada ni ejecutada**. Se verificó lo siguiente:

| Verificación | Herramienta | Resultado |
|---|---|---|
| Compilación de la capa de dominio (`domain/model/*.kt`, `domain/validation/Validators.kt`) | kotlinc-jvm 2.4.21 (fuera de Android) | Compila sin errores |
| 21 comprobaciones de validación equivalentes a `ValidatorsTest.kt` (correo, contraseña, microchip, fechas dd-mm-aaaa, roles, especies) | Arnés de ejecución JVM | 21/21 correctas |
| Compilación de `SessionManager.kt` con kotlinx-coroutines (almacenamiento sustituido por un *stub* en memoria) | kotlinc-jvm 2.4.21 | Compila sin errores |
| Comportamiento de sesión: restaurar sesión vigente; cerrar sesión ante 401; descartar sesión vencida al iniciar; expiración con zona horaria | Arnés de ejecución JVM | 4/4 correctas |
| Sintaxis de los 37 archivos Kotlin y 3 scripts Gradle (KTS) | Analizador tree-sitter-kotlin | Sin errores (2 avisos falsos del analizador en tipos `@Composable () -> Unit`, sintaxis válida de Compose) |

## Pendiente de ejecutar por el equipo en Android Studio
1. Sincronizar Gradle y compilar (`assembleDebug`).
2. `testDebugUnitTest` (incluye `ValidatorsTest.kt`).
3. Ejecutar en emulador Android 9+ contra el backend local: registro → inicio de sesión → agregar mascota → carnet →
   editar → eliminar → cerrar sesión. Registrar capturas de pantalla en esta carpeta.
4. Verificar que el cifrado con Android Keystore funcione (cerrar y reabrir la app mantiene la sesión).
