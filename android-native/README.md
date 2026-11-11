# Las Flores Android 4.0005 — Incidentes

Aplicación Android Kotlin + Jetpack Compose + Room/SQLite, local-first.

## Módulo Incidentes
- Productos (alimentos y bebidas) y operativos (servicio, POS, coordinación).
- Tipos editables con catálogo inicial equivalente al módulo web.
- Fecha/hora, código anual secuencial, área donde se detectó, origen probable,
  origen confirmado después de verificación, prioridad, impacto, hechos y medida inmediata.
- Responsable del seguimiento vinculado a Personal.
- Estados registrado/en atención/pendiente de verificación/cerrado/anulado.
- Acciones trazables: corrección, comunicación, investigación, prevención y
  verificación; cierre solo tras verificación, reapertura y anulación justificadas.
- Histórico, filtros, estadísticas mensuales por área de detección y tipo.
- Cualquier tipo editado mantiene la etiqueta histórica de los casos anteriores.

## Persistencia
- Room v7, migración no destructiva 6→7.
- JSON Android formato 5: incidentes, tipos, acciones y módulos anteriores;
  restauración de versiones 1–4 con advertencia.
- La restauración completa reemplaza registros en todos los módulos, guarda
  copia interna previa y se ejecuta en una transacción.
- El respaldo de la web no se importa parcialmente.

## Pendiente de pruebas
- Compilación, tests unitarios, release firmada y restauración en tablet.
- Exportación Excel en la próxima versión 4.0006.
