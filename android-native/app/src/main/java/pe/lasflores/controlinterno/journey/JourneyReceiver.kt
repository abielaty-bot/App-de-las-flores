package pe.lasflores.controlinterno.journey

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pe.lasflores.controlinterno.LasFloresApp

class JourneyReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        val result=goAsync()
        val app=context.applicationContext as LasFloresApp
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch{
            try{
                if(intent.action==JourneyAlerts.SNOOZE){
                    val key=intent.getStringExtra("key")
                    if(!key.isNullOrBlank())app.journey.snoozeNow(key,10)
                }else if(intent.action==JourneyAlerts.COMPLETE){
                    val key=intent.getStringExtra("key")
                    if(!key.isNullOrBlank())app.journey.completeNow(key)
                }else app.journey.refreshNow()
            }catch(e:Exception){android.util.Log.w("LasFloresJourney","No se pudo procesar el aviso",e)}
            finally{result.finish()}
        }
    }
}
