package pe.lasflores.controlinterno.update

import java.net.URI

data class AppRelease(
    val versionCode: Long,
    val title: String,
    val description: String,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val publishedAt: String = ""


/** Contrato estricto de nuestra distribución: releases android-beta-vN. */
object UpdatePolicy {
    private const val DOWNLOAD_PREFIX = "/abielaty-bot/App-de-las-flores/releases/download/"
    private val VERSION_TAG = Regex("^android-beta-v([1-9][0-9]*)$")
    private val SHA256 = Regex("^[a-fA-F0-9]{64}$")

    /** La etiqueta sigue siendo numérica para actualizar sin romper la Beta v4 instalada. */
    fun versionNameForCode(code: Long): String =
        if (code >= 10_000L) (code / 10_000L).toString() + "." +
            (code % 10_000L).toString().padStart(4, '0')
        else "Beta v" + code

    fun versionFromTag(tag: String): Long? = VERSION_TAG.matchEntire(tag)
        ?.groupValues?.getOrNull(1)?.toLongOrNull()

    fun validSha256(raw: String?): String? {
        val candidate = raw?.removePrefix("sha256:") ?: return null
        return candidate.takeIf { SHA256.matches(it) }?.lowercase()
    }

    fun isTrustedDownloadUrl(url: String): Boolean = try {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null &&
            uri.query == null && uri.fragment == null &&
            uri.path.startsWith(DOWNLOAD_PREFIX) &&
            uri.path.endsWith("/las-flores-beta.apk") &&
            uri.path.removePrefix(DOWNLOAD_PREFIX).split('/').size == 2 &&
            versionFromTag(uri.path.removePrefix(DOWNLOAD_PREFIX).substringBefore('/')) != null
    } catch (_: Exception) { false }

    /** GitHub distribuye releases mediante hosts de recursos bajo githubusercontent.com. */
    fun isTrustedResponseUrl(url:String):Boolean = try{
        val uri=URI(url)
        uri.scheme=="https" && uri.userInfo==null && (uri.port==-1 || uri.port==443) &&
            (uri.host=="github.com" || uri.host=="release-assets.githubusercontent.com" ||
                uri.host=="objects.githubusercontent.com" || uri.host=="github-releases.githubusercontent.com")
    }catch(_:Exception){false}

    fun validContentRange(value:String?,offset:Long,size:Long):Boolean {
        if(offset<=0 || offset>=size || value==null)return false
        val match=Regex("""bytes (\d+)-(\d+)/(\d+)""").matchEntire(value)?:return false
        return match.groupValues[1].toLongOrNull()==offset &&
            match.groupValues[2].toLongOrNull()==size-1 &&
            match.groupValues[3].toLongOrNull()==size
    }

    fun latestCompatible(currentVersionCode: Long, entries: List<AppRelease>): AppRelease? =
        entries.filter { it.versionCode > currentVersionCode && it.sizeBytes in 1..104_857_600L &&
            validSha256(it.sha256) != null && isTrustedDownloadUrl(it.apkUrl) }
            .maxByOrNull { it.versionCode }
}
