# Registro de decisiones técnicas (ADR resumido)

Formato: contexto → decisión → alternativas → consecuencias. Estado: **Adoptada**, **Propuesta** (requiere aprobación
del equipo) o **Pendiente**.

| ID | Decisión | Estado |
|---|---|---|
| DEC-001 | Raíz del repositorio = carpeta `Prototipo` | Adoptada |
| DEC-002 | Monolito modular Django con apps por dominio | Adoptada (Informe 1 §7.1) |
| DEC-003 | PostgreSQL + PostGIS (motor GeoDjango) desde el incremento 1 | Adoptada |
| DEC-004 | Configuración por ambiente + variables de entorno | Adoptada |
| DEC-005 | UUID internos + código PetTech público no secuencial | Adoptada |
| DEC-006 | Tokens con django-rest-knox, TTL por rol | Adoptada |
| DEC-007 | Argon2id para contraseñas | Adoptada |
| DEC-008 | Errores uniformes; 404 para recursos ajenos | Adoptada |
| DEC-009 | Eliminación de cuenta (HU-13) vs. integridad clínica | **Pendiente** |
| DEC-010 | Perfil de mascota = datos declarados por el tutor | Adoptada |
| DEC-011 | Android: Compose + Material 3, MVVM, DI manual | Adoptada |
| DEC-012 | Sesión Android: DataStore + AES-GCM (Android Keystore) | Adoptada |
| DEC-013 | Versiones Android fijadas | Adoptada |
| DEC-014 | Docker Compose como alternativa de desarrollo en Windows | Propuesta |
| DEC-015 | Solo tutores se autorregistran | Adoptada |
| DEC-016 | Administrador sin edición clínica | Adoptada |
| DEC-017 | Textos de interfaz en Kotlin (sin `strings.xml`) en incremento 1 | Adoptada (deuda técnica) |
| DEC-018 | Ajuste de alcance y calendario para Fases 2–5 | **Propuesta** |
| DEC-019 | App móvil reorganizada según el boceto del equipo (5 vistas) | Adoptada |
| DEC-020 | Ficha clínica estandarizada en una sola entidad tipada e inmutable | Adoptada |
| DEC-021 | Acceso de veterinarias: código PetTech + autorización temporal del tutor, verificada en cada operación | Adoptada |
| DEC-022 | Mapa con Maps SDK (opcional por clave) + ruta por intent; ubicación con Fused Location | Adoptada |
| DEC-023 | Foto de la mascota solo en el teléfono; recuperación de contraseña con código de 6 dígitos | Adoptada |
| DEC-024 | Recomendaciones escritas del profesor (pase QR, IA de primera contención, cápsulas) | **Pendiente** |
| DEC-025 | Portal web (Django Templates) para administrador, veterinarias y profesionales | Adoptada (pedido del equipo, 08-11-2026) |
| DEC-026 | Rol CLINIC: la veterinaria autoriza a sus profesionales y publica su horario | Adoptada |
| DEC-027 | Preparación para Google Play: targetSdk 36, firma fuera de Git, privacidad y eliminación de cuenta | Adoptada |

---

### DEC-001 — Raíz del repositorio
Se trabaja directamente en `Prototipo/` (sin subcarpeta `PetTech/`), según instrucción del equipo.

### DEC-002 — Monolito modular
**Contexto:** Informe 1 §7.1 descarta microservicios por costo de operación. **Decisión:** un proyecto Django
(`config/`) con apps `core`, `accounts`, `pets` y, en fases siguientes, `clinics`, `access_control`, `clinical_records`,
sobre una sola base de datos. **Consecuencia:** transacciones locales simples para operaciones críticas (autorización +
bitácora); despliegue único.

### DEC-003 — PostGIS desde el inicio
Aunque el incremento 1 no tiene campos geográficos, se usa `django.contrib.gis.db.backends.postgis`. Las pruebas
verifican la extensión y una consulta `ST_Distance`. Evita descubrir problemas de GDAL/PostGIS recién en la Fase 4.
**Riesgo:** instalar GDAL en Windows (mitigado con DEC-014 o OSGeo4W).

