package pe.lasflores.controlinterno.update

import kotlin.math.ceil

enum class UpdatePhase {
    IDLE,CHECKING,AVAILABLE,DOWNLOADING,VERIFYING_HASH,VERIFYING_PACKAGE,PREPARING,READY,
    CANCELLING,CANCELLED,ERROR,INSTALL_CHECK
}

data class DownloadProgress(
    val bytes:Long=0,val total:Long=0,val elapsedMillis:Long=0,
    val bytesPerSecond:Double=0.0
) {
    val percent:Int get()=if(total>0)(bytes.coerceIn(0,total)*100/total).toInt() else 0
    val remaining:Long get()=(total-bytes).coerceAtLeast(0)
    val etaSeconds:Long? get()=when{
        total<=0->null
        remaining==0L->0
        bytesPerSecond<=0.0 || !bytesPerSecond.isFinite()->null
        else->ceil(remaining/bytesPerSecond).toLong().coerceAtLeast(1)
    }
}

/** Tiempo monotónico, velocidad suavizada, sin contar los bytes recuperados como descargados. */
class DownloadMeter(private val total:Long,private val started:Long){
    private var lastTime=started
    private var lastBytes=0L
    private var speed=0.0
    fun sample(bytes:Long,now:Long,reset:Boolean=false):DownloadProgress {
        val value=bytes.coerceIn(0,total)
        if(reset||value<lastBytes){
            lastBytes=value;lastTime=now;speed=0.0
        }else if(now>lastTime){
            val instantaneous=(value-lastBytes)*1000.0/(now-lastTime)
            speed=if(speed==0.0)instantaneous else speed*0.65+instantaneous*0.35
            lastTime=now;lastBytes=value
        }
        return DownloadProgress(value,total,(now-started).coerceAtLeast(0),speed)
    }
}
