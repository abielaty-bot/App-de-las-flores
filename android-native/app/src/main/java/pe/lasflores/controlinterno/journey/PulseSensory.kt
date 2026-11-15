package pe.lasflores.controlinterno.journey

import android.app.*
import android.content.*
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import pe.lasflores.controlinterno.domain.JourneyTask
import pe.lasflores.controlinterno.domain.JourneyRules
import pe.lasflores.controlinterno.data.JourneyPreferencesEntity
import java.time.ZoneId

internal object PulseSensory {
    const val TEST="lf.pulse.TEST"
    val tones=listOf("signature","attention","urgent","critical","resolved")
    val profiles=listOf("NORMAL","SALON","OFFICE","SILENT")
    val categories=listOf("briefings","uniformes","celulares","funciones","operativo","incidentes","inicio","manual")
    private val patterns=mapOf("signature" to longArrayOf(0,100),"attention" to longArrayOf(0,100,100,100),
        "urgent" to longArrayOf(0,180,100,180,100,350),"critical" to longArrayOf(0,350,150,350,150,350),
        "resolved" to longArrayOf(0,40,60,40))
    private fun prefs(c:Context)=c.getSharedPreferences("pulse_sensory",Context.MODE_PRIVATE)
    private fun nm(c:Context)=c.getSystemService(NotificationManager::class.java)
    fun profile(c:Context)=prefs(c).getString("profile","NORMAL")?.takeIf{it in profiles}?:"NORMAL"
    fun setProfile(c:Context,v:String){require(v in profiles);prefs(c).edit().putString("profile",v).apply()}
    fun automatic(c:Context)=prefs(c).getBoolean("automatic",true)
    fun setAutomatic(c:Context,v:Boolean){prefs(c).edit().putBoolean("automatic",v).apply()}
    fun criticalSound(c:Context)=prefs(c).getBoolean("criticalSound",true)
    fun setCriticalSound(c:Context,v:Boolean){prefs(c).edit().putBoolean("criticalSound",v).apply()}
    fun categoryTone(c:Context,category:String)=prefs(c).getString("tone_"+category,"auto")?:"auto"
    fun setCategoryTone(c:Context,category:String,tone:String){require(category in categories && tone in tones+"auto"+"silent");prefs(c).edit().putString("tone_"+category,tone).apply()}
    private fun uri(c:Context,tone:String):Uri {
        return Uri.parse("android.resource://"+c.packageName+"/raw/pulse_"+tone)
    }
    private val audio=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    fun channel(tone:String,mode:String)="lf_pulse_v1_"+tone+"_"+mode
    fun setup(c:Context){
        for(tone in tones)for(mode in listOf("sound","vibrate","silent")){
            val importance=if(mode=="silent")NotificationManager.IMPORTANCE_LOW else if(tone in listOf("urgent","critical"))NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
            nm(c).createNotificationChannel(NotificationChannel(channel(tone,mode),"Pulse · "+tone.replaceFirstChar{it.uppercase()}+" · "+when(mode){"sound"->"Sonido";"vibrate"->"Vibración";else->"Silencio"},importance).apply{
                setSound(if(mode=="sound")uri(c,tone) else null,audio)
                enableVibration(mode!="silent")
                if(mode!="silent")vibrationPattern=patterns.getValue(tone)
                lockscreenVisibility=Notification.VISIBILITY_PRIVATE
            })
        }
    }
    fun select(c:Context,t:JourneyTask,p:JourneyPreferencesEntity,now:Long):String{
        val category=if(t.manual)"manual" else t.module
        val configured=categoryTone(c,category)
        val tone=when{
            configured in tones->configured
            t.priority=="CRITICAL"->"critical"
            t.module=="incidentes"&&t.priority=="HIGH"->"urgent"
            t.module=="operativo"&&t.priority=="HIGH"->"urgent"
            t.module in listOf("uniformes","celulares","funciones","operativo")->"attention"
            else->"signature"
        }
        val profile=if(automatic(c)&&profile(c)=="NORMAL"&&JourneyRules.quiet(now,p,ZoneId.systemDefault()))"SALON" else profile(c)
        val mode=when{
            configured=="silent"||profile=="SILENT"->"silent"
            profile=="SALON"&&!(tone=="critical"&&criticalSound(c))->"vibrate"
            else->"sound"
        }
        return channel(tone,mode)
    }
    fun preview(c:Context,tone:String){
        require(tone in tones)
        // Preview is user initiated; media volume is controlled by Android.
        runCatching{MediaPlayer.create(c,uri(c,tone))?.apply{setOnCompletionListener{it.release()};setOnErrorListener{m,_,_->m.release();true};start()}}
    }
    @Suppress("DEPRECATION")
    fun vibrate(c:Context,tone:String):Boolean{
        val v=if(Build.VERSION.SDK_INT>=31)c.getSystemService(VibratorManager::class.java).defaultVibrator else c.getSystemService(Vibrator::class.java)
        if(!v.hasVibrator())return false
        v.vibrate(VibrationEffect.createWaveform(patterns.getValue(tone),-1));return true
    }
    fun test(c:Context,tone:String="signature"):Boolean{
        setup(c)
        val ch=channel(tone,"sound")
        if(!JourneyAlerts.allowed(c)||nm(c).getNotificationChannel(ch)?.importance==NotificationManager.IMPORTANCE_NONE)return false
        val open=PendingIntent.getActivity(c,4055,Intent(c,pe.lasflores.controlinterno.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return runCatching{
            nm(c).notify(4056,Notification.Builder(c,ch).setSmallIcon(pe.lasflores.controlinterno.R.drawable.ic_pulse_tulip)
                .setContentTitle("Las Flores Pulse · Prueba").setContentText("Aviso de prueba · "+tone).setContentIntent(open).setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE).build())
            prefs(c).edit().putLong("lastTest",System.currentTimeMillis()).apply();true
        }.getOrDefault(false)
    }
    fun scheduleTest(c:Context){
        val alarm=c.getSystemService(AlarmManager::class.java)
        val pi=PendingIntent.getBroadcast(c,4057,Intent(c,JourneyReceiver::class.java).setAction(TEST),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val at=System.currentTimeMillis()+60_000
        try{if(JourneyAlerts.exactAllowed(c))alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi) else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi)}
        catch(_:SecurityException){alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi)}
        prefs(c).edit().putLong("scheduledTest",at).apply()
    }
    fun lastTest(c:Context)=prefs(c).getLong("lastTest",0)
    fun scheduledTest(c:Context)=prefs(c).getLong("scheduledTest",0)
}
