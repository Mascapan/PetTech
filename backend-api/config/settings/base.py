"""
Configuración base de PetTech (común a todos los ambientes).

Los valores sensibles (SECRET_KEY, credenciales de base de datos) se leen desde
variables de entorno o desde un archivo .env que NO se versiona (ver .env.example).
Los ambientes concretos están en development.py y production.py.
"""
from datetime import timedelta
from pathlib import Path

import environ

BASE_DIR = Path(__file__).resolve().parent.parent.parent

env = environ.Env()
# Lee backend-api/.env si existe (solo desarrollo local; en el piloto se usan variables del servicio).
environ.Env.read_env(BASE_DIR / ".env", overwrite=False)

SECRET_KEY = env("DJANGO_SECRET_KEY")
DEBUG = env.bool("DJANGO_DEBUG", default=False)
ALLOWED_HOSTS = env.list("DJANGO_ALLOWED_HOSTS", default=[])

# --- GeoDjango en Windows: rutas opcionales a las bibliotecas nativas (OSGeo4W) ---
_gdal = env("GDAL_LIBRARY_PATH", default="")
_geos = env("GEOS_LIBRARY_PATH", default="")
if _gdal:
    GDAL_LIBRARY_PATH = _gdal
if _geos:
    GEOS_LIBRARY_PATH = _geos

# ----------------------------------------------------------------------------
# Aplicaciones: monolito modular, un módulo Django por dominio.
# ----------------------------------------------------------------------------
DJANGO_APPS = [
    "django.contrib.admin",
    "django.contrib.auth",
    "django.contrib.contenttypes",
    "django.contrib.sessions",
    "django.contrib.messages",
    "django.contrib.staticfiles",
    "django.contrib.gis",
]
THIRD_PARTY_APPS = [
    "rest_framework",
    "knox",
    "drf_spectacular",
]
LOCAL_APPS = [
    "apps.core",
    "apps.accounts",
    "apps.pets",
    # Fase 2: "apps.clinics", "apps.access_control"
    # Fase 3: "apps.clinical_records"
]
INSTALLED_APPS = DJANGO_APPS + THIRD_PARTY_APPS + LOCAL_APPS

MIDDLEWARE = [
    "django.middleware.security.SecurityMiddleware",
    "django.contrib.sessions.middleware.SessionMiddleware",
    "django.middleware.locale.LocaleMiddleware",
    "django.middleware.common.CommonMiddleware",
    "django.middleware.csrf.CsrfViewMiddleware",
    "django.contrib.auth.middleware.AuthenticationMiddleware",
    "django.contrib.messages.middleware.MessageMiddleware",
    "django.middleware.clickjacking.XFrameOptionsMiddleware",
]

ROOT_URLCONF = "config.urls"
# Ruta del panel Django Admin (configurable para no exponer la ruta por defecto en el piloto).
ADMIN_URL = env("DJANGO_ADMIN_URL", default="admin/")
WSGI_APPLICATION = "config.wsgi.application"
ASGI_APPLICATION = "config.asgi.application"

TEMPLATES = [
    {
        "BACKEND": "django.template.backends.django.DjangoTemplates",
        "DIRS": [],
        "APP_DIRS": True,
        "OPTIONS": {
            "context_processors": [
                "django.template.context_processors.request",
                "django.contrib.auth.context_processors.auth",
                "django.contrib.messages.context_processors.messages",
            ],
        },
    },
]

# ----------------------------------------------------------------------------
# Base de datos: PostgreSQL + PostGIS (motor GeoDjango).
# Formato: postgis://USUARIO:CLAVE@HOST:PUERTO/BASE
# ----------------------------------------------------------------------------
DATABASES = {"default": env.db("DATABASE_URL")}
DATABASES["default"]["ENGINE"] = "django.contrib.gis.db.backends.postgis"
DATABASES["default"].setdefault("CONN_MAX_AGE", env.int("DB_CONN_MAX_AGE", default=60))
DATABASES["default"]["ATOMIC_REQUESTS"] = False  # transacciones explícitas en operaciones críticas

DEFAULT_AUTO_FIELD = "django.db.models.BigAutoField"
AUTH_USER_MODEL = "accounts.User"

# ----------------------------------------------------------------------------
# Contraseñas (RNF-01): Argon2id como algoritmo principal; PBKDF2 como respaldo.
# ----------------------------------------------------------------------------
PASSWORD_HASHERS = [
    "django.contrib.auth.hashers.Argon2PasswordHasher",
    "django.contrib.auth.hashers.PBKDF2PasswordHasher",
    "django.contrib.auth.hashers.PBKDF2SHA1PasswordHasher",
]
AUTH_PASSWORD_VALIDATORS = [
    {"NAME": "django.contrib.auth.password_validation.UserAttributeSimilarityValidator"},
    {
        "NAME": "django.contrib.auth.password_validation.MinimumLengthValidator",
        "OPTIONS": {"min_length": 10},
    },
    {"NAME": "django.contrib.auth.password_validation.CommonPasswordValidator"},
    {"NAME": "django.contrib.auth.password_validation.NumericPasswordValidator"},
]

# ----------------------------------------------------------------------------
# Localización: español de Chile, zona horaria America/Santiago.
# Las fechas se almacenan en UTC (USE_TZ) y se presentan en hora de Chile.
# ----------------------------------------------------------------------------
LANGUAGE_CODE = "es-cl"
TIME_ZONE = "America/Santiago"
USE_I18N = True
USE_TZ = True

