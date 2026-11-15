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

## 2b. Incremento 2 (8-11-2026): ficha clínica, acceso por código PetTech, búsqueda y app según boceto

| Requisito | Componente de software | Pruebas automatizadas | Estado |
|---|---|---|---|
| HU-01 recuperación de acceso por correo | `accounts/services.start/confirm_password_reset`, `PasswordResetCode`; app `ForgotPasswordScreen.kt` | `accounts/tests/test_password_reset.py` (4) | ✅ backend · 🟡 app sin compilar |
| HU-03 antecedentes declarados (sin fechas futuras, próxima fecha) | `clinical_records` (`Origin.OWNER_DECLARED`); app `EntryFormScreen.kt` | `test_tutor_registra_antecedente_declarado`, `test_no_acepta_fechas_futuras_ni_proxima_anterior` | ✅ / 🟡 |
| HU-04 historial cronológico, filtro por tipo, próxima fecha | `services.current_entries`, `build_record_payload` (resumen + próximas fechas); app `ClinicalRecordScreen.kt` | `test_historial_multiclinica_y_filtro_por_tipo` | ✅ / 🟡 |
| Retroalimentación: ficha estandarizada (alergias, patologías crónicas, peso, cirugías, fármacos) | `EntryType` (8 tipos), `summary` | `test_profesional_registra_y_el_tutor_ve_quien_donde_cuando`, `test_peso_obligatorio_en_registro_de_peso` | ✅ / 🟡 |
| HU-07 búsqueda por cercanía (permiso solo al buscar; punto alternativo) | `clinics/services.search` (PostGIS `dwithin` + `Distance`); app `ClinicSearchScreen.kt`, `LocationProvider.kt` | `test_busqueda_por_cercania_respeta_el_radio_e_informa_distancia` | ✅ / 🟡 |
| HU-08 filtros combinables; "abierto según horario declarado"; vacío comunicado | `is_open_at` (incluye franjas nocturnas y 24 h), filtros `emergency/open_now/service` | `OpeningHoursTests` (4), `test_filtros_combinables`, `test_resultado_vacio_es_valido` | ✅ / 🟡 |
| Boceto vista 5: priorizar abiertas a la hora de consulta | orden `(no abierta, distancia)` en `search` | `test_abiertos_segun_horario_aparecen_primero` | ✅ |
| HU-09 ficha con origen y fecha de verificación; antigüedad destacada; sin cupos | `ClinicSerializer` (`info_origin`, `verified_at`, `is_stale`); app `ClinicDetailScreen.kt` | `test_ficha_incluye_origen_y_fecha_de_verificacion`, `test_informacion_antigua_se_marca` | ✅ / 🟡 |
| HU-10 mapa y ruta externa | app `ClinicsMap` (Maps Compose) + `openDirections` (intent) | manual (requiere clave `MAPS_API_KEY`) | 🟡 |
| HU-11 / HU-12 catálogo con bitácora, estados de verificación, umbral configurable | `ClinicAdmin` (LogEntry de Django Admin), `Clinic.info_origin`, `CLINIC_STALE_DAYS` | revisión manual en Admin | 🟡 |
| HU-15 profesionales verificados por establecimiento | `ProfessionalMembership`, `access.membership_for` | `test_profesional_sin_vinculo_verificado_no_puede_solicitar` | ✅ |
| HU-16 solicitud por código PetTech (tolerante al dictado) | `access_control/services.request_access`; app `ProfessionalHomeScreen.kt` | `test_profesional_solicita_con_codigo_tolerante_al_dictado`, `test_codigo_inexistente_y_formato_invalido` | ✅ / 🟡 |
| HU-17 / HU-18 aprobar, rechazar, revocar; expiración 24 h | `services.decide/revoke`, `AccessRequest.effective_status`; app `AccessScreen.kt` | `test_flujo_completo_aprobar_consultar_revocar`, `test_autorizacion_expira`, `test_rechazo_no_concede_acceso`, `test_otro_tutor_no_puede_aprobar` | ✅ / 🟡 |
| RN-01 código ≠ autorización | `require_active_grant` en cada operación | `test_conocer_el_codigo_no_da_acceso_a_la_ficha` | ✅ |
| HU-19..HU-21 consulta y registro profesional multiclínica | `ProfessionalRecordView`, `ProfessionalEntryCreateView` | `test_historial_multiclinica_y_filtro_por_tipo` | ✅ / 🟡 |
| HU-22 / RNF-11 / RN-14 correcciones versionadas, solo misma clínica | `services.correct`, `ClinicalEntry.save/delete` inmutables | `test_correccion_conserva_version_anterior`, `test_otra_clinica_no_corrige_registros_ajenos`, `test_registros_son_inmutables_en_el_modelo` | ✅ |
| HU-23 el tutor ve quién, dónde y cuándo | `author_name`, `clinic_name`, `origin` en cada entrada | `test_profesional_registra_y_el_tutor_ve_quien_donde_cuando` | ✅ / 🟡 |
| HU-24 / RNF-09 bitácora visible e inmutable (incluye denegados) | `AuditEvent` (solo inserción), `PetAuditLogView` | `test_tutor_ve_bitacora_de_su_mascota`, `test_bitacora_no_se_modifica` | ✅ / 🟡 |
| RN-11 admin sin acceso clínico | `IsTutor`/`IsProfessional`; admins de solo lectura | `test_tutor_ajeno_y_administrador_sin_acceso` | ✅ |
| C-03 eliminar mascota sin borrar registros de veterinarias | `PetViewSet.perform_destroy` → 409 | `test_mascota_con_registros_profesionales_no_se_elimina`, `test_mascota_solo_con_declarados_se_elimina` | ✅ (DEC-009 sigue pendiente para la cuenta) |

