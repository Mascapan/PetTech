from django.conf import settings
from django.contrib.auth import authenticate, password_validation
from django.core.exceptions import ValidationError as DjangoValidationError
from django.utils import timezone
from rest_framework import serializers

from .models import User, UserManager


class UserSerializer(serializers.ModelSerializer):
    """Datos públicos de la cuenta propia (nunca incluye contraseña ni tokens)."""

    role_label = serializers.CharField(source="get_role_display", read_only=True)

    class Meta:
        model = User
        fields = ["id", "email", "full_name", "role", "role_label", "date_joined"]
        read_only_fields = fields


class RegisterSerializer(serializers.Serializer):
    """
    Registro de TUTORES (HU-01).

    El rol no es elegible por el cliente: las cuentas profesionales se crean y verifican
    desde el panel de administración (Fase 2). Cualquier campo "role" enviado se ignora.
    """

    full_name = serializers.CharField(max_length=150, trim_whitespace=True)
    email = serializers.EmailField(max_length=254)
    password = serializers.CharField(write_only=True, trim_whitespace=False, style={"input_type": "password"})
    accept_privacy_policy = serializers.BooleanField(write_only=True)

    def validate_full_name(self, value: str) -> str:
        value = " ".join(value.split())
        if len(value) < 3:
            raise serializers.ValidationError("Ingresa tu nombre completo (mínimo 3 caracteres).")
        return value

    def validate_email(self, value: str) -> str:
        value = UserManager.normalize_login_email(value)
        if User.objects.filter(email__iexact=value).exists():
            raise serializers.ValidationError("Ya existe una cuenta registrada con este correo.")
        return value

    def validate_accept_privacy_policy(self, value: bool) -> bool:
        if value is not True:
            raise serializers.ValidationError("Debes aceptar la política de privacidad para crear una cuenta.")
        return value

    def validate(self, attrs):
        candidate = User(email=attrs["email"], full_name=attrs["full_name"])
        try:
            password_validation.validate_password(attrs["password"], user=candidate)
        except DjangoValidationError as exc:
            raise serializers.ValidationError({"password": list(exc.messages)}) from exc
        return attrs

    def create(self, validated_data):
        return User.objects.create_user(
            email=validated_data["email"],
            password=validated_data["password"],
            full_name=validated_data["full_name"],
            role=User.Role.TUTOR,
            privacy_policy_version=settings.PETTECH_PRIVACY_POLICY_VERSION,
            privacy_policy_accepted_at=timezone.now(),
        )


class LoginSerializer(serializers.Serializer):
    email = serializers.EmailField(max_length=254)
    password = serializers.CharField(write_only=True, trim_whitespace=False, style={"input_type": "password"})

    # Mensaje único: no revela si el correo existe, si la clave es incorrecta o si la cuenta está suspendida.
    INVALID_CREDENTIALS = "Correo o contraseña incorrectos."

    def validate(self, attrs):
        user = authenticate(
            request=self.context.get("request"),
            username=UserManager.normalize_login_email(attrs["email"]),
            password=attrs["password"],
        )
        if user is None or not user.is_active:
            raise serializers.ValidationError(self.INVALID_CREDENTIALS, code="invalid_credentials")
        attrs["user"] = user
        return attrs


class AuthResponseSerializer(serializers.Serializer):
    """Documentación OpenAPI de la respuesta de registro/inicio de sesión."""

    token = serializers.CharField()
    expiry = serializers.DateTimeField()
    user = UserSerializer()
