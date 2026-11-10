# Las Flores Android — Fase 2: Uniformes

Proyecto **nativo Android** independiente de la PWA actual. Estado: **prototipo de arquitectura**, no aplicación lista para uso productivo.

## Alcance implementado
- Proyecto Kotlin + Jetpack Compose + Material 3.
- Room 3.0.3 / SQLite con almacenamiento local y observación reactiva.
- Inicio, listado de personal, alta de trabajador, cambio de puesto y activación/desactivación.
- Guardado automático después de cada cambio confirmado, con estado de guardado visible.
- Pruebas unitarias de validación de nombres.
- GitHub Actions para compilación de APK **debug** (sin firma de distribución).

## En desarrollo
- Uniformes: borradores, cierre inicial, corrección y rectificación auditada, estado diario sin revisión.
- Migración no destructiva de Room 1 a Room 2.

## NO implementado aún
- Importación del JSON de la aplicación web.
- Briefings, Funciones, Incidentes, Reportes completos.
- Respaldos JSON y Excel, migraciones de esquemas posteriores.
- Actualizador GitHub Releases y APK de producción firmada.
- Cuentas de usuarios, servidor o sincronización.

**No introduzcas datos operativos reales en esta versión.** No tiene mecanismo de respaldo/restauración todavía.

## Compilar
1. Instalar Android Studio actualizado, Android SDK API 36 y JDK 17 o posterior.
2. Abrir la carpeta `android-native` en Android Studio.
3. Sincronizar Gradle. Este paquete incluye `gradle-wrapper.properties`; para obtener los archivos binarios del Wrapper, ejecutar una vez `gradle wrapper --gradle-version 9.3.1` con Gradle instalado, o abrir el proyecto con Android Studio y configurar el Wrapper desde él. El CI usa Gradle directamente.
4. Compilar `:app:assembleDebug` y ejecutar `:app:testDebugUnitTest`.
5. Usar una tablet Android 8 o superior en pruebas.

## Arquitectura
- `data`: entidad, DAO, base de datos, repositorio local.
- `domain`: contrato del repositorio y validación de nombres/roles.
- `ui`: ViewModel y pantallas Compose.
- `LasFloresApp`: composición de dependencias local (se considerará Hilt cuando la complejidad lo justifique).

La aplicación original en GitHub Pages y sus datos locales siguen intactos.

## Seguridad y versiones
- Identificador provisional `pe.lasflores.controlinterno`; se definirá definitivamente antes del primer APK de producción.
- `versionCode=2`, `versionName=0.2.0-alpha02`. La APK debug usa el sufijo `.preview` para coexistir con la versión ya instalada sin borrar sus datos.
- No hay claves privadas ni datos personales en el repositorio.
- Nunca aplicar migraciones destructivas en datos reales.

## Dependencias
AGP 9.1.1, Gradle 9.3.1, Compose Compiler 2.2.10, KSP 2.3.12, Room 3.0.3.
Documentación: https://developer.android.com/jetpack/androidx/releases/room3 y https://developer.android.com/build/releases/agp-9-1-0-release-notes