STATIC_URL = "static/"
STATIC_ROOT = BASE_DIR / "staticfiles"

# ----------------------------------------------------------------------------
# Django REST Framework
# - Denegar por defecto: toda vista exige autenticación salvo que declare lo contrario.
# - Solo autenticación por token (sin sesión) en la API: evita CSRF en clientes móviles.
# ----------------------------------------------------------------------------
REST_FRAMEWORK = {
    "DEFAULT_AUTHENTICATION_CLASSES": ["knox.auth.TokenAuthentication"],
    "DEFAULT_PERMISSION_CLASSES": ["rest_framework.permissions.IsAuthenticated"],
    "DEFAULT_RENDERER_CLASSES": ["rest_framework.renderers.JSONRenderer"],
    "DEFAULT_PARSER_CLASSES": ["rest_framework.parsers.JSONParser"],
    "DEFAULT_PAGINATION_CLASS": "apps.core.pagination.StandardPagination",
    "PAGE_SIZE": 20,
    "DEFAULT_SCHEMA_CLASS": "drf_spectacular.openapi.AutoSchema",
    "EXCEPTION_HANDLER": "apps.core.exceptions.api_exception_handler",
    "DEFAULT_THROTTLE_CLASSES": [
        "rest_framework.throttling.AnonRateThrottle",
        "rest_framework.throttling.UserRateThrottle",
        "rest_framework.throttling.ScopedRateThrottle",
    ],
    "DEFAULT_THROTTLE_RATES": {
        "anon": env("THROTTLE_ANON", default="60/min"),
        "user": env("THROTTLE_USER", default="240/min"),
        # Protección contra fuerza bruta en inicio de sesión y registro.
        "auth": env("THROTTLE_AUTH", default="10/min"),
    },
    "DATETIME_FORMAT": "iso-8601",
    "DATE_FORMAT": "iso-8601",
    "TEST_REQUEST_DEFAULT_FORMAT": "json",
}

# Tokens (django-rest-knox): se guardan como hash SHA-512, con expiración y revocables.
REST_KNOX = {
    "TOKEN_TTL": timedelta(days=env.int("TOKEN_TTL_TUTOR_DAYS", default=7)),
    "AUTO_REFRESH": False,
    "TOKEN_PREFIX": "pt_",
    "AUTH_HEADER_PREFIX": "Token",
}
# Duraciones por rol (las cuentas profesionales acceden a datos clínicos: sesión más corta).
PETTECH_TOKEN_TTL = {
    "TUTOR": timedelta(days=env.int("TOKEN_TTL_TUTOR_DAYS", default=7)),
    "PROFESSIONAL": timedelta(hours=env.int("TOKEN_TTL_PROFESSIONAL_HOURS", default=12)),
    "ADMIN": timedelta(hours=env.int("TOKEN_TTL_ADMIN_HOURS", default=8)),
}
PETTECH_MAX_TOKENS_PER_USER = env.int("MAX_TOKENS_PER_USER", default=5)

# Versión vigente de la política de privacidad que el tutor debe aceptar (HU-01).
PETTECH_PRIVACY_POLICY_VERSION = env("PRIVACY_POLICY_VERSION", default="2026-11-borrador")

# Documentación OpenAPI (RNF-07).
SPECTACULAR_SETTINGS = {
    "TITLE": "PetTech API",
    "DESCRIPTION": (
        "API REST del prototipo PetTech (Proyecto de Título TIH184, INACAP). "
        "Incremento 1: cuentas, autenticación y mascotas del tutor."
    ),
    "VERSION": "1.0.0-fase1",
    "SERVE_INCLUDE_SCHEMA": False,
    "COMPONENT_SPLIT_REQUEST": True,
    "SERVE_PERMISSIONS": ["rest_framework.permissions.IsAdminUser"],
}

# Caché (usada por la limitación de tasa). En el piloto se recomienda una caché compartida.
CACHES = {
    "default": {
        "BACKEND": "django.core.cache.backends.locmem.LocMemCache",
        "LOCATION": "pettech-default",
    }
}

# ----------------------------------------------------------------------------
# Registro de eventos: nunca registrar contraseñas ni tokens.
# ----------------------------------------------------------------------------
LOG_LEVEL = env("DJANGO_LOG_LEVEL", default="INFO")
LOGGING = {
    "version": 1,
    "disable_existing_loggers": False,
    "formatters": {
        "simple": {"format": "%(asctime)s %(levelname)s %(name)s: %(message)s"},
    },
    "handlers": {
        "console": {"class": "logging.StreamHandler", "formatter": "simple"},
    },
    "root": {"handlers": ["console"], "level": LOG_LEVEL},
    "loggers": {
        "django.db.backends": {"level": "WARNING"},
        # Las respuestas 4xx son errores esperables del cliente; solo se registran los 5xx.
        # Los eventos de seguridad (accesos denegados) se registrarán en la bitácora de auditoría (Fase 2).
        "django.request": {"level": env("DJANGO_REQUEST_LOG_LEVEL", default="ERROR")},
        "pettech": {"handlers": ["console"], "level": LOG_LEVEL, "propagate": False},
    },
}
