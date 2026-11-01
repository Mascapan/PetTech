# Matriz de trazabilidad

Problema → Objetivos → Requisitos → Arquitectura → Componentes de software → Pruebas → Evidencias.

Base: Informe 1 v3 (Tabla 3 causas C1–C5, Tabla 21 objetivos, Tablas 8–9 requisitos, Anexo D) + retroalimentación del
profesor (causa propuesta **C6**). Los elementos marcados *(propuesto)* deben formalizarse en el Informe 2.

## 1. Nivel problema → objetivo → requisito

| Causa del problema | Evidencia | Objetivo | Requisitos | Componentes | Indicadores |
|---|---|---|---|---|---|
| C1. Antecedentes en soportes dispersos | Encuesta P3 (carnet físico 51,4 %; papeles 27,0 %) | OE4 | HU-01..HU-04, HU-13; RNF-01..03 | `accounts`, `pets`, `clinical_records`; app (carnet) | KPI-02, KPI-06 |
| C2. Sin registro unificado bajo control del tutor | P3 (1 de 37 usa app); 8 menciones a centralizar | OE4 | HU-02..HU-04, HU-17, HU-18 | `pets`, `access_control` | KPI-02, KPI-06 |
| **C6 (propuesto).** Antecedentes clínicos no portables entre establecimientos; cada clínica conserva solo sus registros | Retroalimentación del profesor. **Sin evidencia empírica propia aún** (validar con colaboradores, ver C-06) | OE nuevo *(propuesto)*: implementar el historial clínico compartido con autorización del tutor | HU-15..HU-24; RNF-09..11; RN-01..08, RN-14 | `clinics`, `access_control`, `clinical_records` | KPI-B1/B2 *(propuestos)* |
| C3. Información veterinaria heterogénea | P5, P6 (32,4 % no sabe servicios) | OE3 | HU-07, HU-08, HU-10 | `clinics` (PostGIS), app (búsqueda/mapa) | KPI-01, NS-02 |
| C4. Urgencias y horarios poco claros | P6 (54,1 % no sabe si atiende urgencias) | OE3 | HU-08, HU-09 | `clinics` | KPI-01, KPI-03 |
| C5. Origen y actualidad del dato no explícitos | P6 (40,5 % horarios desactualizados o poco claros) | OE3 | HU-09, HU-11, HU-12 | `clinics`, Django Admin | KPI-03, KPI-04, NS-03 |
| Protección de datos del tutor | Ley 21.719; OWASP MASVS | OE2, OE4, OE5 | HU-13; RNF-01..03; RNF-09..11 | `accounts`, `core`, `access_control`; app (sesión cifrada) | Pruebas de seguridad |

## 2. Nivel requisito → componente → prueba → evidencia (incremento 1)

