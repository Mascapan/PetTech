from django import forms
from django.contrib.auth import password_validation
from django.core.exceptions import ValidationError

from apps.accounts.models import User
from apps.clinics.models import OpeningHour, Service


class LoginForm(forms.Form):
    email = forms.EmailField(label="Correo electrónico", widget=forms.EmailInput(attrs={"autocomplete": "email"}))
    password = forms.CharField(label="Contraseña", strip=False,
                               widget=forms.PasswordInput(attrs={"autocomplete": "current-password"}))


def _check_password(form, field: str, email_field: str, name_field: str):
    password = form.cleaned_data.get(field)
    if not password:
        return
    candidate = User(email=form.cleaned_data.get(email_field, ""), full_name=form.cleaned_data.get(name_field, ""))
    try:
        password_validation.validate_password(password, user=candidate)
    except ValidationError as exc:
        form.add_error(field, exc)
    if form.cleaned_data.get(field) != form.cleaned_data.get(f"{field}2"):
        form.add_error(f"{field}2", "Las contraseñas no coinciden.")


class ClinicCreateForm(forms.Form):
    """Alta de una veterinaria por el administrador de PetTech."""

    name = forms.CharField(label="Nombre del establecimiento", max_length=120)
    address = forms.CharField(label="Dirección", max_length=200)
    commune = forms.CharField(label="Comuna", max_length=60, initial="Chillán")
    phone = forms.CharField(label="Teléfono", max_length=20, required=False)
    latitude = forms.FloatField(label="Latitud", min_value=-90, max_value=90,
                                help_text="Ej.: -36.6066 (clic derecho en Google Maps → copiar coordenadas).")
    longitude = forms.FloatField(label="Longitud", min_value=-180, max_value=180, help_text="Ej.: -72.1034")
    attends_emergencies = forms.BooleanField(label="Declara atender urgencias", required=False)
    emergency_24h = forms.BooleanField(label="Declara atención 24 horas", required=False)
    services = forms.MultipleChoiceField(label="Servicios", choices=Service.choices, required=False,
                                         widget=forms.CheckboxSelectMultiple)
    accepts_pettech_records = forms.BooleanField(label="Participa en la red PetTech (ficha clínica compartida)",
                                                 required=False, initial=True)
    account_name = forms.CharField(label="Nombre del responsable", max_length=150)
    account_email = forms.EmailField(label="Correo de acceso de la veterinaria")
    account_password = forms.CharField(label="Contraseña inicial", strip=False, widget=forms.PasswordInput,
                                       help_text="Mínimo 10 caracteres. Entrégala por un canal seguro.")
    account_password2 = forms.CharField(label="Repite la contraseña", strip=False, widget=forms.PasswordInput)

    def clean_account_email(self):
        email = self.cleaned_data["account_email"].strip().lower()
        if User.objects.filter(email__iexact=email).exists():
            raise ValidationError("Ya existe una cuenta con ese correo.")
        return email

    def clean(self):
        super().clean()
        _check_password(self, "account_password", "account_email", "account_name")
        return self.cleaned_data


class ProfessionalCreateForm(forms.Form):
    """La veterinaria registra y autoriza a un profesional de su equipo."""

    full_name = forms.CharField(label="Nombre completo", max_length=150)
    email = forms.EmailField(label="Correo")
    license_number = forms.CharField(label="N.º de registro profesional (opcional)", max_length=40, required=False)
    password = forms.CharField(label="Contraseña inicial", strip=False, widget=forms.PasswordInput,
                               help_text="Mínimo 10 caracteres. El profesional la usará para ingresar al portal o a la app.")
    password2 = forms.CharField(label="Repite la contraseña", strip=False, widget=forms.PasswordInput)

    def clean_email(self):
        email = self.cleaned_data["email"].strip().lower()
        if User.objects.filter(email__iexact=email).exists():
            raise ValidationError("Ya existe una cuenta con ese correo.")
        return email

    def clean(self):
        super().clean()
        _check_password(self, "password", "email", "full_name")
        return self.cleaned_data


