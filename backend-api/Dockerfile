# PetTech backend — imagen para desarrollo local en Windows (Docker Desktop) y base del despliegue.
# Incluye GDAL/GEOS/PROJ, requeridos por GeoDjango.
FROM python:3.12-slim-bookworm

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

RUN apt-get update \
    && apt-get install -y --no-install-recommends gdal-bin libgdal-dev libgeos-dev libproj-dev binutils \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY requirements.txt .
RUN pip install -r requirements.txt

COPY . .

# Usuario sin privilegios para ejecutar la aplicación.
RUN useradd --create-home --uid 10001 pettech && chown -R pettech /app
USER pettech

EXPOSE 8000
# Producción: gunicorn (docker-compose de desarrollo sobrescribe el comando con runserver).
CMD ["sh", "scripts/start_production.sh"]