| Requisito | Componente de software | Pruebas automatizadas | Evidencia | Estado |
|---|---|---|---|---|
| HU-01 registro e inicio de sesión | `apps/accounts/{models,serializers,services,views}.py`; app `ui/screens/auth/*`, `data/repository/AuthRepository.kt` | `accounts/tests/test_auth.py` (21 pruebas) | `evidencias/fase1/pruebas_backend.md`, `smoke_test_api.md` #2–#5 | 🟡 sin recuperación por correo |
| HU-01 política de privacidad | `RegisterSerializer.validate_accept_privacy_policy`; `User.privacy_policy_*`; `RegisterScreen.kt` | `test_registro_exige_aceptar_politica_de_privacidad` | smoke #3 | ✅ backend |
| HU-02 perfil de mascotas | `apps/pets/*`; app `ui/screens/pets/*`, `PetRepository.kt` | `pets/tests/test_pets.py` (23 pruebas) | smoke #7–#9; `persistencia_postgresql.md` | ✅ backend / app sin compilar |
| Carnet digital + código PetTech | `pets/codes.py`, `Pet.public_code`; `PetDetailScreen.kt` | `PublicCodeTests`, `test_registrar_mascota_genera_codigo_publico` | smoke #7 | ✅ backend |
| RN-01 código ≠ autorización | `PetViewSet.get_queryset` (filtra por titular) | `test_conocer_el_codigo_publico_no_da_acceso` | — | ✅ (se extiende en F2) |
| RNF-01 contraseñas KDF / HTTPS | `PASSWORD_HASHERS` (Argon2); `production.py`; `network_security_config.xml` | `test_contrasena_almacenada_con_argon2`; `check --deploy` | `pruebas_backend.md` | 🟡 HTTPS sin desplegar |
| RNF-02 aislamiento por tutor | `IsTutor`, `PetViewSet.get_queryset` | `PetIsolationTests` (7 pruebas) | smoke #10–#12 | ✅ |
| RN-10 solo tutores se registran | `RegisterSerializer.create` | `test_cliente_no_puede_elegir_rol_profesional_ni_admin` | — | ✅ |
| RN-11 admin sin edición clínica | `PetAdmin` solo lectura; `IsTutor` | `test_administrador_no_tiene_acceso_a_mascotas_por_api` | — | ✅ |
| Seguridad: fuerza bruta | `throttle_scope = "auth"` | `test_limite_de_intentos_de_inicio_de_sesion` | — | ✅ |
| Seguridad: tokens | knox + `services.issue_token/revoke_*` | `SessionTests`, `test_token_se_guarda_como_hash`, `test_limite_de_sesiones_simultaneas` | smoke #13–#14 | ✅ |
| Seguridad: errores sin filtrar información | `core/exceptions.py` | `HealthAndErrorFormatTests` | — | ✅ |
| Persistencia en PostgreSQL/PostGIS | `DATABASES` (postgis) | `DatabaseTests` (3 pruebas) | `persistencia_postgresql.md` | ✅ |
| RNF-07 OpenAPI | drf-spectacular | `spectacular --validate` | `docs/api/openapi.yaml` | ✅ |
| Validación en cliente | `domain/validation/Validators.kt` | `ValidatorsTest.kt` (8 pruebas JUnit) | `pruebas_android_dominio.md` (arnés kotlinc) | 🟡 JUnit sin ejecutar en Android Studio |
| Sesión cifrada (MASVS-STORAGE) | `TokenCipher.kt`, `SessionStore.kt`, `SessionManager.kt` | arnés de `SessionManager` (restaurar / 401 / expiración) | `pruebas_android_dominio.md` | 🟡 cifrado Keystore solo verificable en dispositivo |
| Comunicación Android–API | `ApiService.kt`, `AuthInterceptor.kt` | `smoke_test_api.py` reproduce las mismas llamadas HTTP | `smoke_test_api.md` | ⏳ falta ejecución desde emulador |

## 3. Requisitos pendientes → fase planificada

| Requisito | Fase | Prueba prevista |
|---|---|---|
| HU-15 habilitación de profesionales | 2 | profesional no verificado / clínica suspendida no puede solicitar |
| HU-16..HU-18 solicitudes, aprobación, revocación, expiración 24 h | 2 | acceso sin autorización = 404/403; aprobado = 200; revocado = 404/403; expirado = 404/403 |
| RNF-09 bitácora | 2 | cada operación genera registro; bitácora no editable |
| HU-19..HU-23 historial compartido | 3 | clínica B lee atención de clínica A con autorización; no puede editarla |
| RNF-11 correcciones versionadas | 3 | corrección crea versión nueva; original intacto |
| HU-07..HU-12 búsqueda y catálogo | 4 | orden por distancia con PostGIS; "abierto según horario"; origen/fecha visibles |
| HU-06 recordatorios | 4 | WorkManager programa notificación |
| HU-13 eliminación de cuenta | por decidir (DEC-009) | según opción aprobada |
| RNF-04, RNF-08, NS-01 | 5 (piloto) | medición en entorno desplegado |
