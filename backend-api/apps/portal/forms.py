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
    """Información que la propia veterinaria mantiene al día."""

    phone = forms.CharField(label="Teléfono", max_length=20, required=False)
    attends_emergencies = forms.BooleanField(label="Atendemos urgencias", required=False)
    emergency_24h = forms.BooleanField(label="Atención 24 horas", required=False)
    services = forms.MultipleChoiceField(label="Servicios", choices=Service.choices, required=False,
                                         widget=forms.CheckboxSelectMultiple)


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