class ClinicProfileForm(forms.Form):
    """Información que la propia veterinaria mantiene al día (datos, ubicación, contacto y servicios)."""

    name = forms.CharField(label="Nombre del establecimiento", max_length=120)
    address = forms.CharField(label="Dirección", max_length=200)
    commune = forms.CharField(label="Comuna", max_length=60)
    latitude = forms.FloatField(label="Latitud", min_value=-90, max_value=90,
                                widget=forms.NumberInput(attrs={"step": "any"}))
    longitude = forms.FloatField(label="Longitud", min_value=-180, max_value=180,
                                 widget=forms.NumberInput(attrs={"step": "any"}))
    phone = forms.CharField(label="Teléfono", max_length=20, required=False)
    contact_email = forms.EmailField(label="Correo de contacto (público)", required=False)
    website = forms.URLField(label="Sitio web o red social", required=False, max_length=200,
                             widget=forms.URLInput(attrs={"placeholder": "https://"}), assume_scheme="https")
    attends_emergencies = forms.BooleanField(label="Atendemos urgencias", required=False)
    emergency_24h = forms.BooleanField(label="Atención 24 horas", required=False)
    services = forms.MultipleChoiceField(label="Servicios", choices=Service.choices, required=False,
                                         widget=forms.CheckboxSelectMultiple)
    services_detail = forms.CharField(
        label="Otros servicios (detalle)", required=False, max_length=1500,
        widget=forms.Textarea(attrs={"rows": 5, "placeholder": "Ej.: dermatología, ecografía abdominal, "
                                                               "certificados de viaje (SAG), peluquería clínica…"}),
        help_text="Describe en texto libre todos los servicios que ofrecen. Máximo 1.500 caracteres.")

    def clean_services_detail(self):
        return "\n".join(line.rstrip() for line in self.cleaned_data["services_detail"].strip().splitlines())

    def clean(self):
        data = super().clean()
        lat, lng = data.get("latitude"), data.get("longitude")
        # Validación gruesa: la cobertura del piloto es Chile continental.
        if lat is not None and lng is not None and not (-56 <= lat <= -17 and -76 <= lng <= -66):
            raise ValidationError("La ubicación no parece estar en Chile. Revisa latitud y longitud.")
        return data


class DayHoursForm(forms.Form):
    """Una franja por día. Si cierra antes de abrir, la franja termina al día siguiente (turno nocturno)."""

    open_day = forms.BooleanField(label="Abre", required=False)
    opens_at = forms.TimeField(label="Desde", required=False, widget=forms.TimeInput(attrs={"type": "time"}))
    closes_at = forms.TimeField(label="Hasta", required=False, widget=forms.TimeInput(attrs={"type": "time"}))

    def clean(self):
        data = super().clean()
        if data.get("open_day"):
            if not data.get("opens_at") or not data.get("closes_at"):
                raise ValidationError("Indica hora de apertura y cierre.")
            if data["opens_at"] == data["closes_at"]:
                raise ValidationError("La apertura y el cierre no pueden ser iguales (usa \"Atención 24 horas\").")
        return data


WEEKDAYS = OpeningHour.Weekday.choices


def hours_forms(data=None, clinic=None):
    """Siete formularios (lunes a domingo), precargados con el horario actual."""
    current = {}
    if clinic is not None:
        for h in clinic.opening_hours.all():
            current.setdefault(h.weekday, h)
    forms_list = []
    for day, label in WEEKDAYS:
        h = current.get(day)
        initial = {"open_day": h is not None, "opens_at": h.opens_at if h else None,
                   "closes_at": h.closes_at if h else None}
        f = DayHoursForm(data=data, prefix=f"d{day}", initial=initial)
        f.day, f.label = day, label
        forms_list.append(f)
    return forms_list


class AccessRequestForm(forms.Form):
    public_code = forms.CharField(label="Código PetTech", max_length=20,
                                  widget=forms.TextInput(attrs={"placeholder": "PT-XXXX-XXXX", "autocomplete": "off",
                                                                "style": "text-transform:uppercase"}))
    reason = forms.CharField(label="Motivo (opcional)", max_length=200, required=False,
                             widget=forms.TextInput(attrs={"placeholder": "Ej.: control anual, urgencia"}))


