# PetTech — Prototipo funcional

Proyecto de Título TIH184 · Ingeniería en Informática · INACAP · 2.º semestre 2026.

PetTech es una plataforma para **tutores de mascotas** (B2C) y **establecimientos veterinarios** (B2B) cuyo núcleo es un
**carnet e historial clínico digital compartido entre veterinarias autorizadas por el tutor**. Esta carpeta
(`Prototipo`) es la raíz del repositorio del software.

> **Estado actual: Incremento 1 (Fase 1).** Cuentas, autenticación, roles y mascotas del tutor con código PetTech.
> Detalle verificado en [`docs/ESTADO_DESARROLLO.md`](docs/ESTADO_DESARROLLO.md).

## Arquitectura

```
Aplicación Android (Kotlin, Jetpack Compose)  ──HTTPS/JSON──▶  API REST Django + DRF (GeoDjango)  ──▶  PostgreSQL + PostGIS
                                                                   │
                                                                   └── Django Admin (gestión interna)
```

La app **nunca** accede directamente a la base de datos. Toda autorización se decide en el servidor.
Ver [`docs/ARQUITECTURA.md`](docs/ARQUITECTURA.md).

## Estructura

```
Prototipo/
├── backend-api/          API Django REST Framework (monolito modular)
│   ├── config/           settings por ambiente (base / development / production), urls
│   ├── apps/core/        errores uniformes, permisos, paginación, salud, datos demo
│   ├── apps/accounts/    usuarios, roles, autenticación por token
│   ├── apps/pets/        mascotas, código PetTech, carnet
│   └── scripts/          prueba de humo HTTP
├── mobile-android/       App Android (Kotlin, MVVM, Compose)
├── docs/                 arquitectura, modelo de datos, requisitos, trazabilidad, decisiones, API, evidencias
├── tests/                guía de pruebas (las pruebas viven junto a cada módulo)
├── docker-compose.yml    PostgreSQL/PostGIS + API para desarrollo en Windows (opcional)
├── CLAUDE.md             reglas permanentes del proyecto
└── README.md
```

---

## 1. Requisitos de instalación (Windows)

| Herramienta | Versión | Para qué |
|---|---|---|
| Python | 3.12 o 3.13 | Backend |
| PostgreSQL + PostGIS | 16 + 3.4 | Base de datos |
| GDAL/GEOS (OSGeo4W) | la incluida en OSGeo4W | GeoDjango (solo opción nativa) |
| Docker Desktop (alternativa) | reciente, con WSL 2 | Levantar BD + API sin instalar GDAL |
| Android Studio | reciente (incluye JDK) | App móvil; SDK Platform 35 |
| Git | reciente | Control de versiones |

Hay **dos formas** de levantar el backend. Si tienen Docker Desktop, la opción A evita el paso más delicado en Windows
(instalar GDAL para GeoDjango).

### Opción A — Docker Desktop (recomendada en Windows)

```powershell
cd "C:\Users\nicol\Desktop\Nicolas\Inacap\8 - Semestre\Proyecto de Titulo\Prototipo"
copy .env.example .env        # y cambiar las claves locales
docker compose up --build
# en otra terminal, una sola vez: datos ficticios
docker compose exec api python manage.py seed_demo
```

API disponible en `http://localhost:8000/api/v1/`. Para ejecutar pruebas: `docker compose exec api python manage.py test`.

### Opción B — Instalación nativa

1. **PostgreSQL 16** (instalador de EDB) y, con *Stack Builder*, el paquete **PostGIS 3.x Bundle**.
2. Crear rol y base (en *SQL Shell (psql)* como `postgres`):
   ```sql
   CREATE ROLE pettech LOGIN PASSWORD 'elige_una_clave' CREATEDB;
   CREATE DATABASE pettech OWNER pettech;
   \c pettech
   CREATE EXTENSION postgis;
   -- Para que la base de PRUEBAS (test_pettech) herede PostGIS:
   \c template1
   CREATE EXTENSION postgis;
   ```
