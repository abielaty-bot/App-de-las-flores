# Centro de migración Las Flores — primera etapa

## Alcance
Flujo prioritario **Web → Android 4.0011**. Todo el proceso es local, sin
servidor, sin subir datos a GitHub y sin modificar la base Web original.

1. Desde **Web → Configuración → Centro de migración**, selecciona el
   respaldo Web existente o analiza los datos actuales.
2. Si Android ya tiene registros, exporta primero su JSON de respaldo
   formato 5 desde la tablet y selecciónalo también en Web para intentar
   **fusión estricta**. Los conflictos bloquean la descarga; no se sobreescriben
   registros contradictorios silenciosamente.
3. Si Android está vacío, utiliza **modo reemplazo**. No lo uses sobre
   registros Android existentes sin confirmar que tienes un respaldo externo.
4. Descarga `Las_Flores_Transferencia_Android_AAAA-MM-DD.json`. Mantén también
   el respaldo Web antiguo original.
5. En **Android → Respaldos → Centro de migración**, selecciona ese archivo,
   revisa el recuento, confirma el reemplazo de datos cuando proceda.
6. Exporta un **respaldo Android nuevo**; verifica que incluya
   `webSourceArchive`, abre Personal, Briefings, Uniformes, Funciones,
   Incidentes y compara contra el respaldo Web original.

## Seguridad y conservación
- Los datos Web originales se incluyen como `webSourceArchive` dentro del
  JSON convertido y, una vez restaurado, en los respaldos nativos siguientes.
- No es sincronización automática: los cambios futuros en un dispositivo
  no aparecen en el otro sin repetir la transferencia.
- Se comprueban IDs, relaciones, fechas, duplicados y cambios incompatibles.
- Las inspecciones observadas sin todos los requisitos evaluados pueden
  estar bloqueadas: Android exige un cierre explícito y no es seguro inventarlo.
- Las anotaciones históricas sin campo nativo equivalente quedan en el
  original archivado; **no se convierten automáticamente a métricas Android**.
- La fusión es **conservadora** y puede rechazar casos resolubles por un humano,
  como catálogos de funciones que comparten nombre pero tienen IDs diferentes.
- El archivo original debe conservarse incluso después de importar.
- No borrar ni desinstalar la web hasta verificar con datos reales.
- La opción Android → Web aún está pendiente: la web no admite todas las
  fotos y auditorías nativas. Nunca se descartan esos datos para fingir
  compatibilidad bidireccional.

## Límites
- Fuente Web: hasta 15 MB; archivo final Android: hasta 30 MB.
- Convertidor JSON sin soporte de archivos XLSX como origen de datos.
- No publicar ni fusionar automáticamente sin pruebas de integración,
  importación real y comprobación en tablet.

## Pruebas
`node --test tests/migration.test.cjs`. Android utiliza CI de Gradle con
compilación de APK y pruebas unitarias; sigue siendo necesario comprobar
un respaldo real exportado desde la web antes de retirar esa versión.
