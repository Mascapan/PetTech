"""Registro y consulta de la ficha clínica, con trazabilidad completa (RN-05 a RN-08, RN-14)."""
from django.db import transaction
from rest_framework.exceptions import NotFound, PermissionDenied

from apps.access_control.models import AuditEvent
from apps.access_control.services import audit

from .models import ClinicalEntry, Origin


def current_entries(pet, entry_type: str | None = None):
    """Versiones vigentes: excluye las que ya fueron corregidas por una versión posterior."""
    qs = ClinicalEntry.objects.filter(pet=pet, corrected_by__isnull=True)
    if entry_type:
        qs = qs.filter(entry_type=entry_type)
    return list(qs.order_by("-occurred_on", "-created_at"))


def create_by_owner(tutor, pet, data: dict) -> ClinicalEntry:
    return ClinicalEntry.objects.create(
        pet=pet, origin=Origin.OWNER_DECLARED, created_by=tutor, author_name=tutor.full_name, **data
    )


@transaction.atomic
def create_by_professional(professional, membership, pet, data: dict) -> ClinicalEntry:
    entry = ClinicalEntry.objects.create(
        pet=pet, origin=Origin.PROFESSIONAL, created_by=professional, author_name=professional.full_name,
        clinic=membership.clinic, clinic_name=membership.clinic.name, **data,
    )
    audit(professional, AuditEvent.Action.RECORD_CREATED, pet=pet, clinic=membership.clinic, resource_id=entry.pk)
    return entry


def _current_or_404(pet, entry_id) -> ClinicalEntry:
    entry = ClinicalEntry.objects.filter(pk=entry_id, pet=pet).first()
    if entry is None:
        raise NotFound()
    if hasattr(entry, "corrected_by"):
        raise PermissionDenied("Esta versión ya fue corregida; corrige la versión vigente.")
    return entry


@transaction.atomic
def correct(actor, pet, entry_id, data: dict, reason: str, membership=None) -> ClinicalEntry:
    """
    Crea una NUEVA versión que reemplaza a la vigente; la anterior se conserva (RNF-11).
    - Antecedente declarado: solo lo corrige el tutor.
    - Registro profesional: solo un profesional del MISMO establecimiento (RN-14).
    """
    previous = _current_or_404(pet, entry_id)
    if membership is None:
        if previous.origin != Origin.OWNER_DECLARED:
            raise PermissionDenied("Los registros de una veterinaria solo los corrige esa veterinaria.")
        return ClinicalEntry.objects.create(
            pet=pet, origin=Origin.OWNER_DECLARED, created_by=actor, author_name=actor.full_name,
            corrects=previous, correction_reason=reason, **data,
        )
    if previous.origin != Origin.PROFESSIONAL or previous.clinic_id != membership.clinic_id:
        raise PermissionDenied("Solo puedes corregir registros de tu establecimiento.")
    entry = ClinicalEntry.objects.create(
        pet=pet, origin=Origin.PROFESSIONAL, created_by=actor, author_name=actor.full_name,
        clinic=membership.clinic, clinic_name=membership.clinic.name,
        corrects=previous, correction_reason=reason, **data,
    )
    audit(actor, AuditEvent.Action.RECORD_CORRECTED, pet=pet, clinic=membership.clinic, resource_id=entry.pk)
    return entry


def history(pet, entry_id) -> list[ClinicalEntry]:
    """Cadena de versiones de un antecedente, de la más reciente a la original."""
    entry = ClinicalEntry.objects.filter(pk=entry_id, pet=pet).first()
    if entry is None:
        raise NotFound()
    chain = [entry]
    while chain[-1].corrects_id:
        chain.append(chain[-1].corrects)
    return chain
