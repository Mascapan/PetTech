# Restaura un respaldo creado con respaldar_bd.ps1. ATENCIÓN: reemplaza los datos actuales.
# Uso:   .\scripts\restaurar_bd.ps1 respaldos\pettech_20261108_1500.dump
param([Parameter(Mandatory = $true)][string]$Archivo)
$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)
$ok = Read-Host "Se reemplazarán los datos actuales de PetTech. Escribe SI para continuar"
if ($ok -ne "SI") { Write-Host "Cancelado."; exit 1 }
docker compose cp $Archivo "db:/tmp/restaurar.dump"
docker compose exec -T db pg_restore -U pettech -d pettech --clean --if-exists --no-owner "/tmp/restaurar.dump"
docker compose exec -T db rm "/tmp/restaurar.dump"
Write-Host "Restauración terminada. Reinicia el contenedor api."