### DEC-004 — Ambientes
`config/settings/base.py`, `development.py`, `production.py`. Secretos en `.env` (plantilla `.env.example`).
`production.py` rechaza `SECRET_KEY` de desarrollo, exige `ALLOWED_HOSTS`, activa HTTPS/HSTS/cookies seguras.
`check --deploy` solo advierte `SECURE_HSTS_PRELOAD` (no se activa: es un compromiso irreversible para el dominio).

### DEC-005 — Identificadores
PK `uuid4` para usuarios y mascotas. Código público `PT-XXXX-XXXX` (32^8 ≈ 1,1 × 10¹² combinaciones) generado con
`secrets`, `UNIQUE` en BD y reintento ante colisión. Alfabeto Crockford (sin I, L, O, U) y normalización tolerante al
dictado. **El código identifica; no autoriza** (RN-01).

### DEC-006 — Autenticación por token
**Alternativas evaluadas:**
| Opción | Ventajas | Desventajas |
|---|---|---|
| DRF `TokenAuthentication` | incluido | token en texto plano en BD, sin expiración, uno por usuario |
| `djangorestframework-simplejwt` | estándar JWT, sin consulta a BD | revocación requiere lista negra; *refresh* añade lógica al cliente |
| **`django-rest-knox`** | hash SHA-512 en BD, expiración, varias sesiones, revocación inmediata | dependencia externa (Jazzband) |

**Decisión:** knox. TTL tutor 7 días, profesional 12 h, admin 8 h; máximo 5 sesiones. Coincide con "autenticación por
token" del Informe 1 (Tabla 29).

### DEC-007 — Argon2id
Primer `PASSWORD_HASHERS` = Argon2 (recomendación OWASP *Password Storage Cheat Sheet*); PBKDF2 se mantiene para
compatibilidad. Cumple RNF-01 ("función de derivación de clave"). Validadores: largo ≥ 10, contraseñas comunes,
solo numéricas, similitud con datos del usuario.

### DEC-008 — Errores y ocultamiento de existencia
Formato `{"error":{"code","message","fields"}}`. Recurso ajeno → **404**; rol no permitido → **403**; sin token → **401**.
Errores no controlados → 500 genérico, traza solo en el log del servidor.

### DEC-009 — HU-13 (eliminar cuenta) frente a registros clínicos — **PENDIENTE**
**Conflicto:** HU-13 (Must, Ley 21.719) pide eliminar mascotas, eventos y documentos del titular; la retroalimentación
exige no alterar silenciosamente registros clínicos y soportar antecedentes de varios establecimientos.
**Opciones:**
1. Eliminación total (incluye registros profesionales) con confirmación explícita y bitácora del hecho.
2. Eliminar datos personales y antecedentes declarados por el tutor; los registros profesionales se **desvinculan y
   seudonimizan** (sin datos del tutor), conservados por el establecimiento que los generó.
3. Bloquear la eliminación mientras existan registros profesionales y derivar a solicitud manual.

**Recomendación técnica:** opción 2 para el prototipo, documentando que la base legal de conservación requiere
validación (no somos abogados; no constituye asesoría legal). **Mientras se decide:** `owner` usa `PROTECT`.

### DEC-010 — Origen de los datos
El perfil de la mascota (incluido microchip y antecedentes generales) es **declarado por el tutor**
(`data_origin = OWNER_DECLARED`). El microchip no es único en BD para evitar que un tercero "bloquee" un número ajeno;
no se verifica contra el Registro Nacional (fuera de alcance, Informe 1 Tabla 15).

### DEC-011 — Arquitectura Android
- **Jetpack Compose + Material 3**: UI declarativa recomendada por Google para Android nativo; un solo proyecto con
  navegación diferenciada por rol.
- **MVVM** con `ViewModel` + `StateFlow`; repositorios como única puerta a la API.
- **Navigation Compose** (rutas de texto).
- **Retrofit + OkHttp + kotlinx.serialization**: cliente REST estándar; serialización sin reflexión.
- **DI manual** (`AppContainer`) en vez de Hilt/Koin: grafo pequeño; menos configuración y riesgo en el plazo.