Total backend: **91 pruebas automatizadas, 91 OK** (PostgreSQL 16 + PostGIS 3.4, 8-11-2026).

## 3. Requisitos pendientes

| Requisito | Situación | Próximo paso |
|---|---|---|
| HU-14 → **Pase clínico de emergencia** (QR / enlace firmado temporal) | Recomendado como *Must* en la retroalimentación escrita (28-10-2026); no implementado | Decidir en el Informe 2 (ver DEC-024) |
| HU-05 adjuntos (foto del carnet, etiqueta y foto de la mascota) | ✅ Incremento 4: fotos en PostgreSQL (DEC-031) y transcripción asistida (DEC-032) | Medir % de campos corregidos en el piloto |
| HU-06 recordatorios | ✅ Incremento 4: WorkManager + notificaciones locales (DEC-034) | Medir avisos entregados en el piloto |
| HU-13 eliminación de cuenta | por decidir (DEC-009) | según opción aprobada |
| Cápsulas de salud preventiva por especie/etapa | Recomendación del profesor; no implementado | Evaluar como *Should* (Django Admin + lista en la app) |
| RNF-04, RNF-08, NS-01 | 5 (piloto) | medición en entorno desplegado |


## Incremento 4 (15-11-2026)
| Necesidad | Requisito | Diseño / código | Prueba |
|---|---|---|---|
| Difundir la app y facilitar su instalación | Página pública con descarga | `portal/public/landing.html` | `LandingTests` |
| Información del establecimiento al día, sin intermediario | HU-09/HU-12 ampliada | `ClinicProfileForm`, `update_clinic_profile` | `test_veterinaria_publica_su_horario`, `ClinicProfileV2Tests` |
| Seguridad de cuentas del portal | Cambio de contraseña con revocación de sesiones | `PortalPasswordChangeForm`, `change_password` | `PasswordChangeTests` |
| Gestión de la veterinaria | Indicadores agregados (sin datos individuales, RN-11) | `analytics.clinic_attention_metrics` | `ClinicMetricsTests` |
| Estandarizar la ficha (retroalimentación del profesor) | Formato SOAP + signos vitales + datos de la vacuna | `ClinicalEntry` (campos estructurados), `PROFESSIONAL_REQUIRED` | `test_flujo_completo_portal_y_app` |
| Encontrar veterinarias y distinguir la red | HU-07/HU-10 + DEC-029/030 | `directory.py`, `in_pettech_network`, `ClinicsMap.kt` | `test_directory.py`, `DirectoryAdminTests` |
| Respaldo fotográfico de vacunas | HU-05 | `Attachment`, `images.normalize` | `test_attachments.py`, `VetPhotoTests` |
| Usuarios que prefieren no digitar | HU-05 + DEC-032 | `CardTextParser`, `CardScanScreen` | `CardTextParserTest` |
