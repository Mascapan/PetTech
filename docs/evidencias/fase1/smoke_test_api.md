# Evidencia — Prueba de humo de la API (incremento 1)

- Fecha de ejecución: 2026-11-01T13:00:44-03:00
- Servidor: `http://127.0.0.1:8000` (desarrollo local, datos SIMULADOS)
- Script: `backend-api/scripts/smoke_test_api.py`

| # | Verificación | Resultado | Detalle |
|---|---|---|---|
| 1 | Salud del servicio y PostGIS | OK | (200, 31 ms) |
| 2 | Registro de tutor | OK | (201, 175 ms) |
| 3 | Registro sin aceptar política es rechazado | OK | (400) |
| 4 | Inicio de sesión con clave incorrecta es rechazado | OK | (400) |
| 5 | Inicio de sesión correcto (correo sin distinguir mayúsculas) | OK | (200, 162 ms) |
| 6 | Listado de mascotas sin token es rechazado (401) | OK | (401) |
| 7 | Registro de mascota con código PetTech | OK | (201, 19 ms, código PT-Z7QV-XM5B) |
| 8 | Consulta del carnet de la mascota propia | OK | (200, 20 ms) |
| 9 | Fecha de nacimiento futura es rechazada | OK | (400) |
| 10 | Otro tutor NO puede ver la mascota (404) | OK | (404) |
| 11 | Otro tutor NO puede editar la mascota (404) | OK | (404) |
| 12 | Listado del otro tutor no incluye mascotas ajenas | OK | (200) |
| 13 | Cierre de sesión | OK | (204) |
| 14 | Token revocado ya no sirve (401) | OK | (401) |

**Total:** 14/14 verificaciones correctas.

> Los tiempos son de un entorno de desarrollo local y NO representan el desempeño del piloto (RNF-04).
