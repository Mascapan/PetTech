# API REST PetTech — v1 (incremento 1)

- Base: `http://<host>:8000/api/v1/` (desarrollo) · HTTPS obligatorio en el piloto.
- Formato: JSON (`Content-Type: application/json`). Fechas ISO-8601 en `America/Santiago`.
- Autenticación: encabezado `Authorization: Token <token>` (token entregado al registrarse o iniciar sesión).
- Especificación OpenAPI completa: [`openapi.yaml`](openapi.yaml) · interfaz Swagger en `/api/v1/docs/` (solo desarrollo).

## Endpoints

| Método | Ruta | Auth | Rol | Descripción | Respuestas |
|---|---|---|---|---|---|
| GET | `/health/` | No | — | Estado de BD y PostGIS | 200, 503 |
| POST | `/auth/register/` | No | — | Crea cuenta **TUTOR** y devuelve token | 201, 400, 429 |
| POST | `/auth/login/` | No | — | Inicia sesión | 200, 400, 429 |
| POST | `/auth/logout/` | Sí | cualquiera | Revoca el token actual | 204, 401 |
| POST | `/auth/logout-all/` | Sí | cualquiera | Revoca todos los tokens del usuario | 204, 401 |
| GET | `/auth/me/` | Sí | cualquiera | Datos de la cuenta | 200, 401 |
| GET | `/pets/` | Sí | TUTOR | Lista paginada de **mis** mascotas (`?page=`, `?page_size=` ≤ 100) | 200, 401, 403 |
| POST | `/pets/` | Sí | TUTOR | Registra una mascota | 201, 400, 401, 403 |
| GET | `/pets/{id}/` | Sí | TUTOR | Carnet de una mascota propia | 200, 401, 403, 404 |
| PATCH | `/pets/{id}/` | Sí | TUTOR | Edita campos | 200, 400, 401, 403, 404 |
| PUT | `/pets/{id}/` | Sí | TUTOR | Reemplaza datos | 200, 400, 401, 403, 404 |
| DELETE | `/pets/{id}/` | Sí | TUTOR | Elimina una mascota propia | 204, 401, 403, 404 |

`404` también se devuelve para mascotas **ajenas** (no se revela su existencia). `403` cuando el rol no corresponde.

## Ejemplos

### Registro
```http
POST /api/v1/auth/register/
Content-Type: application/json

{"full_name": "Camila Demo", "email": "camila@pettech.test", "password": "Clave-Segura-2026", "accept_privacy_policy": true}
```
```json
201 Created
{
  "token": "pt_3f1c…(64 caracteres)",
  "expiry": "2026-11-08T12:55:03.955457-03:00",
  "user": {"id": "45bc2b60-…", "email": "camila@pettech.test", "full_name": "Camila Demo",
           "role": "TUTOR", "role_label": "Tutor (propietario de mascotas)", "date_joined": "2026-11-01T12:55:03-03:00"}
}
```

### Inicio de sesión fallido (mismo mensaje para correo inexistente o clave incorrecta)
```json
400 Bad Request
{"error": {"code": "validation_error", "message": "Correo o contraseña incorrectos.",
           "fields": {"non_field_errors": ["Correo o contraseña incorrectos."]}}}
```

### Registrar mascota
```http
POST /api/v1/pets/
Authorization: Token pt_3f1c…

{"name": "Luna", "species": "DOG", "sex": "FEMALE", "breed": "Mestiza", "birth_date": "2021-03-15",
 "birth_date_is_estimated": true, "microchip_number": "", "general_notes": "Sin alergias conocidas."}
```
```json
201 Created
{"id": "a0dee316-8ae2-4328-9b07-7275f21fcf44", "public_code": "PT-K791-9PCS", "name": "Luna",
 "species": "DOG", "species_label": "Perro", "breed": "Mestiza", "sex": "FEMALE", "sex_label": "Hembra",
 "birth_date": "2021-03-15", "birth_date_is_estimated": true, "microchip_number": "",
 "general_notes": "Sin alergias conocidas.", "data_origin": "OWNER_DECLARED",
 "created_at": "2026-11-01T12:40:34.771-03:00", "updated_at": "2026-11-01T12:40:34.771-03:00"}
```
`owner`, `id` y `public_code` no se aceptan desde el cliente.

### Validación
```json
400 Bad Request
{"error": {"code": "validation_error", "message": "Revisa los datos ingresados.",
           "fields": {"birth_date": ["La fecha de nacimiento no puede ser posterior a hoy."]}}}
```

### Otros errores
| Código HTTP | `error.code` | Caso |
|---|---|---|
| 401 | `not_authenticated` | sin token |
| 401 | `authentication_failed` | token inválido, expirado, revocado o cuenta suspendida |
| 403 | `permission_denied` | rol sin acceso (p. ej., profesional en `/pets/`) |
| 404 | `not_found` | recurso inexistente o ajeno |
| 429 | `throttled` | más de 10 intentos por minuto en `/auth/` |
| 500 | `server_error` | error no controlado (sin detalles internos) |

## Endpoints planificados (referenciales)

| Fase | Ruta |
|---|---|
| 2 | `/clinics/`, `/professional/pets/lookup/?code=`, `/access-requests/`, `/access-requests/{id}/approve/`, `/access-requests/{id}/reject/`, `/authorizations/`, `/authorizations/{id}/revoke/` |
| 3 | `/pets/{id}/clinical-history/`, `/clinical-records/`, `/clinical-records/{id}/corrections/`, `/vaccinations/` |
| 4 | `/clinics/nearby/?lat=&lng=&radius=&emergency=&open_now=` |
