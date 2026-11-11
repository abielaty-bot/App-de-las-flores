# Las Flores Android — desarrollo 4.0002

App nativa Kotlin/Compose/Material 3 y Room 3/SQLite. Esta rama parte de 4.0001 instalada.

## Mejoras implementadas en la rama
- Versión visible 4.0002; versionCode 40002.
- Catálogo de requisitos editable: puestos, grupo, mínimo de unidades, activación. No requiere nuevo APK para cambios de configuración.
- Snapshots por inspección que congelan nombre, grupo y mínimo originalmente evaluados.
- Cantidad mínima comprobada cuando un requisito tiene mínimo distinto de cero.
- Registro de materiales: entregas, responsables, fecha, motivo, devoluciones y anulaciones.
- JSON nativo formato 2 con todos los módulos actuales y compatibilidad de lectura con formato 1.
- Migración Room 3 a 4 sin borrado de datos.

## Pendiente antes de distribución
CI completo en verde y pruebas de actualización v4.0001→v4.0002, respaldo/restauración y edición de criterios sin pérdida histórica.

## Compatibilidad
- applicationId pe.lasflores.controlinterno.beta sin cambiar.
- Tag prevista android-beta-v40002, una vez aprobada la publicación.
- Los JSON de la web todavía no se importan: sus módulos restantes deben migrarse de forma íntegra para no perder datos.

## Lo siguiente
4.0003 briefings; 4.0004 funciones; 4.0005 incidentes; 4.0006 Excel y migración web.
