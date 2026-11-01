"""
Mascotas e identificación.

Los datos de este modelo son DECLARADOS POR EL TUTOR y no tienen valor de certificación
veterinaria (incluido el número de microchip). Los registros realizados por profesionales
(atenciones, vacunas) vivirán en el módulo clinical_records (Fase 3), con su propia autoría.
"""
import logging
import uuid

from django.conf import settings
from django.core.validators import RegexValidator
from django.db import IntegrityError, models, transaction

from apps.core.models import TimeStampedModel

from .codes import generate_public_code

logger = logging.getLogger("pettech.pets")

microchip_validator = RegexValidator(
    regex=r"^\d{15}$",
    message="El microchip debe tener 15 dígitos (formato ISO 11784).",
)


class Pet(TimeStampedModel):
    class Species(models.TextChoices):
        DOG = "DOG", "Perro"
        CAT = "CAT", "Gato"

    class Sex(models.TextChoices):
        MALE = "MALE", "Macho"
        FEMALE = "FEMALE", "Hembra"
        UNKNOWN = "UNKNOWN", "No informado"

    MAX_CODE_ATTEMPTS = 5

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    public_code = models.CharField(
        "código PetTech",
        max_length=12,
        unique=True,
        editable=False,
        default=generate_public_code,
        help_text="Identificador público para compartir con la veterinaria. No otorga acceso.",
    )
    owner = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        verbose_name="tutor",
        related_name="pets",
        # PROTECT: una cuenta con mascotas no se elimina en cascada de forma silenciosa.
        # La eliminación de cuenta (HU-13) se resolverá con un servicio explícito (ver DEC-009).
        on_delete=models.PROTECT,
    )
    name = models.CharField("nombre", max_length=60)
    species = models.CharField("especie", max_length=10, choices=Species.choices)
    breed = models.CharField("raza", max_length=60, blank=True)
    sex = models.CharField("sexo", max_length=10, choices=Sex.choices, default=Sex.UNKNOWN)
    birth_date = models.DateField("fecha de nacimiento", null=True, blank=True)
    birth_date_is_estimated = models.BooleanField(
        "fecha aproximada", default=True, help_text="Indica si la fecha de nacimiento es estimada."
    )
    microchip_number = models.CharField(
        "número de microchip (declarado)",
        max_length=15,
        blank=True,
        validators=[microchip_validator],
        help_text="Declarado por el tutor; PetTech no lo verifica contra el Registro Nacional.",
    )
    general_notes = models.TextField(
        "antecedentes generales declarados por el tutor",
        max_length=1000,
        blank=True,
    )

    class Meta:
        verbose_name = "mascota"
        verbose_name_plural = "mascotas"
        ordering = ["name", "created_at"]
        indexes = [models.Index(fields=["owner", "name"], name="pets_owner_name_idx")]
        constraints = [
            models.CheckConstraint(
                condition=models.Q(species__in=["DOG", "CAT"]), name="pets_pet_species_valid"
            ),
            models.CheckConstraint(
                condition=models.Q(sex__in=["MALE", "FEMALE", "UNKNOWN"]), name="pets_pet_sex_valid"
            ),
            models.CheckConstraint(
                condition=~models.Q(name=""), name="pets_pet_name_not_empty"
            ),
        ]

    def __str__(self) -> str:
        return f"{self.name} ({self.public_code})"

    def save(self, *args, **kwargs):
        """Guarda la mascota; si el código público colisiona (muy improbable), genera otro."""
        if not self._state.adding:
            return super().save(*args, **kwargs)
        for attempt in range(1, self.MAX_CODE_ATTEMPTS + 1):
            try:
                with transaction.atomic():
                    return super().save(*args, **kwargs)
            except IntegrityError as exc:
                if "public_code" not in str(exc) or attempt == self.MAX_CODE_ATTEMPTS:
                    raise
                logger.warning("Colisión de código público; reintento %s", attempt)
                self.public_code = generate_public_code()
        return None  # inalcanzable
