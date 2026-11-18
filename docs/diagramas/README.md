# Diagramas de PetTech (15-11-2026)

Generados a partir del código actual (no son bocetos). PNG para Word/PowerPoint; SVG para ampliar sin perder calidad.
Las fuentes (`fuentes/*.mmd`, Mermaid; `fuentes/*.dot`, Graphviz) permiten regenerarlos si cambia el sistema.

| N.º | Diagrama | Uso sugerido en el informe |
|---|---|---|
| ER | `er_resumen` / `er_completo` — modelo de datos (11 tablas) | Diseño de la base de datos / anexo diccionario |
| 01 | Arquitectura del sistema (monolito modular) | Arquitectura de software |
| 02 | Despliegue (Render, PostgreSQL, teléfono, GitHub) | Infraestructura / despliegue |
| 03 | Casos de uso por actor | Requisitos funcionales |
| 04 | Arquitectura Android MVVM | Diseño de la app |
| 05 | Mapa de navegación | Diseño de interfaz (boceto → app) |
| 06 | Secuencia: solicitud y autorización de acceso | Núcleo del proyecto (ficha compartida con permiso) |
| 07 | Secuencia: registro de atención con foto | Ficha estandarizada (SOAP) |
| 08 | Secuencia: escaneo del carnet (OCR) | Funcionalidad diferenciadora |
| 09 | Secuencia: recordatorios de vacunas | HU-06 |
| 10 | Estados de la solicitud de acceso | Reglas de negocio |
| 11 | Ciclo de vida de la veterinaria (directorio → Red PetTech) | Modelo de red / disponibilidad |
| 12 | Actividad: búsqueda de veterinarias | HU-07 a HU-10 |
| 13 | Clases del backend (modelos y servicios) | Diseño detallado |
| 14 | Control de acceso (seguridad) | Seguridad / Privacy by Design |

Regenerar: `npx -p @mermaid-js/mermaid-cli mmdc -i fuentes/06_seq_acceso.mmd -o 06_seq_acceso.png -s 2`
y `dot -Tpng fuentes/er_resumen.dot -o er_resumen.png`.
