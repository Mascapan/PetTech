from drf_spectacular.utils import OpenApiResponse, extend_schema
from rest_framework import serializers, status
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from . import services
from .serializers import (
    AuthResponseSerializer,
    LoginSerializer,
    PasswordResetConfirmSerializer,
    PasswordResetRequestSerializer,
    RegisterSerializer,
    UserSerializer,
)


def _auth_response(user, instance, token, http_status):
    return Response(
        {
            "token": token,
            # Se expresa en la zona horaria del proyecto (America/Santiago), igual que el resto de fechas.
            "expiry": serializers.DateTimeField().to_representation(instance.expiry),
            "user": UserSerializer(user).data,
        },
        status=http_status,
    )


class RegisterView(APIView):
    """Crea una cuenta de TUTOR y devuelve un token de sesión (HU-01)."""

    permission_classes = [AllowAny]
    authentication_classes = []
    throttle_scope = "auth"

    @extend_schema(request=RegisterSerializer, responses={201: AuthResponseSerializer}, tags=["auth"])
    def post(self, request):
        serializer = RegisterSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        user = serializer.save()
        instance, token = services.issue_token(user, request)
        return _auth_response(user, instance, token, status.HTTP_201_CREATED)


class LoginView(APIView):
    """Inicia sesión con correo y contraseña (HU-01). Limitado a 10 intentos por minuto."""

    permission_classes = [AllowAny]
    authentication_classes = []
    throttle_scope = "auth"

    @extend_schema(request=LoginSerializer, responses={200: AuthResponseSerializer}, tags=["auth"])
    def post(self, request):
        serializer = LoginSerializer(data=request.data, context={"request": request})
        serializer.is_valid(raise_exception=True)
        user = serializer.validated_data["user"]
        instance, token = services.issue_token(user, request)
        return _auth_response(user, instance, token, status.HTTP_200_OK)


class LogoutView(APIView):
    """Revoca el token utilizado en la solicitud (cierre de sesión en este dispositivo)."""

    permission_classes = [IsAuthenticated]

    @extend_schema(request=None, responses={204: OpenApiResponse(description="Sesión cerrada")}, tags=["auth"])
    def post(self, request):
        services.revoke_token(request.auth)
        return Response(status=status.HTTP_204_NO_CONTENT)


class LogoutAllView(APIView):
    """Revoca todos los tokens del usuario (cierre de sesión en todos los dispositivos)."""

    permission_classes = [IsAuthenticated]

    @extend_schema(request=None, responses={204: OpenApiResponse(description="Sesiones cerradas")}, tags=["auth"])
    def post(self, request):
        services.revoke_all_tokens(request.user)
        return Response(status=status.HTTP_204_NO_CONTENT)


class MeView(APIView):
    """Devuelve los datos de la cuenta autenticada."""

    permission_classes = [IsAuthenticated]

    @extend_schema(responses={200: UserSerializer}, tags=["auth"])
    def get(self, request):
        return Response(UserSerializer(request.user).data)


GENERIC_RESET_MESSAGE = "Si el correo está registrado, recibirás un código para crear una nueva contraseña."


class PasswordResetRequestView(APIView):
    """Solicita un código de recuperación (HU-01). No revela si el correo existe."""

    permission_classes = [AllowAny]
    authentication_classes = []
    throttle_scope = "auth"

    @extend_schema(request=PasswordResetRequestSerializer, responses={200: dict}, tags=["auth"])
    def post(self, request):
        serializer = PasswordResetRequestSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        services.start_password_reset(serializer.validated_data["email"])
        return Response({"detail": GENERIC_RESET_MESSAGE})


class PasswordResetConfirmView(APIView):
    """Crea una nueva contraseña con el código recibido; cierra las sesiones anteriores."""

    permission_classes = [AllowAny]
    authentication_classes = []
    throttle_scope = "auth"

    @extend_schema(request=PasswordResetConfirmSerializer, responses={200: dict}, tags=["auth"])
    def post(self, request):
        serializer = PasswordResetConfirmSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data
        if not services.confirm_password_reset(data["email"], data["code"], data["new_password"]):
            raise serializers.ValidationError({"code": ["El código no es válido o ya venció. Solicita uno nuevo."]})
        return Response({"detail": "Tu contraseña fue actualizada. Ya puedes iniciar sesión."})


class AccountDeletionRequestView(APIView):
    """El titular solicita eliminar su cuenta desde la app. Se registra y se cierran sus sesiones."""

    permission_classes = [IsAuthenticated]

    @extend_schema(request=None, responses={201: dict}, tags=["auth"])
    def post(self, request):
        from .models import AccountDeletionRequest

        reason = str(request.data.get("reason", ""))[:300] if hasattr(request, "data") else ""
        existing = AccountDeletionRequest.objects.filter(user=request.user, status="PENDING").first()
        if existing is None:
            AccountDeletionRequest.objects.create(user=request.user, email=request.user.email, reason=reason)
        services.revoke_all_tokens(request.user)
        return Response(
            {"detail": "Recibimos tu solicitud. Te escribiremos al correo de tu cuenta para confirmarla "
                       "en un plazo máximo de 30 días."},
            status=status.HTTP_201_CREATED,
        )