### DEC-012 — Almacenamiento seguro de la sesión
`androidx.security:security-crypto` (EncryptedSharedPreferences) fue deprecada por Google. Se usa **DataStore** con
cifrado **AES-256/GCM** y clave no exportable del **Android Keystore** (`TokenCipher`). `allowBackup=false` y reglas de
extracción excluyen los datos locales de respaldos y transferencias (OWASP MASVS-STORAGE).

### DEC-013 — Versiones Android
AGP 8.7.3 · Gradle 8.11.1 · Kotlin 2.1.0 · Compose BOM 2024.12.01 · `compileSdk/targetSdk 35` · `minSdk 28` (RNF-06).
Combinación estable y compatible entre sí; Android Studio puede proponer actualizarla (aceptar solo después de
compilar y probar). **Nota:** Google Play exige `targetSdk` más reciente para publicar; no aplica al prototipo.

### DEC-014 — Docker Compose (propuesta)
GeoDjango requiere GDAL/GEOS nativos; en Windows su instalación es la principal fuente de fallas. `docker-compose.yml`
levanta PostGIS y la API con GDAL incluido. **No cambia el stack** (sigue siendo Django + PostgreSQL/PostGIS) y la imagen
sirve de base para el despliegue en Render. Alternativa nativa documentada (OSGeo4W). *El equipo debe confirmar si tiene
Docker Desktop.*

### DEC-015 — Registro solo de tutores
`/auth/register/` crea siempre `TUTOR`; cualquier `role`, `is_staff` o `is_superuser` enviado se ignora (probado).
Las cuentas profesionales se crean en Django Admin y se verifican en la Fase 2 (RN-10).

### DEC-016 — Administrador sin edición clínica
`PetAdmin` es de solo lectura (sin agregar/editar/eliminar). La API de mascotas niega el rol ADMIN (403). En la Fase 3 el
historial clínico tampoco será editable desde Admin.

### DEC-017 — Textos de interfaz
App de un solo idioma (es-CL). En el incremento 1 los textos están en Kotlin para avanzar rápido; mover a
`strings.xml` en la Fase 5 si el tiempo lo permite.

### DEC-018 — Ajuste de alcance y calendario (PROPUESTA para aprobación y para el Informe 2)
**Contexto:** quedan ≈ 4 semanas (1–27 nov). El núcleo nuevo (Fases 2–3) es obligatorio por la retroalimentación;
las historias geográficas (HU-07..HU-12) son *Must* en el Informe 1 y sostienen OE3 y KPI-01/KPI-04.
**Propuesta:**
| Semana | Fase | Contenido mínimo |
|---|---|---|
| 1–8 nov | 1 (cierre) + 2 | Compilar y probar la app; establecimientos, profesionales, solicitudes, autorizaciones 24 h, revocación, bitácora. **Informe 2.** |
| 9–15 nov | 3 | Atenciones, vacunas, alergias, historial cronológico multiclínica, correcciones versionadas, vista del tutor |
| 16–22 nov | 4 reducida | Catálogo (Admin) con origen/fecha de verificación; búsqueda por cercanía PostGIS en **lista**; ficha; filtros urgencias/abierto según horario; botón "Cómo llegar" (intent de mapas, sin clave). Mapa embebido y recordatorios (HU-06) solo si hay capacidad |
| 23–27 nov | 5 | Despliegue piloto, prueba de uso (≥ 10 tutores), escenario con 2 clínicas, evidencias |

**Recortes que requieren aprobación:** HU-10 sin mapa embebido (solo apertura de ruta) si no hay clave de Maps;
HU-12 umbral fijo (ya previsto en el Informe 1 como primer recorte); HU-05 y HU-14 fuera del prototipo.

