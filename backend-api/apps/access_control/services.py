"""Reglas de negocio del control de acceso. Toda decisión de autorización ocurre aquí, en el servidor."""
import logging
from datetime import timedelta

from django.conf import settings
from django.db import transaction
from django.utils import timezone
from rest_framework.exceptions import NotFound, PermissionDenied, ValidationError

from apps.clinics.models import ProfessionalMembership
from apps.pets.codes import normalize_public_code
from apps.pets.models import Pet

from .models import AccessRequest, AuditEvent

logger = logging.getLogger("pettech.access")

PENDING_TTL = timedelta(hours=24)


def grant_duration() -> timedelta:
    return timedelta(hours=settings.PETTECH_ACCESS_GRANT_HOURS)


def audit(actor, action, *, pet=None, clinic=None, result=AuditEvent.Result.SUCCESS, resource_id=""):
    AuditEvent.objects.create(
        actor=actor,
        actor_name=getattr(actor, "full_name", "") or "",
        clinic=clinic,
        clinic_name=clinic.name if clinic else "",
        pet=pet,
        pet_code=pet.public_code if pet else "",
        action=action,
        result=result,
        resource_id=str(resource_id or ""),
    )


def membership_for(professional) -> ProfessionalMembership:
    """Establecimiento verificado del profesional (RN-04). Sin vínculo verificado no opera."""
    membership = (
        ProfessionalMembership.objects.select_related("clinic")
        .filter(professional=professional, is_verified=True, clinic__is_active=True,
                clinic__accepts_pettech_records=True)
        .order_by("created_at")
        .first()
    )
    if membership is None:
        raise PermissionDenied(
            "Tu cuenta profesional no está vinculada a un establecimiento verificado en PetTech."
        )
    return membership


def find_pet_by_code(raw_code: str) -> Pet:
    code = normalize_public_code(raw_code)
    if code is None:
        raise ValidationError({"public_code": ["El código PetTech tiene el formato PT-XXXX-XXXX."]})
    pet = Pet.objects.filter(public_code=code).first()
    if pet is None:
        raise NotFound("No existe una mascota con ese código PetTech.")
    return pet


@transaction.atomic
def request_access(professional, raw_code: str, reason: str = "") -> AccessRequest:
    membership = membership_for(professional)
    pet = find_pet_by_code(raw_code)
    clinic = membership.clinic
    now = timezone.now()
    # Reutiliza una solicitud pendiente o autorización vigente del mismo establecimiento.
    for existing in AccessRequest.objects.filter(pet=pet, clinic=clinic, status__in=["PENDING", "APPROVED"]):
        if existing.effective_status in ("PENDING", "APPROVED"):
            return existing
    req = AccessRequest.objects.create(
        pet=pet, clinic=clinic, professional=professional, reason=reason.strip()[:200],
        expires_at=now + PENDING_TTL,
    )
    audit(professional, AuditEvent.Action.ACCESS_REQUESTED, pet=pet, clinic=clinic, resource_id=req.pk)
    logger.info("Solicitud de acceso creada request_id=%s clinic_id=%s", req.pk, clinic.pk)
    return req


def _tutor_request(tutor, request_id) -> AccessRequest:
    req = AccessRequest.objects.select_related("pet", "clinic", "professional").filter(
        pk=request_id, pet__owner=tutor
    ).first()
    if req is None:
        raise NotFound()
    return req


@transaction.atomic
def decide(tutor, request_id, approve: bool) -> AccessRequest:
    req = _tutor_request(tutor, request_id)
    if req.effective_status != AccessRequest.Status.PENDING:
        raise ValidationError("Esta solicitud ya no está pendiente.")
    now = timezone.now()
    req.decided_at = now
    if approve:
        req.status = AccessRequest.Status.APPROVED
        req.expires_at = now + grant_duration()
        action = AuditEvent.Action.ACCESS_APPROVED
    else:
        req.status = AccessRequest.Status.REJECTED
        req.expires_at = None
        action = AuditEvent.Action.ACCESS_REJECTED
    req.save(update_fields=["status", "decided_at", "expires_at"])
    audit(tutor, action, pet=req.pet, clinic=req.clinic, resource_id=req.pk)
    return req


@transaction.atomic
def revoke(tutor, request_id) -> AccessRequest:
    req = _tutor_request(tutor, request_id)
    if not req.is_active_grant:
        raise ValidationError("Esta autorización no está vigente.")
    req.status = AccessRequest.Status.REVOKED
    req.revoked_at = timezone.now()
    req.save(update_fields=["status", "revoked_at"])
    audit(tutor, AuditEvent.Action.ACCESS_REVOKED, pet=req.pet, clinic=req.clinic, resource_id=req.pk)
    return req


def require_active_grant(professional, raw_code: str, action: str) -> tuple[Pet, ProfessionalMembership]:
    """
    Verifica en CADA operación que exista una autorización vigente del tutor para el
    establecimiento del profesional (RNF-10). Los intentos denegados también se registran.
    Sin autorización responde 404, sin revelar datos de la mascota.
    """
    membership = membership_for(professional)
    pet = find_pet_by_code(raw_code)
    grants = AccessRequest.objects.filter(pet=pet, clinic=membership.clinic, status=AccessRequest.Status.APPROVED)
    if not any(g.is_active_grant for g in grants):
        audit(professional, action, pet=pet, clinic=membership.clinic, result=AuditEvent.Result.DENIED)
        raise NotFound("No tienes una autorización vigente del tutor para esta mascota.")
    return pet, membership
