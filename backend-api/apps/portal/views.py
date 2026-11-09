"""
Portal web de PetTech (DEC-025).

- Administrador de PetTech: habilita veterinarias y ve cuadros de análisis agregados.
- Veterinaria: autoriza a sus profesionales y mantiene su horario/urgencias/servicios.
- Profesional: ingresa el código PetTech, solicita acceso y, con autorización del tutor, usa la ficha clínica.
El tutor NO usa el portal: gestiona sus mascotas y decide los accesos desde la app Android.
"""
import logging

from django.conf import settings
from django.contrib import messages
from django.contrib.auth import authenticate, login, logout
from django.core.cache import cache
from django.shortcuts import get_object_or_404, redirect, render
from django.urls import reverse
from django.utils import timezone
from django.views import View
from rest_framework.exceptions import APIException

from apps.access_control import services as access
from apps.access_control.models import AccessRequest, AuditEvent
from apps.accounts.models import User, UserManager
from apps.clinical_records import services as records
from apps.clinical_records.models import ClinicalEntry, EntryType, Origin
from apps.clinical_records.serializers import ClinicalEntrySerializer, CorrectionSerializer, build_record_payload
from apps.clinics.models import Clinic, Service

from . import analytics, services
from .forms import (
    AccessRequestForm,
    ClinicCreateForm,
    ClinicProfileForm,
    LoginForm,
    ProfessionalCreateForm,
    hours_forms,
)
from .mixins import PORTAL_ROLES, AdminRequiredMixin, ClinicRequiredMixin, ProfessionalRequiredMixin

logger = logging.getLogger("pettech.portal")

HOME_BY_ROLE = {
    User.Role.ADMIN: "portal:admin-dashboard",
    User.Role.CLINIC: "portal:clinic-dashboard",
    User.Role.PROFESSIONAL: "portal:vet-home",
}


def _api_error_message(exc: APIException) -> str:
    detail = exc.detail
    if isinstance(detail, dict):
        return " ".join(str(v[0] if isinstance(v, list) else v) for v in detail.values())
    if isinstance(detail, list):
        return " ".join(str(d) for d in detail)
    return str(detail)


# ------------------------------------------------------------------------------------- Sesión

class LoginView(View):
    """Inicio de sesión del portal con límite de intentos por correo + IP (anti fuerza bruta)."""

    template_name = "portal/login.html"

    def _key(self, request, email):
        ip = request.META.get("REMOTE_ADDR", "")
        return f"portal-login:{ip}:{UserManager.normalize_login_email(email)}"

    def get(self, request):
        if request.user.is_authenticated and request.user.role in PORTAL_ROLES:
            return redirect("portal:home")
        return render(request, self.template_name, {"form": LoginForm()})

    def post(self, request):
        form = LoginForm(request.POST)
        if not form.is_valid():
            return render(request, self.template_name, {"form": form})
        email = form.cleaned_data["email"]
        key = self._key(request, email)
        attempts = cache.get(key, 0)
        if attempts >= settings.PORTAL_LOGIN_MAX_ATTEMPTS:
            form.add_error(None, "Demasiados intentos. Espera 15 minutos antes de volver a intentarlo.")
            return render(request, self.template_name, {"form": form}, status=429)
        user = authenticate(request, username=email, password=form.cleaned_data["password"])
        if user is None or not user.is_active:
            cache.set(key, attempts + 1, 15 * 60)
            form.add_error(None, "Correo o contraseña incorrectos.")
            return render(request, self.template_name, {"form": form})
        if user.role not in PORTAL_ROLES:
            form.add_error(None, "Las cuentas de tutor se usan en la app móvil PetTech, no en el portal.")
            return render(request, self.template_name, {"form": form})
        cache.delete(key)
        login(request, user)  # rota el identificador de sesión
        logger.info("Ingreso al portal user_id=%s role=%s", user.pk, user.role)
        return redirect("portal:home")


class LogoutView(View):
    def post(self, request):
        logout(request)
        messages.info(request, "Cerraste sesión.")
        return redirect("portal:login")


class HomeView(View):
    def get(self, request):
        if not request.user.is_authenticated:
            return redirect("portal:login")
        target = HOME_BY_ROLE.get(request.user.role)
        if target is None:
            logout(request)
            return redirect("portal:login")
        return redirect(target)


