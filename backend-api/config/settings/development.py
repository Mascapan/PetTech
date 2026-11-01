"""
Ambiente de DESARROLLO LOCAL.

Solo para equipos de los integrantes y emulador Android. Nunca usar en el piloto.
"""
from .base import *  # noqa: F401,F403
from .base import REST_FRAMEWORK, SPECTACULAR_SETTINGS, env

DEBUG = env.bool("DJANGO_DEBUG", default=True)

# 10.0.2.2 es la dirección con la que el emulador Android ve al equipo anfitrión.
ALLOWED_HOSTS = env.list(
    "DJANGO_ALLOWED_HOSTS", default=["localhost", "127.0.0.1", "10.0.2.2"]
)

# En desarrollo se habilita la API navegable de DRF para explorar endpoints.
REST_FRAMEWORK = {
    **REST_FRAMEWORK,
    "DEFAULT_RENDERER_CLASSES": [
        "rest_framework.renderers.JSONRenderer",
        "rest_framework.renderers.BrowsableAPIRenderer",
    ],
}

# En desarrollo la documentación OpenAPI es pública para facilitar las pruebas.
SPECTACULAR_SETTINGS = {
    **SPECTACULAR_SETTINGS,
    "SERVE_PERMISSIONS": ["rest_framework.permissions.AllowAny"],
}
