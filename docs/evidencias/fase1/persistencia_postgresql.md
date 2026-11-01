# Evidencia — Persistencia en PostgreSQL/PostGIS (incremento 1)

Consulta directa a la base de desarrollo `pettech` después de `seed_demo` y de la prueba de humo (datos SIMULADOS).
Fecha: 2026-11-01T13:00:54-03:00

```
 public_code  |         name          | species |            tutor
--------------+-----------------------+---------+-----------------------------
 PT-K791-9PCS | Luna [DEMO]           | DOG     | tutora.demo@pettech.test
 PT-HW99-53MV | Michi [DEMO]          | CAT     | tutora.demo@pettech.test
 PT-CSDW-WWJQ | Rocky [DEMO]          | DOG     | tutor2.demo@pettech.test
 PT-19ZX-YZ0R | Luna Smoke [SIMULADO] | DOG     | smoke.a.cf21be@pettech.test
(4 rows)

 tokens_activos | largo_hash
----------------+------------
              2 |        128
(1 row)

 postgis_lib_version
---------------------
 3.4.2
(1 row)

```

- Los tokens se almacenan como hash de 128 caracteres hexadecimales (SHA-512), nunca en texto plano.
