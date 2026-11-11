# Las Flores Android 4.0003 — Briefings

Aplicación nativa offline-first: Kotlin, Compose, Material 3, Room/SQLite.

### Módulo Briefings
- Grupos de 11:00 y 12:00 vinculados a la ficha de Personal.
- Personal programado, apoyos y trabajadores sin grupo fijo.
- Confirmaciones explícitas: Asistió, Atendiendo mesa, No asistió al briefing.
- Atendiendo mesa no se marca como falta; posteriormente se registra quién comunicó las indicaciones.
- Temas del día independientes por fecha y grupo, máximo 3000 caracteres.
- Pendientes de informar de todas las fechas; historial por nombre y fechas; estadísticas mensuales por trabajador.
- Eventos de auditoría para correcciones de asistencia, información posterior y temas.

### Compatibilidad y migraciones
- versionName 4.0003, versionCode 40003, applicationId pe.lasflores.controlinterno.beta.
- Room 4→5 crea nuevas tablas sin borrar Personal, Uniformes ni Materiales.
- JSON Android v3 amplía el respaldo con asistencia, temas y auditoría de Briefings.
- Se admiten respaldos Android v1/v2, pero al restaurar uno antiguo, los Briefings actuales se reemplazarán por registros vacíos. La pantalla avisa antes de confirmar.
- Los respaldos de la web aún no se importan para evitar pérdidas parciales.

### No disponible todavía
- Funciones, Incidentes, Reportes Excel y sincronización de varios perfiles.
- No publicar 4.0003 hasta completar las pruebas de CI y dispositivo.
