# Las Flores Android — evolución

## Publicadas
- 4.0001 Personal, programación y respaldos.
- 4.0002 Uniformes, control de implementos, catálogo, historial.
- 4.0003 Briefings con asistencia, grupos, temas y seguimiento.
- 4.0004 Funciones diarias y mensuales, fotos y auditoría.

## 4.0005 — Incidentes (en desarrollo)
- [x] Room 6→7: nuevas tablas sin alterar registros existentes.
- [x] Tipos de incidentes configurables (alimentos/bebidas, operaciones/servicio).
- [x] Registros INC-AÑO-NNNN con prioridad, fecha, hora, impacto y responsable.
- [x] Diferenciar área de detección, origen probable y origen confirmado.
- [x] Medida inmediata; seguimiento por acción, responsable y estado.
- [x] Cierre con verificación obligatoria, reapertura y anulación auditable.
- [x] Filtros históricos por área, estado, categoría, fechas y texto.
- [x] Estadísticas mensuales por área detectada y tipo, con anulados excluidos.
- [x] Respaldo Android v5 de tipos, incidentes y acciones. Compatible v1-v4.
- [x] Navegación inferior desplazable para más módulos.
- [ ] CI completo en verde y prueba en tablet.
- [ ] Verificar copia/restauración y continuidad de Funciones, Uniformes, Briefings y Personal.

## Próxima etapa: 4.0006 — Reportes y migración
- Excel individual y consolidado diario/semanal/mensual.
- Migración de JSON de la web solo cuando todos los módulos estén representados.

## Criterios
- El área donde se detecta un incidente no es automáticamente el origen.
- Nunca atribuir responsabilidad disciplinaria automáticamente.
- Los anulados y las acciones quedan en historial.
- La APK solo se publica después de pruebas automatizadas y revisión.
