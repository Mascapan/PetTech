"""
Carga DATOS SIMULADOS para desarrollo y demostración.

Todas las cuentas usan el dominio reservado ".test" (RFC 6761) y los nombres llevan la
marca [DEMO]. No representan personas, mascotas ni clínicas reales: los establecimientos
son FICTICIOS, con coordenadas aproximadas en Chillán solo para probar la búsqueda por cercanía.

Uso:  python manage.py seed_demo
"""
import os
from datetime import date, time, timedelta

from django.conf import settings
from django.core.management.base import BaseCommand, CommandError
from django.db import transaction
from django.utils import timezone

from django.contrib.gis.geos import Point

from apps.accounts.models import User
from apps.clinical_records.models import ClinicalEntry, EntryType, Origin
from apps.clinics.models import Clinic, OpeningHour, ProfessionalMembership
from apps.pets.models import Pet

DEMO_USERS = [
    {"email": "tutora.demo@pettech.test", "full_name": "Camila Demo [DEMO]", "role": User.Role.TUTOR},
    {"email": "tutor2.demo@pettech.test", "full_name": "Diego Demo [DEMO]", "role": User.Role.TUTOR},
    {"email": "vet1.demo@pettech.test", "full_name": "Dra. Valentina Demo [DEMO]", "role": User.Role.PROFESSIONAL},
    {"email": "vet2.demo@pettech.test", "full_name": "Dr. Tomás Demo [DEMO]", "role": User.Role.PROFESSIONAL},
    # Profesional SIN vínculo verificado: sirve para demostrar que no puede solicitar accesos.
    {"email": "vet3.demo@pettech.test", "full_name": "Dr. Sin Verificar [DEMO]", "role": User.Role.PROFESSIONAL},
    # Portal web (DEC-025): administrador de PetTech y cuentas de veterinarias.
    {"email": "admin.demo@pettech.test", "full_name": "Administración PetTech [DEMO]", "role": User.Role.ADMIN},
    {"email": "andes.demo@pettech.test", "full_name": "Responsable Clínica Andes [DEMO]", "role": User.Role.CLINIC},
    {"email": "nuble24.demo@pettech.test", "full_name": "Responsable Hospital Ñuble 24 [DEMO]", "role": User.Role.CLINIC},
]

CLINIC_ACCOUNTS = {
    "andes.demo@pettech.test": "Clínica Veterinaria Andes [DEMO]",
    "nuble24.demo@pettech.test": "Hospital Veterinario Ñuble 24 [DEMO]",
}

