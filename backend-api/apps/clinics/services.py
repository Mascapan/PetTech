"""
Búsqueda de establecimientos (HU-07, HU-08) y cálculo de "abierto según horario declarado".

La condición "abierto" se CALCULA con el horario registrado y la hora actual de Chile.
No equivale a confirmar que el establecimiento esté atendiendo ni informa cupos (RN-13).
"""
from dataclasses import dataclass
from datetime import datetime, timedelta

from django.conf import settings
from django.contrib.gis.db.models.functions import Distance
from django.contrib.gis.geos import Point
from django.contrib.gis.measure import D
from django.utils import timezone

from .models import Clinic

MAX_RADIUS_KM = 50


@dataclass
class SearchParams:
    lat: float | None = None
    lng: float | None = None
    radius_km: float = 10
    emergency: bool = False
    open_now: bool = False
    service: str | None = None
    network: bool = False


def is_open_at(clinic: Clinic, moment: datetime) -> bool:
    """True si 'moment' (hora local) cae dentro de alguna franja declarada o si declara atención 24 h."""
    if clinic.emergency_24h:
        return True
    local = timezone.localtime(moment)
    hours = list(clinic.opening_hours.all())
    for slot in hours:
        # Franja que empieza hoy.
        if slot.weekday == local.weekday():
            if slot.closes_at > slot.opens_at:
                if slot.opens_at <= local.time() < slot.closes_at:
                    return True
            elif local.time() >= slot.opens_at:  # cruza la medianoche
                return True
        # Franja nocturna que empezó ayer y aún no termina.
        yesterday = (local.weekday() - 1) % 7
        if slot.weekday == yesterday and slot.closes_at <= slot.opens_at and local.time() < slot.closes_at:
            return True
    return False


def today_hours_label(clinic: Clinic, moment: datetime) -> str:
    if clinic.emergency_24h:
        return "Abierto 24 horas (declarado)"
    local = timezone.localtime(moment)
    slots = [s for s in clinic.opening_hours.all() if s.weekday == local.weekday()]
    if not slots:
        return "Hoy sin horario declarado"
    return "Hoy: " + ", ".join(f"{s.opens_at:%H:%M}–{s.closes_at:%H:%M}" for s in slots)


def is_stale(clinic: Clinic, moment: datetime | None = None) -> bool:
    """La información supera el umbral de antigüedad configurable (HU-09, HU-12)."""
    moment = moment or timezone.now()
    return clinic.verified_at < moment - timedelta(days=settings.PETTECH_CLINIC_STALE_DAYS)


def search(params: SearchParams):
    """
    Devuelve (lista de clínicas, origen) ordenadas: primero abiertas según horario, luego por distancia.
    La ubicación del usuario se usa solo para esta consulta y NO se almacena (RNF-03).
    """
    qs = Clinic.objects.filter(is_active=True).prefetch_related("opening_hours")
    origin = None
    if params.lat is not None and params.lng is not None:
        origin = Point(params.lng, params.lat, srid=4326)
        radius = min(max(params.radius_km, 1), MAX_RADIUS_KM)
        qs = qs.filter(location__dwithin=(origin, D(km=radius))).annotate(distance=Distance("location", origin))
    if params.emergency:
        qs = qs.filter(attends_emergencies=True)
    if params.service:
        qs = qs.filter(services__contains=[params.service])
    if params.network:
        qs = qs.filter(accepts_pettech_records=True, account__isnull=False)

    now = timezone.now()
    results = []
    for clinic in qs:
        clinic.is_open_now = is_open_at(clinic, now)
        if params.open_now and not clinic.is_open_now:
            continue
        clinic.distance_km = round(clinic.distance.km, 2) if origin is not None else None
        results.append(clinic)

    # Orden: abiertas según horario, luego cercanía. La Red PetTech se distingue visualmente, no se privilegia.
    results.sort(key=lambda c: (not c.is_open_now, c.distance_km if c.distance_km is not None else 0, c.name))
    return results
