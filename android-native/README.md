# Las Flores Android 4.0007 — Reportes y entregas

La aplicación sigue siendo offline-first: Kotlin/Jetpack Compose, Room 7 y canal firmado.

## Reportes Excel
- 19 hojas en un solo archivo XLSX: nueve de 4.0006 más Implementos,
  Pendientes uniforme, Rectificaciones, Auditoría uniformes, Entregas,
  Entregas por trabajador, Programación, Faltas por función,
  Incidentes por área e Incidentes por tipo.
- Diario, semanal y mensual; filtro opcional por trabajador.
- Entregas en el período son movimientos, mientras que el saldo vigente
  refleja únicamente lotes actuales no devueltos y no anulados.
- Programación del personal reconstruida con la configuración actual.
  **No constituye asistencia laboral histórica**.
- Sin red ni servicios de pago. Excel no incluye fotografías en binario;
  estas siguen protegidas por el respaldo JSON.

## Entregas de materiales
- Fecha explícita, cantidad, motivo y persona que entregó.
- Historial filtrable por persona/material/estado/rango de fechas.
- Aviso previo de la misma entrega en los 30 días anteriores, incluidas
  devoluciones: advierte de una reposición, no acredita mal uso.
- Balance vigente por persona y material. Una devolución cierra el lote
  completo, no una devolución parcial.
- Historial de devoluciones y anulaciones sin eliminación.

## Persistencia y seguridad
- Room permanece en versión 7; no hay migración destructiva.
- Mismo package y firma que 4.0006; versionCode 40007.
- JSON Android sigue en formato 5, con todos los módulos existentes.
- No importar aún archivos JSON de la web: formatos incompatibles.
- La firma y compilación no sustituyen pruebas en tablet.

## Verificación necesaria
- CI unit tests, compilación de debug y release en verde.
- Exportar y abrir 19 hojas en Excel/Google Sheets.
- Comprobar los saldos de materiales y la advertencia de 30 días.
- Confirmar que Personal, Uniformes, Briefings, Funciones e Incidentes
  mantienen todos sus registros después de actualizar.