# ------------------------------------------------------------------------------ Administrador

class AdminDashboardView(AdminRequiredMixin, View):
    def get(self, request):
        return render(request, "portal/admin/dashboard.html", {"m": analytics.admin_metrics(), "section": "dash"})


class AdminClinicListView(AdminRequiredMixin, View):
    def get(self, request):
        clinics = Clinic.objects.select_related("account").order_by("name")
        rows = [{"clinic": c, "professionals": c.memberships.filter(is_verified=True).count()} for c in clinics]
        return render(request, "portal/admin/clinics.html", {"rows": rows, "section": "clinics"})


class AdminClinicCreateView(AdminRequiredMixin, View):
    template = "portal/admin/clinic_form.html"

    def get(self, request):
        return render(request, self.template, {"form": ClinicCreateForm(), "section": "clinics"})

    def post(self, request):
        form = ClinicCreateForm(request.POST)
        if form.is_valid():
            try:
                clinic = services.create_clinic_with_account(request.user, form.cleaned_data)
            except services.PortalError as exc:
                form.add_error(None, str(exc))
            else:
                messages.success(request, f"Veterinaria «{clinic.name}» creada. Entrega a su responsable el correo "
                                          f"{clinic.account.email} y la contraseña inicial por un canal seguro.")
                return redirect("portal:admin-clinic", pk=clinic.pk)
        return render(request, self.template, {"form": form, "section": "clinics"})


class AdminClinicDetailView(AdminRequiredMixin, View):
    def get(self, request, pk):
        clinic = get_object_or_404(Clinic.objects.select_related("account"), pk=pk)
        return render(request, "portal/admin/clinic_detail.html", {
            "clinic": clinic,
            "memberships": clinic.memberships.select_related("professional").order_by("professional__full_name"),
            "hours": clinic.opening_hours.all(),
            "m": analytics.clinic_metrics(clinic),
            "section": "clinics",
        })

    def post(self, request, pk):
        clinic = get_object_or_404(Clinic, pk=pk)
        action = request.POST.get("action")
        if action == "toggle_active":
            clinic.is_active = not clinic.is_active
            clinic.save(update_fields=["is_active", "updated_at"])
            if clinic.account_id:
                User.objects.filter(pk=clinic.account_id).update(is_active=clinic.is_active)
            messages.success(request, "Veterinaria habilitada." if clinic.is_active else "Veterinaria suspendida.")
        elif action == "toggle_network":
            clinic.accepts_pettech_records = not clinic.accepts_pettech_records
            clinic.save(update_fields=["accepts_pettech_records", "updated_at"])
            messages.success(request, "Participación en la red PetTech actualizada.")
        return redirect("portal:admin-clinic", pk=pk)


# --------------------------------------------------------------------------------- Veterinaria

class ClinicDashboardView(ClinicRequiredMixin, View):
    def get(self, request):
        clinic = self.clinic
        recent = (
            AccessRequest.objects.filter(clinic=clinic).select_related("professional", "pet").order_by("-created_at")[:10]
        )
        return render(request, "portal/clinic/dashboard.html", {
            "clinic": clinic,
            "m": analytics.clinic_metrics(clinic),
            "memberships": clinic.memberships.select_related("professional").order_by("professional__full_name"),
            "recent": recent,
            "section": "dash",
        })


class ClinicProfessionalCreateView(ClinicRequiredMixin, View):
    template = "portal/clinic/professional_form.html"

    def get(self, request):
        return render(request, self.template, {"form": ProfessionalCreateForm(), "section": "team"})

    def post(self, request):
        form = ProfessionalCreateForm(request.POST)
        if form.is_valid():
            try:
                m = services.create_professional(self.clinic, request.user, form.cleaned_data)
            except services.PortalError as exc:
                form.add_error(None, str(exc))
            else:
                messages.success(request, f"{m.professional.full_name} quedó autorizado/a. Puede ingresar al portal "
                                          f"con {m.professional.email}.")
                return redirect("portal:clinic-dashboard")
        return render(request, self.template, {"form": form, "section": "team"})


