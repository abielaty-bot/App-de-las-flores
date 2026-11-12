package pe.lasflores.controlinterno.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pe.lasflores.controlinterno.MainActivity

internal object UpdateNotifications{
    const val CHANNEL="lf_update_download"
    const val AVAILABLE_CHANNEL="lf_update_available"
    const val ACTIVE_ID=4015
    const val AVAILABLE_ID=4016
    const val OPEN="pe.lasflores.controlinterno.OPEN_UPDATES"
    fun setup(context:Context){
        val nm=context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL,"Descargas de Las Flores",NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(AVAILABLE_CHANNEL,"Nuevas versiones",NotificationManager.IMPORTANCE_DEFAULT))
    }
    fun allowed(context:Context):Boolean =
        (Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(context,android.Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) &&
            context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    fun open(context:Context):PendingIntent = PendingIntent.getActivity(context,4015,
        Intent(context,MainActivity::class.java).setAction(OPEN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun available(context:Context,release:AppRelease):Boolean{
        setup(context)
        if(!allowed(context))return false
        val nm=context.getSystemService(NotificationManager::class.java)
        if(nm.getNotificationChannel(AVAILABLE_CHANNEL)?.importance==NotificationManager.IMPORTANCE_NONE)return false
        nm.notify(AVAILABLE_ID,Notification.Builder(context,AVAILABLE_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle("Nueva versión de Las Flores")
            .setContentText("Disponible "+UpdatePolicy.versionNameForCode(release.versionCode))
            .setContentIntent(open(context)).setAutoCancel(true).build())
        return true
    }
}

/** El servicio visible mantiene la descarga al cambiar de módulo o apagar la pantalla.
 * Si Android mata el proceso, conserva la parte y requiere un reintento explícito. */
class UpdateDownloadService:Service(){
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var job:Job?=null
    private var ticker:Job?=null
    private var userCancelled=false
    private var timedOut=false
    private var current:AppRelease?=null
    private var startedAt=0L
    @Volatile private var lastBytesAt=0L
    private lateinit var repository:GitHubUpdateRepository
    private lateinit var manager:NotificationManager
    override fun onCreate(){
        super.onCreate()
        repository=GitHubUpdateRepository(this)
        manager=getSystemService(NotificationManager::class.java)
        UpdateRuntime.initialize(this)
        UpdateNotifications.setup(this)
    }
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
        if(intent?.action==CANCEL){
            if(job?.isActive==true){
                userCancelled=true
                UpdateRuntime.change{it.copy(phase=UpdatePhase.CANCELLING,status="Cancelando descarga…")}
                job?.cancel()
            }else{
                UpdateRuntime.state.value.release?.let(repository::discardPartial)
                UpdateRuntime.change{it.copy(busy=false,phase=UpdatePhase.CANCELLED,status="Descarga cancelada",
                    progress=DownloadProgress(total=it.release?.sizeBytes?:0),downloadedFile=null,error=false)}
                stopSelf()
            }
            return START_NOT_STICKY
        }
        if(job?.isActive==true)return START_NOT_STICKY
        val release=UpdateRuntime.state.value.release ?: run{stopSelf();return START_NOT_STICKY}
        current=release
        userCancelled=false;timedOut=false
        startedAt=SystemClock.elapsedRealtime();lastBytesAt=startedAt
        UpdateRuntime.change{it.copy(busy=true,error=false,downloadedFile=null,phase=UpdatePhase.DOWNLOADING,
            status="Conectando con GitHub…",progress=DownloadProgress(repository.partialBytes(release),release.sizeBytes))}
        startForeground(UpdateNotifications.ACTIVE_ID,notification(UpdateRuntime.state.value,true))
        ticker=scope.launch{
            while(isActive){
                delay(1000)
                val now=SystemClock.elapsedRealtime()
                UpdateRuntime.change{state->
                    if(state.phase==UpdatePhase.DOWNLOADING)state.copy(progress=state.progress.copy(
                        elapsedMillis=now-startedAt,bytesPerSecond=if(now-lastBytesAt>3500)0.0 else state.progress.bytesPerSecond),
                        status=if(now-lastBytesAt>3500)"Esperando datos de la conexión…" else "Descargando actualización…")
                    else state
                }
                if(UpdateRuntime.state.value.busy)manager.notify(UpdateNotifications.ACTIVE_ID,notification(UpdateRuntime.state.value,true))
            }
        }
        job=scope.launch{
            try{
                val apk=repository.downloadAndVerify(release,onProgress={progress->
                    lastBytesAt=SystemClock.elapsedRealtime()
                    UpdateRuntime.change{if(it.phase==UpdatePhase.CANCELLING)it else it.copy(progress=progress)}
                },onPhase={phase->
                    UpdateRuntime.change{if(it.phase==UpdatePhase.CANCELLING)it else it.copy(phase=phase,status=phaseText(phase))}
                })
                UpdateRuntime.change{it.copy(busy=false,phase=UpdatePhase.READY,status="Descarga verificada. Lista para instalar.",
                    downloadedFile=apk,progress=it.progress.copy(bytes=release.sizeBytes,total=release.sizeBytes))}
            }catch(e:CancellationException){
                if(userCancelled)repository.discardPartial(release)
                UpdateRuntime.change{it.copy(busy=false,downloadedFile=null,error=false,
                    phase=if(userCancelled)UpdatePhase.CANCELLED else UpdatePhase.AVAILABLE,
                    status=if(userCancelled)"Descarga cancelada" else "Descarga interrumpida. Reintenta para continuar.",
                    progress=DownloadProgress(repository.partialBytes(release),release.sizeBytes))}
            }catch(e:Exception){
                UpdateRuntime.change{it.copy(busy=false,error=true,downloadedFile=null,phase=UpdatePhase.ERROR,
                    status="No se pudo preparar la actualización: "+(e.message?:"Error de descarga"),
                    progress=it.progress.copy(bytes=repository.partialBytes(release),bytesPerSecond=0.0))}
            }finally{
                ticker?.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                if(!userCancelled&&!timedOut&&UpdateNotifications.allowed(this@UpdateDownloadService))
                    manager.notify(UpdateNotifications.ACTIVE_ID,notification(UpdateRuntime.state.value,false))
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }
    private fun notification(state:UpdateUiState,ongoing:Boolean):Notification{
        val p=state.progress
        val builder=Notification.Builder(this,UpdateNotifications.CHANNEL)
            .setSmallIcon(if(state.phase==UpdatePhase.READY)android.R.drawable.stat_sys_download_done else android.R.drawable.stat_sys_download)
            .setContentTitle(if(state.phase==UpdatePhase.READY)"Actualización lista para instalar" else "Las Flores · Actualizaciones")
            .setContentText(if(state.phase==UpdatePhase.DOWNLOADING)
                p.percent.toString()+"% · "+UpdateFormat.bytes(p.bytes)+" / "+UpdateFormat.bytes(p.total) else state.status)
            .setOnlyAlertOnce(true).setOngoing(ongoing).setAutoCancel(!ongoing)
            .setContentIntent(UpdateNotifications.open(this))
        if(ongoing){
            builder.setProgress(100,p.percent,state.phase!=UpdatePhase.DOWNLOADING)
            val cancel=PendingIntent.getService(this,4017,Intent(this,UpdateDownloadService::class.java).setAction(CANCEL),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(Notification.Action.Builder(null,"Cancelar",cancel).build())
        }
        return builder.build()
    }
    override fun onTimeout(startId:Int,fgsType:Int){
        timedOut=true
        job?.cancel()
        ticker?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy(){scope.cancel();super.onDestroy()}
    companion object{
        const val START="lf.update.START"
        const val CANCEL="lf.update.CANCEL"
        fun start(context:Context){ContextCompat.startForegroundService(context,Intent(context,UpdateDownloadService::class.java).setAction(START))}
        fun cancel(context:Context){context.startService(Intent(context,UpdateDownloadService::class.java).setAction(CANCEL))}
    }
}

internal fun phaseText(phase:UpdatePhase)=when(phase){
    UpdatePhase.DOWNLOADING->"Descargando actualización…"
    UpdatePhase.VERIFYING_HASH->"Verificando integridad SHA-256…"
    UpdatePhase.VERIFYING_PACKAGE->"Verificando paquete, versión y firma…"
    UpdatePhase.PREPARING->"Preparando instalación…"
    else->"Procesando actualización…"
}

internal object UpdateFormat{
    fun bytes(value:Long):String=when{
        value>=1_000_000->String.format(Locale("es","PE"),"%.2f MB",value/1_000_000.0)
        value>=1000->String.format(Locale("es","PE"),"%.1f KB",value/1000.0)
        else->value.toString()+" B"
    }
    fun duration(seconds:Long?):String=when{
        seconds==null->"Calculando…"
        seconds>=3600->(seconds/3600).toString()+" h "+(seconds%3600/60)+" min"
        seconds>=60->(seconds/60).toString()+" min "+(seconds%60)+" s"
        else->seconds.toString()+" s"
    }
}
