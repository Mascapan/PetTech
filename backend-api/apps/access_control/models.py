"""
Autorizaciones de acceso a la ficha clínica y bitácora de auditoría.

Flujo (HU-16 a HU-18, RN-01 a RN-03, RN-12):
  1. El profesional verificado ingresa el CÓDIGO PetTech y solicita acceso en nombre de su establecimiento.
  2. El tutor ve la solicitud (quién y desde dónde) y la aprueba o rechaza.
  3. La aprobación crea una autorización TEMPORAL (24 h por defecto) para ese establecimiento.
  4. El tutor puede revocarla en cualquier momento; la revocación tiene efecto inmediato.
El código identifica a la mascota, pero por sí solo NO concede acceso.
"""
import uuid

from django.conf import settings
from django.db import models
from django.utils import timezone


class AccessRequest(models.Model):
    class Status(models.TextChoices):
        PENDING = "PENDING", "Pendiente"
        APPROVED = "APPROVED", "Autorizada"
        REJECTED = "REJECTED", "Rechazada"
        REVOKED = "REVOKED", "Revocada"
        EXPIRED = "EXPIRED", "Expirada"

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    pet = models.ForeignKey("pets.Pet", related_name="access_requests", on_delete=models.CASCADE)
    clinic = models.ForeignKey("clinics.Clinic", related_name="access_requests", on_delete=models.PROTECT)
    professional = models.ForeignKey(
        settings.AUTH_USER_MODEL, related_name="access_requests", on_delete=models.PROTECT
    )
    reason = models.CharField("motivo de la solicitud", max_length=200, blank=True)
    status = models.CharField(max_length=10, choices=Status.choices, default=Status.PENDING)
    created_at = models.DateTimeField(auto_now_add=True)
    decided_at = models.DateTimeField(null=True, blank=True)
    expires_at = models.DateTimeField(
        null=True, blank=True,
        help_text="Pendiente: plazo para responder. Autorizada: fin de la autorización.",
    )
    revoked_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        verbose_name = "solicitud / autorización de acceso"
        verbose_name_plural = "solicitudes y autorizaciones de acceso"
        ordering = ["-created_at"]
        indexes = [models.Index(fields=["pet", "clinic", "status"], name="access_pet_clinic_status_idx")]

    def __str__(self) -> str:
        return f"{self.clinic} → {self.pet.public_code} ({self.effective_status})"

    @property
    def effective_status(self) -> str:
        """El vencimiento se evalúa al momento de cada consulta (no depende de tareas programadas)."""
        if self.status in (self.Status.PENDING, self.Status.APPROVED) and self.expires_at and \
                self.expires_at <= timezone.now():
            return self.Status.EXPIRED
        return self.status

    @property
    def is_active_grant(self) -> bool:
        return self.effective_status == self.Status.APPROVED


class AuditEvent(models.Model):
    """Registro inmutable de accesos y cambios (RNF-09). No se edita ni se elimina desde la aplicación."""

    class Action(models.TextChoices):
        ACCESS_REQUESTED = "ACCESS_REQUESTED", "Solicitó acceso"
        ACCESS_APPROVED = "ACCESS_APPROVED", "Autorizó acceso"
        ACCESS_REJECTED = "ACCESS_REJECTED", "Rechazó acceso"
        ACCESS_REVOKED = "ACCESS_REVOKED", "Revocó acceso"
        RECORD_READ = "RECORD_READ", "Consultó la ficha clínica"
        RECORD_CREATED = "RECORD_CREATED", "Registró un antecedente"
        RECORD_CORRECTED = "RECORD_CORRECTED", "Corrigió un antecedente"
        CATALOG_UPDATED = "CATALOG_UPDATED", "Actualizó la información del establecimiento"
        PROFESSIONAL_AUTHORIZED = "PROFESSIONAL_AUTHORIZED", "Autorizó a un profesional"
        PROFESSIONAL_SUSPENDED = "PROFESSIONAL_SUSPENDED", "Suspendió a un profesional"
        CLINIC_CREATED = "CLINIC_CREATED", "Creó una veterinaria"

    class Result(models.TextChoices):
        SUCCESS = "SUCCESS", "Permitido"
        DENIED = "DENIED", "Denegado"

    id = models.BigAutoField(primary_key=True)
    created_at = models.DateTimeField(auto_now_add=True, db_index=True)
    actor = models.ForeignKey(settings.AUTH_USER_MODEL, null=True, on_delete=models.SET_NULL)
    actor_name = models.CharField(max_length=150)
    clinic = models.ForeignKey("clinics.Clinic", null=True, blank=True, on_delete=models.SET_NULL)
    clinic_name = models.CharField(max_length=120, blank=True)
    pet = models.ForeignKey("pets.Pet", null=True, blank=True, on_delete=models.SET_NULL, related_name="audit_events")
    pet_code = models.CharField(max_length=12, blank=True)
    action = models.CharField(max_length=30, choices=Action.choices)
    result = models.CharField(max_length=10, choices=Result.choices, default=Result.SUCCESS)
    resource_id = models.CharField(max_length=40, blank=True)

    class Meta:
        verbose_name = "evento de bitácora"
        verbose_name_plural = "bitácora de auditoría"
        ordering = ["-created_at"]

    def save(self, *args, **kwargs):
        if not self._state.adding:
            raise ValueError("La bitácora es de solo inserción.")
        super().save(*args, **kwargs)

    def delete(self, *args, **kwargs):
        raise ValueError("La bitácora es de solo inserción.")
