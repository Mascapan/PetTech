# Respaldo de la base de datos PetTech (PostgreSQL + PostGIS en Docker Desktop).
# Uso (PowerShell, desde la carpeta PetTech):   .\scripts\respaldar_bd.ps1
# Genera respaldos\pettech_AAAAMMDD_HHMM.dump (formato custom de pg_dump, comprimido).
$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)
$stamp = Get-Date -Format "yyyyMMdd_HHmm"
New-Item -ItemType Directory -Force -Path "respaldos" | Out-Null
docker compose exec -T db pg_dump -U pettech -d pettech -Fc -f "/tmp/pettech_$stamp.dump"
docker compose cp "db:/tmp/pettech_$stamp.dump" "respaldos/pettech_$stamp.dump"
docker compose exec -T db rm "/tmp/pettech_$stamp.dump"
Write-Host "Respaldo creado: respaldos\pettech_$stamp.dump"
