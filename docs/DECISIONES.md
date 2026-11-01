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
