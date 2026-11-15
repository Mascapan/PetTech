# Estado del desarrollo

**Última actualización:** 15 de noviembre de 2026 · **Incremento:** 4 — página pública, portal de la veterinaria,
ficha estructurada, red PetTech en el mapa, fotografías y transcripción del carnet.

## Incremento 4 (15-11-2026)
| Funcionalidad | Dónde | Estado |
|---|---|---|
| Página pública (autopromoción, descarga del APK, ingreso de veterinarias) | `/` (`LandingView`, `PETTECH_APK_URL`) | ✅ pruebas + captura |
| Perfil editable de la veterinaria (nombre, dirección, ubicación, contacto, "otros servicios") | Portal → Datos, horario y servicios | ✅ pruebas + captura |
| Cambio de contraseña (todos los roles del portal; cierra sesiones de la app) | `/portal/contrasena/` | ✅ pruebas |
| Métricas de atención de la veterinaria (tipo, especie, mes, día, profesional, vacunas, pacientes nuevos/recurrentes) | Portal → Métricas | ✅ pruebas + captura (datos SIMULADOS de `seed_demo`) |
| Ficha clínica en formulario estandarizado (SOAP + signos vitales + vacuna con laboratorio, lote, vencimiento, vía) | Portal profesional + API | ✅ pruebas + captura |
| Directorio no afiliado (OSM / manual con fuente) y afiliación a la Red PetTech | Portal admin + `import_osm_directory` | ✅ pruebas · ⏳ carga de datos reales de Chillán |
| Mapa OpenStreetMap con ubicación, marcadores Red PetTech vs directorio, filtro "Red PetTech" | App (osmdroid) | 🟡 escrito, falta compilar |
| Fotos: etiqueta de vacuna (portal y app), normalizadas sin EXIF, en PostgreSQL | API + portal + app | ✅ API/portal con pruebas · 🟡 app sin compilar |
| Transcripción asistida del carnet (OCR ML Kit en el teléfono + revisión) y vista "Ver como carnet" | App | 🟡 intérprete verificado (5 casos) · app sin compilar |

| Directorio inicial de 15 veterinarias de Chillán/Chillán Viejo (fuentes públicas, carga automática al desplegar) | `load_directory` | ✅ pruebas |
| Foto de la mascota en el servidor (con caché y migración de fotos locales) | API `pets/{id}/photo/` + app | ✅ API con pruebas · 🟡 app sin compilar |
| Registros de la ficha desplegables con todo el detalle, miniatura y foto del carnet de origen | App | 🟡 sin compilar |
| Recordatorios de vacunas/controles: 7 días antes, diario, 09:00–21:00 (HU-06, WorkManager) | App | 🟡 reglas verificadas (`ReminderPolicyTest`) · app sin compilar |

Backend: **147/147 pruebas** (PostgreSQL 16 + PostGIS). Pendiente: compilar la app (versión 0.4.0) en Android Studio y
desplegar en Render.

Incremento anterior: 2 (ficha clínica, acceso por código PetTech,
búsqueda de veterinarias y app según el boceto del equipo)

Leyenda: ✅ verificado (ejecutado) · 🟡 escrito, verificación parcial · ⏳ pendiente · ⛔ bloqueado.

## 1. Implementado

### Backend (`backend-api/`) — ✅ verificado (91/91 pruebas, PostgreSQL 16 + PostGIS 3.4)
| Módulo | Contenido | Pruebas |
|---|---|---|
| `accounts` | Registro de tutores, inicio de sesión, tokens knox, **recuperación de contraseña con código de 6 dígitos** | 25 |
| `pets` | Mascotas del tutor, código PetTech `PT-XXXX-XXXX`; eliminación protegida si hay registros de veterinarias (409) | 23 |
| `clinics` | Catálogo con ubicación PostGIS, horario declarado (incluye franjas nocturnas y 24 h), servicios, urgencias, origen y fecha de verificación, umbral de antigüedad; búsqueda por cercanía con filtros; abiertas primero | 11 |
| `access_control` | Solicitud por código PetTech, aprobación/rechazo/revocación por el tutor, autorización de 24 h, bitácora inmutable (incluye intentos denegados) | 13 |
| `clinical_records` | Ficha clínica estandarizada (8 tipos), origen tutor/profesional, autor y establecimiento, resumen clínico, correcciones versionadas, inmutabilidad | 12 |
| `core` | Errores uniformes (+409), permisos `IsTutor`/`IsProfessional`, salud, `seed_demo` ampliado | 7 |

