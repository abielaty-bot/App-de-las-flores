package pe.lasflores.controlinterno.journey

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.room3.withWriteTransaction
import java.time.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pe.lasflores.controlinterno.MainActivity
import pe.lasflores.controlinterno.data.*
import pe.lasflores.controlinterno.domain.*
import pe.lasflores.controlinterno.update.UpdateNotifications

data class JourneyState(
    val dateIso:String=LocalDate.now().toString(),
    val preferences:JourneyPreferencesEntity=JourneyPreferencesEntity(),
    val reminders:List<JourneyReminderEntity> = emptyList(),
    val tasks:List<JourneyTask> = emptyList(),
    val occurrences:List<JourneyOccurrenceEntity> = emptyList(),
    val notifications:List<JourneyNotificationEntity> = emptyList(),
    val error:String="",
    val loaded:Boolean=false
)
data class JourneyNavigation(val module:String,val dateIso:String,val sessionSlot:Int?=null,
    val taskKey:String="",val notificationId:String="",val nonce:String=UUID.randomUUID().toString())

/** Un solo reloj de alarmas por app. Las tareas automáticas se derivan de Room,
 * nunca de una segunda casilla de cumplimiento. */
class JourneyEngine(private val context:Context,private val database:LasFloresDatabase){
    private val dao=database.journeyDao()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val lock=Mutex()
    private val _state=MutableStateFlow(JourneyState())
    val state=_state.asStateFlow()
    private var started=false
    @OptIn(FlowPreview::class,ExperimentalCoroutinesApi::class)
    @Synchronized fun start(){
        if(started)return
        started=true
        val staff=database.staffDao()
        val flows=listOf(
            staff.observeStaff().map{Unit},staff.observeVacations().map{Unit},staff.observeOverrides().map{Unit},
            database.uniformDao().observeHistory().map{Unit},dao.observeActivity().map{Unit},
            database.briefingDao().observeSessions().map{Unit},database.briefingDao().observeHistory().map{Unit},
            database.functionDao().observeChecks().map{Unit},database.incidentDao().observeIncidents().map{Unit},
            dao.observePreferences().map{Unit},dao.observeReminders().map{Unit},
            dao.observeOccurrences().map{Unit},dao.observeNotifications().map{Unit},database.phoneDao().observeChecks().map{Unit})
        scope.launch{
            merge(*flows.toTypedArray()).debounce(250).catch{error->
                _state.value=_state.value.copy(error=error.message?:"No se pudo observar la jornada")
            }.collect{refreshNow()}
        }
        refresh()
    }
    fun refresh(){scope.launch{refreshNow()}}
    suspend fun refreshNow(){lock.withLock{
        try{reconcile()}
        catch(e:CancellationException){throw e}
        catch(e:Exception){_state.value=_state.value.copy(error=e.message?:"No se pudo actualizar la jornada")}
    }}

