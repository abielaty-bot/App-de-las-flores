package pe.lasflores.controlinterno.data

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import pe.lasflores.controlinterno.LasFloresApp

class DailyBackupJob:JobService(){
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var job:Job?=null
    override fun onStartJob(params:JobParameters):Boolean{
        job=scope.launch{
            val retry=try{
                (application as LasFloresApp).automaticBackups.daily(true)
                false
            }catch(e:CancellationException){throw e}
            catch(_:Exception){true}
            jobFinished(params,retry)
        }
        return true
    }
    override fun onStopJob(params:JobParameters):Boolean{job?.cancel();return true}
    override fun onDestroy(){scope.cancel();super.onDestroy()}
    companion object{
        private const val ID=4021
        fun configure(context:Context){
            val scheduler=context.getSystemService(JobScheduler::class.java)
            if(scheduler.getPendingJob(ID)!=null)return
            scheduler.schedule(JobInfo.Builder(ID,ComponentName(context,DailyBackupJob::class.java))
                .setPeriodic(24L*60*60*1000).setPersisted(true).build())
        }
    }
}
