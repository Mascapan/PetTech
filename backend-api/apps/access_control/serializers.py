from rest_framework import serializers

from .models import AccessRequest, AuditEvent


class AccessRequestCreateSerializer(serializers.Serializer):
    public_code = serializers.CharField(max_length=20)
    reason = serializers.CharField(max_length=200, required=False, allow_blank=True, default="")


class AccessRequestSerializer(serializers.ModelSerializer):
    """
    Vista de una solicitud. El nombre de la mascota se muestra al profesional SOLO si la
    autorización está vigente; antes de eso solo ve el código que él mismo ingresó.
    """

    status = serializers.SerializerMethodField()
    status_label = serializers.SerializerMethodField()
    pet_id = serializers.SerializerMethodField()
    pet_code = serializers.CharField(source="pet.public_code", read_only=True)
    pet_name = serializers.SerializerMethodField()
    clinic_id = serializers.UUIDField(source="clinic.id", read_only=True)
    clinic_name = serializers.CharField(source="clinic.name", read_only=True)
    professional_name = serializers.CharField(source="professional.full_name", read_only=True)

    class Meta:
        model = AccessRequest
        fields = [
            "id", "status", "status_label", "pet_id", "pet_code", "pet_name", "clinic_id", "clinic_name",
            "professional_name", "reason", "created_at", "decided_at", "expires_at", "revoked_at",
        ]

    def _is_tutor(self) -> bool:
        user = self.context["request"].user
        return user.role == "TUTOR"

    def get_status(self, obj) -> str:
        return obj.effective_status

    def get_status_label(self, obj) -> str:
        return AccessRequest.Status(obj.effective_status).label

    def get_pet_id(self, obj) -> str | None:
        return str(obj.pet_id) if self._is_tutor() else None

    def get_pet_name(self, obj) -> str | None:
        if self._is_tutor() or obj.is_active_grant:
            return obj.pet.name
        return None


class AuditEventSerializer(serializers.ModelSerializer):
    action_label = serializers.CharField(source="get_action_display", read_only=True)
    result_label = serializers.CharField(source="get_result_display", read_only=True)

    class Meta:
        model = AuditEvent
        fields = ["id", "created_at", "actor_name", "clinic_name", "action", "action_label", "result",
                  "result_label"]
