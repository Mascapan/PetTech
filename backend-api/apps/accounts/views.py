from drf_spectacular.utils import OpenApiResponse, extend_schema
from rest_framework import serializers, status
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from . import services
from .serializers import AuthResponseSerializer, LoginSerializer, RegisterSerializer, UserSerializer


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