3. **GDAL/GEOS**: instalar [OSGeo4W](https://trac.osgeo.org/osgeo4w/) (instalación *Express*, paquete GDAL). Luego ubicar
   los archivos: `dir C:\OSGeo4W\bin\gdal*.dll` y `dir C:\OSGeo4W\bin\geos_c.dll`.
4. Backend:
   ```powershell
   cd backend-api
   py -3.12 -m venv .venv
   .\.venv\Scripts\Activate.ps1
   pip install -r requirements.txt
   copy .env.example .env      # y editar .env
   ```
   En `.env` ajustar `DATABASE_URL` con la clave elegida y, si Django no encuentra GDAL, descomentar
   `GDAL_LIBRARY_PATH` y `GEOS_LIBRARY_PATH` con los nombres reales de los `.dll`.
5. Migrar, cargar datos simulados y ejecutar:
   ```powershell
   python manage.py migrate
   python manage.py seed_demo
   python manage.py createsuperuser      # cuenta de administración (rol ADMIN)
   python manage.py runserver 0.0.0.0:8000
   ```

## 2. Verificar el backend

| Recurso | URL |
|---|---|
| Salud (BD + PostGIS) | `http://localhost:8000/api/v1/health/` |
| Documentación interactiva OpenAPI | `http://localhost:8000/api/v1/docs/` (solo en desarrollo) |
| Django Admin | `http://localhost:8000/admin/` |

```powershell
python manage.py test                     # 51 pruebas automatizadas (requiere PostgreSQL/PostGIS)
python scripts\smoke_test_api.py          # recorrido HTTP real contra el servidor en ejecución
```

## 3. Ejecutar la app Android

1. Abrir **Android Studio → Open** y seleccionar la carpeta `mobile-android`.
2. Aceptar la sincronización de Gradle (descarga dependencias la primera vez). Si lo pide, instalar *Android SDK Platform 35*.
3. Crear un emulador (Device Manager) con Android 9 o superior y presionar **Run ▶**.

### Conexión con la API

| Escenario | Configuración |
|---|---|
| Emulador Android | Nada que hacer: la app usa `http://10.0.2.2:8000/api/v1/` (10.0.2.2 = el PC anfitrión). |
| Teléfono físico (misma red Wi-Fi) | En `mobile-android/local.properties` agregar `pettech.apiBaseUrl=http://<IP-del-PC>:8000/api/v1/`; ejecutar el backend con `runserver 0.0.0.0:8000`; agregar la IP a `DJANGO_ALLOWED_HOSTS` en `.env`; permitir el puerto 8000 en el Firewall de Windows. |

La versión *debug* permite HTTP solo para desarrollo local; la versión *release* exige HTTPS.

### Cuentas de demostración (datos SIMULADOS)

Creadas por `python manage.py seed_demo` (contraseña = `DEMO_PASSWORD` de `.env`, por defecto `PetTech-Demo-2026`):

| Correo | Rol | Mascotas |
|---|---|---|
| `tutora.demo@pettech.test` | Tutor | Luna [DEMO], Michi [DEMO] |
| `tutor2.demo@pettech.test` | Tutor | Rocky [DEMO] (para probar aislamiento) |
| `vet1.demo@pettech.test` | Profesional | — (clínicas y verificación en Fase 2) |

El dominio `.test` está reservado y no corresponde a personas reales.

## 4. Pruebas

- Backend: `python manage.py test` (ver [`tests/README.md`](tests/README.md)).
- Android (JVM): en Android Studio, clic derecho en `app/src/test` → *Run Tests*, o `gradlew.bat testDebugUnitTest`.
- Evidencias: [`docs/evidencias/`](docs/evidencias/).

## 5. Seguridad (resumen)

Contraseñas con Argon2id · tokens con hash, expiración por rol y revocación · autorización a nivel de objeto
(una mascota ajena responde 404) · límite de intentos de inicio de sesión · errores sin detalles internos ·
sesión cifrada en el teléfono con Android Keystore · sin secretos en Git (`.env` excluido).

## 6. Documentación técnica

[Arquitectura](docs/ARQUITECTURA.md) · [Modelo de datos](docs/MODELO_DATOS.md) · [Requisitos](docs/REQUISITOS.md) ·
[Trazabilidad](docs/TRAZABILIDAD.md) · [Decisiones](docs/DECISIONES.md) · [API](docs/api/API.md) ·
[Estado del desarrollo](docs/ESTADO_DESARROLLO.md)
