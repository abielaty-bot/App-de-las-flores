package pe.lasflores.controlinterno.update

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class InstalledVersion(val code:Long,val name:String,val installedAt:Long)
data class UpdateUiState(
    val status:String="Sin comprobar",
    val busy:Boolean=false,
    val release:AppRelease?=null,
    val downloadedFile:File?=null,
    val error:Boolean=false,
    val phase:UpdatePhase=UpdatePhase.IDLE,
    val progress:DownloadProgress=DownloadProgress(),
    val history:List<InstalledVersion> = emptyList(),
    val automaticChecks:Boolean=false
)

internal class UpdateStore(context:Context){
    private val prefs=context.getSharedPreferences("lf_update_center",Context.MODE_PRIVATE)
    fun saveRelease(r:AppRelease?){
        val edit=prefs.edit()
        if(r==null)edit.remove("release") else edit.putString("release",JSONObject().apply{
            put("code",r.versionCode);put("title",r.title);put("description",r.description)
            put("url",r.apkUrl);put("sha",r.sha256);put("size",r.sizeBytes);put("published",r.publishedAt)
        }.toString())
        edit.apply()
    }
    fun loadRelease():AppRelease?=runCatching{
        val json=JSONObject(prefs.getString("release",null)?:return null)
        AppRelease(json.getLong("code"),json.getString("title"),json.getString("description"),
            json.getString("url"),json.getString("sha"),json.getLong("size"),json.optString("published"))
    }.getOrNull()
    fun history():List<InstalledVersion> = runCatching{
        val array=JSONArray(prefs.getString("history","[]")?:"[]")
        (0 until array.length()).map{index->array.getJSONObject(index).let{
            InstalledVersion(it.getLong("code"),it.getString("name"),it.getLong("at"))
        }}
    }.getOrDefault(emptyList())
    @Suppress("DEPRECATION")
    fun recordInstalled(info:PackageInfo):List<InstalledVersion>{
        val code=if(Build.VERSION.SDK_INT>=28)info.longVersionCode else info.versionCode.toLong()
        val record=InstalledVersion(code,info.versionName?:UpdatePolicy.versionNameForCode(code),info.lastUpdateTime)
        val history=(listOf(record)+history().filter{it.code!=code}).take(30)
        prefs.edit().putString("history",JSONArray().apply{
            history.forEach{put(JSONObject().apply{put("code",it.code);put("name",it.name);put("at",it.installedAt)})}
        }.toString()).apply()
        return history
    }
    var automaticChecks:Boolean
        get()=prefs.getBoolean("automatic",false)
        set(value){prefs.edit().putBoolean("automatic",value).apply()}
    var lastNotifiedVersion:Long
        get()=prefs.getLong("notified",0)
        set(value){prefs.edit().putLong("notified",value).apply()}
}

/** Estado compartido por el servicio y las pantallas, sin depender de una Activity. */
internal object UpdateRuntime {
    private val _state=MutableStateFlow(UpdateUiState())
    val state=_state.asStateFlow()
    private var initialized=false
    @Synchronized fun initialize(context:Context){
        if(initialized)return
        val app=context.applicationContext
        val store=UpdateStore(app)
        val repo=GitHubUpdateRepository(app)
        val history=store.recordInstalled(app.packageManager.getPackageInfo(app.packageName,0))
        val release=store.loadRelease()?.let{UpdatePolicy.latestCompatible(repo.currentVersionCode(),listOf(it))}
        if(release==null)store.saveRelease(null)
        val ready=release?.let{runCatching{repo.verifiedFile(it).takeIf{file->file.isFile&&file.length()==it.sizeBytes}}.getOrNull()}
        val bytes=release?.let(repo::partialBytes)?:0
        _state.value=UpdateUiState(
            status=when{
                ready!=null->"Descarga guardada. Se verificará antes de instalar."
                bytes>0->"Descarga interrumpida. Puedes reintentar o cancelar."
                release!=null->"Actualización disponible"
                else->"Sin comprobar"
            },
            release=release,downloadedFile=ready,
            phase=when{ready!=null->UpdatePhase.READY;release!=null->UpdatePhase.AVAILABLE;else->UpdatePhase.IDLE},
            progress=DownloadProgress(if(ready!=null)release?.sizeBytes?:0 else bytes,release?.sizeBytes?:0),
            history=history,automaticChecks=store.automaticChecks
        )
        initialized=true
    }
    fun set(value:UpdateUiState){_state.value=value}
    fun change(block:(UpdateUiState)->UpdateUiState){
        // CAS mantiene las actualizaciones de progreso coherentes con Cancelar.
        while(true){val old=_state.value;if(_state.compareAndSet(old,block(old)))return}
    }
}
