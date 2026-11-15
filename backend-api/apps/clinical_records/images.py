"""
Normalización de imágenes subidas (DEC-031, OWASP File Upload):
- se acepta solo JPEG, PNG, WEBP o HEIF decodificables (no se confía en la extensión ni en el Content-Type);
- se corrige la orientación, se reduce a un máximo de 1600 px y se re-codifica a JPEG (calidad 82);
- al re-codificar se descartan los metadatos EXIF (fecha, modelo del teléfono, coordenadas GPS).
"""
import hashlib
import io
from dataclasses import dataclass

from PIL import Image, ImageOps, UnidentifiedImageError

MAX_UPLOAD_BYTES = 8 * 1024 * 1024
MAX_SIDE = 1600
MAX_PIXELS = 40_000_000  # protección contra "bombas de descompresión"
ALLOWED_FORMATS = {"JPEG", "PNG", "WEBP", "MPO"}


class InvalidImage(ValueError):
    pass


@dataclass
class NormalizedImage:
    content: bytes
    width: int
    height: int
    sha256: str


def normalize(upload) -> NormalizedImage:
    if upload.size > MAX_UPLOAD_BYTES:
        raise InvalidImage("La imagen supera 8 MB.")
    raw = upload.read()
    Image.MAX_IMAGE_PIXELS = MAX_PIXELS
    try:
        with Image.open(io.BytesIO(raw)) as probe:
            fmt = probe.format
            probe.verify()
        if fmt not in ALLOWED_FORMATS:
            raise InvalidImage("Formato no permitido. Usa una foto JPEG, PNG o WEBP.")
        with Image.open(io.BytesIO(raw)) as img:
            img = ImageOps.exif_transpose(img)
            img = img.convert("RGB")
            img.thumbnail((MAX_SIDE, MAX_SIDE))
            out = io.BytesIO()
            img.save(out, format="JPEG", quality=82, optimize=True)  # sin exif=… → no se copian metadatos
            content = out.getvalue()
            width, height = img.size
    except (UnidentifiedImageError, OSError, Image.DecompressionBombError) as exc:
        raise InvalidImage("El archivo no es una imagen válida.") from exc
    return NormalizedImage(content, width, height, hashlib.sha256(content).hexdigest())
