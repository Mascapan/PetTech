"""
Simula las acciones de un veterinario de demostración (SOLO desarrollo, DEBUG=True).

Útil para demostrar el flujo con un solo emulador:
  python manage.py demo_veterinario solicitar PT-XXXX-XXXX   # vet1 pide acceso con el código PetTech
  (el tutor autoriza en la app: campanita → Autorizar 24 h)
  python manage.py demo_veterinario registrar PT-XXXX-XXXX   # vet1 lee la ficha y registra una vacuna [SIMULADO]

Usa los mismos servicios que la API: respeta la autorización del tutor y deja todo en la bitácora.
"""
from datetime import timedelta

from django.conf import settings
from django.core.management.base import BaseCommand, CommandError
from django.utils import timezone
from rest_framework.exceptions import APIException

from apps.access_control import services as access
from apps.access_control.models import AuditEvent
from apps.accounts.models import User
from apps.clinical_records import services as records
from apps.clinical_records.models import EntryType


class Command(BaseCommand):
    help = "Simula a vet1.demo: solicitar acceso o registrar una atención (solo desarrollo)."

    def add_arguments(self, parser):
        parser.add_argument("accion", choices=["solicitar", "registrar"])
        parser.add_argument("codigo")
        parser.add_argument("--email", default="vet1.demo@pettech.test")

    def handle(self, accion, codigo, email, **options):
        if not settings.DEBUG:
            raise CommandError("Solo disponible en desarrollo (DEBUG=True).")
        vet = User.objects.filter(email=email, role=User.Role.PROFESSIONAL).first()
        if vet is None:
            raise CommandError("No existe el profesional. Ejecuta antes: python manage.py seed_demo")
        try:
            if accion == "solicitar":
                req = access.request_access(vet, codigo, "Control [SIMULADO]")
                self.stdout.write(self.style.SUCCESS(
                    f"Solicitud {req.effective_status} de {req.clinic.name} para {req.pet.public_code}. "
                    "Autorízala en la app del tutor (campanita)."
                ))
            else:
                pet, membership = access.require_active_grant(vet, codigo, AuditEvent.Action.RECORD_READ)
                access.audit(vet, AuditEvent.Action.RECORD_READ, pet=pet, clinic=membership.clinic)
                today = timezone.localdate()
                records.create_by_professional(vet, membership, pet, {
                    "entry_type": EntryType.VACCINE, "title": "Antirrábica [SIMULADO]", "occurred_on": today,
                    "next_due_on": today + timedelta(days=365), "lot_number": "LOTE-DEMO-02",
                    "notes": "Registro de demostración.",
                })
                records.create_by_professional(vet, membership, pet, {
                    "entry_type": EntryType.VISIT, "title": "Control general [SIMULADO]", "occurred_on": today,
                    "weight_kg": 12.4, "notes": "Examen sin hallazgos. Dato simulado.",
                })
                self.stdout.write(self.style.SUCCESS(
                    f"{vet.full_name} ({membership.clinic.name}) leyó la ficha de {pet.name} y registró 2 antecedentes."
                ))
        except APIException as exc:
            raise CommandError(f"Denegado por las reglas de acceso: {exc.detail}") from exc
