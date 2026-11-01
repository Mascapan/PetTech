"""
Código PetTech de la mascota (identificador público legible).

Formato: PT-XXXX-XXXX, con 8 caracteres del alfabeto Base32 de Crockford
(sin I, L, O ni U para evitar confusiones al dictarlo). Espacio: 32^8 ≈ 1,1 × 10^12.

- Se genera con `secrets` (generador criptográficamente seguro), no es secuencial
  ni derivable de otros datos; por eso resiste la enumeración.
- La unicidad la garantiza una restricción UNIQUE en la base de datos y un reintento
  ante colisión (ver Pet.save).
- IMPORTANTE: conocer el código NO otorga acceso al historial. Toda consulta exige
  autenticación y una autorización vigente del tutor (Fase 2).
"""
import re
import secrets

ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
PREFIX = "PT"
CODE_LENGTH = 8
CODE_REGEX = re.compile(rf"^{PREFIX}-[{ALPHABET}]{{4}}-[{ALPHABET}]{{4}}$")

# Sustituciones tolerantes al dictado (Crockford): I/L -> 1, O -> 0.
_TRANSLATE = str.maketrans({"I": "1", "L": "1", "O": "0"})


def generate_public_code() -> str:
    raw = "".join(secrets.choice(ALPHABET) for _ in range(CODE_LENGTH))
    return f"{PREFIX}-{raw[:4]}-{raw[4:]}"


def normalize_public_code(value: str) -> str | None:
    """
    Normaliza un código ingresado por una persona (minúsculas, espacios, guiones, I/L/O).
    Devuelve None si no tiene un formato válido. Se usará en la búsqueda profesional (Fase 2).
    """
    if not value:
        return None
    cleaned = re.sub(r"[\s\-]", "", value.upper())
    if cleaned.startswith(PREFIX):
        cleaned = cleaned[len(PREFIX):]
    cleaned = cleaned.translate(_TRANSLATE)
    if len(cleaned) != CODE_LENGTH or any(ch not in ALPHABET for ch in cleaned):
        return None
    return f"{PREFIX}-{cleaned[:4]}-{cleaned[4:]}"
