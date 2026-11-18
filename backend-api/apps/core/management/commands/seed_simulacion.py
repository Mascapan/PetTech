"""
Carga (o elimina) DATOS SIMULADOS para pruebas manuales en el servidor del piloto.

Qué crea (definido en apps/core/data/simulacion.json):
- 2 veterinarias de la Red PetTech, cada una con su cuenta del portal y 2 profesionales verificados.
- 6 tutores con 13 mascotas y algunos antecedentes declarados por el tutor (vacunas con próxima dosis
  cercana, para probar los recordatorios).
- Historial de consultas de los últimos 12 meses en la veterinaria habitual de cada mascota (formato SOAP,
  signos vitales, vacunas con laboratorio y lote), respaldado por autorizaciones del tutor ya vencidas.
- Foto de perfil de la mascota si existe apps/core/data/fotos_simulacion/<Nombre>.jpg (opcional).

Las personas, mascotas y establecimientos son FICTICIOS, aunque con nombres verosímiles. Para
identificarlos sin marcar la interfaz:
- los establecimientos tienen external_ref "sim:..." y source_note "Datos simulados (seed_simulacion)";
- las cuentas están listadas en el JSON.
En el repositorio solo se guardan hashes Argon2 de las contraseñas (nunca el texto en claro).

Es idempotente: lo que ya existe no se modifica (tampoco la contraseña, si el usuario la cambió).

Uso:
  python manage.py seed_simulacion            # crea lo que falte
  python manage.py seed_simulacion --remove   # elimina los datos simulados
En Render se activa con la variable PETTECH_SIM_DATA=1 (o =remove) desde scripts/start_production.sh.
"""
import json
import random
from datetime import datetime, time, timedelta
from decimal import Decimal
from pathlib import Path

from django.conf import settings
from django.contrib.gis.geos import Point
from django.core.files import File
from django.core.management.base import BaseCommand
from django.db import transaction
from django.utils import timezone

from apps.access_control.models import AccessRequest, AuditEvent
from apps.accounts.models import User
from apps.clinical_records import images
from apps.clinical_records.models import Attachment, ClinicalEntry, EntryType, Origin
from apps.clinics.models import Clinic, OpeningHour, ProfessionalMembership
from apps.pets.models import Pet

DATA_DIR = Path(__file__).resolve().parents[2] / "data"
DATA_FILE = DATA_DIR / "simulacion.json"
PHOTOS_DIR = DATA_DIR / "fotos_simulacion"
SOURCE_NOTE = "Datos simulados para pruebas del piloto (seed_simulacion)"
SIM_NOTE = "Antecedente de datos simulados para pruebas."

