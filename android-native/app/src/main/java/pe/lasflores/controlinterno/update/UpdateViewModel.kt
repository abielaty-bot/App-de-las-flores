package pe.lasflores.controlinterno.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class UpdateViewModel(app:Application):AndroidViewModel(app){
    private val repository=GitHubUpdateRepository(app)
    private val store=UpdateStore(app)
    init{UpdateRuntime.initialize(app)}
    val state=UpdateRuntime.state
    fun checkForUpdates(){
        if(state.value.busy)return
        if(getApplication<Application>().packageName!="pe.lasflores.controlinterno.beta"){
            UpdateRuntime.change{it.copy(status="La APK de desarrollo no usa el canal de distribución firmado.")}
            return
        }
        UpdateRuntime.change{it.copy(busy=true,error=false,phase=UpdatePhase.CHECKING,status="Buscando versiones publicadas…")}
        viewModelScope.launch{
            try{
                val release=repository.check()
                store.saveRelease(release)
                val ready=release?.let{r->repository.verifiedFile(r).takeIf{it.isFile&&it.length()==r.sizeBytes}}
                val bytes=if(ready!=null)release?.sizeBytes?:0 else release?.let(repository::partialBytes)?:0
                UpdateRuntime.change{it.copy(busy=false,error=false,release=release,downloadedFile=ready,
                    phase=if(ready!=null)UpdatePhase.READY else if(release!=null)UpdatePhase.AVAILABLE else UpdatePhase.IDLE,
                    progress=DownloadProgress(bytes,release?.sizeBytes?:0),
                    status=if(release==null)"No hay actualizaciones publicadas para esta versión." else if(ready!=null)
                        "Descarga guardada. Lista para verificar e instalar." else "Nueva versión "+UpdatePolicy.versionNameForCode(release.versionCode)+" disponible.")}
            }catch(e:CancellationException){
                UpdateRuntime.change{it.copy(busy=false,phase=if(it.downloadedFile!=null)UpdatePhase.READY else if(it.release!=null)UpdatePhase.AVAILABLE else UpdatePhase.IDLE,status="Consulta interrumpida")}
                throw e
            }catch(e:Exception){UpdateRuntime.change{it.copy(busy=false,error=true,phase=UpdatePhase.ERROR,status="Error al consultar GitHub: "+e.message)}}
        }
    }
    fun download(){
        val release=state.value.release?:return
        if(state.value.busy)return
        store.saveRelease(release)
        UpdateRuntime.change{it.copy(busy=true,error=false,phase=UpdatePhase.DOWNLOADING,status="Preparando descarga…",downloadedFile=null,
            progress=DownloadProgress(repository.partialBytes(release),release.sizeBytes))}
        try{UpdateDownloadService.start(getApplication<Application>())}
        catch(e:Exception){UpdateRuntime.change{it.copy(busy=false,error=true,phase=UpdatePhase.ERROR,status="No se pudo iniciar la descarga: "+e.message)}}
    }
    fun cancel(){
        val s=state.value
        if(s.phase in setOf(UpdatePhase.DOWNLOADING,UpdatePhase.VERIFYING_HASH,UpdatePhase.VERIFYING_PACKAGE,UpdatePhase.PREPARING)){
            try{UpdateDownloadService.cancel(getApplication<Application>())}
            catch(e:Exception){UpdateRuntime.change{it.copy(status="No se pudo cancelar: "+e.message,error=true)}}
        }else if(!s.busy && s.downloadedFile==null){
            s.release?.let(repository::discardPartial)
            UpdateRuntime.change{it.copy(progress=DownloadProgress(total=it.release?.sizeBytes?:0),phase=UpdatePhase.CANCELLED,status="Descarga cancelada",error=false)}
        }
    }
    fun install(onReady:(File)->Unit){
        val release=state.value.release?:return
        if(state.value.busy||state.value.downloadedFile==null)return
        UpdateRuntime.change{it.copy(busy=true,error=false,phase=UpdatePhase.INSTALL_CHECK,status="Comprobando APK antes de instalar…")}
        viewModelScope.launch{
            try{
                val apk=repository.verifyReady(release)
                UpdateRuntime.change{it.copy(busy=false,phase=UpdatePhase.READY,status="APK verificada. Confirma la instalación en Android.",downloadedFile=apk)}
                onReady(apk)
            }catch(e:CancellationException){
                UpdateRuntime.change{it.copy(busy=false,phase=UpdatePhase.READY,status="Comprobación interrumpida. Vuelve a pulsar Instalar.")}
                throw e
            }catch(e:Exception){
                UpdateRuntime.change{it.copy(busy=false,phase=UpdatePhase.ERROR,error=true,downloadedFile=null,
                    status="No se pudo instalar: "+(e.message?:"Error de verificación"))}
            }
        }
    }
    fun setAutomaticChecks(enabled:Boolean){
        store.automaticChecks=enabled
        UpdateRuntime.change{it.copy(automaticChecks=enabled)}
        UpdateCheckJob.configure(getApplication<Application>(),enabled)
    }
    class Factory(private val app:Application):ViewModelProvider.Factory{
        @Suppress("UNCHECKED_CAST")
        override fun <T:ViewModel> create(modelClass:Class<T>):T{
            require(modelClass.isAssignableFrom(UpdateViewModel::class.java))
            return UpdateViewModel(app) as T
        }
    }
}
