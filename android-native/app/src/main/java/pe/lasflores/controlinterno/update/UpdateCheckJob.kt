package pe.lasflores.controlinterno.update

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Comprobación opcional y diferida, sin descargas ni instalaciones automáticas. */
class UpdateCheckJob:JobService(){
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var job:Job?=null
    override fun onStartJob(params:JobParameters):Boolean{
        val store=UpdateStore(this)
        if(!store.automaticChecks || packageName!="pe.lasflores.controlinterno.beta")return false
        job=scope.launch{
            try{
                val release=GitHubUpdateRepository(this@UpdateCheckJob).check()
                if(release!=null && store.automaticChecks && release.versionCode>store.lastNotifiedVersion &&
                    UpdateNotifications.available(this@UpdateCheckJob,release))store.lastNotifiedVersion=release.versionCode
            }catch(e:CancellationException){throw e}
            catch(_:Exception){/* Siguiente ventana periódica; no alarma repetitiva de error. */}
            jobFinished(params,false)
        }
        return true
    }
    override fun onStopJob(params:JobParameters):Boolean{job?.cancel();return false}
    override fun onDestroy(){scope.cancel();super.onDestroy()}
    companion object{
        private const val ID=4018
        /** Optional functionality must never prevent the application from opening. */
        fun configure(context:Context,enabled:Boolean):Boolean{
            return try{
                val scheduler=context.getSystemService(JobScheduler::class.java)
                    ?: return false
                if(!enabled){
                    scheduler.cancel(ID)
                    true
                }else if(scheduler.getPendingJob(ID)!=null){
                    true
                }else{
                    scheduler.schedule(JobInfo.Builder(ID,ComponentName(context,UpdateCheckJob::class.java))
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPeriodic(24L*60*60*1000).setPersisted(true).build())==JobScheduler.RESULT_SUCCESS
                }
            }catch(e:Exception){
                Log.w("LasFloresUpdates","No se pudo programar la consulta opcional",e)
                false
            }
        }
    }
}