# Catálogo de atenciones verosímiles (productos comerciales habituales en Chile; lotes inventados).
DOG_VACCINES = [
    ("Antirrábica", {"manufacturer": "Boehringer Ingelheim (Rabisin)", "route": "SC", "dose": "1 ml, dosis anual"}),
    ("Séxtuple canina", {"manufacturer": "Zoetis (Vanguard Plus 5/CV-L)", "route": "SC", "dose": "1 ml, refuerzo anual"}),
    ("Óctuple canina", {"manufacturer": "MSD Salud Animal (Nobivac)", "route": "SC", "dose": "1 ml, refuerzo anual"}),
]
CAT_VACCINES = [
    ("Triple felina", {"manufacturer": "Zoetis (Felocell 3)", "route": "SC", "dose": "1 ml, refuerzo anual"}),
    ("Antirrábica", {"manufacturer": "Boehringer Ingelheim (Rabisin)", "route": "SC", "dose": "1 ml, dosis anual"}),
    ("Leucemia felina", {"manufacturer": "Boehringer Ingelheim (Purevax FeLV)", "route": "SC", "dose": "1 ml, refuerzo anual"}),
]
DEWORMERS = [
    ("Antiparasitario interno", {"manufacturer": "Elanco (Drontal Plus)", "route": "ORAL", "dose": "1 comprimido cada 10 kg, dosis única"}, 90),
    ("Antiparasitario externo", {"manufacturer": "MSD Salud Animal (Bravecto)", "route": "ORAL", "dose": "1 comprimido masticable, cada 12 semanas"}, 84),
    ("Antiparasitario interno y externo", {"manufacturer": "Boehringer Ingelheim (NexGard Spectra)", "route": "ORAL", "dose": "1 comprimido mensual"}, 30),
]
VISITS = [
    ("Control sano anual", "Tutor consulta por control anual. Come y bebe normal, sin vómitos ni diarrea.",
     "Paciente alerta, hidratado. Auscultación cardiopulmonar normal. Abdomen blando, no doloroso.",
     "Paciente sano.", "Mantener plan sanitario. Control en 12 meses.", 365),
    ("Otitis externa", "Se rasca la oreja derecha y sacude la cabeza hace 4 días.",
     "Conducto auditivo derecho eritematoso con cerumen oscuro. Dolor a la palpación.",
     "Otitis externa derecha.", "Limpieza ótica y gotas óticas cada 12 h por 7 días. Control en 10 días.", 10),
    ("Gastroenteritis", "Diarrea y un vómito desde ayer. Cambio reciente de alimento.",
     "Leve dolor abdominal craneal. Deshidratación menor al 5 %.",
     "Gastroenteritis aguda por cambio de dieta.", "Dieta blanda 5 días, probiótico y protector gástrico. Control si persiste.", 5),
    ("Dermatitis", "Prurito y caída de pelo en zona lumbar hace dos semanas.",
     "Eritema y pápulas en zona dorsolumbar; se observan heces de pulga.",
     "Dermatitis alérgica por picadura de pulga.", "Antiparasitario externo y antiinflamatorio por 5 días. Control en 15 días.", 15),
    ("Cojera", "Cojea de la extremidad posterior izquierda tras jugar en el parque.",
     "Dolor leve a la manipulación de rodilla izquierda; sin inestabilidad articular.",
     "Esguince leve.", "Reposo relativo 7 días y antiinflamatorio. Si no mejora, radiografía.", 7),
    ("Control dental", "Mal aliento desde hace un mes.",
     "Sarro grado 2 en premolares y molares, gingivitis leve.",
     "Enfermedad periodontal grado 2.", "Se agenda destartraje con anestesia general. Cepillado dental.", 20),
]


def _vitals(rng: random.Random, dog: bool) -> dict:
    return {
        "temperature_c": Decimal(str(round(rng.uniform(38.0, 39.1), 1))),
        "heart_rate_bpm": rng.randint(80, 130) if dog else rng.randint(150, 200),
        "respiratory_rate_rpm": rng.randint(16, 30) if dog else rng.randint(20, 32),
        "body_condition_score": rng.choice((4, 5, 5, 5, 6)),
        "mucous_membranes": "PINK",
    }


def _visit_items(rng: random.Random, pet: Pet, dog: bool, *, first: bool, skip: set) -> list[tuple]:
    """Lo que se registra en una visita: una consulta (con signos vitales) y a veces vacuna o desparasitación."""
    vitals = _vitals(rng, dog)
    title, s, o, a, plan, control = VISITS[0] if first else rng.choice(VISITS)
    items = [(EntryType.VISIT, title, {"anamnesis": s, "physical_exam": o, "diagnosis": a, "treatment": plan,
                                       "_next_due": control, **vitals})]
    # No se repite una vacuna que el tutor ya tiene registrada con refuerzo pendiente (sus recordatorios).
    options = [v for v in (DOG_VACCINES if dog else CAT_VACCINES) if v[0] not in skip]
    if options and (first or rng.random() < 0.45):
        name, extra = rng.choice(options)
        lot = f"{rng.choice('ABCDKLMR')}{rng.randint(100, 999)}{rng.choice('ABCXYZ')}{rng.randint(10, 99)}"
        items.append((EntryType.VACCINE, name, {**extra, "lot_number": lot, "_next_due": 365,
                                                "lot_expires_on": timezone.localdate() + timedelta(days=rng.randint(200, 600))}))
    if rng.random() < 0.4:
        name, extra, every = rng.choice(DEWORMERS)
        items.append((EntryType.DEWORMING, name, {**extra, "_next_due": every}))
    if rng.random() < 0.35:
        base = {"Labrador Retriever": 34, "Golden Retriever": 30, "Pastor Alemán": 31, "Beagle": 13,
                "Bulldog Francés": 12, "Poodle": 7, "Jack Russell Terrier": 7, "Mestiza": 18}.get(pet.breed, 4.5)
        items.append((EntryType.WEIGHT, "Control de peso",
                      {"weight_kg": Decimal(str(round(base * rng.uniform(0.92, 1.08), 1)))}))
    return items


