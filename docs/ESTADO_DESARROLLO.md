# Estado del desarrollo

**Última actualización:** 1 de noviembre de 2026 · **Incremento:** 1 (Fase 1 — arquitectura y sistema ejecutable)

Leyenda: ✅ verificado (ejecutado) · 🟡 escrito, verificación parcial · ⏳ pendiente · ⛔ bloqueado.

## 1. Implementado

### Backend (`backend-api/`) — ✅ verificado
| Elemento | Detalle | Verificación |
|---|---|---|
| Proyecto Django 5.2 LTS + DRF 3.16 + GeoDjango | monolito modular `core`, `accounts`, `pets`; settings `base/development/production` | `manage.py check` y `check --deploy` (solo aviso HSTS preload, intencional) |
| PostgreSQL 16 + PostGIS 3.4 | motor `postgis`; migraciones iniciales aplicadas | `DatabaseTests` (incluye `ST_Distance`) |
| Usuario personalizado | UUID, correo único sin mayúsculas, roles TUTOR/PROFESSIONAL/ADMIN, consentimiento versionado | pruebas `accounts` |
| Autenticación por token | registro (solo tutores), login, logout, logout-all, me; knox con hash, TTL por rol, máx. 5 sesiones; *throttling* 10/min | 21 pruebas `accounts` |
| Mascotas | CRUD propio, código `PT-XXXX-XXXX`, validaciones, aislamiento por tutor, 404 para ajenas, 403 para otros roles | 23 pruebas `pets` |
| Errores uniformes y salud | `{"error":{...}}`; `/api/v1/health/` (BD + PostGIS) | 7 pruebas `core` |
| Django Admin | usuarios (crear cuentas profesionales, suspender); mascotas solo lectura | revisión manual pendiente |
| OpenAPI | `docs/api/openapi.yaml` validado; Swagger en `/api/v1/docs/` (dev) | `spectacular --validate` |
| Datos simulados | `seed_demo`: 2 tutores, 1 profesional, 3 mascotas `[DEMO]` (bloqueado si `DEBUG=False`) | ejecutado |
| Prueba de humo HTTP | `scripts/smoke_test_api.py` | 14/14 ✅ |

### App Android (`mobile-android/`) — 🟡 escrita, NO compilada aún
| Elemento | Detalle |
|---|---|
| Proyecto | Kotlin 2.1, AGP 8.7.3, Compose BOM 2024.12.01, `minSdk 28` (Android 9), `targetSdk 35` |
| Arquitectura | MVVM (ViewModel + StateFlow), repositorios, DI manual, Navigation Compose |
| Red | Retrofit + OkHttp + kotlinx.serialization; `AuthInterceptor` (token + manejo de 401); errores del backend mapeados a mensajes por campo |
| Sesión | DataStore cifrado con AES-GCM (Android Keystore); restauración al abrir; expiración |
| Seguridad de plataforma | `allowBackup=false`; reglas de extracción; HTTP solo en *debug*; HTTPS obligatorio en *release*; *logging* HTTP solo nivel BASIC en *debug* |
| Pantallas | Bienvenida · Registro (con política de privacidad) · Inicio de sesión · Panel del tutor · Mis mascotas · Agregar/editar mascota · Carnet digital (código, copiar, eliminar con confirmación) · Panel profesional (informativo) |
| Identidad visual | Logotipo original (huella + escudo), paleta verde azulado/coral, ícono adaptable, tema claro/oscuro |
| Pruebas | `ValidatorsTest.kt` (8 pruebas JUnit) |

Verificación parcial realizada: compilación y ejecución de la capa de dominio y de `SessionManager` con kotlinc fuera de
Android, y análisis sintáctico de todos los archivos (`docs/evidencias/fase1/pruebas_android_dominio.md`).

## 2. En desarrollo / siguiente paso inmediato
1. **Equipo:** abrir `mobile-android` en Android Studio, sincronizar, compilar y ejecutar en emulador contra el backend.
   Reportar cualquier error de compilación tal como aparece (se corrige en la próxima intervención).
2. **Equipo:** levantar el backend en Windows (Docker u OSGeo4W) y ejecutar `python manage.py test`.
3. Fase 2 (al aprobar el plan DEC-018): `clinics`, `access_control`, bitácora.

