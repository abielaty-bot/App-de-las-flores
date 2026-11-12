package pe.lasflores.controlinterno.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import java.io.IOException
import java.io.RandomAccessFile
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
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
                            sizeBytes = asset.optLong("size", -1),
                            publishedAt = release.optString("published_at").take(40)
                        )
                        add(candidate)
                    }
                }
            }
            UpdatePolicy.latestCompatible(currentVersionCode(), available)
        } finally { conn.disconnect() }
    }

    private fun folder():File = File(context.cacheDir,"updates").apply{
        check(isDirectory || mkdirs()) { "No se pudo crear la carpeta de actualizaciones." }
    }
    private fun stem(release:AppRelease) = "lf-"+release.versionCode+"-"+release.sha256.lowercase()
    fun partialFile(release:AppRelease)=File(folder(),stem(release)+".part")
    fun verifiedFile(release:AppRelease)=File(folder(),stem(release)+".apk")
    fun partialBytes(release:AppRelease):Long = runCatching{
        partialFile(release).length().coerceIn(0,release.sizeBytes)
    }.getOrDefault(0)
    fun discardPartial(release:AppRelease){runCatching{partialFile(release).delete()}}
    private fun checkRelease(release:AppRelease) {
        require(UpdatePolicy.latestCompatible(currentVersionCode(),listOf(release))==release) {
            "La publicación no es compatible con esta aplicación."
        }
    }

    /** Solo se reanuda el mismo archivo (versión y SHA-256). Una respuesta 200 reinicia;
     * 206 exige un Content-Range exacto. Ninguna APK parcial llega al instalador. */
    suspend fun downloadAndVerify(release:AppRelease,
        onProgress:(DownloadProgress)->Unit={},onPhase:(UpdatePhase)->Unit={}):File = withContext(Dispatchers.IO) {
        checkRelease(release)
        val partial=partialFile(release)
        val apk=verifiedFile(release)
        if(partial.length()>release.sizeBytes)partial.delete()
        val remaining=(release.sizeBytes-partial.length()).coerceAtLeast(0L)
        check(folder().usableSpace>=remaining+2_097_152L) { "No hay espacio suficiente. Libera almacenamiento y vuelve a intentar." }
        // Elimina descargas de versiones diferentes; nunca elimina información de Room.
        folder().listFiles()?.filter{it.name.startsWith("lf-") && it!=partial && it!=apk}
            ?.forEach{it.delete()}
        val meter=DownloadMeter(release.sizeBytes,SystemClock.elapsedRealtime())
        onPhase(UpdatePhase.DOWNLOADING)
        var offset=partial.length()
        onProgress(meter.sample(offset,SystemClock.elapsedRealtime(),true))
        if(offset<release.sizeBytes){
            val conn=(URL(release.apkUrl).openConnection() as HttpURLConnection).apply{
                connectTimeout=15_000;readTimeout=15_000
                setRequestProperty("User-Agent","LasFlores-Android-Updater")
                setRequestProperty("Accept-Encoding","identity")
                if(offset>0)setRequestProperty("Range","bytes="+offset+"-")
                instanceFollowRedirects=true
            }
            try{
                currentCoroutineContext().ensureActive()
                val response=conn.responseCode
                check(UpdatePolicy.isTrustedResponseUrl(conn.url.toString())){"Destino de descarga no permitido."}
                when(response){
                    200->{offset=0;onProgress(meter.sample(0,SystemClock.elapsedRealtime(),true))}
                    206->{
                        check(offset>0 && UpdatePolicy.validContentRange(conn.getHeaderField("Content-Range"),offset,release.sizeBytes)){
                            "El servidor no confirmó la parte solicitada. Cancela y descarga de nuevo."
                        }
                    }
                    416->{partial.delete();throw IOException("La parte anterior ya no está disponible. Reintenta desde cero.")}
                    else->throw IOException("No se pudo descargar la APK (HTTP "+response+").")
                }
                val expected=release.sizeBytes-offset
                val contentLength=conn.getHeaderField("Content-Length")?.toLongOrNull()
                check(contentLength==null||contentLength==expected){"El tamaño anunciado no coincide."}
                RandomAccessFile(partial,"rw").use{output->
                    output.setLength(offset);output.seek(offset)
                    conn.inputStream.use{input->
                        val buffer=ByteArray(64*1024)
                        var total=offset
                        var last=SystemClock.elapsedRealtime()
                        while(true){
                            currentCoroutineContext().ensureActive()
                            val count=input.read(buffer)
                            if(count<0)break
                            check(total+count<=release.sizeBytes){"Descarga mayor que el tamaño esperado."}
                            output.write(buffer,0,count);total+=count
                            val now=SystemClock.elapsedRealtime()
                            if(now-last>=350 || total==release.sizeBytes){
                                onProgress(meter.sample(total,now));last=now
                            }
                        }
                        currentCoroutineContext().ensureActive()
                        if(total!=release.sizeBytes)throw IOException("Descarga interrumpida. Puedes reintentar.")
                        output.fd.sync()
                        onProgress(meter.sample(total,SystemClock.elapsedRealtime()))
                    }
                }
            }catch(e:CancellationException){throw e}
            catch(e:Exception){
                // Un error de transporte conserva la parte para reintentar; un tamaño/rango
                // imposible se descarta para evitar concatenar datos inconsistentes.
                if(e !is IOException)partial.delete()
                throw e
            }finally{conn.disconnect()}
        }
        try{
            onPhase(UpdatePhase.VERIFYING_HASH)
            verifyHash(partial,release)
            currentCoroutineContext().ensureActive()
            onPhase(UpdatePhase.VERIFYING_PACKAGE)
            verifyApkPackage(partial,release)
            currentCoroutineContext().ensureActive()
            onPhase(UpdatePhase.PREPARING)
            if(apk.exists())check(apk.delete()){"No se pudo sustituir la APK anterior."}
            check(partial.renameTo(apk)){"No se pudo preparar la instalación."}
            apk
        }catch(e:CancellationException){throw e}
        catch(e:Exception){partial.delete();throw e}
    }

    /** Se verifica nuevamente al abrir el instalador, incluso tras reiniciar el proceso. */
    suspend fun verifyReady(release:AppRelease):File=withContext(Dispatchers.IO){
        checkRelease(release)
        val apk=verifiedFile(release)
        verifyHash(apk,release)
        verifyApkPackage(apk,release)
        apk
    }
    private suspend fun verifyHash(file:File,release:AppRelease){
        check(file.isFile && file.length()==release.sizeBytes){"El archivo está incompleto o ya no existe."}
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use{input->
            val buffer=ByteArray(64*1024)
            while(true){
                currentCoroutineContext().ensureActive()
                val count=input.read(buffer)
                if(count<0)break
                digest.update(buffer,0,count)
            }
        }
        check(digest.digest().joinToString(""){"%02x".format(it)}.equals(release.sha256,true)){
            "Falló la verificación SHA-256. Vuelve a descargar."
        }
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