def _load() -> dict:
    with DATA_FILE.open(encoding="utf-8") as fh:
        return json.load(fh)


def _all_accounts(data: dict) -> list[dict]:
    accounts = []
    for clinic in data["clinicas"]:
        accounts.append({**clinic["cuenta"], "role": User.Role.CLINIC})
        accounts += [{**p, "role": User.Role.PROFESSIONAL} for p in clinic["profesionales"]]
    accounts += [{**t, "role": User.Role.TUTOR} for t in data["tutores"]]
    return accounts


def _hhmm(value: str) -> time:
    hours, minutes = value.split(":")
    return time(int(hours), int(minutes))


class Command(BaseCommand):
    help = "Crea (o elimina con --remove) cuentas, veterinarias y mascotas simuladas para pruebas manuales."

    def add_arguments(self, parser):
        parser.add_argument("--remove", action="store_true", help="Elimina los datos simulados.")

    def handle(self, *args, **options):
        data = _load()
        if options["remove"]:
            self._remove(data)
        else:
            self._seed(data)

    # ------------------------------------------------------------------ carga
    @transaction.atomic
    def _seed(self, data: dict) -> None:
        users = {}
        created_users = 0
        for acc in _all_accounts(data):
            user = User.objects.filter(email__iexact=acc["email"]).first()
            if user is None:
                user = User(email=acc["email"], full_name=acc["nombre"], role=acc["role"],
                            privacy_policy_version=settings.PETTECH_PRIVACY_POLICY_VERSION,
                            privacy_policy_accepted_at=timezone.now())
                user.password = acc["hash"]  # hash Argon2 ya calculado: no hay contraseña en claro en el código
                user.save()
                created_users += 1
            elif user.role != acc["role"]:
                self.stderr.write(f"seed_simulacion: {acc['email']} ya existe con otro rol; se omite.")
                continue
            users[acc["email"].lower()] = user

        now = timezone.now()
        for item in data["clinicas"]:
            clinic = Clinic.objects.filter(external_ref=item["ref"]).first()
            if clinic is None:
                clinic = Clinic.objects.create(
                    name=item["nombre"], address=item["direccion"], commune=item["comuna"], phone=item["telefono"],
                    contact_email=item["email"], location=Point(item["lng"], item["lat"], srid=4326),
                    attends_emergencies=item["urgencias"], emergency_24h=item["urgencias_24h"],
                    services=item["servicios"], services_detail=item["detalle"], source_note=SOURCE_NOTE,
                    external_ref=item["ref"], info_origin=Clinic.InfoOrigin.CONFIRMED, verified_at=now,
                    accepts_pettech_records=True, is_active=True,
                )
                for weekday, (opens, closes) in item["horario"].items():
                    OpeningHour.objects.create(clinic=clinic, weekday=int(weekday),
                                               opens_at=_hhmm(opens), closes_at=_hhmm(closes))
                self.stdout.write(f"seed_simulacion: veterinaria creada: {clinic.name}")
            account = users.get(item["cuenta"]["email"].lower())
            if clinic.account_id is None and account is not None and not hasattr(account, "managed_clinic"):
                clinic.account = account
                clinic.save(update_fields=["account"])
            for prof in item["profesionales"]:
                user = users.get(prof["email"].lower())
                if user is not None:
                    ProfessionalMembership.objects.get_or_create(
                        professional=user, clinic=clinic,
                        defaults={"is_verified": True, "verified_at": now, "license_number": prof["registro"]},
                    )

        today = timezone.localdate()
        created_pets = 0
        for tutor in data["tutores"]:
            owner = users.get(tutor["email"].lower())
            if owner is None:
                continue
            for p in tutor["mascotas"]:
                pet, created = Pet.objects.get_or_create(
                    owner=owner, name=p["nombre"],
                    defaults=dict(species=p["especie"], breed=p["raza"], sex=p["sexo"], birth_date=p["nacimiento"],
                                  birth_date_is_estimated=p["estimada"], general_notes=p["notas"]),
                )
                if not created:
                    continue
                created_pets += 1
                for etype, title, ago, due_in, weight in p["registros"]:
                    ClinicalEntry.objects.create(
                        pet=pet, entry_type=etype, title=title,
                        occurred_on=today + timedelta(days=ago),
                        next_due_on=today + timedelta(days=due_in) if due_in is not None else None,
                        weight_kg=Decimal(str(weight)) if weight is not None else None,
                        notes=SIM_NOTE, origin=Origin.OWNER_DECLARED, created_by=owner, author_name=owner.full_name,
                    )
        clinics_by_ref = {c.external_ref: c for c in Clinic.objects.filter(external_ref__startswith="sim:")}
        vets_by_ref = {
            item["ref"]: [users[p["email"].lower()] for p in item["profesionales"] if p["email"].lower() in users]
            for item in data["clinicas"]
        }
        consultations = photos = 0
        for tutor in data["tutores"]:
            owner = users.get(tutor["email"].lower())
            if owner is None:
                continue
            for p in tutor["mascotas"]:
                pet = Pet.objects.filter(owner=owner, name=p["nombre"]).first()
                if pet is None:
                    continue
                clinic = clinics_by_ref.get(p.get("veterinaria", ""))
                if clinic is not None and vets_by_ref.get(clinic.external_ref):
                    consultations += self._seed_consultations(pet, clinic, vets_by_ref[clinic.external_ref], today)
                photos += self._seed_photo(pet, owner)
        self.stdout.write(self.style.SUCCESS(
            f"seed_simulacion: listo ({created_users} cuentas, {created_pets} mascotas, "
            f"{consultations} consultas y {photos} fotos nuevas)."))

    # ------------------------------------------------- consultas simuladas
    def _seed_consultations(self, pet: Pet, clinic: Clinic, vets: list, today) -> int:
        """
        Historial de atenciones de la veterinaria habitual en los últimos 12 meses, con el mismo formato que
        produce el portal (SOAP, signos vitales, producto y lote). Determinístico por mascota. Cada visita
        queda respaldada por una autorización del tutor ya vencida, como ocurriría en la realidad; no deja
        accesos vigentes, para que el flujo de solicitud se pueda probar desde cero.
        """
        if ClinicalEntry.objects.filter(pet=pet, clinic=clinic, origin=Origin.PROFESSIONAL).exists():
            return 0
        rng = random.Random(f"{pet.name}|{pet.owner.email}")
        skip = set(ClinicalEntry.objects.filter(pet=pet, origin=Origin.OWNER_DECLARED, entry_type=EntryType.VACCINE,
                                                next_due_on__gte=today).values_list("title", flat=True))
        dog = pet.species == Pet.Species.DOG
        visits = sorted(rng.sample(range(20, 360), rng.randint(3, 5)), reverse=True)
        created = 0
        for i, ago in enumerate(visits):
            vet = rng.choice(vets)
            day = today - timedelta(days=ago)
            when = timezone.make_aware(datetime.combine(day, time(rng.randint(10, 18), rng.choice((0, 15, 30, 45)))))
            req = AccessRequest.objects.create(
                pet=pet, clinic=clinic, professional=vet, reason="Atención en consulta",
                status=AccessRequest.Status.APPROVED, decided_at=when + timedelta(minutes=2),
                expires_at=when + timedelta(hours=24),
            )
            AccessRequest.objects.filter(pk=req.pk).update(created_at=when)
            items = _visit_items(rng, pet, dog, first=(i == 0), skip=skip)
            for etype, title, extra in items:
                due = extra.pop("_next_due", None)
                entry = ClinicalEntry.objects.create(
                    pet=pet, entry_type=etype, title=title, occurred_on=day,
                    next_due_on=day + timedelta(days=due) if due else None,
                    origin=Origin.PROFESSIONAL, created_by=vet, author_name=vet.full_name,
                    clinic=clinic, clinic_name=clinic.name, **extra,
                )
                ClinicalEntry.objects.filter(pk=entry.pk).update(created_at=when + timedelta(minutes=10))
                created += 1
            for action, minutes in ((AuditEvent.Action.ACCESS_REQUESTED, 0), (AuditEvent.Action.ACCESS_APPROVED, 2),
                                    (AuditEvent.Action.RECORD_CREATED, 10)):
                actor = pet.owner if action == AuditEvent.Action.ACCESS_APPROVED else vet
                ev = AuditEvent.objects.create(actor=actor, actor_name=actor.full_name, clinic=clinic,
                                               clinic_name=clinic.name, pet=pet, pet_code=pet.public_code,
                                               action=action, resource_id=str(req.pk))
                AuditEvent.objects.filter(pk=ev.pk).update(created_at=when + timedelta(minutes=minutes))
        return created

    # ------------------------------------------------------ fotos opcionales
    def _seed_photo(self, pet: Pet, owner: User) -> int:
        if Attachment.objects.filter(pet=pet, kind=Attachment.Kind.PET_PHOTO).exists():
            return 0
        for ext in (".jpg", ".jpeg", ".png"):
            path = PHOTOS_DIR / f"{pet.name}{ext}"
            if path.exists():
                with path.open("rb") as fh:
                    image = images.normalize(File(fh, name=path.name))
                Attachment.objects.create(
                    pet=pet, kind=Attachment.Kind.PET_PHOTO, content=image.content, size_bytes=len(image.content),
                    width=image.width, height=image.height, sha256=image.sha256, origin=Origin.OWNER_DECLARED,
                    uploaded_by=owner, author_name=owner.full_name,
                )
                return 1
        return 0

    # ------------------------------------------------------------ eliminación
    @transaction.atomic
    def _remove(self, data: dict) -> None:
        emails = [a["email"] for a in _all_accounts(data)]
        sim_users = list(User.objects.filter(email__in=emails))
        sim_clinics = list(Clinic.objects.filter(external_ref__in=[c["ref"] for c in data["clinicas"]]))

        # Mascotas de los tutores simulados, con todo su historial.
        pets = Pet.objects.filter(owner__in=sim_users)
        Attachment.objects.filter(pet__in=pets, entry__isnull=False).delete()
        entries = ClinicalEntry.objects.filter(pet__in=pets)
        for entry in entries.filter(corrects__isnull=False).order_by("-created_at"):
            entry.delete()
        entries.delete()
        Attachment.objects.filter(pet__in=pets).delete()
        removed_pets = pets.delete()[1].get("pets.Pet", 0)

        # Si un profesional simulado registró algo en la ficha de una mascota real, esos registros
        # NO se borran (RN-06): la cuenta y la veterinaria solo se desactivan.
        kept = 0
        for clinic in sim_clinics:
            in_use = (ClinicalEntry.objects.filter(clinic=clinic).exists()
                      or Attachment.objects.filter(clinic=clinic).exists()
                      or clinic.access_requests.exists())
            if in_use:
                clinic.is_active = False
                clinic.accepts_pettech_records = False
                clinic.save(update_fields=["is_active", "accepts_pettech_records"])
                kept += 1
            else:
                clinic.delete()
        for user in sim_users:
            in_use = (ClinicalEntry.objects.filter(created_by=user).exists()
                      or Attachment.objects.filter(uploaded_by=user).exists()
                      or user.access_requests.exists())
            if in_use:
                user.is_active = False
                user.save(update_fields=["is_active"])
                kept += 1
            else:
                user.delete()
        self.stdout.write(self.style.SUCCESS(
            f"seed_simulacion --remove: {removed_pets} mascotas eliminadas; "
            f"{kept} cuentas/veterinarias desactivadas por tener registros en fichas reales."))
