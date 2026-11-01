"""
Cuentas de usuario de PetTech.

Decisiones:
- El identificador primario es un UUID (no secuencial) para resistir la enumeración.
- El correo es el identificador de inicio de sesión y es único sin distinguir mayúsculas.
- El rol determina la interfaz y los permisos base. El rol ADMIN NO otorga permisos
  clínicos: las operaciones clínicas exigirán rol PROFESSIONAL verificado (Fase 2).
"""
import uuid

from django.contrib.auth.models import AbstractBaseUser, BaseUserManager, PermissionsMixin
from django.db import models
from django.db.models.functions import Lower
from django.utils import timezone


class UserManager(BaseUserManager):
    use_in_migrations = True

    @staticmethod
    def normalize_login_email(email: str) -> str:
        return (email or "").strip().lower()

    def _create_user(self, email: str, password: str | None, **extra_fields):
        if not email:
            raise ValueError("El correo electrónico es obligatorio.")
        email = self.normalize_login_email(email)
        user = self.model(email=email, **extra_fields)
        user.set_password(password)
        user.save(using=self._db)
        return user

    def create_user(self, email: str, password: str | None = None, **extra_fields):
        extra_fields.setdefault("role", User.Role.TUTOR)
        extra_fields.setdefault("is_staff", False)
        extra_fields.setdefault("is_superuser", False)
        return self._create_user(email, password, **extra_fields)

    def create_superuser(self, email: str, password: str | None = None, **extra_fields):
        extra_fields.setdefault("role", User.Role.ADMIN)
        extra_fields.setdefault("is_staff", True)
        extra_fields.setdefault("is_superuser", True)
        if extra_fields.get("is_staff") is not True or extra_fields.get("is_superuser") is not True:
            raise ValueError("El superusuario debe tener is_staff=True e is_superuser=True.")
        return self._create_user(email, password, **extra_fields)

    def get_by_natural_key(self, username):
        # Inicio de sesión sin distinguir mayúsculas en el correo.
        return self.get(email__iexact=self.normalize_login_email(username))


class User(AbstractBaseUser, PermissionsMixin):
    class Role(models.TextChoices):
        TUTOR = "TUTOR", "Tutor (propietario de mascotas)"
        PROFESSIONAL = "PROFESSIONAL", "Profesional veterinario"
        ADMIN = "ADMIN", "Administrador de PetTech"

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    email = models.EmailField("correo electrónico", max_length=254, unique=True)
    full_name = models.CharField("nombre completo", max_length=150)
    role = models.CharField("rol", max_length=20, choices=Role.choices, default=Role.TUTOR)

    is_active = models.BooleanField(
        "activa",
        default=True,
        help_text="Desmarcar para suspender la cuenta sin eliminar sus datos.",
    )
    is_staff = models.BooleanField(
        "acceso al panel de administración",
        default=False,
        help_text="Permite ingresar a Django Admin. No otorga permisos clínicos.",
    )
    date_joined = models.DateTimeField("fecha de registro", default=timezone.now)

    # Consentimiento informado (HU-01; Ley N.º 21.719, principio de licitud y transparencia).
    privacy_policy_version = models.CharField("versión de política aceptada", max_length=40, blank=True)
    privacy_policy_accepted_at = models.DateTimeField("aceptó la política el", null=True, blank=True)

    objects = UserManager()

    USERNAME_FIELD = "email"
    EMAIL_FIELD = "email"
    REQUIRED_FIELDS = ["full_name"]

    class Meta:
        verbose_name = "usuario"
        verbose_name_plural = "usuarios"
        ordering = ["-date_joined"]
        constraints = [
            models.UniqueConstraint(Lower("email"), name="accounts_user_email_ci_unique"),
            models.CheckConstraint(
                condition=models.Q(role__in=["TUTOR", "PROFESSIONAL", "ADMIN"]),
                name="accounts_user_role_valid",
            ),
        ]

    def __str__(self) -> str:
        return f"{self.full_name} <{self.email}>"

    def save(self, *args, **kwargs):
        self.email = UserManager.normalize_login_email(self.email)
        super().save(*args, **kwargs)

    @property
    def is_tutor(self) -> bool:
        return self.role == self.Role.TUTOR

    @property
    def is_professional(self) -> bool:
        return self.role == self.Role.PROFESSIONAL