class ClinicProfessionalToggleView(ClinicRequiredMixin, View):
    def post(self, request, pk):
        authorize = request.POST.get("authorize") == "1"
        try:
            m = services.set_professional_authorized(self.clinic, request.user, pk, authorize)
        except services.PortalError as exc:
            messages.error(request, str(exc))
        else:
            messages.success(request, f"{m.professional.full_name}: "
                                      f"{'autorizado/a' if authorize else 'suspendido/a'}.")
        return redirect("portal:clinic-dashboard")


class ClinicProfileView(ClinicRequiredMixin, View):
    template = "portal/clinic/profile.html"

    def _ctx(self, form, days, error=None):
        return {"clinic": self.clinic, "form": form, "days": days, "error": error, "section": "profile"}

    def get(self, request):
        c = self.clinic
        form = ClinicProfileForm(initial={"phone": c.phone, "attends_emergencies": c.attends_emergencies,
                                          "emergency_24h": c.emergency_24h, "services": c.services})
        return render(request, self.template, self._ctx(form, hours_forms(clinic=c)))

    def post(self, request):
        form = ClinicProfileForm(request.POST)
        days = hours_forms(data=request.POST, clinic=self.clinic)
        if form.is_valid() and all(d.is_valid() for d in days):
            hours = {
                d.day: (d.cleaned_data["opens_at"], d.cleaned_data["closes_at"]) if d.cleaned_data.get("open_day") else None
                for d in days
            }
            if not form.cleaned_data["emergency_24h"] and not any(hours.values()):
                return render(request, self.template, self._ctx(
                    form, days, "Indica al menos un día de atención o marca \"Atención 24 horas\"."))
            services.update_clinic_profile(self.clinic, request.user, form.cleaned_data, hours)
            messages.success(request, "Información publicada. Los tutores la verán como «Confirmado por el "
                                      "establecimiento» con la fecha de hoy.")
            return redirect("portal:clinic-profile")
        return render(request, self.template, self._ctx(form, days))


# ------------------------------------------------------------------------------- Profesional

class VetHomeView(ProfessionalRequiredMixin, View):
    template = "portal/vet/home.html"

    def _ctx(self, request, form):
        try:
            membership = access.membership_for(request.user)
        except APIException as exc:
            return {"blocked": _api_error_message(exc), "form": form, "section": "vet"}
        reqs = AccessRequest.objects.filter(professional=request.user).select_related("pet", "clinic")[:50]
        rows = [{"r": r, "status": r.effective_status, "label": AccessRequest.Status(r.effective_status).label,
                 "pet_name": r.pet.name if r.is_active_grant else None} for r in reqs]
        return {"membership": membership, "rows": rows, "form": form, "section": "vet",
                "has_pending": any(x["status"] == "PENDING" for x in rows)}

    def get(self, request):
        return render(request, self.template, self._ctx(request, AccessRequestForm()))

    def post(self, request):
        form = AccessRequestForm(request.POST)
        if form.is_valid():
            try:
                req = access.request_access(request.user, form.cleaned_data["public_code"],
                                            form.cleaned_data.get("reason", ""))
            except APIException as exc:
                form.add_error("public_code", _api_error_message(exc))
            else:
                if req.is_active_grant:
                    messages.info(request, f"Ya tienes autorización vigente para {req.pet.public_code}.")
                    return redirect("portal:vet-record", code=req.pet.public_code)
                messages.success(request, f"Solicitud enviada para {req.pet.public_code}. El tutor la verá en su app "
                                          "y esta página se actualizará cuando responda.")
                return redirect("portal:vet-home")
        return render(request, self.template, self._ctx(request, form))


def _entry_types():
    return [{"value": t.value, "label": t.label} for t in EntryType]


