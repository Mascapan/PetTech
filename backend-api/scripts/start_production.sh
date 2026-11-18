#!/bin/sh
# Arranque en el servidor del piloto: migraciones, estáticos y gunicorn.
set -e
python manage.py migrate --noinput
python manage.py collectstatic --noinput
# Administrador inicial desde variables de entorno (sin consola en el plan gratuito). Idempotente.
python manage.py ensure_admin
# Directorio de veterinarias no afiliadas de Chillán (fuentes públicas, DEC-029). Idempotente.
python manage.py load_directory
# Datos SIMULADOS para pruebas manuales (cuentas, 2 veterinarias de la red, tutores y mascotas).
# Solo si se define PETTECH_SIM_DATA en el panel: "1" los crea (idempotente), "remove" los elimina.
case "${PETTECH_SIM_DATA:-}" in
  1) python manage.py seed_simulacion ;;
  remove) python manage.py seed_simulacion --remove ;;
esac
exec gunicorn config.wsgi:application --bind 0.0.0.0:${PORT:-8000} --workers ${WEB_CONCURRENCY:-2} --timeout 60 --access-logfile -