class PortalPasswordChangeForm(forms.Form):
    """Cambio de contraseña para cualquier rol del portal (exige la contraseña actual)."""

    old_password = forms.CharField(label="Contraseña actual", strip=False,
                                   widget=forms.PasswordInput(attrs={"autocomplete": "current-password"}))
    new_password = forms.CharField(label="Nueva contraseña", strip=False,
                                   widget=forms.PasswordInput(attrs={"autocomplete": "new-password"}),
                                   help_text="Mínimo 10 caracteres; evita datos personales o contraseñas comunes.")
    new_password2 = forms.CharField(label="Repite la nueva contraseña", strip=False,
                                    widget=forms.PasswordInput(attrs={"autocomplete": "new-password"}))

    def __init__(self, user, *args, **kwargs):
        self.user = user
        super().__init__(*args, **kwargs)

    def clean_old_password(self):
        value = self.cleaned_data["old_password"]
        if not self.user.check_password(value):
            raise ValidationError("La contraseña actual no es correcta.")
        return value

    def clean(self):
        data = super().clean()
        new = data.get("new_password")
        if new:
            try:
                password_validation.validate_password(new, user=self.user)
            except ValidationError as exc:
                self.add_error("new_password", exc)
            if data.get("old_password") and new == data["old_password"]:
                self.add_error("new_password", "La nueva contraseña debe ser distinta de la actual.")
            if new != data.get("new_password2"):
                self.add_error("new_password2", "Las contraseñas no coinciden.")
        return data


class DirectoryClinicForm(forms.Form):
    """Alta manual de una veterinaria NO afiliada al directorio, con su fuente pública (DEC-029)."""

    name = forms.CharField(label="Nombre del establecimiento", max_length=120)
    address = forms.CharField(label="Dirección", max_length=200)
    commune = forms.CharField(label="Comuna", max_length=60, initial="Chillán")
    phone = forms.CharField(label="Teléfono", max_length=20, required=False)
    website = forms.URLField(label="Sitio web o red social", required=False, max_length=200, assume_scheme="https")
    latitude = forms.FloatField(label="Latitud", min_value=-90, max_value=90)
    longitude = forms.FloatField(label="Longitud", min_value=-180, max_value=180)
    attends_emergencies = forms.BooleanField(label="Declara públicamente atender urgencias", required=False)
    services = forms.MultipleChoiceField(label="Servicios publicados", choices=Service.choices, required=False,
                                         widget=forms.CheckboxSelectMultiple)
    source_note = forms.CharField(
        label="Fuente de la información", max_length=200,
        help_text="Dónde se verificó el dato. Ej.: sitio web oficial de la clínica (consultado el 15-11-2026). "
                  "No copiar información de Google Maps: sus términos lo prohíben.")

    def clean_source_note(self):
        value = self.cleaned_data["source_note"].strip()
        if "google" in value.lower():
            raise ValidationError("Los términos de Google Maps no permiten copiar su información. "
                                  "Usa el sitio o red social del establecimiento, u OpenStreetMap.")
        return value


class DirectoryImportForm(forms.Form):
    file = forms.FileField(label="Archivo JSON exportado de Overpass (OpenStreetMap)",
                           help_text="Máximo 2 MB. Ver instrucciones en docs/DIRECTORIO_VETERINARIAS.md.")
    commune = forms.CharField(label="Comuna por defecto", max_length=60, initial="Chillán")

    def clean_file(self):
        f = self.cleaned_data["file"]
        if f.size > 2 * 1024 * 1024:
            raise ValidationError("El archivo supera 2 MB.")
        return f


class AffiliateClinicForm(forms.Form):
    """Afilia a la Red PetTech una veterinaria del directorio, creando su cuenta de acceso al portal."""

    account_name = forms.CharField(label="Nombre del responsable", max_length=150)
    account_email = forms.EmailField(label="Correo de acceso de la veterinaria")
    account_password = forms.CharField(label="Contraseña inicial", strip=False, widget=forms.PasswordInput,
                                       help_text="Mínimo 10 caracteres. Entrégala por un canal seguro.")
    account_password2 = forms.CharField(label="Repite la contraseña", strip=False, widget=forms.PasswordInput)

    clean_account_email = ClinicCreateForm.clean_account_email

    def clean(self):
        super().clean()
        _check_password(self, "account_password", "account_email", "account_name")
        return self.cleaned_data
