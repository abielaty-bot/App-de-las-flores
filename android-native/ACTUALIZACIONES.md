# Actualizador de Las Flores Android (canal Beta)

## Qué hace la aplicación

- En **Actualizaciones**, consulta los releases públicos de `abielaty-bot/App-de-las-flores` sin introducir tokens GitHub en Android.
- Solo acepta tags `android-beta-vN` con un archivo exacto `las-flores-beta.apk` y un SHA-256 proporcionado por GitHub como `asset.digest`.
- Selecciona la versión más reciente cuyo código sea mayor al instalado.
- Descarga la APK a la caché privada; comprueba bytes, SHA-256, `applicationId`, `versionCode` y certificado Android.
- Abre el instalador del sistema mediante `FileProvider`. Android puede pedir autorizar la aplicación como fuente de instalación; nunca se instala silenciosamente.

## Primer paso obligatorio: firma de distribución

La Beta de desarrollo instalada anteriormente (`pe.lasflores.controlinterno.preview`) fue firmada como **debug** y puede cambiar su certificado. **No se puede actualizar directamente a una APK firmada con otra clave**. Para evitar pérdida de datos, el canal firmado utiliza el ID independiente `pe.lasflores.controlinterno.beta` (visible como «Las Flores Actualizable»). Se instala junto a la Beta antigua; no se copian automáticamente sus registros.

La clave de firma es privada. La crea y conserva el propietario del proyecto; **nunca se sube al repositorio ni se comparte por chat**. Se pueden generar claves con Android Studio («Build > Generate Signed App Bundle or APK») o con `keytool`. Para usar el workflow de GitHub Actions, configura los siguientes *Repository secrets*:

- `LF_KEYSTORE_BASE64`: contenido del keystore convertido a base64 en una única cadena.
- `LF_KEYSTORE_PASSWORD`: contraseña del almacén de claves.
- `LF_KEY_ALIAS`: alias de la clave.
- `LF_KEY_PASSWORD`: contraseña de la clave.

Mantén una copia offline segura del archivo `.jks` y sus credenciales. Perderla impide publicar actualizaciones firmadas con la misma identidad.

## Publicación

1. Confirma `versionCode` y `versionName` en `app/build.gradle.kts`. Por ejemplo `versionCode=3` corresponde a `android-beta-v3`.
2. Configura los secretos del repositorio desde GitHub (solo propietario o administrador autorizado).
3. Crea y envía un tag `android-beta-v3` apuntando al commit revisado en la rama Android; esto ejecuta `.github/workflows/android-beta-release.yml`.
4. El workflow compila, comprueba la firma y publica el APK directo en GitHub Releases.
5. Instala **una sola vez** la primera versión firmada desde ese Release. Para posteriores versiones, el botón «Actualizar» dentro de la app detectará y preparará la APK.

No publiques releases con APK de depuración o sin firmar. No instales una APK de otro canal sobre una que tenga datos. No borramos bases de datos automáticamente.

## Restricciones y verificaciones pendientes

- El workflow de firma no puede publicar el primer release hasta configurar los secretos privados.
- La función depende de internet para consultar y descargar, pero **no** para el registro operativo local.
- Las API públicas de GitHub pueden aplicar límites de consultas.
- Deben probarse en una tablet real la solicitud de permiso, el instalador, la firma y una actualización de `android-beta-v3` a `android-beta-v4` con datos preexistentes.
- El sistema de copias de seguridad recuperables todavía es una fase pendiente.
