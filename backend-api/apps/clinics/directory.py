"""
Directorio de veterinarias NO afiliadas (fuente pública).

Decisión DEC-029: no se copian datos de Google Maps (sus términos lo prohíben). El directorio se arma con
OpenStreetMap (licencia ODbL, exige atribución) y con datos que el equipo verifica en fuentes públicas
de cada establecimiento. Estos registros se rotulan "Fuente pública no confirmada" y NO forman parte de
la Red PetTech hasta que el administrador les crea una cuenta (afiliación).
"""
import re
from datetime import time

from django.contrib.gis.geos import Point
from django.db import transaction
from django.utils import timezone

from .models import Clinic, OpeningHour

OSM_ATTRIBUTION = "© colaboradores de OpenStreetMap (ODbL)"
DAYS = {"Mo": 0, "Tu": 1, "We": 2, "Th": 3, "Fr": 4, "Sa": 5, "Su": 6}
_RULE = re.compile(r"^\s*(?P<days>[A-Za-z,\- ]+)\s+(?P<ranges>\d{1,2}:\d{2}-\d{1,2}:\d{2}(?:\s*,\s*\d{1,2}:\d{2}-\d{1,2}:\d{2})*)\s*$")


def _expand_days(spec: str) -> list[int]:
    out = []
    for part in spec.replace(" ", "").split(","):
        if "-" in part:
            a, b = part.split("-", 1)
            if a not in DAYS or b not in DAYS:
                return []
            i, j = DAYS[a], DAYS[b]
            out.extend(range(i, j + 1) if i <= j else [*range(i, 7), *range(0, j + 1)])
        elif part in DAYS:
            out.append(DAYS[part])
        else:
            return []
    return out


def parse_opening_hours(value: str) -> tuple[dict[int, list[tuple[time, time]]], bool] | None:
    """
    Interpreta el subconjunto simple del formato opening_hours de OSM ("Mo-Fr 09:00-19:00; Sa 10:00-14:00",
    "24/7"). Devuelve (franjas por día, es_24h) o None si el formato no es reconocible (se ignora).
    """
    value = (value or "").strip()
    if not value:
        return None
    if value == "24/7":
        return {}, True
    slots: dict[int, list[tuple[time, time]]] = {}
    for rule in value.split(";"):
        if not rule.strip() or rule.strip().lower().endswith("off"):
            continue
        m = _RULE.match(rule)
        if not m:
            return None
        days = _expand_days(m["days"])
        if not days:
            return None
        for rng in m["ranges"].split(","):
            a, b = rng.strip().split("-")
            ha, ma = map(int, a.split(":"))
            hb, mb = map(int, b.split(":"))
            if ha > 23 or hb > 24 or ma > 59 or mb > 59:
                return None
            opens, closes = time(ha, ma), time(0, 0) if hb == 24 else time(hb, mb)
            if opens == closes:
                return None
            for d in days:
                slots.setdefault(d, []).append((opens, closes))
    return (slots, False) if slots else None


def _address(tags: dict) -> str:
    street = " ".join(x for x in [tags.get("addr:street", ""), tags.get("addr:housenumber", "")] if x).strip()
    return street or tags.get("addr:full", "") or "Dirección no informada"


@transaction.atomic
def import_osm(elements: list[dict], default_commune: str = "Chillán") -> dict:
    """Crea o actualiza establecimientos del directorio desde elementos Overpass (out center tags)."""
    stats = {"created": 0, "updated": 0, "skipped": 0}
    now = timezone.now()
    for el in elements:
        tags = el.get("tags") or {}
        name = (tags.get("name") or "").strip()
        lat = el.get("lat", (el.get("center") or {}).get("lat"))
        lon = el.get("lon", (el.get("center") or {}).get("lon"))
        if not name or lat is None or lon is None or tags.get("amenity") != "veterinary":
            stats["skipped"] += 1
            continue
        ref = f"osm:{el.get('type', 'node')}/{el.get('id')}"
        clinic = Clinic.objects.filter(external_ref=ref).first()
        if clinic is None and Clinic.objects.filter(name__iexact=name).exists():
            stats["skipped"] += 1  # ya existe (posiblemente afiliada o cargada a mano): no se duplica
            continue
        if clinic is not None and clinic.info_origin == Clinic.InfoOrigin.CONFIRMED:
            stats["skipped"] += 1  # la propia veterinaria ya confirmó sus datos: prevalecen sobre OSM
            continue
        hours = parse_opening_hours(tags.get("opening_hours", ""))
        data = dict(
            name=name[:120], address=_address(tags)[:200],
            commune=(tags.get("addr:city") or default_commune)[:60],
            phone=(tags.get("phone") or tags.get("contact:phone") or "")[:20],
            website=(tags.get("website") or tags.get("contact:website") or "")[:200],
            contact_email=(tags.get("email") or tags.get("contact:email") or "")[:254],
            location=Point(float(lon), float(lat), srid=4326),
            attends_emergencies=tags.get("emergency") == "yes" or bool(hours and hours[1]),
            emergency_24h=bool(hours and hours[1]),
            source_note=f"OpenStreetMap {ref} · {OSM_ATTRIBUTION}"[:200],
            info_origin=Clinic.InfoOrigin.PUBLIC_UNCONFIRMED, verified_at=now,
        )
        if clinic is None:
            clinic = Clinic.objects.create(external_ref=ref, accepts_pettech_records=False, services=["GENERAL"], **data)
            stats["created"] += 1
        else:
            for k, v in data.items():
                setattr(clinic, k, v)
            clinic.save()
            stats["updated"] += 1
        if hours is not None:
            clinic.opening_hours.all().delete()
            OpeningHour.objects.bulk_create([
                OpeningHour(clinic=clinic, weekday=d, opens_at=o, closes_at=c)
                for d, ranges in hours[0].items() for o, c in ranges
            ])
    return stats
