# Hoja de ruta — Las Flores Android

## Instalada y comprobada: 4.0001
- Actualización Beta v4 → 4.0001 desde la aplicación en tablet.
- Conservación del trabajador de prueba y de una inspección 9/9 observada comprobada mediante capturas.
- Respaldo y restauración Android efectuados; aún se requiere una prueba sistemática de regresión sobre el conjunto completo de registros.

## Rama de desarrollo: 4.0002 — Uniformes e implementos
- [x] Room v3 → v4 no destructiva.
- [x] Requisitos configurables por puesto, grupo, cantidad y activación.
- [x] Registro inmutable de requisitos por inspección, para no alterar históricos al editar criterios.
- [x] Comprobación de cantidades mínimas (ej. lapiceros).
- [x] Entregas, devoluciones y anulaciones de materiales con responsable e historial.
- [x] Pantallas nativas de inspección, configuración de requisitos y materiales.
- [x] JSON Android versión 2: guarda catálogo, snapshots e historial de materiales; acepta JSON versión 1.
- [ ] CI completo de la rama; pruebas reales de migración v3 a v4.
- [ ] Prueba de copias JSON v1/v2, devoluciones y continuidad de inspecciones en tablet.

## Etapas siguientes, sin iniciar aún
- 4.0003 — Briefings: asistencia presencial, información posterior, temas, histórico y estadísticas.
- 4.0004 — Funciones por oficinas, correcciones, evidencias y análisis individual.
- 4.0005 — Incidentes: alimentos, bebidas, atención, causas, acciones y cierre.
- 4.0006 — Excel/estadísticas y migración total de respaldos de la web.

## Criterios de publicación
No fusionar ni publicar sin CI y pruebas en dispositivo. La web no se desinstala ni se importan respaldos incompletos. Se mantiene paquete y firma de actualización del canal Android.
