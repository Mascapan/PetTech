from django.utils import timezone
from rest_framework import serializers

from . import services
from .models import Clinic, OpeningHour, Service


class OpeningHourSerializer(serializers.ModelSerializer):
    weekday_label = serializers.CharField(source="get_weekday_display", read_only=True)

    class Meta:
        model = OpeningHour
        fields = ["weekday", "weekday_label", "opens_at", "closes_at"]


class ClinicSerializer(serializers.ModelSerializer):
    """
    Ficha del establecimiento (HU-09): horario, servicios, urgencias y contacto, con el ORIGEN
    y la FECHA de verificación de la información. No incluye cupos ni promete atención inmediata.
    """

    latitude = serializers.SerializerMethodField()
    longitude = serializers.SerializerMethodField()
    distance_km = serializers.SerializerMethodField()
    is_open_now = serializers.SerializerMethodField(
        help_text="Calculado con el horario declarado y la hora actual. No confirma atención."
    )
    today_hours = serializers.SerializerMethodField()
    services = serializers.SerializerMethodField()
    info_origin_label = serializers.CharField(source="get_info_origin_display", read_only=True)
    is_stale = serializers.SerializerMethodField(help_text="La información supera el umbral de antigüedad.")
    opening_hours = OpeningHourSerializer(many=True, read_only=True)

    class Meta:
        model = Clinic
        fields = [
            "id", "name", "address", "commune", "phone", "latitude", "longitude", "distance_km",
            "attends_emergencies", "emergency_24h", "services", "is_open_now", "today_hours",
            "opening_hours", "info_origin", "info_origin_label", "verified_at", "is_stale",
            "accepts_pettech_records",
        ]

    def get_latitude(self, obj) -> float:
        return obj.location.y

    def get_longitude(self, obj) -> float:
        return obj.location.x

    def get_distance_km(self, obj) -> float | None:
        return getattr(obj, "distance_km", None)

    def get_is_open_now(self, obj) -> bool:
        value = getattr(obj, "is_open_now", None)
        return services.is_open_at(obj, timezone.now()) if value is None else value

    def get_today_hours(self, obj) -> str:
        return services.today_hours_label(obj, timezone.now())

    def get_services(self, obj) -> list[dict]:
        return obj.service_list()

    def get_is_stale(self, obj) -> bool:
        return services.is_stale(obj)


class ClinicSearchQuerySerializer(serializers.Serializer):
    lat = serializers.FloatField(required=False, min_value=-90, max_value=90)
    lng = serializers.FloatField(required=False, min_value=-180, max_value=180)
    radius_km = serializers.FloatField(required=False, default=10, min_value=1, max_value=services.MAX_RADIUS_KM)
    emergency = serializers.BooleanField(required=False, default=False)
    open_now = serializers.BooleanField(required=False, default=False)
    service = serializers.ChoiceField(choices=Service.choices, required=False)

    def validate(self, attrs):
        if ("lat" in attrs) != ("lng" in attrs):
            raise serializers.ValidationError("Debes indicar latitud y longitud juntas.")
        return attrs
