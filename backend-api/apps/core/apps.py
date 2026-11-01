from django.apps import AppConfig


class CoreConfig(AppConfig):
    """Utilidades transversales: errores uniformes, paginación, permisos y salud del servicio."""

    default_auto_field = "django.db.models.BigAutoField"
    name = "apps.core"
    label = "core"
    verbose_name = "Núcleo"