## 3. Pendiente
| Elemento | Fase |
|---|---|
| Recuperación de contraseña por correo (HU-01) | 2 (requiere definir proveedor de correo, C-09) |
| Establecimientos, profesionales verificados, membresías | 2 |
| Solicitudes, aprobación/rechazo, autorización 24 h, revocación, bitácora | 2 |
| Atenciones, vacunas, alergias, correcciones versionadas, historial multiclínica | 3 |
| Antecedentes declarados por el tutor (HU-03) e historial con filtros (HU-04) | 3 |
| Búsqueda por cercanía, ficha con origen/fecha, filtros, ruta externa (HU-07..HU-12) | 4 (alcance reducido propuesto) |
| Recordatorios con WorkManager (HU-06) | 4 (si hay capacidad) |
| Despliegue piloto (Render + PostgreSQL/PostGIS gestionado), monitoreo, respaldos (RNF-08, NS-01) | 5 |
| Textos a `strings.xml`, R8 en *release*, pruebas instrumentadas de UI | 5 |

## 4. Bloqueado / dependencias externas
| Elemento | Bloqueo | Acción |
|---|---|---|
| Compilación Android en el entorno asistido | Sin Android SDK ni acceso a `maven.google.com` | El equipo compila en Android Studio |
| Mapa embebido (HU-10) | Clave de Google Maps Platform con facturación | Decidir si se habilita; alternativa sin clave: botón "Cómo llegar" |
| Decisión HU-13 vs. registros clínicos | DEC-009 | Equipo / profesor |
| Evidencia B2B | Sin validación con veterinarias (C-06) | Entrevista breve con los ≥ 2 establecimientos colaboradores |
| Confirmación escrita de la retroalimentación | No hay registro en el repositorio | Solicitar al profesor por correo |

## 5. Pruebas realizadas
| Fecha | Prueba | Resultado | Evidencia |
|---|---|---|---|
| 01-11-2026 | 51 pruebas automatizadas backend (PostgreSQL 16 + PostGIS 3.4) | ✅ 51/51 | `evidencias/fase1/pruebas_backend.md` |
| 01-11-2026 | Prueba de humo HTTP con servidor en ejecución | ✅ 14/14 | `evidencias/fase1/smoke_test_api.md` |
| 01-11-2026 | Persistencia verificada con consulta SQL directa | ✅ | `evidencias/fase1/persistencia_postgresql.md` |
| 01-11-2026 | `check --deploy` con settings de producción | ✅ (1 aviso intencional) | este documento |
| 01-11-2026 | Validación OpenAPI | ✅ | `docs/api/openapi.yaml` |
| 01-11-2026 | Dominio Android y `SessionManager` compilados con kotlinc + arnés | ✅ 25/25 | `evidencias/fase1/pruebas_android_dominio.md` |
| — | Compilación y ejecución de la app en emulador | ⏳ | — |
| — | Pruebas en Windows de los integrantes | ⏳ | — |

## 6. Limitaciones conocidas
- Los tiempos medidos son de desarrollo local y **no** sirven para RNF-04 (se miden en el piloto).
- La caché de *throttling* es local al proceso (en el piloto con varios procesos conviene una caché compartida).
- `docker-compose.yml` y `Dockerfile` no se ejecutaron en este entorno (sin demonio Docker); su sintaxis fue validada.
- El listado de mascotas en la app carga hasta 100 por página (suficiente para el piloto).
- El panel profesional del incremento 1 es solo informativo.
- La política de privacidad es un **borrador** sin revisión legal.

## 7. Riesgos para el cronograma (evaluación honesta)
- **Atraso**: según el Informe 1, al 25-oct debían existir cuentas y catálogo; el repositorio estaba vacío al 1-nov.
- **Carga**: Fases 2–3 (núcleo nuevo) + Fase 4 (compromisos del Informe 1) + Informe 2 en ≈ 4 semanas.
- **Mitigación**: plan DEC-018 (Fase 4 reducida), priorizar el escenario de demostración con dos clínicas, congelar
  alcance tras aprobar el Informe 2.
