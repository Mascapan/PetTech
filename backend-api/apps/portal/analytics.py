"""
Indicadores para los cuadros de análisis del portal.

El administrador ve SOLO cifras agregadas (sin datos clínicos de mascotas concretas), en
coherencia con RN-11: el rol ADMIN no otorga acceso clínico.
"""
from datetime import timedelta
from statistics import median

from django.conf import settings
from django.db.models import Count
from django.db.models.functions import TruncDate
from django.utils import timezone

from apps.access_control.models import AccessRequest
from apps.accounts.models import User
from apps.clinical_records.models import ClinicalEntry, EntryType, Origin
from apps.clinics.models import Clinic, ProfessionalMembership
from apps.pets.models import Pet

DAYS = 14


def _daily_series(qs, field: str, days: int = DAYS) -> dict:
    today = timezone.localdate()
    start = today - timedelta(days=days - 1)
    rows = (
        qs.filter(**{f"{field}__date__gte": start})
        .annotate(day=TruncDate(field))
        .values("day")
        .annotate(n=Count("pk"))
    )
    counts = {r["day"]: r["n"] for r in rows}
    labels, values = [], []
    for i in range(days):
        d = start + timedelta(days=i)
        labels.append(d.strftime("%d-%m"))
        values.append(counts.get(d, 0))
    return {"labels": labels, "values": values}


def _request_stats(qs) -> dict:
    """Estados efectivos, tasa de aprobación y tiempo de respuesta del tutor."""
    status_counts = {s: 0 for s in AccessRequest.Status.values}
    response_minutes = []
    for req in qs.only("status", "expires_at", "created_at", "decided_at"):
        status_counts[req.effective_status] += 1
        if req.decided_at:
            response_minutes.append((req.decided_at - req.created_at).total_seconds() / 60)
    decided = qs.filter(decided_at__isnull=False).count()
    approved_ever = qs.filter(decided_at__isnull=False).exclude(status="REJECTED").count()
    return {
        "by_status": {AccessRequest.Status(k).label: v for k, v in status_counts.items()},
        "approval_rate": round(100 * approved_ever / decided) if decided else None,
        "median_response_min": round(median(response_minutes), 1) if response_minutes else None,
        "total": sum(status_counts.values()),
    }


def admin_metrics() -> dict:
    now = timezone.now()
    stale_limit = now - timedelta(days=settings.PETTECH_CLINIC_STALE_DAYS)
    clinics = Clinic.objects.all()
    entries = ClinicalEntry.objects.filter(corrected_by__isnull=True)
    requests = AccessRequest.objects.all()

    by_type = dict(entries.values_list("entry_type").annotate(n=Count("pk")))
    per_clinic = (
        ClinicalEntry.objects.filter(origin=Origin.PROFESSIONAL)
        .values("clinic_name").annotate(n=Count("pk")).order_by("-n")[:8]
    )
    return {
        "kpis": {
            "tutors": User.objects.filter(role=User.Role.TUTOR, is_active=True).count(),
            "pets": Pet.objects.count(),
            "clinics_network": clinics.filter(is_active=True, accepts_pettech_records=True).count(),
            "clinics_total": clinics.filter(is_active=True).count(),
            "professionals": ProfessionalMembership.objects.filter(is_verified=True).count(),
            "records_professional": entries.filter(origin=Origin.PROFESSIONAL).count(),
            "records_owner": entries.filter(origin=Origin.OWNER_DECLARED).count(),
        },
        "requests": _request_stats(requests),
        "charts": {
            "requests_daily": _daily_series(requests, "created_at"),
            "records_daily": _daily_series(ClinicalEntry.objects.all(), "created_at"),
            "records_by_type": {
                "labels": [t.label for t in EntryType],
                "values": [by_type.get(t.value, 0) for t in EntryType],
            },
            "records_per_clinic": {
                "labels": [r["clinic_name"] for r in per_clinic],
                "values": [r["n"] for r in per_clinic],
            },
            "catalog": {
                "labels": ["Confirmado y vigente", "Confirmado pero antiguo", "Fuente pública no confirmada"],
                "values": [
                    clinics.filter(is_active=True, info_origin="CONFIRMED", verified_at__gte=stale_limit).count(),
                    clinics.filter(is_active=True, info_origin="CONFIRMED", verified_at__lt=stale_limit).count(),
                    clinics.filter(is_active=True, info_origin="PUBLIC_UNCONFIRMED").count(),
                ],
            },
        },
        "generated_at": now,
    }


def clinic_metrics(clinic: Clinic) -> dict:
    requests = AccessRequest.objects.filter(clinic=clinic)
    entries = ClinicalEntry.objects.filter(clinic=clinic, origin=Origin.PROFESSIONAL)
    return {
        "professionals": clinic.memberships.filter(is_verified=True).count(),
        "requests": _request_stats(requests),
        "records": entries.count(),
        "patients": entries.values("pet").distinct().count(),
        "requests_daily": _daily_series(requests, "created_at"),
        "active_grants": sum(1 for r in requests.filter(status="APPROVED") if r.is_active_grant),
        "pending": sum(1 for r in requests.filter(status="PENDING") if r.effective_status == "PENDING"),
        "records_by_type": dict(entries.values_list("entry_type").annotate(n=Count("pk"))),
        "info_is_stale": clinic.verified_at < timezone.now() - timedelta(days=settings.PETTECH_CLINIC_STALE_DAYS),
    }


