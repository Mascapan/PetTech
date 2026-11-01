# Evidencia — Pruebas automatizadas del backend (incremento 1)

- Fecha de ejecución: 2026-11-01T13:00:43-03:00
- Comando: `python manage.py test --parallel 1 -v 2` (desde `backend-api/`)
- Base de datos: PostgreSQL 16.15 + PostGIS 3.4.2 (base temporal `test_pettech`)
- Entorno: Python 3.13, Django 5.2, DRF 3.16 — entorno Linux de la sesión de desarrollo asistido
- Resultado: **51 pruebas ejecutadas, OK** en 8.449 s

| # | Módulo | Grupo | Prueba | Resultado |
|---|---|---|---|---|
| 1 | accounts | LoginTests | `test_cuenta_suspendida_no_puede_iniciar_sesion` | OK |
| 2 | accounts | LoginTests | `test_duracion_de_sesion_menor_para_profesionales` | OK |
| 3 | accounts | LoginTests | `test_inicio_de_sesion_correcto` | OK |
| 4 | accounts | LoginTests | `test_limite_de_intentos_de_inicio_de_sesion` | OK |
| 5 | accounts | LoginTests | `test_limite_de_sesiones_simultaneas` | OK |
| 6 | accounts | LoginTests | `test_mensaje_generico_para_clave_incorrecta_y_correo_inexistente` | OK |
| 7 | accounts | LoginTests | `test_token_se_guarda_como_hash` | OK |
| 8 | accounts | LoginTests | `test_token_suspendido_deja_de_funcionar` | OK |
| 9 | accounts | RegisterTests | `test_cliente_no_puede_elegir_rol_profesional_ni_admin` | OK |
| 10 | accounts | RegisterTests | `test_contrasena_almacenada_con_argon2` | OK |
| 11 | accounts | RegisterTests | `test_correo_se_normaliza_a_minusculas` | OK |
| 12 | accounts | RegisterTests | `test_registro_crea_tutor_y_entrega_token` | OK |
| 13 | accounts | RegisterTests | `test_registro_exige_aceptar_politica_de_privacidad` | OK |
| 14 | accounts | RegisterTests | `test_registro_rechaza_contrasena_debil` | OK |
| 15 | accounts | RegisterTests | `test_registro_rechaza_correo_duplicado_sin_distinguir_mayusculas` | OK |
| 16 | accounts | SessionTests | `test_cierre_de_sesion_revoca_el_token` | OK |
| 17 | accounts | SessionTests | `test_cierre_en_todos_los_dispositivos` | OK |
| 18 | accounts | SessionTests | `test_me_devuelve_datos_propios` | OK |
| 19 | accounts | SessionTests | `test_me_requiere_autenticacion` | OK |
| 20 | accounts | SessionTests | `test_token_expirado_es_rechazado` | OK |
| 21 | accounts | SessionTests | `test_token_inventado_es_rechazado` | OK |
| 22 | core | DatabaseTests | `test_extension_postgis_disponible` | OK |
| 23 | core | DatabaseTests | `test_motor_es_postgresql` | OK |
| 24 | core | DatabaseTests | `test_postgis_resuelve_consulta_espacial` | OK |
| 25 | core | HealthAndErrorFormatTests | `test_error_401_tiene_formato_uniforme` | OK |
| 26 | core | HealthAndErrorFormatTests | `test_error_validacion_incluye_campos` | OK |
| 27 | core | HealthAndErrorFormatTests | `test_health_publico_y_sin_detalles_internos` | OK |
| 28 | core | HealthAndErrorFormatTests | `test_json_malformado_responde_400_sin_traza` | OK |
| 29 | pets | PetCrudTests | `test_anonimo_no_puede_listar_ni_crear` | OK |
| 30 | pets | PetCrudTests | `test_campos_obligatorios` | OK |
| 31 | pets | PetCrudTests | `test_editar_mascota_propia_sin_cambiar_codigo` | OK |
| 32 | pets | PetCrudTests | `test_eliminar_mascota_propia` | OK |
| 33 | pets | PetCrudTests | `test_especie_fuera_del_alcance_es_invalida` | OK |
| 34 | pets | PetCrudTests | `test_fecha_de_nacimiento_futura_es_invalida` | OK |
| 35 | pets | PetCrudTests | `test_fecha_de_nacimiento_opcional` | OK |
| 36 | pets | PetCrudTests | `test_listado_paginado` | OK |
| 37 | pets | PetCrudTests | `test_microchip_invalido` | OK |
| 38 | pets | PetCrudTests | `test_microchip_valido_de_15_digitos` | OK |
| 39 | pets | PetCrudTests | `test_nombre_solo_espacios_es_invalido` | OK |
| 40 | pets | PetCrudTests | `test_propietario_se_toma_del_token_no_del_cuerpo` | OK |
| 41 | pets | PetCrudTests | `test_registrar_mascota_genera_codigo_publico` | OK |
| 42 | pets | PetIsolationTests | `test_administrador_no_tiene_acceso_a_mascotas_por_api` | OK |
| 43 | pets | PetIsolationTests | `test_conocer_el_codigo_publico_no_da_acceso` | OK |
| 44 | pets | PetIsolationTests | `test_listado_solo_incluye_mascotas_propias` | OK |
| 45 | pets | PetIsolationTests | `test_no_puede_editar_mascota_ajena` | OK |
| 46 | pets | PetIsolationTests | `test_no_puede_eliminar_mascota_ajena` | OK |
| 47 | pets | PetIsolationTests | `test_no_puede_ver_mascota_ajena` | OK |
| 48 | pets | PetIsolationTests | `test_profesional_no_usa_endpoints_de_tutor` | OK |
| 49 | pets | PublicCodeTests | `test_codigos_unicos_en_muchas_mascotas` | OK |
| 50 | pets | PublicCodeTests | `test_normalizacion_de_codigo_dictado` | OK |
| 51 | pets | PublicCodeTests | `test_reintenta_si_el_codigo_colisiona` | OK (registra la advertencia esperada de colisión) |

> Pruebas ejecutadas realmente. Deben repetirse en el equipo de cada integrante (Windows) y registrar su resultado aquí.
