"""
Carga el directorio de veterinarias NO afiliadas desde un archivo exportado de OpenStreetMap (Overpass).

Cómo obtener el archivo (en el navegador, sin programar):
  1. Abrir https://overpass-turbo.eu
  2. Pegar la consulta de docs/DIRECTORIO_VETERINARIAS.md y presionar "Ejecutar".
  3. "Exportar" → "datos sin procesar (raw OSM data / JSON)" → guardar como directorio_chillan.json

Uso:  python manage.py import_osm_directory directorio_chillan.json [--dry-run]
Es idempotente: vuelve a ejecutarse sin duplicar (usa el identificador OSM).
"""
import json

from django.core.management.base import BaseCommand, CommandError
from django.db import transaction

from apps.clinics.directory import import_osm


class Command(BaseCommand):
    help = "Importa veterinarias (amenity=veterinary) desde un JSON de Overpass al directorio no afiliado."

    def add_arguments(self, parser):
        parser.add_argument("path")
        parser.add_argument("--commune", default="Chillán")
        parser.add_argument("--dry-run", action="store_true", help="Muestra el resultado sin guardar.")

    def handle(self, path, commune, dry_run, **options):
        try:
            with open(path, encoding="utf-8") as fh:
                payload = json.load(fh)
        except (OSError, ValueError) as exc:
            raise CommandError(f"No se pudo leer el archivo: {exc}") from exc
        elements = payload.get("elements") if isinstance(payload, dict) else None
        if not isinstance(elements, list):
            raise CommandError("El archivo no tiene el formato de Overpass (falta 'elements').")
        with transaction.atomic():
            stats = import_osm(elements, default_commune=commune)
            if dry_run:
                transaction.set_rollback(True)
        prefix = "[simulación] " if dry_run else ""
        self.stdout.write(self.style.SUCCESS(
            f"{prefix}Creadas: {stats['created']} · actualizadas: {stats['updated']} · omitidas: {stats['skipped']}"))
