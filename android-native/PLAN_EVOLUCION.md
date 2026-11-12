# Plan de evolución Las Flores Android

Base publicada: 4.0012. Desarrollar y verificar por entregas. Ninguna entrega debe borrar registros ni requerir desinstalación.

## Entrega 1 — Briefings y datos (4.0013, candidata)
- [x] Calendario directo, flechas, Hoy, fecha compartida en las cuatro secciones.
- [x] Sesiones variables, hora programada y real, participantes explícitos, estados, día sin briefing.
- [x] Comunicación presencial y delegación con confirmación; historial y auditoría.
- [x] Room 7→8 y respaldo v6; restauración v1–v5, conversión histórica sin inventar horas reales.
- [x] Reportes con sesiones, horas, seguimiento, cancelaciones excluidas de métricas.
- [ ] CI y pruebas de migración con SQLite, restauración en tablet, revisión visual y publicación firmada.

## Entrega 2 — Recordatorios y notificaciones
- [ ] Preferencias de jornada, descanso y horario silencioso, recordatorios personalizados (únicos/recurrentes).
- [ ] Permisos Android, canales, alarmas puntuales, reprogramación por cambios/reinicio, modo degradado si no hay permiso exacto.
- [ ] Avisos vinculados a sesiones, preparación/realización, seguimiento presencial, posponer, abrir módulo y fecha, no duplicar.
- [ ] Notificación de prueba; persistencia y respaldos.

## Entrega 3 — Mi jornada y pendientes
- [ ] Inicio con próxima actividad y estado operativo, campana, historial de avisos.
- [ ] Uniformes, funciones, incidentes, pendientes de informar, tareas manuales de abastecimiento y reportes/respaldos.
- [ ] Completar por evidencia del módulo; descartar aviso no completa tarea; prioridades y filtros.

## Entrega 4 — Centro de actualizaciones
- [ ] Bytes reales, porcentaje, velocidad, tiempo transcurrido y estimado; tamaño y fecha de publicación.
- [ ] Descarga en segundo plano con notificación, cancelación/reintento, reanudación solo si es segura.
- [ ] Espacio, SHA-256, paquete/versión/firma, instalación Android, historial y novedades, errores copiables.

## Entrega 5 — Pruebas integrales
- [ ] Compatibilidad de respaldos, actualización firmada sobre versión instalada, pantalla apagada y reinicio, ambos temas, tablet vertical.
- [ ] Verificar regresiones en Personal, Uniformes, Funciones, Incidentes y Reportes.

**Decisiones de compatibilidad:** las sesiones históricas conservan fecha e identificador del grupo y se etiquetan como históricas; la convocatoria antigua solo puede reconstruirse de los trabajadores registrados. Las sesiones antiguas sin hora real no reciben una hora real inventada. El respaldo v6 no se puede restaurar en versiones Android antiguas. La web de migración debe comprobar compatibilidad v6 antes de permitir una nueva fusión, nunca descartar campos nuevos.
