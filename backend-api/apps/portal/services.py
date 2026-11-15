"""
Operaciones del portal web. Toda la lógica de autorización clínica se reutiliza de
apps.access_control y apps.clinical_records: el portal es otra "cara" de la misma API,
no una vía alternativa de acceso.
"""
from datetime import time

from django.contrib.gis.geos import Point
from django.db import IntegrityError, transaction
from django.utils import timezone

from apps.access_control.models import AuditEvent
from apps.access_control.services import audit
from apps.accounts.models import User
from apps.clinics.models import Clinic, OpeningHour, ProfessionalMembership


class PortalError(Exception):
    """Error de negocio presentable al usuario del portal."""


def _new_user(email: str, full_name: str, password: str, role: str) -> User:
    if User.objects.filter(email__iexact=email).exists():
        raise PortalError("Ya existe una cuenta con ese correo.")
    try:
        return User.objects.create_user(email=email, password=password, full_name=full_name, role=role)
    except IntegrityError as exc:  # carrera improbable con otro registro simultáneo
        raise PortalError("Ya existe una cuenta con ese correo.") from exc


@transaction.atomic
def create_clinic_with_account(admin_user, data: dict) -> Clinic:
    """El administrador de PetTech habilita una veterinaria y su cuenta de acceso al portal."""
    account = _new_user(data["account_email"], data["account_name"], data["account_password"], User.Role.CLINIC)
    clinic = Clinic.objects.create(
        name=data["name"],
        address=data["address"],
        commune=data["commune"],
        phone=data.get("phone", ""),
        location=Point(data["longitude"], data["latitude"], srid=4326),
        attends_emergencies=data.get("attends_emergencies", False),
        emergency_24h=data.get("emergency_24h", False),
        services=list(data.get("services", [])),
        # Hasta que la propia veterinaria confirme sus datos en el portal, se rotulan como no confirmados.
        info_origin=Clinic.InfoOrigin.PUBLIC_UNCONFIRMED,
        verified_at=timezone.now(),
        accepts_pettech_records=data.get("accepts_pettech_records", True),
        account=account,
    )
    audit(admin_user, AuditEvent.Action.CLINIC_CREATED, clinic=clinic, resource_id=clinic.pk)
    return clinic


@transaction.atomic
def create_professional(clinic: Clinic, actor, data: dict) -> ProfessionalMembership:
    """La veterinaria crea la cuenta de un profesional y lo autoriza a operar en su nombre."""
    user = _new_user(data["email"], data["full_name"], data["password"], User.Role.PROFESSIONAL)
    membership = ProfessionalMembership.objects.create(
        professional=user, clinic=clinic, license_number=data.get("license_number", ""),
        is_verified=True, verified_at=timezone.now(),
    )
    audit(actor, AuditEvent.Action.PROFESSIONAL_AUTHORIZED, clinic=clinic, resource_id=user.pk)
    return membership


@transaction.atomic
def set_professional_authorized(clinic: Clinic, actor, membership_id: int, authorized: bool) -> ProfessionalMembership:
    membership = ProfessionalMembership.objects.select_for_update().filter(pk=membership_id, clinic=clinic).first()
    if membership is None:
        raise PortalError("El profesional no pertenece a tu veterinaria.")
    membership.is_verified = authorized
    membership.verified_at = timezone.now() if authorized else membership.verified_at
    membership.save(update_fields=["is_verified", "verified_at", "updated_at"])
    action = AuditEvent.Action.PROFESSIONAL_AUTHORIZED if authorized else AuditEvent.Action.PROFESSIONAL_SUSPENDED
    audit(actor, action, clinic=clinic, resource_id=membership.professional_id)
    return membership


@transaction.atomic
def update_clinic_profile(clinic: Clinic, actor, data: dict, hours: dict[int, tuple[time, time] | None]) -> Clinic:
    """
    La veterinaria actualiza ella misma sus datos, ubicación, contacto, horario, urgencias y servicios
    (autogestión del catálogo).
    Al guardar, el dato queda "confirmado por el establecimiento" con la fecha actual (HU-09, HU-12).
    """
    clinic.name = data["name"]
    clinic.address = data["address"]
    clinic.commune = data["commune"]
    clinic.location = Point(data["longitude"], data["latitude"], srid=4326)
    clinic.phone = data.get("phone", "")
    clinic.contact_email = data.get("contact_email", "")
    clinic.website = data.get("website", "")
    clinic.services_detail = data.get("services_detail", "")
    clinic.attends_emergencies = data.get("attends_emergencies", False)
    clinic.emergency_24h = data.get("emergency_24h", False)
    clinic.services = list(data.get("services", []))
    clinic.info_origin = Clinic.InfoOrigin.CONFIRMED
    clinic.verified_at = timezone.now()
    clinic.save()
    clinic.opening_hours.all().delete()
    OpeningHour.objects.bulk_create([
        OpeningHour(clinic=clinic, weekday=day, opens_at=slot[0], closes_at=slot[1])
        for day, slot in sorted(hours.items()) if slot is not None
    ])
    audit(actor, AuditEvent.Action.CATALOG_UPDATED, clinic=clinic, resource_id=clinic.pk)
    return clinic


@transaction.atomic
def change_password(user: User, new_password: str) -> None:
    """
    Cambia la contraseña y cierra las sesiones de la app (tokens) del usuario: si alguien más
    conocía la contraseña anterior, pierde el acceso. La sesión web actual se conserva en la vista.
    """
    from knox.models import AuthToken

    user.set_password(new_password)
    user.save(update_fields=["password"])
    AuthToken.objects.filter(user=user).delete()
    audit(user, AuditEvent.Action.PASSWORD_CHANGED, resource_id=user.pk)


@transaction.atomic
def create_directory_clinic(admin_user, data: dict) -> Clinic:
    """Veterinaria NO afiliada: visible en la búsqueda como información pública no confirmada."""
    clinic = Clinic.objects.create(
        name=data["name"], address=data["address"], commune=data["commune"], phone=data.get("phone", ""),
        website=data.get("website", ""), location=Point(data["longitude"], data["latitude"], srid=4326),
        attends_emergencies=data.get("attends_emergencies", False), services=list(data.get("services", [])),
        source_note=data["source_note"], info_origin=Clinic.InfoOrigin.PUBLIC_UNCONFIRMED,
        verified_at=timezone.now(), accepts_pettech_records=False,
    )
    audit(admin_user, AuditEvent.Action.DIRECTORY_UPDATED, clinic=clinic, resource_id=clinic.pk)
    return clinic


@transaction.atomic
def affiliate_clinic(admin_user, clinic: Clinic, data: dict) -> Clinic:
    """
    La veterinaria del directorio se une a la Red PetTech: se crea su cuenta y pasa a participar de la
    ficha compartida. Desde ese momento la app la muestra con el distintivo "Red PetTech".
    """
    clinic = Clinic.objects.select_for_update().get(pk=clinic.pk)
    if clinic.account_id:
        raise PortalError("Esta veterinaria ya tiene una cuenta en el portal.")
    clinic.account = _new_user(data["account_email"], data["account_name"], data["account_password"],
                               User.Role.CLINIC)
    clinic.accepts_pettech_records = True
    clinic.is_active = True
    clinic.save(update_fields=["account", "accepts_pettech_records", "is_active", "updated_at"])
    audit(admin_user, AuditEvent.Action.CLINIC_AFFILIATED, clinic=clinic, resource_id=clinic.pk)
    return clinic
