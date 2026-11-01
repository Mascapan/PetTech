"""Modelos abstractos reutilizables (sin tablas propias)."""
from django.db import models


class TimeStampedModel(models.Model):
    """Agrega fechas de creación y última modificación (almacenadas en UTC)."""

    created_at = models.DateTimeField("creado el", auto_now_add=True, editable=False)
    updated_at = models.DateTimeField("actualizado el", auto_now=True)

    class Meta:
        abstract = True
