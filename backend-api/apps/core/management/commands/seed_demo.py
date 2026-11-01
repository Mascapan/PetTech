"""
Carga DATOS SIMULADOS para desarrollo y demostración (incremento 1).

Todas las cuentas usan el dominio reservado ".test" (RFC 6761) y los nombres llevan la
marca [DEMO]. No representan personas, mascotas ni clínicas reales.

Uso:  python manage.py seed_demo
"""
import os

from django.conf import settings
from django.core.management.base import BaseCommand, CommandError
from django.db import transaction
from django.utils import timezone

from apps.accounts.models import User
from apps.pets.models import Pet

DEMO_USERS = [
    {"email": "tutora.demo@pettech.test", "full_name": "Camila Demo [DEMO]", "role": User.Role.TUTOR},
    {"email": "tutor2.demo@pettech.test", "full_name": "Diego Demo [DEMO]", "role": User.Role.TUTOR},
    # Profesional sin establecimiento aún: la verificación y clínicas llegan en la Fase 2.
    {"email": "vet1.demo@pettech.test", "full_name": "Dra. Valentina Demo [DEMO]", "role": User.Role.PROFESSIONAL},
]

DEMO_PETS = [
    {
        "owner": "tutora.demo@pettech.test",
        "name": "Luna [DEMO]",
        "species": Pet.Species.DOG,
        "breed": "Mestiza",
        "sex": Pet.Sex.FEMALE,
        "birth_date": "2021-03-15",
        "birth_date_is_estimated": True,
        "general_notes": "Dato simulado para pruebas. Alergia declarada por el tutor: ninguna conocida.",
    },
    {
        "owner": "tutora.demo@pettech.test",
        "name": "Michi [DEMO]",
        "species": Pet.Species.CAT,
        "breed": "Doméstico pelo corto",
        "sex": Pet.Sex.MALE,
        "birth_date": "2023-08-01",
        "birth_date_is_estimated": True,
        "general_notes": "Dato simulado para pruebas.",
    },
    {
        "owner": "tutor2.demo@pettech.test",
        "name": "Rocky [DEMO]",
        "species": Pet.Species.DOG,
        "breed": "Labrador",
        "sex": Pet.Sex.MALE,
        "birth_date": None,
        "birth_date_is_estimated": True,
        "general_notes": "Mascota de otro tutor: sirve para demostrar el aislamiento entre cuentas.",
    },
]


class Command(BaseCommand):
    help = "Crea cuentas y mascotas ficticias (solo con DEBUG=True)."

    def handle(self, *args, **options):
        if not settings.DEBUG:
            raise CommandError("seed_demo solo puede ejecutarse en desarrollo (DEBUG=True).")
        password = os.environ.get("DEMO_PASSWORD") or getattr(settings, "DEMO_PASSWORD", None) or "PetTech-Demo-2026"

        with transaction.atomic():
            users = {}
            for data in DEMO_USERS:
                user, created = User.objects.get_or_create(
                    email=data["email"],
                    defaults={"full_name": data["full_name"], "role": data["role"]},
                )
                if created:
                    user.set_password(password)
                    user.privacy_policy_version = settings.PETTECH_PRIVACY_POLICY_VERSION
                    user.privacy_policy_accepted_at = timezone.now()
                    user.save()
                users[data["email"]] = user
                self.stdout.write(f"{'Creado' if created else 'Existente'}: {user.email} ({user.role})")

            for data in DEMO_PETS:
                owner = users[data["owner"]]
                fields = {k: v for k, v in data.items() if k not in {"owner", "name"}}
                pet, created = Pet.objects.get_or_create(owner=owner, name=data["name"], defaults=fields)
                self.stdout.write(f"{'Creada' if created else 'Existente'}: {pet.name} -> {pet.public_code}")

        self.stdout.write(self.style.SUCCESS("Datos simulados listos. Contraseña: la definida en DEMO_PASSWORD."))
