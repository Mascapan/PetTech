"""
Ambiente de PILOTO / DESPLIEGUE.

Exige variables de entorno explícitas y activa los controles de transporte seguro (RNF-01).
Pendiente de validar en la fase de despliegue (Render + base PostgreSQL/PostGIS gestionada).
"""
from django.core.exceptions import ImproperlyConfigured

from .base import *  # noqa: F401,F403
from .base import ALLOWED_HOSTS, MIDDLEWARE, SECRET_KEY, env

DEBUG = False

CSRF_TRUSTED_ORIGINS = env.list("DJANGO_CSRF_TRUSTED_ORIGINS", default=[])

# Render informa el dominio público asignado al servicio (p. ej. pettech-api.onrender.com):
# se agrega automáticamente para no depender de configurarlo a mano.
_render_host = env("RENDER_EXTERNAL_HOSTNAME", default="")
if _render_host:
    ALLOWED_HOSTS = [*ALLOWED_HOSTS, _render_host]
    CSRF_TRUSTED_ORIGINS = [*CSRF_TRUSTED_ORIGINS, f"https://{_render_host}"]

if not ALLOWED_HOSTS:
    raise ImproperlyConfigured("DJANGO_ALLOWED_HOSTS es obligatorio en producción.")
# Clave aleatoria de al menos 256 bits: la que genera Render (generateValue) tiene 44 caracteres en base64.
if SECRET_KEY.startswith("dev-") or len(SECRET_KEY) < 40:
    raise ImproperlyConfigured("DJANGO_SECRET_KEY de producción debe ser aleatoria y de al menos 40 caracteres.")

# HTTPS obligatorio (el proveedor termina TLS y reenvía X-Forwarded-Proto).
SECURE_PROXY_SSL_HEADER = ("HTTP_X_FORWARDED_PROTO", "https")
SECURE_SSL_REDIRECT = env.bool("DJANGO_SECURE_SSL_REDIRECT", default=True)
SESSION_COOKIE_SECURE = True
CSRF_COOKIE_SECURE = True
SECURE_HSTS_SECONDS = env.int("DJANGO_SECURE_HSTS_SECONDS", default=60 * 60 * 24 * 30)
SECURE_HSTS_INCLUDE_SUBDOMAINS = True
SECURE_CONTENT_TYPE_NOSNIFF = True
SECURE_REFERRER_POLICY = "same-origin"
X_FRAME_OPTIONS = "DENY"
SESSION_COOKIE_HTTPONLY = True


# Archivos estáticos del portal web servidos por la propia aplicación (sin CDN externo).
MIDDLEWARE = list(MIDDLEWARE)
MIDDLEWARE.insert(MIDDLEWARE.index("django.middleware.security.SecurityMiddleware") + 1,
                  "whitenoise.middleware.WhiteNoiseMiddleware")
STORAGES = {
    "default": {"BACKEND": "django.core.files.storage.FileSystemStorage"},
    "staticfiles": {"BACKEND": "whitenoise.storage.CompressedManifestStaticFilesStorage"},
}
CSRF_COOKIE_HTTPONLY = True
SESSION_COOKIE_SAMESITE = "Lax"