### DEC-019 — Interfaz según el boceto del equipo (8-11-2026)
Se reemplaza el panel de tarjetas del incremento 1 por el flujo dibujado por el equipo:
1) **Inicio de sesión** (usuario, contraseña, ¿olvidaste tu contraseña?, registrarse, continuar, Google);
2) **Mis mascotas** (fichas en cuadrícula + botón inferior "Buscar veterinaria");
3) **Ficha de la mascota** (foto, nombre, fecha, raza, información general, código PetTech y botón "Ficha clínica");
4) **Ficha clínica** (carnet de control: vacunas, desparasitaciones, visitas y quién atendió);
5) **Buscar veterinaria** (lista + mapa, priorizando las abiertas según horario declarado, con "Cómo llegar").
Se eliminan `WelcomeScreen` y `PetListScreen` (la vista 2 cumple su función). "Continuar con Google" se muestra pero
informa que estará disponible más adelante: requiere un cliente OAuth y verificación del token en el backend, no
justificados dentro del plazo. La campanita de "Mis mascotas" lleva a las solicitudes de acceso de veterinarias.

### DEC-020 — Ficha clínica estandarizada (respuesta a la retroalimentación escrita del 28-10-2026)
Una sola tabla `ClinicalEntry` con `entry_type` ∈ {VACCINE, DEWORMING, VISIT, ALLERGY, CHRONIC_CONDITION, SURGERY,
MEDICATION, WEIGHT}, `origin` (declarado por el tutor / registro profesional), autor y establecimiento "congelados"
como texto (sobreviven a cambios de nombre). **Alternativa descartada:** una tabla por tipo (más migraciones y
endpoints sin beneficio para el prototipo). **Inmutabilidad:** `save()` solo inserta y `delete()` falla para registros
profesionales; la corrección es un registro nuevo con `corrects` (OneToOne) y motivo. El resumen clínico (último peso,
alergias, patologías y fármacos vigentes, próximas fechas) se calcula en el servidor. **No se adopta** un estándar
externo (p. ej., HL7 FHIR): se documenta como evolución; los códigos de tipo son estables y exportables.

### DEC-021 — Acceso por código PetTech
El profesional verificado ingresa el código; se crea una solicitud (vigencia 24 h para responder). El tutor ve
establecimiento, profesional y motivo, y aprueba (autorización de 24 h, `ACCESS_GRANT_HOURS`) o rechaza; puede
revocar en cualquier momento. El vencimiento se evalúa al consultar (`effective_status`), sin tareas programadas.
Sin autorización vigente la API responde **404** (no revela la mascota) y registra el intento **denegado** en la
bitácora. Antes de la autorización el profesional solo ve el código que él ingresó.

### DEC-022 — Mapa y ubicación
Se mantiene lo comprometido en el Informe 1 (Tabla 29: Maps SDK for Android) mediante **Maps Compose**. La clave se lee
de `local.properties` (`MAPS_API_KEY`); **sin clave la app funciona en modo lista** y "Cómo llegar" abre la app de
mapas del teléfono (intent `google.navigation:` / `geo:`), por lo que HU-10 no depende de la facturación de Google.
Ubicación: `FusedLocationProviderClient.getCurrentLocation` solo al entrar a la búsqueda; si se rechaza el permiso, se
busca desde el centro de Chillán y se puede elegir un punto tocando el mapa (HU-07). La ubicación no se almacena.
Orden de resultados: abiertas según horario declarado primero, luego por distancia (boceto, vista 5).

### DEC-023 — Foto local y recuperación de contraseña
- **Foto:** se guarda en el almacenamiento interno del teléfono (reducida a 1024 px, excluida de respaldos). No se sube
  al servidor ni la ve la veterinaria; subir archivos es HU-05 (*Should*) y requiere almacenamiento de objetos.
- **Recuperación (HU-01):** código de 6 dígitos (más usable en móvil que un enlace), guardado como hash, 15 minutos,
  máximo 5 intentos, un solo uso; al cambiar la contraseña se cierran todas las sesiones. Respuesta idéntica exista o
  no el correo. En desarrollo el correo se imprime en la consola del servidor; para el piloto falta definir proveedor SMTP.

### DEC-024 — Recomendaciones escritas del profesor (28-10-2026) — PENDIENTE
La retroalimentación escrita (archivo `retroalimentacion.docx`, calificación 46,6/60) recomienda tres mejoras:
1. **Ficha clínica estandarizada + HU-14 como Must con "Pase clínico de emergencia" (QR / enlace firmado temporal).**
   La ficha estandarizada ya está implementada (DEC-020). El pase QR **no**; es compatible con la arquitectura
   (token firmado de un solo uso y de corta duración, vista de solo lectura, registrado en bitácora) y se estima en
   2–3 días. *Recomendación técnica:* implementarlo como siguiente prioridad, porque responde directamente a la crítica
   de "solo un CRUD" y a la interoperabilidad sin integración.
