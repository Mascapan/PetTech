#!/bin/sh
# Arranque en el servidor del piloto: migraciones, estáticos y gunicorn.
set -e
python manage.py migrate --noinput
python manage.py collectstatic --noinput
# Administrador inicial desde variables de entorno (sin consola en el plan gratuito). Idempotente.
python manage.py ensure_admin
exec gunicorn config.wsgi:application --bind 0.0.0.0:${PORT:-8000} --workers ${WEB_CONCURRENCY:-2} --timeout 60 --access-logfile -
