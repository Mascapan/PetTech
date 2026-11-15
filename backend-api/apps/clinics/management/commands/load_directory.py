"""
Carga (o actualiza) el directorio de veterinarias no afiliadas de Chillán desde
apps/clinics/data/directorio_chillan.json. Idempotente: se ejecuta en cada despliegue.

Uso:  python manage.py load_directory
"""
from django.core.management.base import BaseCommand

from apps.clinics.directory import load_curated


class Command(BaseCommand):
    help = "Carga el directorio de veterinarias de Chillán recopilado desde fuentes públicas."

    def handle(self, *args, **options):
        s = load_curated()
        self.stdout.write(self.style.SUCCESS(
            f"Directorio: {s['created']} nuevas · {s['updated']} actualizadas · {s['skipped']} omitidas"))
