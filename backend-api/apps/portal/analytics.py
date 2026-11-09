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
