# Pruebas

Las pruebas viven junto al código que verifican (convención de Django y de Gradle):

| Ubicación | Tipo | Cómo ejecutar |
|---|---|---|
| `backend-api/apps/*/tests/` | Unitarias, integración API, seguridad (Django `APITestCase`, PostgreSQL/PostGIS real) | `cd backend-api && python manage.py test` |
| `backend-api/scripts/smoke_test_api.py` | Prueba de humo HTTP contra el servidor en ejecución | `python scripts/smoke_test_api.py --report ../docs/evidencias/faseN/smoke_test_api.md` |
| `mobile-android/app/src/test/` | Unitarias JVM (validaciones, sesión) | `gradlew.bat testDebugUnitTest` |

## Casos de seguridad cubiertos (incremento 1)
- Acceso anónimo denegado (401) · tokens inventados/expirados/revocados rechazados.
- Aislamiento entre tutores: listar, ver, editar y eliminar mascotas ajenas → 404.
- Roles: profesional y administrador no usan endpoints del tutor (403); el cliente no puede autoasignarse rol.
- Fuerza bruta: 11.º intento de inicio de sesión en un minuto → 429.
- Contraseñas con Argon2; tokens con hash; mensajes de error sin enumeración de cuentas ni trazas internas.

## Casos planificados (fases 2–3)
Veterinaria sin autorización · autorización vigente · revocación efectiva · caducidad a las 24 h · profesional no
verificado · clínica suspendida · edición de registros de otra clínica · corrección auditable · historial entre dos clínicas.
