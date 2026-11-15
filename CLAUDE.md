# CLAUDE.md — Reglas permanentes del proyecto PetTech

Instrucciones para cualquier sesión de asistencia (humana o IA) que trabaje en este repositorio.

## Contexto
- Proyecto de Título TIH184 (INACAP, 2.º semestre 2026), equipo de dos estudiantes.
- Hitos: Informe 2 (primera semana de noviembre) · prototipo funcional (27 de noviembre) · defensa (fines de noviembre o diciembre).
- Línea base: *Proyecto de Título – Informe 1 v3*. La **retroalimentación más reciente del profesor tiene prioridad**:
  el núcleo es el **historial clínico compartido entre veterinarias autorizadas por el tutor** (ver `docs/REQUISITOS.md`).
- La raíz del repositorio es la carpeta `Prototipo`. No modificar informes ni presentaciones del directorio padre.

## Arquitectura obligatoria (no sustituir sin aprobación expresa del equipo)
Android nativo (Kotlin) → API REST Django + DRF + GeoDjango (monolito modular) → PostgreSQL + PostGIS.
- La app móvil nunca accede a la base de datos.
- Django Admin para administración interna.
- Portal web (`apps/portal`, Django Templates) SOLO para administrador, veterinarias y profesionales (DEC-025).
  El tutor usa exclusivamente la app Android.
- Prohibido introducir: React, Flutter, Firebase/Firestore, otra BD, microservicios, IA, pagos, chatbots,
  telemedicina, marketplace, iOS. Excepción documentada: OCR en el dispositivo (ML Kit, sin Firebase) solo para
  proponer la transcripción del carnet, siempre con confirmación del tutor (DEC-032).
- Mapa con OpenStreetMap (osmdroid, sin clave; DEC-030). Datos de Google Maps: no se copian (DEC-029).
  WorkManager implementado para HU-06 (recordatorios locales, sin push/Firebase; DEC-034).
- La app móvil sigue el boceto del equipo (DEC-019): login → Mis mascotas → ficha de mascota → ficha clínica; Buscar veterinaria.

## Módulos del backend (`backend-api/apps/`)
| App | Responsabilidad | Estado |
|---|---|---|
| `core` | errores uniformes, permisos por rol, paginación, salud, datos demo | Fase 1 |
| `accounts` | usuarios, roles (TUTOR, PROFESSIONAL, CLINIC, ADMIN), tokens, recuperación y eliminación de cuenta | ✅ |
| `pets` | mascotas, código PetTech, carnet | Fase 1 |
| `clinics` | establecimientos, profesionales, verificación, ubicación | Incremento 2 ✅ |
| `access_control` | solicitudes, autorizaciones temporales, revocación, bitácora | Incremento 2 ✅ |
| `clinical_records` | atenciones, vacunas, alergias, correcciones auditables | Incremento 2 ✅ |
| `portal` | portal web por rol, cuadros de análisis, páginas públicas (privacidad, eliminación) | Incremento 3 ✅ |

## Convenciones
- Código en inglés (nombres de clases, campos, endpoints); textos al usuario, comentarios y documentación en **español de Chile**.
- Zona horaria `America/Santiago`; fechas en BD en UTC (`USE_TZ=True`); fechas de API en ISO-8601.
- Endpoints versionados bajo `/api/v1/`, con barra final, recursos en plural, JSON.
- Errores: `{"error": {"code", "message", "fields?"}}`.
- Lógica de negocio en `services.py`; vistas delgadas; validación en serializers.
- Android: MVVM (ViewModel + StateFlow), Compose + Material 3, repositorios, DI manual en `AppContainer`.
- Cada funcionalidad nueva: requisito (HU/RNF) → código → prueba → evidencia → actualizar `docs/TRAZABILIDAD.md`
  y `docs/ESTADO_DESARROLLO.md`. Decisiones relevantes en `docs/DECISIONES.md`.

## Reglas de seguridad (no negociables)
1. Denegar por defecto: toda vista requiere autenticación salvo declaración explícita.
2. Autorización a nivel de objeto en el servidor en **cada** operación (filtrar queryset por titular o por autorización vigente).
3. Un código o identificador de mascota **no es una autorización**.
4. Recurso ajeno → 404 (no revelar existencia). Rol incorrecto → 403.
5. El rol ADMIN no otorga permisos clínicos; solo un PROFESSIONAL verificado, vinculado a un establecimiento con
   autorización vigente del tutor, puede leer o escribir historial.
6. Registros clínicos: nunca UPDATE/DELETE silencioso; las correcciones crean una nueva versión con autor, fecha y motivo.
7. Toda lectura/escritura del historial queda en la bitácora (actor, acción, recurso, fecha, resultado).
8. No registrar contraseñas, tokens ni datos clínicos en logs.
9. Nunca versionar `.env`, claves, keystores ni `local.properties`.
10. Solo datos ficticios en desarrollo (dominio `.test`, sufijo `[DEMO]`/`[SIMULADO]`).

## Comandos de desarrollo
```bash
# Backend (desde backend-api/, con el entorno virtual activo)
python manage.py migrate
python manage.py seed_demo
python manage.py runserver 0.0.0.0:8000
python manage.py test
python manage.py spectacular --file ../docs/api/openapi.yaml --validate
python scripts/smoke_test_api.py --report ../docs/evidencias/fase1/smoke_test_api.md

# Android (desde mobile-android/)
gradlew.bat assembleDebug
gradlew.bat testDebugUnitTest
```

## Restricciones permanentes
- No declarar que algo funciona si no se ejecutó. Distinguir: verificado · escrito sin ejecutar · pendiente · bloqueado.
- No inventar entrevistas, acuerdos con clínicas, métricas ni validaciones.
- No presentar horarios declarados como disponibilidad real de profesionales o cupos.
- No eliminar silenciosamente funcionalidades comprometidas en el Informe 1: proponer ajuste de alcance documentado.