    private data class Computed(val state:JourneyState,val future:List<JourneyTask>)
    private suspend fun calculate():Computed=database.withWriteTransaction{
        val today=LocalDate.now();val tomorrow=today.plusDays(1);val zone=ZoneId.systemDefault()
        val prefs=dao.allPreferences().firstOrNull()?:JourneyPreferencesEntity()
        val defs=dao.allReminders()
        val people=database.staffDao().allStaff()
        val vacations=database.staffDao().allVacations()
        val overrides=database.staffDao().allOverrides()
        val occurrences=dao.occurrences(today.minusDays(7).toString(),tomorrow.toString())
        suspend fun data(day:LocalDate):JourneyData{
            val date=day.toString()
            val scheduled=people.filter{StaffScheduleRules.isScheduled(StaffScheduleRules.status(it,date,vacations,overrides))}
                .map{it.id}.toSet()
            val reviews=dao.inspections(date).filter{it.workflow=="FINALIZED"&&it.staffId in scheduled}
            val activity=dao.activity(date)
            val f=dao.functions(date)
            val incidents=dao.openIncidents(date)
            val sessions=dao.sessions(date)
            val pending=dao.pendingBriefings(date)
            val mesa=dao.mesaCount(date)
            val phones=PhoneRules.counts(PhoneRules.roster(date,people,vacations,overrides,database.phoneDao().dayChecks(date),day==today))
            return JourneyData(scheduledCount=scheduled.size,reviewedCount=reviews.map{it.staffId}.distinct().size,
                noUniformReview=activity.any{it.module=="uniformes"&&it.status=="NO_REALIZADA"},
                sessions=sessions,noBriefing=activity.any{it.module=="briefings"&&it.status=="NO_REALIZADA"},
                pendingBriefings=pending.map{it.staffId}.distinct().size,
                functionCount=f.size,pendingFunctions=f.count{it.result=="PENDING"},
                openIncidents=incidents.size,urgentIncidents=incidents.count{it.priority in setOf("alta","critica")},
                communicationRequired=mesa>0||pending.isNotEmpty(),phoneTotal=phones.total,phoneReviewed=phones.reviewed,
                noPhoneReview=activity.any{it.module=="celulares"&&it.status=="NO_REALIZADA"})
        }
        val tasks=JourneyRules.build(today,zone,prefs,defs,data(today),occurrences)
        val future=JourneyRules.build(tomorrow,zone,prefs,defs,data(tomorrow),occurrences)
        // Una tarea única pasada sigue visible como pendiente, sin revivir alarmas antiguas.
        val overdue=defs.filter{it.enabled&&it.recurrence=="ONCE"&&it.startDateIso<today.toString()}.mapNotNull{r->
            val key="manual-"+r.id+":"+r.startDateIso
            if(dao.occurrence(key)?.completedAtMillis!=null)return@mapNotNull null
            val day=LocalDate.parse(r.startDateIso)
            if(r.workdaysOnly&&!JourneyRules.workday(day,prefs))return@mapNotNull null
            val due=JourneyRules.millis(day,r.time,zone)
            JourneyTask(key,day.toString(),r.title,"Actividad de una fecha anterior",r.module,due,due-r.leadMinutes*60_000L,
                manual=true,notify=false,priority=r.priority)
        }
        Computed(JourneyState(today.toString(),prefs,defs,tasks+overdue,occurrences,
            dao.allNotifications().sortedByDescending{it.atMillis},loaded=true),future)
    }
    private suspend fun reconcile(){
        val computed=calculate()
        val s=computed.state
        _state.value=s
        val prefs=s.preferences
        val now=System.currentTimeMillis()
        val candidates=s.tasks+computed.future
        val old=s.occurrences.associateBy{it.taskKey}
        val next=mutableListOf<Long>()
        JourneyAlerts.setup(context)
        val allowed=prefs.enabled&&JourneyAlerts.allowed(context)
        val validKeys=candidates.filter{it.applicable&&!it.completed&&it.notify&&old[it.key]?.dueAtMillis==it.alertAtMillis}.map{it.key}.toSet()
        JourneyAlerts.cancelObsolete(context,if(allowed)validKeys else emptySet())
        if(allowed){
            for(task in candidates){
                val previous=old[task.key]
                val target=JourneyRules.nextAlert(task,previous,now)?:continue
                if(target>now){next.add(target);continue}
                val log=JourneyNotificationEntity(UUID.randomUUID().toString(),task.key,task.dateIso,task.title,task.detail,
                    task.module,task.sessionSlot,now,null)
                if(!JourneyAlerts.post(context,task,log,prefs))continue
                val same=previous?.dueAtMillis==task.alertAtMillis
                database.withWriteTransaction{
                    dao.saveOccurrence(JourneyOccurrenceEntity(task.key,task.dateIso,task.alertAtMillis,
                        previous?.completedAtMillis,null,now,if(same)(previous?.notificationCount?:0)+1 else 1))
                    dao.saveNotification(log)
                }
            }
        }
        // Se recalcula al comenzar otro día, incluso si no hay avisos próximos.
        val midnight=LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()+1000
        next.add(midnight)
        JourneyAlerts.schedule(context,if(prefs.enabled)next.minOrNull() else null)
        if(allowed)_state.value=calculate().state
    }
    private fun action(block:suspend ()->Unit){scope.launch{
        try{lock.withLock{block();reconcile()}}
        catch(e:CancellationException){throw e}
        catch(e:Exception){_state.value=_state.value.copy(error=e.message?:"No se guardó el cambio")}
    }}
    fun savePreferences(value:JourneyPreferencesEntity)=action{
        require(JourneyRules.validPreferences(value)){"Revisa las horas y los días de trabajo."}
        dao.savePreferences(value)
    }
    fun saveReminder(value:JourneyReminderEntity)=action{
        require(JourneyRules.validReminder(value)){"Revisa el nombre, fecha, horario y repetición."}
        require(dao.reminder(value.id)!=null||dao.allReminders().size<500){"Límite de recordatorios alcanzado."}
        dao.saveReminder(value.copy(updatedAtMillis=System.currentTimeMillis()))
    }
    fun deleteReminder(id:String)=action{dao.deleteReminder(id)}
    fun templates()=action{
        val day=LocalDate.now().toString()
        val defaults=listOf(
            listOf("abastecimiento","Revisar abastecimiento","08:40","DAILY","0","inicio"),
            listOf("supervision","Iniciar supervisión del salón","12:30","DAILY","0","inicio"),
            listOf("manteles","Verificar fotografías de manteles","17:25","DAILY","0","funciones"),
            listOf("finsemana","Confirmar personal del fin de semana","16:00","WEEKLY","32","personal"),
            listOf("horario","Preparar horario del domingo","16:30","WEEKLY","32","personal"),
            listOf("ventas","Preparar reporte de ventas","16:45","WEEKLY","32","reportes"),
            listOf("respaldo","Crear respaldo de seguridad","16:40","WEEKLY","2","backups"))
        database.withWriteTransaction{
            val existing=dao.allReminders().map{it.id}.toSet()
            for(r in defaults)if("template-"+r[0] !in existing){
                dao.saveReminder(JourneyReminderEntity("template-"+r[0],r[1],day,r[2],r[3],r[4].toInt(),
                    0,true,"NORMAL",r[5],true,System.currentTimeMillis()))
            }
        }
    }
    private suspend fun completeTask(key:String,completed:Boolean){
        val current=calculate().state.tasks.firstOrNull{it.key==key&&it.manual}?:error("Actividad manual no encontrada.")
        val old=dao.occurrence(key)
        dao.saveOccurrence(JourneyOccurrenceEntity(key,current.dateIso,current.alertAtMillis,
            if(completed)System.currentTimeMillis() else null,null,old?.lastNotifiedAtMillis,old?.notificationCount?:0))
        if(completed)JourneyAlerts.cancel(context,key)
    }
    fun complete(key:String,completed:Boolean)=action{completeTask(key,completed)}
    suspend fun completeNow(key:String){lock.withLock{completeTask(key,true);reconcile()}}
    fun snooze(key:String,minutes:Int){scope.launch{
        try{snoozeNow(key,minutes)}catch(e:CancellationException){throw e}
        catch(e:Exception){_state.value=_state.value.copy(error=e.message?:"No se pudo posponer")}
    }}
    suspend fun snoozeNow(key:String,minutes:Int){
        lock.withLock{
            require(minutes in setOf(5,10,15,30))
            val computed=calculate()
            val task=(computed.state.tasks+computed.future).firstOrNull{it.key==key&&it.applicable&&!it.completed&&it.notify}
                ?:return@withLock
            val old=dao.occurrence(key)
            dao.saveOccurrence(JourneyOccurrenceEntity(key,task.dateIso,task.alertAtMillis,old?.completedAtMillis,
                System.currentTimeMillis()+minutes*60_000L,old?.lastNotifiedAtMillis,old?.notificationCount?:0))
            JourneyAlerts.cancel(context,key)
            reconcile()
        }
    }
    fun read(id:String)=action{dao.readNotification(id,System.currentTimeMillis())}
    fun readAll()=action{dao.readAll(System.currentTimeMillis())}
    fun test(){
        val sent=JourneyAlerts.test(context)
        if(!sent)_state.value=_state.value.copy(error="Android no permite notificaciones. Revisa los permisos.")
    }
}

