# Hoja de ruta — Las Flores Android

## Publicadas (beta)
- 4.0001: Personal, programación y respaldos Android.
- 4.0002: Uniformes, requisitos configurables, materiales, historial y publicación automatizada.

## En desarrollo: 4.0003 — Briefings
- [x] Rama aislada desde la versión firmada 4.0002.
- [x] Base de datos Room v5 y migración no destructiva de 4 a 5.
- [x] Un registro por trabajador y fecha, con grupo 11:00/12:00.
- [x] Estados: Asistió, Atendiendo mesa, No asistió al briefing, Sin registrar.
- [x] Seguimiento de comunicación posterior con responsable e historial.
- [x] Temas e indicaciones por grupo y fecha.
- [x] Pendientes históricos, historial filtrable y estadísticas mensuales por persona.
- [x] Auditoría de cambios; sin falta automática para registros no marcados.
- [x] Respaldo JSON Android formato 3, compatible con importación de formatos 1 y 2.
- [ ] Validar CI completo, migración real en tablet, respaldo/restauración y grupos.
- [ ] Publicación 4.0003 firmada solo después de pruebas.

## Próximas etapas
- 4.0004: Funciones por oficina, evidencias y cumplimiento diario/mensual.
- 4.0005: Incidentes, soluciones y seguimiento.
- 4.0006: Reportes Excel y migración web completa.

## Reglas de seguridad
No desinstalar la app web ni sobrescribir los datos de producción durante la migración.
No convertir registros omitidos en faltas automáticamente.
La restauración completa reemplaza todos los módulos: advertir cuando el respaldo proviene de una versión anterior.