OpenAPI regenerado y validado (`docs/api/openapi.yaml`, 31 operaciones). Recorrido HTTP manual verificado:
búsqueda → solicitud del profesional → aprobación del tutor → lectura y registro → bitácora.

### App Android (`mobile-android/`) — ✅ compilada y ejecutada en emulador (Android 16, API 37)
Pantallas según el boceto (DEC-019):

| Vista del boceto | Pantalla | Funciones |
|---|---|---|
| 1. Inicio de sesión | `LoginScreen`, `ForgotPasswordScreen`, `RegisterScreen` | Usuario/contraseña, olvidé mi contraseña (código por correo), registrarse, continuar; "Google" informa que llegará después |
| 2. Mis mascotas | `TutorHomeScreen` | Fichas en cuadrícula con foto, agregar mascota, aviso de solicitudes pendientes, botón inferior "Buscar veterinaria" |
| 3. Ficha de la mascota | `PetDetailScreen` | Foto (galería, guardada en el teléfono), nombre, fecha, raza, información general, código PetTech, accesos vigentes, botón "Ficha clínica" |
| 4. Ficha clínica | `ClinicalRecordScreen`, `EntryFormScreen` | Resumen (peso, alergias, patologías, fármacos, próximas fechas), filtro por tipo, quién/dónde atendió, agregar y corregir |
| 5. Buscar veterinaria | `ClinicSearchScreen`, `ClinicDetailScreen` | Mi ubicación / centro de Chillán / punto en el mapa; filtros abierto ahora, urgencias, servicio, radio; mapa (si hay clave); "Cómo llegar"; llamar |
| Tutor: accesos | `AccessScreen` | Autorizar 24 h, rechazar, revocar, historial y bitácora por mascota |
| Veterinario | `ProfessionalHomeScreen` + `ClinicalRecordScreen` | Ingresar código PetTech, solicitar acceso, ver estado, abrir y registrar en la ficha autorizada |

Nuevas dependencias: `play-services-location 21.3.0`, `play-services-maps 19.0.0`, `maps-compose 6.2.1`.
Pruebas JVM: `ValidatorsTest.kt` (12 pruebas; 4 nuevas) — sin ejecutar en este entorno.

### Incremento 3 — Portal web y publicación (08-11-2026)
| Elemento | Estado |
|---|---|
| Portal web `/portal/` con 3 perfiles (administrador, veterinaria, profesional), sesión segura, CSRF, límite de intentos | ✅ 19 pruebas |
| Cuadros de análisis del administrador (KPI + 6 gráficos, solo datos agregados) | ✅ revisado visualmente |
| Rol `CLINIC`: crea/autoriza/suspende profesionales; publica horario, urgencias y servicios (dato confirmado) | ✅ |
| Profesional en la web: código PetTech, solicitudes con autoactualización, ficha clínica, registro y corrección | ✅ |
| Páginas públicas `/privacidad/` y `/eliminar-cuenta/`; solicitud de eliminación desde la app | ✅ backend · 🟡 app |
| Producción: gunicorn + whitenoise + `render.yaml`; arranque verificado con settings de producción | ✅ local · ⏳ despliegue |
| App: `targetSdk 36`, versión 0.3.0, firma *release* desde `local.properties`, menú "Mi cuenta" | 🟡 ver §5 |
| Respaldo/restauración de BD (`scripts/*.ps1`) | 🟡 escrito, sin ejecutar en Windows |
| Total backend | ✅ **111/111** pruebas |
| Recorrido extremo a extremo (08-11-2026, 17:15): admin crea veterinaria nueva + cuenta → veterinaria autoriza profesional → profesional solicita con código PetTech en la web → tutor ve y aprueba con la API de la app → profesional registra vacuna en la web → tutor la ve en la app con autor y establecimiento; bitácora con lecturas y registro | ✅ (Playwright + API) |
| Código Kotlin de la app (incremento 3) | ✅ compila (620 clases, 0 errores) con el verificador local; ⏳ compilación Gradle con compileSdk 36 |

