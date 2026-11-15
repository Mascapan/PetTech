# Directorio de veterinarias (no afiliadas) — DEC-029

Las veterinarias **no afiliadas** aparecen en la app como información pública referencial, sin el distintivo
**Red PetTech**. No se copia información de Google Maps (sus términos lo prohíben).

## Opción A: importar desde OpenStreetMap (recomendada)
1. Abrir <https://overpass-turbo.eu> y pegar la consulta (Chillán y Chillán Viejo):
   ```
   [out:json][timeout:25];
   nwr["amenity"="veterinary"](-36.70,-72.20,-36.52,-72.00);
   out center tags;
   ```
2. Presionar **Ejecutar** → **Exportar** → **datos sin procesar (raw data) JSON** → guardar el archivo.
3. Portal → **Veterinarias → ＋ Agregar al directorio → Importar desde OpenStreetMap** → elegir el archivo.
   (Local: `python manage.py import_osm_directory archivo.json --dry-run` y luego sin `--dry-run`.)

La importación es idempotente (usa el id de OSM), no duplica nombres existentes, no modifica veterinarias que ya
confirmaron sus datos y guarda la atribución "© colaboradores de OpenStreetMap (ODbL)".
Si una veterinaria de Chillán no está en OSM, se puede agregar en <https://www.openstreetmap.org> (beneficia a todos) o
cargarla manualmente (opción B).

## Opción B: alta manual
Portal → **＋ Agregar al directorio → Agregar manualmente**. Es obligatorio indicar la **fuente** (p. ej. "sitio web
oficial de la clínica, consultado el 15-11-2026"). El formulario rechaza fuentes de Google Maps.

## Afiliar a la Red PetTech
Portal → ficha de la veterinaria → **🤝 Afiliar a la Red PetTech**: crea la cuenta del establecimiento. Desde ese momento
la app la muestra con el distintivo y su responsable puede confirmar sus datos y autorizar profesionales.