WEEKDAYS = range(0, 5)
# (nombre, dirección, lat, lng, urgencias, 24h, servicios, origen, días desde verificación, horario, red PetTech)
DEMO_CLINICS = [
    ("Clínica Veterinaria Andes [DEMO]", "Av. Libertad 100, Chillán [SIMULADO]", -36.6052, -72.1022,
     True, False, ["GENERAL", "VACCINATION", "SURGERY", "LAB"], "CONFIRMED", 3,
     {d: (time(9), time(20)) for d in WEEKDAYS} | {5: (time(10), time(14))}, True),
    ("Hospital Veterinario Ñuble 24 [DEMO]", "Av. Collín 200, Chillán [SIMULADO]", -36.6150, -72.0950,
     True, True, ["GENERAL", "SURGERY", "HOSPITALIZATION", "IMAGING", "LAB"], "CONFIRMED", 10, {}, True),
    ("Centro Veterinario Las Termas [DEMO]", "Av. O'Higgins 300, Chillán [SIMULADO]", -36.5980, -72.0890,
     False, False, ["GENERAL", "VACCINATION"], "PUBLIC_UNCONFIRMED", 45,
     {d: (time(10), time(19)) for d in WEEKDAYS}, False),
    ("Veterinaria Plaza [DEMO]", "Calle El Roble 400, Chillán [SIMULADO]", -36.6080, -72.1100,
     False, False, ["GENERAL", "VACCINATION", "EXOTICS"], "CONFIRMED", 7,
     {d: (time(9, 30), time(18, 30)) for d in range(0, 6)}, False),
    ("Clínica Veterinaria Nocturna [DEMO]", "Av. Ecuador 500, Chillán [SIMULADO]", -36.6200, -72.1150,
     True, False, ["GENERAL", "HOSPITALIZATION"], "PUBLIC_UNCONFIRMED", 60,
     {d: (time(20), time(8)) for d in range(0, 7)}, False),
    ("Veterinaria San Carlos [DEMO]", "Calle Independencia 600, San Carlos [SIMULADO]", -36.4245, -71.9580,
     False, False, ["GENERAL", "VACCINATION"], "CONFIRMED", 15,
     {d: (time(9), time(18)) for d in WEEKDAYS}, False),
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
                    if data["role"] == User.Role.ADMIN:
                        user.is_staff = True  # también puede usar Django Admin
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

            clinics = self._seed_clinics()
            for email, clinic_name in [("vet1.demo@pettech.test", "Clínica Veterinaria Andes [DEMO]"),
                                       ("vet2.demo@pettech.test", "Hospital Veterinario Ñuble 24 [DEMO]")]:
                ProfessionalMembership.objects.get_or_create(
                    professional=users[email], clinic=clinics[clinic_name],
                    defaults={"is_verified": True, "verified_at": timezone.now(), "license_number": "DEMO-0000"},
                )
            for email, clinic_name in CLINIC_ACCOUNTS.items():
                clinic = clinics[clinic_name]
                if clinic.account_id is None:
                    clinic.account = users[email]
                    clinic.save(update_fields=["account"])
            self._seed_records(users, clinics)
            self._seed_metrics_history(users, clinics)

        self.stdout.write(self.style.SUCCESS("Datos simulados listos. Contraseña: la definida en DEMO_PASSWORD."))

    def _seed_clinics(self):
        clinics = {}
        now = timezone.now()
        for (name, address, lat, lng, emergencies, h24, services, origin, age, hours, network) in DEMO_CLINICS:
            clinic, created = Clinic.objects.get_or_create(
                name=name,
                defaults=dict(
                    address=address, commune="San Carlos" if "San Carlos" in address else "Chillán",
                    phone="+56 9 0000 0000", location=Point(lng, lat, srid=4326),
                    attends_emergencies=emergencies, emergency_24h=h24, services=services, info_origin=origin,
                    verified_at=now - timedelta(days=age), accepts_pettech_records=network,
                ),
            )
            if created:
                for weekday, (opens, closes) in hours.items():
                    OpeningHour.objects.create(clinic=clinic, weekday=weekday, opens_at=opens, closes_at=closes)
            clinics[name] = clinic
            self.stdout.write(f"{'Creado' if created else 'Existente'}: {name}")
        return clinics

    def _seed_records(self, users, clinics):
        luna = Pet.objects.filter(name="Luna [DEMO]", owner=users["tutora.demo@pettech.test"]).first()
        if luna is None or ClinicalEntry.objects.filter(pet=luna).exists():
            return
        tutor = users["tutora.demo@pettech.test"]
        vet = users["vet2.demo@pettech.test"]
        clinic = clinics["Hospital Veterinario Ñuble 24 [DEMO]"]
        today = timezone.localdate()
        declared = dict(origin=Origin.OWNER_DECLARED, created_by=tutor, author_name=tutor.full_name)
        professional = dict(origin=Origin.PROFESSIONAL, created_by=vet, author_name=vet.full_name,
                            clinic=clinic, clinic_name=clinic.name)
        ClinicalEntry.objects.create(pet=luna, entry_type=EntryType.VACCINE, title="Antirrábica [SIMULADO]",
                                     occurred_on=date(today.year - 1, 3, 10),
                                     next_due_on=date(today.year, 3, 10), **declared)
        ClinicalEntry.objects.create(pet=luna, entry_type=EntryType.DEWORMING, title="Antiparasitario interno [SIMULADO]",
                                     occurred_on=today - timedelta(days=80), next_due_on=today + timedelta(days=10),
                                     **declared)
        ClinicalEntry.objects.create(pet=luna, entry_type=EntryType.VISIT, title="Control anual [SIMULADO]",
                                     occurred_on=today - timedelta(days=30), weight_kg=14.2,
                                     notes="Examen general sin hallazgos. Dato simulado.",
                                     next_due_on=today + timedelta(days=335), **professional)
        ClinicalEntry.objects.create(pet=luna, entry_type=EntryType.VACCINE, title="Séxtuple canina [SIMULADO]",
                                     occurred_on=today - timedelta(days=30), next_due_on=today + timedelta(days=335),
                                     lot_number="LOTE-DEMO-01", **professional)
        ClinicalEntry.objects.create(pet=luna, entry_type=EntryType.ALLERGY, title="Penicilina [SIMULADO]",
                                     occurred_on=today - timedelta(days=30), **professional)
        self.stdout.write("Creada: ficha clínica simulada de Luna [DEMO]")

    def _seed_metrics_history(self, users, clinics):
        """
        Historial SIMULADO de atenciones de la Clínica Andes [DEMO] en los últimos 12 meses, solo para que
        los cuadros de métricas del portal tengan datos en la demostración. Determinístico e idempotente.
        """
        import random

        clinic = clinics["Clínica Veterinaria Andes [DEMO]"]
        if ClinicalEntry.objects.filter(clinic=clinic, title__endswith="[SIMULADO-M]").exists():
            return
        vet = users["vet1.demo@pettech.test"]
        pets = list(Pet.objects.filter(name__in=["Luna [DEMO]", "Michi [DEMO]", "Rocky [DEMO]"]))
        if not pets:
            return
        rng = random.Random(2026)
        today = timezone.localdate()
        catalog = [
            (EntryType.VACCINE, "Antirrábica", {"manufacturer": "Laboratorio Demo", "route": "SC", "lot_number": "DEMO-R1"}),
            (EntryType.VACCINE, "Óctuple canina", {"manufacturer": "Laboratorio Demo", "route": "SC", "lot_number": "DEMO-O8"}),
            (EntryType.VACCINE, "Triple felina", {"manufacturer": "Laboratorio Demo", "route": "SC", "lot_number": "DEMO-T3"}),
            (EntryType.VISIT, "Control general", {"anamnesis": "Control rutinario.", "diagnosis": "Paciente sano.",
                                                  "temperature_c": 38.6, "heart_rate_bpm": 100, "respiratory_rate_rpm": 24}),
            (EntryType.VISIT, "Consulta por vómitos", {"anamnesis": "Vómitos hace 2 días.", "diagnosis": "Gastritis aguda.",
                                                       "treatment": "Dieta blanda y control en 5 días."}),
            (EntryType.DEWORMING, "Antiparasitario interno", {"manufacturer": "Laboratorio Demo", "route": "ORAL",
                                                              "dose": "1 comprimido, dosis única"}),
            (EntryType.SURGERY, "Esterilización", {"diagnosis": "Esterilización electiva.", "treatment": "Analgesia 3 días."}),
        ]
        weights = [5, 3, 2, 6, 2, 4, 1]
        created = 0
        for _ in range(42):
            etype, title, extra = rng.choices(catalog, weights=weights)[0]
            pet = rng.choice(pets)
            if title == "Triple felina" and pet.species != Pet.Species.CAT:
                continue
            if title == "Óctuple canina" and pet.species != Pet.Species.DOG:
                continue
            occurred = today - timedelta(days=rng.randint(0, 360))
            ClinicalEntry.objects.create(
                pet=pet, entry_type=etype, title=f"{title} [SIMULADO-M]", occurred_on=occurred,
                next_due_on=occurred + timedelta(days=365) if etype == EntryType.VACCINE else None,
                origin=Origin.PROFESSIONAL, created_by=vet, author_name=vet.full_name,
                clinic=clinic, clinic_name=clinic.name, **extra,
            )
            created += 1
        self.stdout.write(f"Creado: historial simulado de {created} atenciones para métricas (Clínica Andes [DEMO])")