class VetRecordView(ProfessionalRequiredMixin, View):
    """Ficha clínica para el profesional autorizado; cada consulta se valida y registra (RNF-09, RNF-10)."""

    template = "portal/vet/record.html"

    def _load(self, request, code, action):
        pet, membership = access.require_active_grant(request.user, code, action)
        return pet, membership

    def _render(self, request, pet, membership, form_errors=None, form_values=None, status=200):
        type_filter = request.GET.get("type") if request.GET.get("type") in EntryType.values else None
        entries = records.current_entries(pet, type_filter)
        payload = build_record_payload(pet, entries, include_pet_id=False)
        grant = (AccessRequest.objects.filter(pet=pet, clinic=membership.clinic, status="APPROVED")
                 .order_by("-decided_at").first())
        return render(request, self.template, {
            "pet": payload["pet"], "summary": payload["summary"], "entries": entries,
            "membership": membership, "grant": grant, "types": _entry_types(), "type_filter": type_filter,
            "errors": form_errors or {}, "values": form_values or {}, "section": "vet",
            "today": timezone.localdate().isoformat(),
        }, status=status)

    def get(self, request, code):
        try:
            pet, membership = self._load(request, code, AuditEvent.Action.RECORD_READ)
        except APIException as exc:
            messages.error(request, _api_error_message(exc))
            return redirect("portal:vet-home")
        access.audit(request.user, AuditEvent.Action.RECORD_READ, pet=pet, clinic=membership.clinic)
        return self._render(request, pet, membership)

    def post(self, request, code):
        try:
            pet, membership = self._load(request, code, AuditEvent.Action.RECORD_CREATED)
        except APIException as exc:
            messages.error(request, _api_error_message(exc))
            return redirect("portal:vet-home")
        data = {k: v for k, v in request.POST.items() if k != "csrfmiddlewaretoken" and v != ""}
        data["is_active"] = request.POST.get("is_active") == "on"
        serializer = ClinicalEntrySerializer(data=data)
        if not serializer.is_valid():
            errors = {k: " ".join(str(x) for x in v) for k, v in serializer.errors.items()}
            return self._render(request, pet, membership, errors, request.POST, status=400)
        records.create_by_professional(request.user, membership, pet, serializer.validated_data)
        messages.success(request, "Registro guardado. El tutor ya puede verlo en su app.")
        return redirect(reverse("portal:vet-record", kwargs={"code": pet.public_code}))


class VetCorrectView(ProfessionalRequiredMixin, View):
    template = "portal/vet/correct.html"

    def get(self, request, code, entry_id):
        try:
            pet, membership = access.require_active_grant(request.user, code, AuditEvent.Action.RECORD_CORRECTED)
        except APIException as exc:
            messages.error(request, _api_error_message(exc))
            return redirect("portal:vet-home")
        entry = get_object_or_404(ClinicalEntry, pk=entry_id, pet=pet, origin=Origin.PROFESSIONAL,
                                  clinic=membership.clinic)
        return render(request, self.template, {"pet": pet, "entry": entry, "types": _entry_types(),
                                               "errors": {}, "section": "vet"})

    def post(self, request, code, entry_id):
        try:
            pet, membership = access.require_active_grant(request.user, code, AuditEvent.Action.RECORD_CORRECTED)
        except APIException as exc:
            messages.error(request, _api_error_message(exc))
            return redirect("portal:vet-home")
        entry = get_object_or_404(ClinicalEntry, pk=entry_id, pet=pet)
        data = {k: v for k, v in request.POST.items() if k != "csrfmiddlewaretoken" and v != ""}
        data["is_active"] = request.POST.get("is_active") == "on"
        serializer = CorrectionSerializer(data=data)
        if serializer.is_valid():
            values = dict(serializer.validated_data)
            reason = values.pop("reason")
            try:
                records.correct(request.user, pet, entry.pk, values, reason, membership=membership)
            except APIException as exc:
                messages.error(request, _api_error_message(exc))
            else:
                messages.success(request, "Corrección guardada. La versión anterior se conserva.")
            return redirect("portal:vet-record", code=pet.public_code)
        errors = {k: " ".join(str(x) for x in v) for k, v in serializer.errors.items()}
        return render(request, self.template, {"pet": pet, "entry": entry, "types": _entry_types(),
                                               "errors": errors, "values": request.POST, "section": "vet"},
                      status=400)


SERVICE_LABELS = dict(Service.choices)


# --------------------------------------------------------------------------- Páginas públicas

def _public_ctx():
    return {"contact_email": settings.PETTECH_CONTACT_EMAIL, "version": settings.PETTECH_PRIVACY_POLICY_VERSION}


def privacy_policy(request):
    """Política de privacidad pública (requisito de Google Play: URL accesible sin iniciar sesión)."""
    return render(request, "portal/public/privacy.html", _public_ctx())


def account_deletion(request):
    """Instrucciones para solicitar la eliminación de la cuenta (requisito de Google Play)."""
    return render(request, "portal/public/account_deletion.html", _public_ctx())