internal object JourneyAlerts{
    const val ALARM="lf.journey.ALARM"
    const val SNOOZE="lf.journey.SNOOZE"
    const val COMPLETE="lf.journey.COMPLETE"
    const val OPEN="lf.journey.OPEN"
    private const val NORMAL="lf_journey_normal"
    private const val HIGH="lf_journey_high"
    private const val QUIET="lf_journey_quiet"
    private const val TAG="lf-journey:"
    private const val NOTIFICATION_ID=4019
    private fun manager(context:Context)=context.getSystemService(NotificationManager::class.java)
    fun setup(context:Context){
        manager(context).createNotificationChannel(NotificationChannel(NORMAL,"Recordatorios operativos",NotificationManager.IMPORTANCE_DEFAULT))
        manager(context).createNotificationChannel(NotificationChannel(HIGH,"Briefings y avisos prioritarios",NotificationManager.IMPORTANCE_HIGH))
        manager(context).createNotificationChannel(NotificationChannel(QUIET,"Recordatorios silenciosos",NotificationManager.IMPORTANCE_LOW).apply{
            setSound(null,null);enableVibration(false)
        })
    }
    fun allowed(context:Context)=UpdateNotifications.allowed(context)
    fun exactAllowed(context:Context)=Build.VERSION.SDK_INT<31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    private fun pendingAlarm(context:Context)=PendingIntent.getBroadcast(context,4020,
        Intent(context,JourneyReceiver::class.java).setAction(ALARM),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun schedule(context:Context,at:Long?){
        val alarm=context.getSystemService(AlarmManager::class.java)
        val intent=pendingAlarm(context)
        alarm.cancel(intent)
        if(at==null)return
        val target=at.coerceAtLeast(System.currentTimeMillis()+1000)
        try{
            if(exactAllowed(context))alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,target,intent)
            else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,target,intent)
        }catch(_:SecurityException){alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,target,intent)}
    }
    fun cancel(context:Context,key:String){manager(context).cancel(TAG+key,NOTIFICATION_ID)}
    fun cancelObsolete(context:Context,keys:Set<String>){
        manager(context).activeNotifications.filter{it.tag?.startsWith(TAG)==true && it.tag.removePrefix(TAG) !in keys}
            .forEach{manager(context).cancel(it.tag,it.id)}
    }
    fun route(intent:Intent):JourneyNavigation?{
        if(intent.action!=OPEN)return null
        val module=intent.getStringExtra("module")?:return null
        val date=intent.getStringExtra("date")?:return null
        if(module !in JourneyRules.MODULES||runCatching{LocalDate.parse(date)}.isFailure)return null
        val slot=intent.getIntExtra("slot",-1).takeIf{BriefingRules.validSlot(it)}
        return JourneyNavigation(module,date,slot,intent.getStringExtra("key").orEmpty(),intent.getStringExtra("notification").orEmpty())
    }
    private fun open(context:Context,log:JourneyNotificationEntity)=PendingIntent.getActivity(context,4021,
        Intent(context,MainActivity::class.java).setAction(OPEN).setData(Uri.parse("lasflores://journey/"+Uri.encode(log.id)))
            .putExtra("module",log.module).putExtra("date",log.dateIso).putExtra("slot",log.sessionSlot?:-1)
            .putExtra("key",log.taskKey).putExtra("notification",log.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun post(context:Context,task:JourneyTask,log:JourneyNotificationEntity,p:JourneyPreferencesEntity):Boolean{
        setup(context)
        if(!allowed(context))return false
        val channel=if(task.priority=="HIGH")HIGH else if(JourneyRules.quiet(log.atMillis,p,ZoneId.systemDefault()))QUIET else NORMAL
        if(manager(context).getNotificationChannel(channel)?.importance==NotificationManager.IMPORTANCE_NONE)return false
        val snooze=PendingIntent.getBroadcast(context,4022,
            Intent(context,JourneyReceiver::class.java).setAction(SNOOZE)
                .setData(Uri.parse("lasflores://snooze/"+Uri.encode(task.key))).putExtra("key",task.key),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return runCatching{
            val builder=Notification.Builder(context,channel)
                .setSmallIcon(pe.lasflores.controlinterno.R.drawable.ic_lf_bell).setContentTitle(task.title)
                .setContentText(task.detail).setStyle(Notification.BigTextStyle().bigText(task.detail))
                .setContentIntent(open(context,log)).setAutoCancel(true).setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(Notification.Builder(context,channel)
                    .setSmallIcon(pe.lasflores.controlinterno.R.drawable.ic_lf_bell)
                    .setContentTitle("Las Flores").setContentText("Tienes una actividad operativa pendiente").build())
                .addAction(Notification.Action.Builder(null,"Posponer 10 min",snooze).build())
            if(task.manual){
                val complete=PendingIntent.getBroadcast(context,4024,Intent(context,JourneyReceiver::class.java).setAction(COMPLETE)
                    .setData(Uri.parse("lasflores://complete/"+Uri.encode(task.key))).putExtra("key",task.key),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                builder.addAction(Notification.Action.Builder(null,"Completar",complete).build())
            }
            manager(context).notify(TAG+task.key,NOTIFICATION_ID,builder.build())
            true
        }.getOrDefault(false)
    }
    fun test(context:Context):Boolean{
        setup(context)
        if(!allowed(context))return false
        if(manager(context).getNotificationChannel(NORMAL)?.importance==NotificationManager.IMPORTANCE_NONE)return false
        return runCatching{
            manager(context).notify(4023,Notification.Builder(context,NORMAL)
                .setSmallIcon(pe.lasflores.controlinterno.R.drawable.ic_lf_bell).setContentTitle("Las Flores · Notificación de prueba")
                .setContentText("Los avisos operativos están funcionando.").setAutoCancel(true).build())
            true
        }.getOrDefault(false)
    }
}
