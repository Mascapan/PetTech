# Requisitos del prototipo PetTech

**Fuentes y jerarquía**

1. Retroalimentación más reciente del profesor: **escrita, del 28-10-2026** (`1er informe/retroalimentacion.docx`,
   46,6/60). Recomienda ficha clínica estandarizada, HU-14 como *Must* (pase clínico QR / enlace firmado), primera
   contención asistida y cápsulas preventivas (ver DEC-024). Además, el equipo informó el 1-11-2026 el foco en el
   historial compartido entre veterinarias autorizadas por el tutor.
2. *Proyecto de Título – Informe 1 v3* (Tabla 8 historias de usuario, Tabla 9 RNF, Tabla 15 alcance) — línea base.
3. Decisiones del equipo registradas en [`DECISIONES.md`](DECISIONES.md).

Convención de estado: ✅ implementado y verificado · 🟡 implementado parcialmente · ⏳ pendiente · ⛔ bloqueado.

---

## 1. Historias de usuario del Informe 1 (línea base)

| ID | Historia (resumen) | Prioridad I1 | Estado | Incremento |
|---|---|---|---|---|
| HU-01 | Tutor crea cuenta e inicia sesión con correo y contraseña; rechaza correos duplicados; **recupera acceso por correo**; acepta política de privacidad | Must | ✅ backend (recuperación con código por correo) · 🟡 app | 1–2 |
| HU-02 | Tutor registra y edita perfil de una o más mascotas (nombre, especie, fecha aprox.; microchip opcional declarado); asociada solo a su tutor; editar y eliminar | Must | ✅ | 1 |
| HU-03 | Tutor registra vacunas, desparasitaciones y controles (tipo, fecha, observación, próxima fecha; sin fechas futuras para eventos realizados) | Must | ✅ backend · 🟡 app — antecedente **declarado por el tutor**, distinto del registro profesional (C-04) | 2 |
| HU-04 | Tutor consulta historial cronológico, filtra por tipo, ve próxima fecha | Must | ✅ backend · 🟡 app — incluye registros de veterinarias y resumen clínico | 2 |
| HU-05 | Adjuntar fotografías/documentos | Should | ⏳ (deseable) | — |
| HU-06 | Recordatorio local antes de próxima fecha (WorkManager) | Should | ⏳ | 4 |
| HU-07 | Buscar establecimientos cercanos (permiso de ubicación al buscar; orden por distancia; elegir punto si se rechaza) | Must | ✅ backend · 🟡 app | 2 |
| HU-08 | Filtrar por urgencias, servicio y "abierto según horario declarado" | Must | ✅ backend · 🟡 app | 2 |
| HU-09 | Ficha del establecimiento con origen y fecha de verificación; sin cupos ni promesa de atención | Must | ✅ backend · 🟡 app | 2 |
| HU-10 | Mapa y apertura de ruta en app de navegación | Must | 🟡 app (mapa requiere `MAPS_API_KEY`; ruta por intent sin clave) | 2 |
| HU-11 | Administrador registra/actualiza establecimientos con bitácora | Must | 🟡 Django Admin + LogEntry (revisión manual pendiente) | 2 |
| HU-12 | Administrador gestiona estado de verificación y umbral de antigüedad | Must | 🟡 estados en Admin; umbral por variable `CLINIC_STALE_DAYS` | 2 |
| HU-13 | Titular elimina su cuenta y sus datos | Must | ⏳ — **en conflicto** con la conservación de registros clínicos (C-03) | Por decidir |
| HU-14 | Exportar historial de una mascota | Could → **Must según retroalimentación** | ⏳ — el profesor pide un pase clínico QR / enlace firmado (DEC-024) | 3 |

## 2. Requerimientos no funcionales del Informe 1

| ID | Requerimiento (resumen) | Estado en incremento 1 |
|---|---|---|
| RNF-01 | HTTPS en toda comunicación; contraseñas con función de derivación de clave | 🟡 Argon2id ✅; HTTPS forzado en `production.py` y en Android *release* (sin desplegar aún) |
| RNF-02 | Cada tutor accede solo a sus mascotas (autorización a nivel de objeto) | ✅ pruebas de acceso cruzado |
| RNF-03 | Ubicación solo durante la búsqueda, no almacenada; minimización de datos | ⏳ (Fase 4); minimización aplicada en el modelo de cuentas |
| RNF-04 | Búsqueda e historial < 2 s en el 95 % (entorno del piloto) | ⏳ medible solo en el piloto |
| RNF-05 | Tareas principales sin asistencia; errores que indican cómo corregir | 🟡 mensajes por campo implementados; se mide en prueba de uso |
| RNF-06 | Android 9 o superior | 🟡 `minSdk = 28`; falta prueba en emulador y dos dispositivos |
| RNF-07 | API documentada con OpenAPI; módulos por dominio | ✅ `docs/api/openapi.yaml` validado |
| RNF-08 | Planes sin suspensión y respaldo diario en el piloto | ⏳ (despliegue) |

