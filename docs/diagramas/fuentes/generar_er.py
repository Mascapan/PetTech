from django.apps import apps
from django.db import models
import html
APPS = {"accounts": ("Cuentas", "#e0f2f1"), "pets": ("Mascotas", "#fff1cc"), "clinics": ("Veterinarias", "#ddebfa"),
        "access_control": ("Control de acceso y bitácora", "#fde2df"), "clinical_records": ("Ficha clínica", "#ddf3e4")}
TYPE = {"UUIDField": "uuid", "CharField": "varchar", "TextField": "text", "EmailField": "varchar", "URLField": "varchar",
        "BooleanField": "bool", "DateField": "date", "DateTimeField": "timestamptz", "TimeField": "time", "DecimalField": "decimal",
        "PositiveSmallIntegerField": "smallint", "PositiveIntegerField": "int", "IntegerField": "int", "BigAutoField": "bigint",
        "AutoField": "int", "JSONField": "jsonb", "BinaryField": "bytea", "PointField": "geography(Point)", "ForeignKey": "fk",
        "OneToOneField": "fk"}
def label(m, full=True):
    rows = []
    for f in m._meta.concrete_fields:
        if not full and not (f.primary_key or f.is_relation):
            continue
        t = TYPE.get(f.get_internal_type(), f.get_internal_type())
        if f.is_relation:
            t = "→ " + f.related_model.__name__
        key = "PK" if f.primary_key else ("FK" if f.is_relation else ("UQ" if f.unique else ""))
        name = f.column if not f.is_relation else f.name + "_id"
        style = "<B>%s</B>" % name if key == "PK" else name
        rows.append('<TR><TD ALIGN="LEFT" WIDTH="22"><FONT COLOR="#9b2c20" POINT-SIZE="9">%s</FONT></TD><TD ALIGN="LEFT">%s</TD><TD ALIGN="LEFT"><FONT COLOR="#4a5e5c" POINT-SIZE="9">%s</FONT></TD></TR>' % (key or " ", style, html.escape(t)))
    if not full:
        rows.append('<TR><TD COLSPAN="3"><FONT COLOR="#4a5e5c" POINT-SIZE="8">+ %d campos</FONT></TD></TR>' % (len(m._meta.concrete_fields) - len(rows)))
    return '<<TABLE BORDER="0" CELLBORDER="0" CELLSPACING="0" CELLPADDING="3" BGCOLOR="white"><TR><TD COLSPAN="3" BGCOLOR="#00695c"><FONT COLOR="white"><B>%s</B></FONT></TD></TR><TR><TD COLSPAN="3"><FONT POINT-SIZE="8" COLOR="#4a5e5c">%s</FONT></TD></TR>%s</TABLE>>' % (m.__name__, m._meta.db_table, "".join(rows))
def dot(full):
    out = ['digraph ER {', 'graph [rankdir=LR, splines=spline, nodesep=0.5, ranksep=1.0, fontname="Helvetica", bgcolor="white", label="PetTech — Modelo de datos (PostgreSQL 16 + PostGIS)%s", labelloc=t, fontsize=20];' % ("" if full else " · vista resumida"),
           'node [shape=plain, fontname="Helvetica", fontsize=10];', 'edge [fontname="Helvetica", fontsize=9, color="#4a5e5c", arrowhead=crow, arrowtail=tee, dir=both];']
    ms = [m for m in apps.get_models() if m._meta.app_label in APPS]
    for app, (title, color) in APPS.items():
        out.append('subgraph cluster_%s { label="%s"; style="rounded,filled"; fillcolor="%s"; color="#b2dfdb"; fontsize=13;' % (app, title, color))
        for m in ms:
            if m._meta.app_label == app:
                out.append('%s [label=%s];' % (m.__name__, label(m, full)))
        out.append('}')
    for m in ms:
        for f in m._meta.concrete_fields:
            if f.is_relation and f.related_model in ms:
                one = isinstance(f, models.OneToOneField)
                out.append('%s -> %s [label="%s", arrowhead=%s];' % (f.related_model.__name__, m.__name__, f.name, "teeodot" if one and f.null else ("tee" if one else ("crowodot" if f.null else "crow"))))
    out.append('}')
    return "\n".join(out)
open("/home/claude/er/er_completo.dot", "w").write(dot(True))
open("/home/claude/er/er_resumen.dot", "w").write(dot(False))
