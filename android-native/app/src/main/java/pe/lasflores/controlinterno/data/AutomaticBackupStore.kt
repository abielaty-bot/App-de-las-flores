package pe.lasflores.controlinterno.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class RecoveryCopy(val name:String,val timestamp:Long,val size:Long){
    val beforeUpdate:Boolean get()=name=="before-update.json"
}
data class RecoveryStatus(val copies:List<RecoveryCopy> = emptyList(),val busy:Boolean=false,val error:Boolean=false,val message:String="Preparando protección local…")

/** Recovery snapshots; Room remains the source of truth. */
class AutomaticBackupStore(context:Context,private val manager:OfflineBackupManager){
    private val directory=File(context.filesDir,"automatic-recovery")
    private val mutex=Mutex()
    private val _state=MutableStateFlow(RecoveryStatus())
    val state=_state.asStateFlow()
    private fun files():List<File> = directory.listFiles()?.filter{
        it.isFile && (it.name=="before-update.json" || BackupRetention.isDaily(it.name))
    }?.sortedByDescending{it.lastModified()} ?: emptyList()
    private fun copies()=files().map{RecoveryCopy(it.name,it.lastModified(),it.length())}
    suspend fun refresh()=withContext(Dispatchers.IO){mutex.withLock{
        _state.value=_state.value.copy(copies=copies())
    }}
    suspend fun daily(refreshToday:Boolean=false)=save("daily-"+LocalDate.now()+".json",refreshToday)
    suspend fun beforeUpdate()=save("before-update.json",true)
    private suspend fun save(name:String,replace:Boolean):RecoveryCopy=withContext(Dispatchers.IO){
        mutex.withLock{
            _state.value=RecoveryStatus(copies(),true,false,"Creando copia de recuperación…")
            try{
                check(directory.isDirectory || directory.mkdirs()){"No se pudo abrir almacenamiento interno"}
                val path=File(directory,name)
                val atomic=AtomicFile(path)
                if(!replace && path.isFile){
                    val valid=runCatching{
                        manager.preview(atomic.openRead().use{it.readBytes().toString(Charsets.UTF_8)})
                    }.isSuccess
                    if(valid){
                        _state.value=RecoveryStatus(copies(),message="Copia diaria disponible")
                        return@withLock RecoveryCopy(name,path.lastModified(),path.length())
                    }
                }
                val json=manager.exportJson()
                manager.preview(json)
                val bytes=json.toByteArray(Charsets.UTF_8)
                val stream=atomic.startWrite()
                try{
                    stream.write(bytes)
                    atomic.finishWrite(stream)
                }catch(e:Exception){
                    atomic.failWrite(stream)
                    throw e
                }
                val saved=atomic.openRead().use{it.readBytes()}
                check(saved.contentEquals(bytes)){"La copia no coincide con los datos guardados"}
                manager.preview(saved.toString(Charsets.UTF_8))
                BackupRetention.expired(files().map{it.name}).forEach{AtomicFile(File(directory,it)).delete()}
                _state.value=RecoveryStatus(copies(),message="Copia verificada y guardada")
                RecoveryCopy(name,path.lastModified(),path.length())
            }catch(e:CancellationException){
                _state.value=RecoveryStatus(copies(),message="Copia interrumpida; se reintentará")
                throw e
            }catch(e:Exception){
                _state.value=RecoveryStatus(copies(),error=true,message="No se pudo verificar la copia. Tus registros de Room no se han borrado.")
                throw e
            }
        }
    }
    suspend fun read(name:String):String=withContext(Dispatchers.IO){mutex.withLock{
        require(name=="before-update.json" || BackupRetention.isDaily(name)){"Copia no válida"}
        val json=AtomicFile(File(directory,name)).openRead().use{it.readBytes().toString(Charsets.UTF_8)}
        manager.preview(json)
        json
    }}
}
internal object BackupRetention{
    private val daily=Regex("""daily-\d{4}-\d{2}-\d{2}\.json""")
    fun isDaily(name:String)=daily.matches(name) &&
        runCatching{LocalDate.parse(name.removePrefix("daily-").removeSuffix(".json"))}.isSuccess
    fun expired(names:List<String>)=names.filter(::isDaily).distinct().sortedDescending().drop(7)
}
