# Publicación de PetTech en Google Play (prueba cerrada)

Estado al 09-11-2026: **app y backend preparados para Render (plan gratuito); falta crear las cuentas de Render y de Play Console**
(ambos requieren cuentas y pagos del equipo). Requisitos verificados en la documentación oficial de Google ese día.

## 0. Requisitos de Google que afectan el calendario

| Requisito | Situación en PetTech |
|---|---|
| Apps nuevas deben apuntar a **Android 16 (API 36)** desde el 31-08-2026 | ✅ `targetSdk = 36` (DEC-027) |
| Cuentas **personales** creadas después del 13-11-2023: **prueba cerrada con ≥ 12 testers durante 14 días seguidos** antes de producción | ⚠️ Empezar cuanto antes: con la entrega el 27-11 casi no queda margen. Para la defensa basta la prueba cerrada |
| Política de privacidad en una URL pública | ✅ `/privacidad/` (borrador; revisar antes del piloto) |
| Apps con creación de cuentas: opción en la app y en la web para pedir la eliminación | ✅ "Mi cuenta → Eliminar mi cuenta" y `/eliminar-cuenta/` |
| Formulario de seguridad de los datos (Data safety) | Completar con la tabla de la sección 5 |
| La app no puede depender de `localhost`/`10.0.2.2` | ⏳ Requiere el backend desplegado con HTTPS (sección 1) |

## 1. Desplegar el backend con HTTPS (necesario antes de generar el AAB)

`render.yaml` (raíz del repositorio) crea en **Render** la base PostgreSQL/PostGIS y el servicio web (API + portal).
Plan actual: **gratuito** (DEC-028). Preparado para el plan gratuito, que **no tiene consola**: el administrador se
crea solo al arrancar y el dominio público se configura automáticamente.

1. Confirmar que el último commit está en GitHub (`git push`).
2. Crear la cuenta en https://render.com con **"Sign up with GitHub"** y autorizar el acceso al repositorio `PetTech`.
3. *New* → **Blueprint** → elegir el repositorio → rama `main`. Render muestra lo que creará (`pettech-db`, `pettech-api`).
4. Completar las variables que pide:
   - `PETTECH_ADMIN_EMAIL`: correo con el que entrarás al portal como administrador.
   - `PETTECH_ADMIN_PASSWORD`: contraseña de **12+ caracteres** (guárdala en un gestor de contraseñas).
   - `PETTECH_CONTACT_EMAIL`: correo de contacto que verán los usuarios en /privacidad/.
5. *Apply*. La primera construcción tarda ~10–15 min (instala GDAL). Ver avance en *pettech-api → Logs*;
   al final deben aparecer `ensure_admin: cuenta de administrador creada.` y `Listening at`.
6. Copiar el dominio asignado (arriba en *pettech-api*, p. ej. `https://pettech-api.onrender.com`; puede tener un sufijo).
7. Verificar en el navegador:
   - `https://<dominio>/api/v1/health/` → `{"status":"ok","database":"ok","postgis":"ok"}`
   - `https://<dominio>/portal/` → ingresar con el administrador del paso 4 y crear las veterinarias reales.
8. **No** se cargan datos `[DEMO]` en este servidor (`seed_demo` está bloqueado en producción).

Limitaciones del plan gratuito (verificadas en la documentación de Render el 09-11-2026):
- El servicio web se suspende tras **15 min** sin tráfico y tarda **~1 min** en volver (la app espera hasta 90 s).
- La base gratuita **expira a los 30 días** (14 días de gracia) y **no tiene respaldos**.
- **Antes de invitar testers**: *pettech-db → Upgrade* (plan pagado) y *pettech-api → Upgrade* (Starter). Se conserva la base.

Verificado en el entorno asistido (09-11-2026): arranque idéntico al de Render (URL `postgres://`, clave de 44
caracteres, `RENDER_EXTERNAL_HOSTNAME`, gunicorn) → 37 migraciones, estáticos, administrador creado, salud OK,
portal/privacidad 200, HTTP → HTTPS 301, dominio ajeno 400, registro de tutor por la API OK.

## 2. Configurar la app para el servidor publicado

En `mobile-android/local.properties` (no versionado):

```properties
pettech.apiBaseUrl.release=https://pettech-api.onrender.com/api/v1/
MAPS_API_KEY=AIza...            # opcional; restringir la clave al paquete cl.pettech.app y a la huella SHA-1 de Play
```

## 3. Firma de publicación (upload key)

Crear el keystore una sola vez (PowerShell, carpeta `mobile-android`; `keytool` viene con Android Studio en
`C:\Program Files\Android\Android Studio\jbr\bin`):

```powershell
mkdir keystore
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore keystore\pettech-upload.jks `
  -alias pettech-upload -keyalg RSA -keysize 2048 -validity 10000
```

Agregar en `local.properties`:

```properties
pettech.upload.storeFile=keystore/pettech-upload.jks
pettech.upload.storePassword=<la que elegiste>
pettech.upload.keyAlias=pettech-upload
pettech.upload.keyPassword=<la que elegiste>
```

**Respaldar el `.jks` y las claves fuera del computador** (gestor de contraseñas). Con *Play App Signing* Google
custodia la clave de firma final; si se pierde la de carga, se puede pedir su reemplazo desde Play Console.

## 4. Generar el paquete

Android Studio → *Build* → *Generate Signed App Bundle / APK* → **Android App Bundle** → release, o:

```powershell
cd mobile-android
.\gradlew.bat bundleRelease      # resultado: app\build\outputs\bundle\release\app-release.aab
```

Antes de subir, instalar el APK *release* en un teléfono y probar: registro, mascota, ficha clínica, búsqueda,
autorización de una veterinaria desde el portal web publicado.

## 5. Play Console

1. Crear la cuenta de desarrollador (pago único; requiere verificación de identidad, puede tardar días).
2. *Crear app* → nombre "PetTech", idioma español (Latinoamérica), gratuita.
3. *Configuración de la app*: política de privacidad (`https://<dominio>/privacidad/`), acceso a la app (entregar
   una cuenta de prueba de tutor a revisión), anuncios: no, clasificación de contenido, público objetivo: adultos,
   **eliminación de cuenta** (`https://<dominio>/eliminar-cuenta/`), Data safety (tabla abajo).
4. *Pruebas → Prueba cerrada* → crear pista, subir el `.aab`, agregar la lista de correos de los **12+ testers**
   (los participantes de la prueba de uso del Informe 1, KPI con ≥ 10 tutores), enviar a revisión.
5. Los testers aceptan la invitación desde el enlace de participación e instalan desde Play. Contar 14 días.

**Data safety (borrador para el formulario):**

| Dato | Se recopila | Se comparte | Finalidad | Obligatorio |
|---|---|---|---|---|
| Nombre y correo | Sí | No | Cuenta | Sí |
| Ubicación aproximada/precisa | Sí, solo en uso | No (no se almacena) | Buscar veterinarias cercanas | No |
| Datos de salud de la mascota (no de la persona) | Sí | Con veterinarias que el tutor autoriza | Ficha clínica | No |
| Fotos | No (la foto queda en el teléfono) | — | — | — |
Cifrado en tránsito: sí (HTTPS). El usuario puede pedir la eliminación: sí.

## 6. Pendiente técnico antes de producción (no bloquea la prueba cerrada)

- Actualizar AGP (hoy 8.7.3 con aviso suprimido para compileSdk 36).
- Proveedor SMTP real para la recuperación de contraseña.
- Ejecutar el procedimiento de eliminación de cuentas según DEC-009.
- Activar R8 (`isMinifyEnabled = true`) en *release* y probarlo.