---

## 3. Requisitos derivados de la retroalimentación del profesor (PROPUESTOS)

> IDs propuestos por el equipo técnico para incorporar en el Informe 2. **No figuran en el Informe 1.**
> El núcleo del prototipo pasa a ser el historial clínico compartido. El tutor sigue siendo el titular que autoriza.

| ID | Historia | Prioridad propuesta | Fase |
|---|---|---|---|
| HU-15 | Como **administrador**, quiero registrar establecimientos y habilitar profesionales verificados asociados a ellos, para que solo profesionales autorizados operen en PetTech. | Must | 2 |
| HU-16 | Como **profesional**, quiero buscar una mascota por su código PetTech y solicitar acceso a su historial en nombre de mi establecimiento. | Must | 2 |
| HU-17 | Como **tutor**, quiero aprobar o rechazar las solicitudes de acceso, viendo qué establecimiento y profesional las realiza. | Must | 2 |
| HU-18 | Como **tutor**, quiero ver las autorizaciones vigentes y revocarlas en cualquier momento; expiran automáticamente (24 h configurables). | Must | 2 |
| HU-19 | Como **profesional autorizado**, quiero consultar antecedentes, vacunas, alergias, controles y atenciones previas, incluidas las de otros establecimientos. | Must | 3 |
| HU-20 | Como **profesional autorizado**, quiero registrar una atención (motivo, observaciones, procedimientos, tratamiento, próximo control). | Must | 3 |
| HU-21 | Como **profesional autorizado**, quiero registrar vacunaciones con su próxima dosis. | Must | 3 |
| HU-22 | Como **profesional**, quiero corregir un registro de mi establecimiento mediante una corrección que conserve la versión anterior, el autor y el motivo. | Must | 3 |
| HU-23 | Como **tutor**, quiero ver las actualizaciones registradas por veterinarias (quién, dónde, cuándo). | Must | 3 |
| HU-24 | Como **tutor**, quiero ver qué establecimientos consultaron el historial (bitácora visible). | Should | 3 |

| ID | Requerimiento no funcional propuesto |
|---|---|
| RNF-09 | **Auditoría**: cada consulta y modificación del historial genera un registro inmutable (actor, establecimiento, acción, recurso, fecha, resultado). |
| RNF-10 | **Autorización temporal por establecimiento**: verificada en el servidor en cada operación de lectura/escritura; revocación con efecto inmediato. |
| RNF-11 | **Integridad clínica**: los registros profesionales no se modifican ni eliminan; las correcciones son versiones nuevas enlazadas. |

---

## 4. Reglas de negocio

| ID | Regla | Origen | Implementación |
|---|---|---|---|
| RN-01 | Un identificador de mascota no equivale a una autorización. | Profesor | Código PetTech no es credencial; pruebas `test_conocer_el_codigo_publico_no_da_acceso` (F1) y de acceso en F2 |
| RN-02 | Ninguna veterinaria accede sin autenticación y permisos válidos. | Profesor | F2 |
| RN-03 | El tutor aprueba, rechaza y revoca autorizaciones. | Profesor | F2 |
| RN-04 | Solo profesionales verificados y vinculados a un establecimiento habilitado registran atenciones. | Profesor | F2–F3 |
| RN-05 | Cada atención conserva autoría (profesional), establecimiento y fecha. | Profesor | F3 |
| RN-06 | No se alteran silenciosamente registros clínicos anteriores. | Profesor | F3 (RNF-11) |
| RN-07 | El tutor puede consultar las actualizaciones hechas por veterinarias. | Profesor | F3 |
| RN-08 | El historial admite antecedentes de distintos establecimientos. | Profesor | F3 |
| RN-09 | Los datos ingresados por el tutor se rotulan como "declarados" y no tienen valor de certificación. | Equipo | `data_origin = OWNER_DECLARED` (F1) |
| RN-10 | Solo los tutores se autorregistran; las cuentas profesionales las crea y verifica el administrador. | Equipo | F1 (`RegisterSerializer` fuerza TUTOR) |
| RN-11 | El administrador no edita información clínica por el solo hecho de serlo. | Profesor | F1: `PetAdmin` solo lectura; API de mascotas niega ADMIN |
| RN-12 | La autorización dura 24 h por defecto (configurable). | Profesor | F2 |
| RN-13 | Horarios declarados ≠ disponibilidad real ni cupos. | Profesor / Informe 1 | F4 |
| RN-14 | Un profesional no puede editar registros de otra clínica. | Profesor | F3 |

## 5. Criterios de aceptación — incremento 1

