# Las Flores Android — versión 4.0001 en desarrollo

Aplicación nativa con Kotlin, Jetpack Compose, Material 3 y Room 3/SQLite.

## Funciones de la rama
- Personal: lista única, categoría, puesto, grupo de briefing, descanso semanal, apoyo de fin de semana, vacaciones y situación diaria.
- Uniformes: inspección por puesto, borradores, cierre, correcciones y auditoría; solo trabajadores programados.
- Respaldos: descarga JSON de todas las tablas Android actuales, selector de archivo, validación, vista previa y restauración transaccional; se conserva copia interna previa.
- Actualizaciones: comprobación desde GitHub, descarga, verificación de SHA-256, paquete, versión y certificado; Android confirma la instalación.
- Guardado local para uso sin conexión.

## Límites explícitos
- Los JSON de la versión web se rechazan por el momento: no existen todavía tablas compatibles con todos sus módulos. Esto impide perder briefings, funciones, incidentes y materiales.
- El respaldo Android no sustituye la prueba de restauración en una tablet física.
- Briefings, Funciones, Incidentes, entregas de materiales y Excel están pendientes.
- No se incluyen cuentas multiusuario ni sincronización.

## Compatibilidad desde Beta v4
- versionName: 4.0001
- versionCode: 40001
- Tag de publicación: android-beta-v40001 (compatible con el actualizador Beta v4 instalado).
- applicationId: pe.lasflores.controlinterno.beta; conservar la firma original.
- Room v2 -> v3 mediante MIGRATION_2_3 sin borrar tablas.
- Antes de publicar: crear datos de prueba en la Beta v4, actualizar desde dentro y comprobar que se conservan.

## Desarrollo y publicación
El CI ejecuta pruebas y compilaciones secuenciales por los conflictos previos entre KSP y Room. Las APK de distribución requieren la clave firmante configurada como secreto en GitHub; nunca guardarla en este repositorio.
