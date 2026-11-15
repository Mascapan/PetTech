"""
Establecimientos veterinarios (catálogo) y profesionales vinculados.

Modelo de "disponibilidad" del Informe 1 (Tabla 12):
  1. Horario declarado (OpeningHour)                         -> incluido
  2. Abierto según horario declarado (calculado, services.py) -> incluido
  3. Atención de urgencias / 24 h (declarado)                 -> incluido
  4. Cupos u horas disponibles                                -> FUERA de alcance
Cada establecimiento informa el ORIGEN del dato y su FECHA de última verificación (HU-09, HU-12).
Los cambios hechos en Django Admin quedan en su bitácora (LogEntry: autor, fecha, objeto, cambio) (HU-11).
"""
import uuid

from django.conf import settings
from django.contrib.gis.db import models as gis_models
from django.db import models

from apps.core.models import TimeStampedModel


class Service(models.TextChoices):
    GENERAL = "GENERAL", "Consulta general"
    VACCINATION = "VACCINATION", "Vacunación"
    SURGERY = "SURGERY", "Cirugía"
    LAB = "LAB", "Laboratorio"
    IMAGING = "IMAGING", "Imagenología"
    HOSPITALIZATION = "HOSPITALIZATION", "Hospitalización"
    EXOTICS = "EXOTICS", "Animales exóticos"


class Clinic(TimeStampedModel):
    class InfoOrigin(models.TextChoices):
        CONFIRMED = "CONFIRMED", "Confirmado por el establecimiento"
        PUBLIC_UNCONFIRMED = "PUBLIC_UNCONFIRMED", "Fuente pública no confirmada"

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    name = models.CharField("nombre", max_length=120)
    address = models.CharField("dirección", max_length=200)
    commune = models.CharField("comuna", max_length=60, default="Chillán")
    phone = models.CharField("teléfono", max_length=20, blank=True)
    contact_email = models.EmailField("correo de contacto", blank=True)
    website = models.URLField("sitio web o red social", max_length=200, blank=True)
    # geography=True: distancias en metros sobre el elipsoide (WGS 84).
    location = gis_models.PointField("ubicación", geography=True, srid=4326)
    attends_emergencies = models.BooleanField("declara atender urgencias", default=False)
    emergency_24h = models.BooleanField("declara atención 24 horas", default=False)
    services = models.JSONField(
        "servicios declarados", default=list, blank=True,
        help_text=f"Lista de códigos: {', '.join(Service.values)}",
    )
    services_detail = models.TextField(
        "otros servicios (detalle)", max_length=1500, blank=True,
        help_text="Texto libre con el detalle de servicios, especialidades o prestaciones adicionales.",
    )
    source_note = models.CharField(
        "fuente del dato (directorio)", max_length=200, blank=True,
        help_text="Para establecimientos no afiliados: de dónde se obtuvo la información pública (p. ej. OpenStreetMap).",
    )
    external_ref = models.CharField(
        "referencia externa", max_length=40, blank=True, db_index=True,
        help_text="Identificador en la fuente pública (p. ej. osm:node/123) para evitar duplicados al importar.",
    )
    info_origin = models.CharField(
        "origen de la información", max_length=20, choices=InfoOrigin.choices,
        default=InfoOrigin.PUBLIC_UNCONFIRMED,
    )
    verified_at = models.DateTimeField("fecha de última verificación")
    accepts_pettech_records = models.BooleanField(
        "participa en la red PetTech", default=False,
        help_text="Sus profesionales verificados pueden solicitar acceso y registrar atenciones.",
    )
    is_active = models.BooleanField("visible en la búsqueda", default=True)
    account = models.OneToOneField(
        settings.AUTH_USER_MODEL, null=True, blank=True, on_delete=models.SET_NULL,
        related_name="managed_clinic", limit_choices_to={"role": "CLINIC"},
        verbose_name="cuenta de la veterinaria",
        help_text="Cuenta (rol CLINIC) con la que el establecimiento ingresa al portal web.",
    )

    class Meta:
        verbose_name = "establecimiento veterinario"
        verbose_name_plural = "establecimientos veterinarios"
        ordering = ["name"]
        constraints = [
            models.UniqueConstraint(fields=["external_ref"], condition=~models.Q(external_ref=""),
                                    name="clinics_external_ref_unique"),
        ]

    def __str__(self) -> str:
        return self.name

    @property
    def in_pettech_network(self) -> bool:
        """Afiliada a la Red PetTech: tiene cuenta en el portal y participa de la ficha compartida."""
        return bool(self.is_active and self.accepts_pettech_records and self.account_id)

    def service_list(self) -> list[dict]:
        labels = dict(Service.choices)
        return [{"code": code, "label": labels[code]} for code in self.services if code in labels]


class OpeningHour(models.Model):
    """Franja semanal declarada. Si closes_at <= opens_at la franja termina al día siguiente."""

    class Weekday(models.IntegerChoices):
        MONDAY = 0, "Lunes"
        TUESDAY = 1, "Martes"
        WEDNESDAY = 2, "Miércoles"
        THURSDAY = 3, "Jueves"
        FRIDAY = 4, "Viernes"
        SATURDAY = 5, "Sábado"
        SUNDAY = 6, "Domingo"

    clinic = models.ForeignKey(Clinic, related_name="opening_hours", on_delete=models.CASCADE)
    weekday = models.PositiveSmallIntegerField("día", choices=Weekday.choices)
    opens_at = models.TimeField("abre")
    closes_at = models.TimeField("cierra")

    class Meta:
        verbose_name = "horario declarado"
        verbose_name_plural = "horarios declarados"
        ordering = ["weekday", "opens_at"]

    def __str__(self) -> str:
        return f"{self.get_weekday_display()} {self.opens_at:%H:%M}-{self.closes_at:%H:%M}"


class ProfessionalMembership(TimeStampedModel):
    """
    Vínculo profesional ↔ establecimiento (RN-04, RN-10).
    El administrador de PetTech habilita la VETERINARIA; la veterinaria crea y autoriza
    a sus PROFESIONALES desde el portal web (is_verified). Solo un vínculo autorizado opera.
    """

    professional = models.ForeignKey(
        settings.AUTH_USER_MODEL, related_name="memberships", on_delete=models.CASCADE,
        limit_choices_to={"role": "PROFESSIONAL"},
    )
    clinic = models.ForeignKey(Clinic, related_name="memberships", on_delete=models.CASCADE)
    license_number = models.CharField("n.º de registro profesional (declarado)", max_length=40, blank=True)
    is_verified = models.BooleanField("verificado por PetTech", default=False)
    verified_at = models.DateTimeField("verificado el", null=True, blank=True)

    class Meta:
        verbose_name = "vínculo profesional"
        verbose_name_plural = "vínculos profesionales"
        constraints = [
            models.UniqueConstraint(fields=["professional", "clinic"], name="clinics_membership_unique"),
        ]

    def __str__(self) -> str:
        return f"{self.professional} @ {self.clinic}"