| Criterio | Prueba automatizada |
|---|---|
| Registro de tutor exige aceptar la política y devuelve token | `test_registro_crea_tutor_y_entrega_token`, `test_registro_exige_aceptar_politica_de_privacidad` |
| Correo duplicado (sin distinguir mayúsculas) es rechazado | `test_registro_rechaza_correo_duplicado_sin_distinguir_mayusculas` |
| Contraseñas débiles rechazadas; almacenadas con Argon2 | `test_registro_rechaza_contrasena_debil`, `test_contrasena_almacenada_con_argon2` |
| El cliente no puede autoasignarse rol profesional/admin | `test_cliente_no_puede_elegir_rol_profesional_ni_admin` |
| Mensaje de error de inicio de sesión no permite enumerar cuentas | `test_mensaje_generico_para_clave_incorrecta_y_correo_inexistente` |
| Bloqueo temporal tras 10 intentos/minuto | `test_limite_de_intentos_de_inicio_de_sesion` |
| Cierre de sesión revoca el token; tokens expirados o inventados rechazados | `test_cierre_de_sesion_revoca_el_token`, `test_token_expirado_es_rechazado`, `test_token_inventado_es_rechazado` |
| Mascota creada queda asociada al tutor del token y recibe código PetTech único | `test_registrar_mascota_genera_codigo_publico`, `test_propietario_se_toma_del_token_no_del_cuerpo`, `test_codigos_unicos_en_muchas_mascotas` |
| Tutor no ve, edita ni elimina mascotas ajenas (404) | `PetIsolationTests` |
| Profesional y administrador no usan endpoints de tutor (403) | `test_profesional_no_usa_endpoints_de_tutor`, `test_administrador_no_tiene_acceso_a_mascotas_por_api` |
| Validación de nombre, especie, fecha y microchip | `test_campos_obligatorios`, `test_fecha_de_nacimiento_futura_es_invalida`, `test_microchip_invalido`, … |

---

## 6. Contradicciones y vacíos detectados (requieren decisión)

| ID | Hallazgo | Impacto | Propuesta |
|---|---|---|---|
| C-01 | El **problema y el objetivo general** del Informe 1 se centran solo en el tutor (antecedentes + búsqueda); la retroalimentación pone a las veterinarias como clientes y al historial compartido como núcleo. | Trazabilidad problema → objetivo → requisito se rompe si no se reformula. | Reformular en el Informe 2 el problema (agregar causa: antecedentes no portables entre establecimientos) y el objetivo general; agregar OE para el flujo B2B. |
| C-02 | Tabla 15 del Informe 1 deja **fuera de alcance** "actualización directa por cada establecimiento" e "integración con sistemas clínicos". | Las cuentas profesionales que registran atenciones contradicen la exclusión literal. | Precisar en el Informe 2: se incorporan cuentas profesionales **dentro** de PetTech; sigue fuera la integración automática con sistemas clínicos externos y la autogestión del catálogo por clínicas. |
| C-03 | **HU-13** (eliminar cuenta y todos los datos) vs. RN-06 (registros clínicos inalterables) y RN-08. | Eliminar en cascada borraría registros de terceros (clínicas). | Decisión pendiente (DEC-009). Opciones en `DECISIONES.md`. La FK `owner` usa `PROTECT` para impedir borrados silenciosos mientras tanto. |
| C-04 | **HU-03** asigna al tutor el registro de vacunas; ahora también las registra el profesional. | Dos orígenes del mismo tipo de dato. | Mantener ambos con `origin` (DECLARADO_TUTOR / REGISTRO_PROFESIONAL) y mostrar el origen en la interfaz. |
| C-05 | El cronograma del Informe 1 situaba cuentas y catálogo en la Iteración 1 (12–25 oct). Al 1-11-2026 la carpeta `Prototipo` estaba **vacía**. | Atraso aproximado de una iteración; quedan ≈ 4 semanas. | Si existe código en otra ubicación, incorporarlo. Ajuste de alcance propuesto en `ESTADO_DESARROLLO.md`. |
| C-06 | La evidencia del Informe 1 proviene de **37 tutores**; no hubo entrevistas a veterinarias. | El núcleo B2B no tiene evidencia propia: probable pregunta de la comisión. | Validar con los ≥ 2 establecimientos colaboradores (entrevista breve estructurada, registrada con fecha) antes de la defensa. No inventar resultados. |
| C-07 | Los **KPI-01..06** no miden el flujo entre veterinarias. | El núcleo no tendría indicador. | Proponer KPI de seguridad (100 % de accesos no autorizados rechazados en pruebas) y de eficacia (tarea "segunda clínica consulta historial autorizado" completada sin asistencia). |
| C-08 | **HU-14** (exportar para compartir con un profesional) queda parcialmente superada por el acceso autorizado. | Esfuerzo duplicado. | Mantener como Could o retirar en el Informe 2. |
| C-09 | **HU-01** exige recuperación de acceso por correo, que requiere un servicio de correo no presupuestado en la sección 6.4. | Must incompleto. | Implementar con el flujo estándar de Django (enlace firmado con expiración); en desarrollo, backend de correo de consola; definir proveedor para el piloto. |
