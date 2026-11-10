package pe.lasflores.controlinterno.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** Sin token ni permisos de GitHub: solo releases publicados en un repositorio público. */
class GitHubUpdateRepository(private val context: Context) {
    private val releasesUrl =
        "https://api.github.com/repos/abielaty-bot/App-de-las-flores/releases?per_page=30"

    fun currentVersionCode(): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    suspend fun check(): AppRelease? = withContext(Dispatchers.IO) {
        val conn = (URL(releasesUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "LasFlores-Android-Updater")
        }
        try {
            check(conn.responseCode == 200) { "GitHub no respondió correctamente (${conn.responseCode})." }
            val text = conn.inputStream.bufferedReader().use { reader ->
                reader.readText().take(600_000)
            }
            val array = JSONArray(text)
            val available = buildList {
                for (i in 0 until array.length()) {
                    val release = array.getJSONObject(i)
                    if (release.optBoolean("draft")) continue
                    val version = UpdatePolicy.versionFromTag(release.optString("tag_name")) ?: continue
                    val assets = release.optJSONArray("assets") ?: continue
                    for (j in 0 until assets.length()) {
                        val asset = assets.getJSONObject(j)
                        if (asset.optString("name") != "las-flores-beta.apk") continue
                        val sha256 = UpdatePolicy.validSha256(asset.optString("digest")) ?: continue
                        val candidate = AppRelease(
                            versionCode = version,
                            title = release.optString("name").ifBlank { "Versión $version" }.take(100),
                            description = release.optString("body").take(1800),
                            apkUrl = asset.optString("browser_download_url"),
                            sha256 = sha256,
                            sizeBytes = asset.optLong("size", -1)
                        )
                        add(candidate)
                    }
                }
            }
            UpdatePolicy.latestCompatible(currentVersionCode(), available)
        } finally { conn.disconnect() }
    }

    /** Descarga temporal, SHA-256, versión, identificador y certificado ANTES de invocar Android. */
    suspend fun downloadAndVerify(release: AppRelease): File = withContext(Dispatchers.IO) {
        require(UpdatePolicy.isTrustedDownloadUrl(release.apkUrl)) { "Origen de descarga no permitido." }
        require(release.sizeBytes in 1..104_857_600L) { "Tamaño de APK no permitido." }
        val folder = File(context.cacheDir, "updates").apply { mkdirs() }
        val partial = File(folder, "las-flores-beta-staging.apk")
        val apk = File(folder, "las-flores-beta.apk")
        if (partial.exists()) partial.delete()
        val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 25_000
            setRequestProperty("User-Agent", "LasFlores-Android-Updater")
            instanceFollowRedirects = true
        }
        try {
            check(conn.responseCode == 200) { "No se pudo descargar la APK (${conn.responseCode})." }
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            conn.inputStream.use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= release.sizeBytes) { "Descarga mayor que el tamaño esperado." }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
            check(total == release.sizeBytes) { "La descarga está incompleta." }
            val received = digest.digest().joinToString("") { "%02x".format(it) }
            check(received.equals(release.sha256, ignoreCase = true)) { "Falló la verificación SHA-256." }
            verifyApkPackage(partial, release)
            if (apk.exists()) check(apk.delete()) { "No se pudo reemplazar la descarga anterior." }
            check(partial.renameTo(apk)) { "No se pudo preparar la instalación." }
            apk
        } catch (e: Exception) {
            Log.w("LasFloresUpdater", "La actualización fue rechazada", e)
            partial.delete()
            throw e
        } finally { conn.disconnect() }
    }

    @Suppress("DEPRECATION")
    private fun verifyApkPackage(apk: File, release: AppRelease) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
            else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: error("El archivo descargado no es una APK válida.")
        check(archive.packageName == context.packageName) { "La APK pertenece a otra aplicación." }
        val downloadedVersion = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode
            else archive.versionCode.toLong()
        check(downloadedVersion == release.versionCode && downloadedVersion > currentVersionCode()) {
            "La versión de la APK no coincide con la publicación."
        }
        val installed = pm.getPackageInfo(context.packageName, flags)
        val archiveCerts = if (Build.VERSION.SDK_INT >= 28) archive.signingInfo?.apkContentsSigners
            else archive.signatures
        val installedCerts = if (Build.VERSION.SDK_INT >= 28) installed.signingInfo?.apkContentsSigners
            else installed.signatures
        check(!archiveCerts.isNullOrEmpty() && !installedCerts.isNullOrEmpty()) {
            "No se pudo verificar el certificado de firma."
        }
        check(archiveCerts.map { it.toCharsString() }.toSet() ==
            installedCerts.map { it.toCharsString() }.toSet()) {
            "La firma de la nueva APK no coincide con la aplicación instalada."
        }
    }
}
