# Baseline de rendimiento — 2026-08-19

Esta baseline resume dos ejecuciones idénticas. Las tablas muestran la media simple de ambos point
estimates para facilitar lectura; cada JSON conserva iteraciones, media, error JMH al 99,9% y
secondary metrics. No hay thresholds ni pass/fail de rendimiento.

## Insert end-to-end

| Filas | JPA default ms | JPA batch ms | JDBC batch ms | COPY ms | COPY filas/s |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 10 | 1,73 | 1,36 | 0,97 | 0,85 | 11.800 |
| 100 | 7,70 | 3,30 | 2,72 | 1,80 | 55.485 |
| 1.000 | 45,59 | 12,14 | 8,43 | 7,12 | 140.371 |
| 10.000 | 420,42 | 95,56 | 69,44 | 66,80 | 149.711 |
| 100.000 | 4.381,78 | 868,10 | 633,79 | 580,13 | 172.375 |

COPY fue más rápido que ambas variantes JPA en todos los tamaños de este dataset. Frente a JDBC
batch fue 3,8–33,7% más rápido según tamaño, pero desde 10K la separación es pequeña frente a la
variación de las corridas. El perfil único de 1M produjo 5.868,50 ms para JDBC (170.401 filas/s) y
5.653,41 ms para COPY (176.884 filas/s): diferencia de 3,7% y errores JMH amplios, por lo que se
consideran prácticamente empatados en este host.

Bytes asignados por operación a 100K: JPA default 531,1 MB, JPA batch 410,7 MB, JDBC 196,2 MB y
COPY 314,5 MB. COPY reduce tiempo pero no asignación frente a JDBC en la implementación actual.

## Batch size COPY a 100K

| Batch | ms/op | filas/s | bytes/op |
| ---: | ---: | ---: | ---: |
| 100 | 1.114,28 | 89.744 | 375,9 MB |
| 1.000 | 585,94 | 170.665 | 308,5 MB |
| 10.000 | 368,96 | 271.035 | 304,9 MB |
| 100.000 | 332,79 | 300.488 | 307,9 MB |

En este host los batches grandes redujeron round trips. All-in-one fue 43% más rápido que el
default 1.000 a 100K, pero implica una unidad COPY mayor y peor granularidad de fallo/autocommit;
no se cambia el default productivo a partir de una sola máquina.

## Lookup sobre target de 100K

| Keys | SQL IN ms | temp COPY JOIN ms | ganador observado |
| ---: | ---: | ---: | --- |
| 10 | 0,41 | 2,84 | SQL IN |
| 100 | 1,31 | 3,57 | SQL IN |
| 1.000 | 13,38 | 10,08 | temp COPY JOIN |
| 10.000 | 38,45 | 51,20 | SQL IN |

No apareció un crossover monotónico: la temporal ganó a 1K en ambas corridas, pero perdió a 10K.
Esto justifica un experimento secundario futuro con planes, índice temporal y `ANALYZE`; no
justifica hoy selección adaptativa ni tuning productivo. La estrategia temporal sigue aportando
la semántica de keys masivas sin límites de parámetros, aunque no sea siempre la más rápida.

## Overhead de observabilidad

| Filas | disabled ms | enabled ms | delta point estimate |
| ---: | ---: | ---: | ---: |
| 100 | 1,44 | 1,75 | +21,6% |
| 1.000 | 7,05 | 7,46 | +5,9% |

Los intervalos se solapan y los casos pequeños son ruidosos. El incremento de asignación fue
aproximadamente 5,3 KB a 100 filas y estuvo dentro del ruido a 1K. Se conserva enabled por default;
esta evidencia no prueba un coste universal.

## Estabilidad y límites

Entre las 36 combinaciones, la mediana del cambio absoluto entre corridas fue 8,7%. Para los 22
casos de al menos 1K fue 6,1% (media 7,8%); el peor fue observabilidad disabled a 1K, 26,4%. En
casos sub-milisegundo/pequeños el peor delta fue 57,2%. Se requieren más forks, host aislado y
series históricas antes de detectar regresiones pequeñas.

Limitaciones: una máquina, PostgreSQL 15, cliente y servidor co-localizados, una hebra, tabla de
100K para lookup, todas las keys existentes, metadata caliente, sin índice/ANALYZE temporal, sin
red remota, sin competencia y sin JPA a 1M. CPU/heap total no se perfiló; `-prof gc` reporta sólo
asignación de la JVM benchmark. No se derivan claims universales.

## Evidencia

- [Raw baseline 1](raw/baseline-run-1.json) y [CSV](baseline-run-1.csv).
- [Raw baseline 2](raw/baseline-run-2.json) y [CSV](baseline-run-2.csv).
- [Raw 1M](raw/large-1m.json) y [CSV](large-1m.csv).
- [Metodología y reproducción](methodology.md).
