from django.apps import AppConfig


class AccountsConfig(AppConfig):
    """Usuarios, roles y autenticación."""

    default_auto_field = "django.db.models.BigAutoField"
    name = "apps.accounts"
    label = "accounts"
    verbose_name = "Cuentas y roles"