## 2. Siguiente paso inmediato (equipo)
1. ✅ Hecho el 08-11-2026 (sincronizado, compilado y ejecutado). Falta probar el rol profesional iniciando sesión con `vet1.demo@pettech.test`.
2. Backend con Docker: `docker compose up --build` y luego `docker compose exec api python manage.py migrate` y
   `docker compose exec api python manage.py seed_demo` (hay migraciones nuevas).
3. Opcional: clave de Maps SDK en `mobile-android/local.properties` → `MAPS_API_KEY=...` para ver el mapa.
4. En el emulador, fijar la ubicación en Chillán (Extended controls → Location: -36.6066, -72.1034) o usar el
   botón "Centro de Chillán".

## 3. Pendiente
| Elemento | Prioridad sugerida |
|---|---|
| Pase clínico de emergencia QR / enlace firmado (HU-14 *Must* según retroalimentación) | Alta — DEC-024 |
| Recordatorios con WorkManager (HU-06) | Media |
| Cápsulas de salud preventiva (retroalimentación) | Media |
| Adjuntos / foto del carnet de vacunas en el servidor (HU-05) | Baja |
| Eliminación de cuenta (HU-13, DEC-009) | Decisión pendiente |
| Inicio con Google | Futuro (no comprometido en el Informe 1) |
| Despliegue piloto, respaldos, medición RNF-04 | Fase 5 |

## 4. Bloqueado / dependencias externas
| Elemento | Bloqueo | Acción |
|---|---|---|
| Compilación Android en el entorno asistido | Sin acceso a `maven.google.com` | El equipo compila en Android Studio |
| Mapa embebido | Clave de Maps SDK (requiere cuenta de facturación de Google Cloud) | Sin clave funciona en modo lista + ruta externa |
| Correo real de recuperación | Proveedor SMTP no definido | En desarrollo el código se ve en la consola del servidor |
| Evidencia B2B | Sin entrevistas ni cartas de las clínicas colaboradoras (lo exige la retroalimentación) | Entrevista breve con ≥ 2 establecimientos |

## 5. Pruebas realizadas
| Fecha | Prueba | Resultado |
|---|---|---|
| 08-11-2026 | 91 pruebas automatizadas backend (PostgreSQL 16 + PostGIS 3.4) | ✅ 91/91 |
| 08-11-2026 | Validación OpenAPI | ✅ |
| 08-11-2026 | Recorrido HTTP manual del flujo veterinario y comparación de JSON con los DTO de la app | ✅ |
| 08-11-2026 | Compilación Kotlin 2.1 + Compose (603 clases, 0 errores) y 12/12 pruebas JUnit de `ValidatorsTest` | ✅ |
| 08-11-2026 | `gradle assembleDebug` + instalación en emulador desde Android Studio | ✅ |
| 08-11-2026 | Recorrido en emulador contra el backend en Docker: Mis mascotas → ficha → ficha clínica; Buscar veterinaria (permiso, respaldo a centro de Chillán, abiertas primero, ficha del establecimiento); solicitud de vet1 por código → aviso en campanita → Autorizar 24 h → registro del veterinario visible en la ficha del tutor | ✅ |
| — | Pantallas del profesional en la app (requiere iniciar sesión como `vet1.demo`) | ⏳ prueba manual del equipo |

## 6. Limitaciones conocidas
- Todos los establecimientos, cuentas y antecedentes de `seed_demo` son **ficticios** ([DEMO]/[SIMULADO]).
- "Abierto" se calcula con el horario declarado; no confirma atención ni cupos (Tabla 12 del Informe 1).
- La foto de la mascota no se sincroniza entre teléfonos ni la ve la veterinaria.
- El umbral de antigüedad se configura por variable de entorno, no desde el panel.
- Los textos de la app siguen en Kotlin (DEC-017).