PERIODS = {30: "Últimos 30 días", 90: "Últimos 90 días", 365: "Último año"}
WEEKDAY_LABELS = ["Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"]
MONTH_LABELS = ["Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"]


def _months_back(today, n: int) -> list[tuple[int, int]]:
    year, month, out = today.year, today.month, []
    for _ in range(n):
        out.append((year, month))
        month -= 1
        if month == 0:
            year, month = year - 1, 12
    return list(reversed(out))


def clinic_attention_metrics(clinic: Clinic, days: int = 90) -> dict:
    """
    Indicadores de atención de UNA veterinaria (solo cifras agregadas: la cuenta de la veterinaria
    no ve fichas individuales, RN-11). Período configurable: 30, 90 o 365 días.
    """
    from apps.pets.models import Pet  # import local para evitar ciclos en el arranque

    days = days if days in PERIODS else 90
    today = timezone.localdate()
    start = today - timedelta(days=days - 1)
    base = ClinicalEntry.objects.filter(clinic=clinic, origin=Origin.PROFESSIONAL, corrected_by__isnull=True)
    period = base.filter(occurred_on__gte=start)

    by_type = dict(period.values_list("entry_type").annotate(n=Count("pk")))
    patients = set(period.values_list("pet_id", flat=True))
    species_rows = dict(
        Pet.objects.filter(pk__in=patients).values_list("species").annotate(n=Count("pk")).order_by()
    )
    seen_before = set(base.filter(occurred_on__lt=start, pet_id__in=patients).values_list("pet_id", flat=True))
    visits_per_pet = list(period.values("pet_id").annotate(n=Count("pk")).values_list("n", flat=True))

    # Serie mensual (12 meses) para ver estacionalidad, independiente del filtro de período.
    months = _months_back(today, 12)
    monthly_rows = (
        base.filter(occurred_on__gte=f"{months[0][0]}-{months[0][1]:02d}-01")
        .values_list("occurred_on__year", "occurred_on__month").annotate(n=Count("pk"))
    )
    monthly = {(y, m): n for y, m, n in monthly_rows}

    weekday = [0] * 7
    for d in period.values_list("occurred_on", flat=True):
        weekday[d.weekday()] += 1

    per_pro = period.values("author_name").annotate(n=Count("pk")).order_by("-n")[:8]
    vaccines = (
        period.filter(entry_type=EntryType.VACCINE).values("title").annotate(n=Count("pk")).order_by("-n")[:8]
    )
    upcoming = base.filter(next_due_on__gte=today, next_due_on__lte=today + timedelta(days=30))
    overdue = base.filter(next_due_on__lt=today, next_due_on__gte=today - timedelta(days=90))
    vitals = period.filter(entry_type=EntryType.VISIT)
    structured = vitals.exclude(diagnosis="").count()

    requests = AccessRequest.objects.filter(clinic=clinic, created_at__date__gte=start)
    return {
        "days": days,
        "period_label": PERIODS[days],
        "periods": PERIODS,
        "kpis": {
            "attentions": period.count(),
            "patients": len(patients),
            "new_patients": len(patients - seen_before),
            "returning_patients": len(patients & seen_before),
            "vaccines": by_type.get(EntryType.VACCINE, 0),
            "visits": by_type.get(EntryType.VISIT, 0),
            "avg_per_patient": round(sum(visits_per_pet) / len(visits_per_pet), 1) if visits_per_pet else None,
            "upcoming_30d": upcoming.count(),
            "overdue_90d": overdue.count(),
            "structured_pct": round(100 * structured / vitals.count()) if vitals.exists() else None,
            "professionals_active": period.values("created_by").distinct().count(),
        },
        "requests": _request_stats(requests),
        "charts": {
            "by_type": {"labels": [t.label for t in EntryType], "values": [by_type.get(t.value, 0) for t in EntryType]},
            "by_species": {"labels": [s.label for s in Pet.Species],
                           "values": [species_rows.get(s.value, 0) for s in Pet.Species]},
            "monthly": {"labels": [f"{MONTH_LABELS[m - 1]} {str(y)[2:]}" for y, m in months],
                        "values": [monthly.get(k, 0) for k in months]},
            "weekday": {"labels": WEEKDAY_LABELS, "values": weekday},
            "per_professional": {"labels": [r["author_name"] for r in per_pro], "values": [r["n"] for r in per_pro]},
            "top_vaccines": {"labels": [r["title"] for r in vaccines], "values": [r["n"] for r in vaccines]},
            "requests_daily": _daily_series(AccessRequest.objects.filter(clinic=clinic), "created_at",
                                            days=min(days, 30)),
        },
        "generated_at": timezone.now(),
    }
