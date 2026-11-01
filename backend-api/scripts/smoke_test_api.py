"""
Prueba de humo de la API PetTech contra un servidor EN EJECUCIÓN (incremento 1).

Simula el recorrido que hace la aplicación Android, usando HTTP real:
salud → registro → inicio de sesión → mascotas → aislamiento → cierre de sesión.

Uso (con el backend corriendo en otra terminal):
    python scripts/smoke_test_api.py                       # http://127.0.0.1:8000
    python scripts/smoke_test_api.py --base http://127.0.0.1:8000 --report ../docs/evidencias/fase1/smoke.md

Solo usa la biblioteca estándar. Crea cuentas ficticias con dominio .test.
"""
import argparse
import json
import secrets
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime

RESULTS: list[tuple[str, bool, str]] = []


def call(base: str, method: str, path: str, body: dict | None = None, token: str | None = None):
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(f"{base}/api/v1{path}", data=data, method=method)
    request.add_header("Content-Type", "application/json")
    request.add_header("Accept", "application/json")
    if token:
        request.add_header("Authorization", f"Token {token}")
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            status, raw = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, raw = error.code, error.read()
    elapsed_ms = (time.perf_counter() - started) * 1000
    payload = json.loads(raw) if raw else None
    return status, payload, elapsed_ms


def check(name: str, condition: bool, detail: str = "") -> None:
    RESULTS.append((name, bool(condition), detail))
    print(f"[{'OK ' if condition else 'FALLA'}] {name} {detail}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8000")
    parser.add_argument("--report", default=None, help="Ruta de un informe Markdown de evidencia")
    args = parser.parse_args()
    base = args.base.rstrip("/")
    suffix = secrets.token_hex(3)
    password = "Clave-Smoke-" + secrets.token_hex(4)

    status, body, ms = call(base, "GET", "/health/")
    check("Salud del servicio y PostGIS", status == 200 and body.get("postgis") == "ok", f"({status}, {ms:.0f} ms)")

    alice = {"full_name": "Tutora Smoke [SIMULADO]", "email": f"smoke.a.{suffix}@pettech.test",
             "password": password, "accept_privacy_policy": True}
    status, body, ms = call(base, "POST", "/auth/register/", alice)
    check("Registro de tutor", status == 201 and body["user"]["role"] == "TUTOR", f"({status}, {ms:.0f} ms)")

    status, body, ms = call(base, "POST", "/auth/register/", {**alice, "accept_privacy_policy": False,
                                                             "email": f"x.{suffix}@pettech.test"})
    check("Registro sin aceptar política es rechazado", status == 400, f"({status})")

    status, body, ms = call(base, "POST", "/auth/login/", {"email": alice["email"], "password": "incorrecta-000"})
    check("Inicio de sesión con clave incorrecta es rechazado", status == 400, f"({status})")

    status, body, ms = call(base, "POST", "/auth/login/", {"email": alice["email"].upper(), "password": password})
    token_a = body.get("token") if body else None
    check("Inicio de sesión correcto (correo sin distinguir mayúsculas)", status == 200 and token_a, f"({status}, {ms:.0f} ms)")

    status, body, ms = call(base, "GET", "/pets/")
    check("Listado de mascotas sin token es rechazado (401)", status == 401, f"({status})")

    pet = {"name": "Luna Smoke [SIMULADO]", "species": "DOG", "sex": "FEMALE", "breed": "Mestiza",
           "birth_date": "2021-03-15", "birth_date_is_estimated": True, "microchip_number": "", "general_notes": ""}
    status, body, ms = call(base, "POST", "/pets/", pet, token_a)
    pet_id = body.get("id") if body else None
    code = body.get("public_code") if body else None
    check("Registro de mascota con código PetTech", status == 201 and code and code.startswith("PT-"),
          f"({status}, {ms:.0f} ms, código {code})")

    status, body, ms = call(base, "GET", f"/pets/{pet_id}/", token=token_a)
    check("Consulta del carnet de la mascota propia", status == 200 and body["public_code"] == code, f"({status}, {ms:.0f} ms)")

    status, body, ms = call(base, "POST", "/pets/", {**pet, "birth_date": "2999-01-01"}, token_a)
    check("Fecha de nacimiento futura es rechazada", status == 400 and "birth_date" in body["error"].get("fields", {}),
          f"({status})")

    bob = {**alice, "email": f"smoke.b.{suffix}@pettech.test", "full_name": "Tutor B Smoke [SIMULADO]"}
    status, body, _ = call(base, "POST", "/auth/register/", bob)
    token_b = body.get("token") if body else None
    status, body, _ = call(base, "GET", f"/pets/{pet_id}/", token=token_b)
    check("Otro tutor NO puede ver la mascota (404)", status == 404, f"({status})")
    status, body, _ = call(base, "PATCH", f"/pets/{pet_id}/", {"name": "Modificado"}, token_b)
    check("Otro tutor NO puede editar la mascota (404)", status == 404, f"({status})")
    status, body, _ = call(base, "GET", "/pets/", token=token_b)
    check("Listado del otro tutor no incluye mascotas ajenas", status == 200 and body["count"] == 0, f"({status})")

    status, _, _ = call(base, "POST", "/auth/logout/", token=token_a)
    check("Cierre de sesión", status == 204, f"({status})")
    status, _, _ = call(base, "GET", "/pets/", token=token_a)
    check("Token revocado ya no sirve (401)", status == 401, f"({status})")

    passed = sum(1 for _, ok, _ in RESULTS if ok)
    print(f"\nResultado: {passed}/{len(RESULTS)} verificaciones correctas")

    if args.report:
        lines = [
            "# Evidencia — Prueba de humo de la API (incremento 1)",
            "",
            f"- Fecha de ejecución: {datetime.now().astimezone().isoformat(timespec='seconds')}",
            f"- Servidor: `{base}` (desarrollo local, datos SIMULADOS)",
            "- Script: `backend-api/scripts/smoke_test_api.py`",
            "",
            "| # | Verificación | Resultado | Detalle |",
            "|---|---|---|---|",
        ]
        for i, (name, ok, detail) in enumerate(RESULTS, 1):
            lines.append(f"| {i} | {name} | {'OK' if ok else 'FALLA'} | {detail.strip()} |")
        lines += ["", f"**Total:** {passed}/{len(RESULTS)} verificaciones correctas.", "",
                  "> Los tiempos son de un entorno de desarrollo local y NO representan el desempeño del piloto (RNF-04)."]
        with open(args.report, "w", encoding="utf-8") as handle:
            handle.write("\n".join(lines) + "\n")
    return 0 if passed == len(RESULTS) else 1


if __name__ == "__main__":
    sys.exit(main())
