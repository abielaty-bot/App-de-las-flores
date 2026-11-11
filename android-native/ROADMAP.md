# Hoja de ruta — Las Flores Android

## En desarrollo: versión visible 4.0001 (código Android 40001)
- [x] Rama aislada desde Beta v4 para proteger el canal instalable.
- [x] Migración Room 2 a 3, sin eliminar datos existentes.
- [x] Personal: puestos, categorías, grupos de briefing, descansos, apoyos de fin de semana, vacaciones y excepciones por fecha.
- [x] Pantalla de Personal y exclusión de trabajadores no programados en Uniformes.
- [x] Exportación JSON de las tablas Android actuales; restauración transaccional con validación y copia interna previa.
- [x] Nombre visible 4.0001 y etiqueta compatible android-beta-v40001.
- [ ] Compilación CI de la rama completa y pruebas de migración en tablet.
- [ ] Restauración real y actualización Beta v4 a 4.0001 comprobadas.

## Versiones planificadas
- 4.0002: Uniformes e implementos completos, requisitos editables y entrega de materiales.
- 4.0003: Briefings a las 11:00 y 12:00, asistencia e historial.
- 4.0004: Funciones por oficina, correcciones, evidencias y control mensual.
- 4.0005: Incidentes, acciones y cierre.
- 4.0006: Reportes Excel, estadísticas y migración completa de respaldos de la web.

## Política de seguridad
- La aplicación web permanece disponible hasta verificar la migración.
- No importar respaldos web de forma parcial ni perder historiales silenciosamente.
- La clave de firma debe permanecer fuera del repositorio.
- Las migraciones son explícitas y nunca destructivas.
- No publicar una versión sin pruebas de compilación, respaldo y actualización.