2. **Módulo de primera contención asistida por IA.** Contradice la regla del proyecto "sin IA" (CLAUDE.md) y conlleva
   riesgo clínico y legal. *Recomendación:* no incluir IA generativa en el prototipo; como alternativa defendible, un
   **protocolo estático validado** (fichas de primeros auxilios redactadas desde una guía citada) que deriva al buscador
   con filtro "Urgencias". Requiere decisión del equipo y, si se acepta la IA, aprobación expresa.
3. **Cápsulas de salud preventiva** por especie y etapa de vida desde Django Admin: bajo costo, *Should*.
La retroalimentación también exige **evidencia de las clínicas colaboradoras** (nombres, entrevistas, cartas): es
trabajo de gestión, no de software, y sigue pendiente (C-06).

### DEC-025 — Portal web en el mismo monolito (08-11-2026)
**Contexto:** el equipo pidió un portal web para el administrador (crear veterinarias, cuadros de análisis), las
veterinarias (autorizar profesionales) y los profesionales (solicitar acceso y usar la ficha clínica), conectado en
tiempo real con la app. CLAUDE.md prohibía "Django Templates como app de usuario": esa regla apuntaba al **tutor**, que
sigue usando solo la app Android. Este pedido expreso del equipo actualiza la regla.
**Decisión:** app `apps/portal` en el mismo proyecto Django, con plantillas del servidor, sesión de navegador
(cookie HttpOnly, CSRF, expiración 8 h, límite de intentos de ingreso) y **los mismos servicios** de
`access_control` y `clinical_records` que usa la API. Así la app y la web comparten base de datos y reglas: lo que el
profesional registra en la web aparece al instante en la app del tutor y viceversa.
**Alternativas descartadas:** SPA en React/Vue (otra base de código, CORS, tokens en el navegador, más tiempo);
usar Django Admin como portal (no permite separar roles con la granularidad requerida ni una interfaz amigable).
Chart.js 4.4.1 se incluye como archivo local (sin CDN). Django Admin se mantiene para administración avanzada.

### DEC-026 — Rol CLINIC y autogestión del catálogo
Nuevo rol `CLINIC` (cuenta del establecimiento, `Clinic.account`). Flujo de habilitación: el **administrador** crea la
veterinaria y su cuenta → la **veterinaria** crea y autoriza (o suspende) a sus profesionales → el **profesional**
solicita acceso con el código PetTech → el **tutor** autoriza en la app. Reemplaza la verificación de profesionales
por el administrador (RN-04/RN-10 siguen cumpliéndose: nadie se autorregistra como profesional).
La veterinaria publica ella misma horario, urgencias y servicios; al guardar, el dato queda "confirmado por el
establecimiento" con la fecha actual y en la bitácora. **Responde a la crítica del profesor** sobre datos digitados
manualmente por un integrante con SLA de 1 día hábil: ahora la fuente es el propio establecimiento, sin intermediario.

### DEC-027 — Preparación para Google Play
- `compileSdk`/`targetSdk` 36: obligatorio para apps nuevas desde el 31-08-2026. AGP 8.7.3 se mantiene (probado hasta 35)
  con `android.suppressUnsupportedCompileSdk=36` para no cambiar herramientas a semanas de la entrega; actualizar AGP
  después de la defensa.
- Firma de publicación con keystore propio leído desde `local.properties` (nunca en Git); usar Play App Signing.
- Política de privacidad pública en `/privacidad/` y página de eliminación en `/eliminar-cuenta/`; en la app,
  "Mi cuenta → Eliminar mi cuenta" registra la solicitud (`AccountDeletionRequest`) y cierra las sesiones.
  La ejecución sigue el criterio recomendado en DEC-009 (opción 2).
- Backend listo para producción: gunicorn + whitenoise + `render.yaml` (no desplegado: requiere cuenta del equipo).
