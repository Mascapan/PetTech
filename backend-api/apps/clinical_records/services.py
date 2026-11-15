"""Registro y consulta de la ficha clínica, con trazabilidad completa (RN-05 a RN-08, RN-14)."""
from django.db import transaction
from rest_framework.exceptions import NotFound, PermissionDenied, ValidationError

from apps.access_control.models import AuditEvent
from apps.access_control.services import audit

from . import images
from .models import Attachment, ClinicalEntry, Origin


def current_entries(pet, entry_type: str | None = None):
    """Versiones vigentes: excluye las que ya fueron corregidas por una versión posterior."""
    qs = ClinicalEntry.objects.filter(pet=pet, corrected_by__isnull=True)
    if entry_type:
        qs = qs.filter(entry_type=entry_type)
    return list(qs.order_by("-occurred_on", "-created_at"))


def _source_attachment(pet, attachment_id):
    if not attachment_id:
        return None
    att = Attachment.objects.filter(pk=attachment_id, pet=pet, kind=Attachment.Kind.CARD_SCAN).first()
    if att is None:
        raise ValidationError({"source_attachment": ["La foto del carnet no existe o no es de esta mascota."]})
    return att


def create_by_owner(tutor, pet, data: dict) -> ClinicalEntry:
    data = dict(data)
    data["source_attachment"] = _source_attachment(pet, data.pop("source_attachment", None))
    return ClinicalEntry.objects.create(
        pet=pet, origin=Origin.OWNER_DECLARED, created_by=tutor, author_name=tutor.full_name, **data
    )


@transaction.atomic
def create_by_professional(professional, membership, pet, data: dict) -> ClinicalEntry:
    data = {k: v for k, v in data.items() if k != "source_attachment"}  # solo el tutor transcribe su carnet
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
            corrects=previous, correction_reason=reason,
            **{k: v for k, v in data.items() if k != "source_attachment"},
            source_attachment=previous.source_attachment,
        )
    if previous.origin != Origin.PROFESSIONAL or previous.clinic_id != membership.clinic_id:
        raise PermissionDenied("Solo puedes corregir registros de tu establecimiento.")
    entry = ClinicalEntry.objects.create(
        pet=pet, origin=Origin.PROFESSIONAL, created_by=actor, author_name=actor.full_name,
        clinic=membership.clinic, clinic_name=membership.clinic.name,
        corrects=previous, correction_reason=reason, **{k: v for k, v in data.items() if k != "source_attachment"},
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


# ------------------------------------------------------------------ Fotografías (DEC-031)

def add_attachment(actor, pet, upload, kind: str, *, entry_id=None, membership=None) -> Attachment:
    """
    Agrega una foto a la ficha. El tutor puede adjuntar a cualquier antecedente de su mascota (p. ej. la etiqueta
    de una vacuna que registró la veterinaria). El profesional, solo a registros de su establecimiento.
    """
    if kind not in Attachment.Kind.values:
        raise ValidationError({"kind": ["Tipo de fotografía no válido."]})
    entry = None
    if entry_id:
        entry = ClinicalEntry.objects.filter(pk=entry_id, pet=pet).first()
        if entry is None:
            raise NotFound()
        if membership is not None and (entry.origin != Origin.PROFESSIONAL or entry.clinic_id != membership.clinic_id):
            raise PermissionDenied("Solo puedes adjuntar fotos a registros de tu establecimiento.")
    elif membership is not None:
        raise ValidationError({"entry": ["Indica a qué registro corresponde la foto."]})
    try:
        image = images.normalize(upload)
    except images.InvalidImage as exc:
        raise ValidationError({"file": [str(exc)]}) from exc
    if Attachment.objects.filter(pet=pet, entry=entry, sha256=image.sha256).exists():
        raise ValidationError({"file": ["Esta foto ya está adjunta."]})
    att = Attachment.objects.create(
        pet=pet, entry=entry, kind=kind, content=image.content, size_bytes=len(image.content),
        width=image.width, height=image.height, sha256=image.sha256,
        origin=Origin.PROFESSIONAL if membership else Origin.OWNER_DECLARED,
        uploaded_by=actor, author_name=actor.full_name, clinic=membership.clinic if membership else None,
    )
    if membership is not None:
        audit(actor, AuditEvent.Action.ATTACHMENT_ADDED, pet=pet, clinic=membership.clinic, resource_id=att.pk)
    return att


def delete_own_attachment(tutor, pet, attachment_id) -> None:
    att = Attachment.objects.filter(pk=attachment_id, pet=pet).first()
    if att is None:
        raise NotFound()
    if att.origin != Origin.OWNER_DECLARED or att.uploaded_by_id != tutor.pk:
        raise PermissionDenied("Las fotos registradas por una veterinaria no se eliminan.")
    if att.derived_entries.exists():
        raise PermissionDenied("Esta foto respalda antecedentes transcritos; no se puede eliminar.")
    att.delete()


def attachments_by_entry(pet) -> dict:
    """Fotos por antecedente vigente, incluyendo las de sus versiones anteriores (las correcciones no las pierden)."""
    parents = dict(ClinicalEntry.objects.filter(pet=pet).values_list("pk", "corrects_id"))
    by_version: dict = {}
    for att in Attachment.objects.filter(pet=pet, entry__isnull=False).defer("content"):
        by_version.setdefault(att.entry_id, []).append(att)
    result = {}
    for entry_id in parents:
        chain, current = [], entry_id
        while current:
            chain.extend(by_version.get(current, []))
            current = parents.get(current)
        result[entry_id] = sorted(chain, key=lambda a: a.created_at)
    return result
