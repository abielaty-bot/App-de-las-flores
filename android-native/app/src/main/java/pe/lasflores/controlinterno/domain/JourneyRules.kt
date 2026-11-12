package pe.lasflores.controlinterno.domain

import java.time.*
import pe.lasflores.controlinterno.data.*

data class JourneyTask(
    val key:String,val dateIso:String,val title:String,val detail:String,val module:String,
    val dueAtMillis:Long,val alertAtMillis:Long,val completed:Boolean=false,
    val applicable:Boolean=true,val manual:Boolean=false,val notify:Boolean=true,
    val priority:String="NORMAL",val sessionSlot:Int?=null
)
data class JourneyData(
    val scheduledCount:Int=0,val reviewedCount:Int=0,val noUniformReview:Boolean=false,
    val sessions:List<BriefingSessionEntity> = emptyList(),val noBriefing:Boolean=false,
    val pendingBriefings:Int=0,val functionCount:Int=0,val pendingFunctions:Int=0,
    val openIncidents:Int=0,val urgentIncidents:Int=0,val communicationRequired:Boolean=false,
    val phoneTotal:Int=0,val phoneReviewed:Int=0,val noPhoneReview:Boolean=false
)
object JourneyRules{
    val RECURRENCES=setOf("ONCE","DAILY","WEEKLY","MONTHLY")
    val MODULES=setOf("inicio","personal","briefings","uniformes","funciones","incidentes","reportes","backups","notifications","celulares")
    val PRIORITIES=setOf("NORMAL","HIGH")
    fun validPreferences(p:JourneyPreferencesEntity):Boolean=p.id==1 &&
        listOf(p.startTime,p.endTime,p.uniformsTime,p.informTime,p.functionsTime,p.summaryTime,p.quietStart,p.quietEnd,p.phoneTime).all(BriefingRules::validTime) &&
        p.startTime<p.endTime && p.workingDaysMask in 1..127 && p.briefingLead in 0..60
    fun validReminder(r:JourneyReminderEntity):Boolean=r.id.isNotBlank()&&r.id.length<=100&&r.title.trim().length in 3..140 &&
        runCatching{LocalDate.parse(r.startDateIso).let{it.year in 1970..9999&&it.toString()==r.startDateIso}}.getOrDefault(false)&&BriefingRules.validTime(r.time)&&
        r.recurrence in RECURRENCES && r.daysMask in 0..127 && (r.recurrence!="WEEKLY"||r.daysMask>0) &&
        r.leadMinutes in 0..1440 && r.priority in PRIORITIES && r.module in MODULES
    fun millis(day:LocalDate,time:String,zone:ZoneId):Long=day.atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()
    fun workday(day:LocalDate,p:JourneyPreferencesEntity)=StaffScheduleRules.isRestDay(p.workingDaysMask,day)
    fun occurs(r:JourneyReminderEntity,day:LocalDate,p:JourneyPreferencesEntity):Boolean{
        if(!r.enabled||!validReminder(r))return false
        val start=LocalDate.parse(r.startDateIso)
        if(day<start || (r.workdaysOnly&&!workday(day,p)))return false
        return when(r.recurrence){
            "ONCE"->day==start
            "DAILY"->true
            "WEEKLY"->StaffScheduleRules.isRestDay(r.daysMask,day)
            "MONTHLY"->day.dayOfMonth==start.dayOfMonth
            else->false
        }
    }
    fun quiet(at:Long,p:JourneyPreferencesEntity,zone:ZoneId):Boolean{
        if(!p.quietEnabled || p.quietStart==p.quietEnd)return false
        val time=Instant.ofEpochMilli(at).atZone(zone).toLocalTime()
        val start=LocalTime.parse(p.quietStart);val end=LocalTime.parse(p.quietEnd)
        return if(start<end)time>=start&&time<end else time>=start||time<end
    }
    fun build(day:LocalDate,zone:ZoneId,p:JourneyPreferencesEntity,defs:List<JourneyReminderEntity>,data:JourneyData,
        occurrences:List<JourneyOccurrenceEntity>):List<JourneyTask>{
        val date=day.toString();val stored=occurrences.associateBy{it.taskKey};val list=mutableListOf<JourneyTask>()
        fun built(id:String,title:String,detail:String,module:String,time:String,done:Boolean=false,
            applies:Boolean=true,notify:Boolean=true,priority:String="NORMAL",lead:Int=0,slot:Int?=null){
            val due=millis(day,time,zone)
            list.add(JourneyTask(id+":"+date,date,title,detail,module,due,due-lead*60_000L,done,applies,false,notify,priority,slot))
        }
        val work=workday(day,p)
        val live=data.sessions.filter{it.status!="CANCELADO"&&it.status!="HISTORICO"}
        if(work){
            built("plan","Planificar briefing","Define las sesiones del día o registra que no habrá briefing.","briefings",p.startTime,
                data.noBriefing||data.sessions.any{it.status!="CANCELADO"},notify=p.briefingAlerts)
            built("uniform","Control de uniformes",
                data.reviewedCount.toString()+" de "+data.scheduledCount+" trabajadores programados revisados",
                "uniformes",p.uniformsTime,data.reviewedCount>=data.scheduledCount,
                !data.noUniformReview&&data.scheduledCount>0,p.uniformAlerts)
            built("phone","Control de celulares",data.phoneReviewed.toString()+" de "+data.phoneTotal+" trabajadores revisados",
                "celulares",p.phoneTime,data.phoneReviewed>=data.phoneTotal,!data.noPhoneReview&&data.phoneTotal>0,p.phoneAlerts)
            built("inform","Comunicación presencial",data.pendingBriefings.toString()+" trabajadores por informar (incluye fechas anteriores)",
                "briefings",p.informTime,data.pendingBriefings==0,data.communicationRequired,p.briefingAlerts)
            built("functions","Verificar funciones",data.pendingFunctions.toString()+" de "+data.functionCount+" asignaciones sin evaluar",
                "funciones",p.functionsTime,data.pendingFunctions==0,data.functionCount>0,p.functionAlerts)
        }
        live.forEach{s->
            if(!BriefingRules.validTime(s.scheduledTime))return@forEach
            val time=s.scheduledTime
            built("briefing-"+s.groupHour,"Briefing · "+time,
                BriefingRules.participants(s).size.toString()+" convocados · "+BriefingRules.stateLabel(s.status),
                "briefings",time,s.status=="REALIZADO",true,p.briefingAlerts&&s.status=="PROGRAMADO","HIGH",0,s.groupHour)
            if(p.briefingLead>0 && s.status=="PROGRAMADO"){
                val due=millis(day,time,zone)-p.briefingLead*60_000L
                list.add(JourneyTask("prep-"+s.groupHour+":"+date,date,"Preparar briefing · "+time,
                    "Revisa las indicaciones de las áreas.","briefings",due,due,false,true,false,p.briefingAlerts,"HIGH",s.groupHour))
            }
        }
        defs.filter{occurs(it,day,p)}.forEach{r->
            val key="manual-"+r.id+":"+date
            val due=millis(day,r.time,zone)
            list.add(JourneyTask(key,date,r.title,"Recordatorio personal",r.module,due,due-r.leadMinutes*60_000L,
                stored[key]?.completedAtMillis!=null,true,true,true,r.priority))
        }
        if(work && data.urgentIncidents>0)
            built("incident","Incidentes prioritarios",data.urgentIncidents.toString()+" casos de prioridad alta o crítica abiertos",
                "incidentes",p.informTime,false,true,p.incidentAlerts,"HIGH")
        if(work&&p.summaryAlerts){
            val pending=list.count{it.applicable&&!it.completed&&!it.key.startsWith("prep-")}
            built("summary","Revisar pendientes de la jornada",pending.toString()+" actividades pendientes",
                "inicio",p.summaryTime,pending==0,true,true)
        }
        return list.sortedWith(compareBy<JourneyTask>{it.dueAtMillis}.thenBy{it.key})
    }
    fun isProgressTask(task:JourneyTask)=task.applicable&&!task.key.startsWith("prep-")&&!task.key.startsWith("summary:")
    fun nextAlert(task:JourneyTask,old:JourneyOccurrenceEntity?,now:Long):Long?{
        if(task.completed||!task.applicable||!task.notify)return null
        val same=old?.dueAtMillis==task.alertAtMillis
        val previous=old.takeIf{same}
        val snooze=previous?.snoozedUntilMillis
        if((previous?.notificationCount?:0)>=4 && snooze==null)return null
        if(previous?.lastNotifiedAtMillis!=null && snooze==null)return null
        val target=snooze?:task.alertAtMillis
        if(target<now-60*60_000L)return null
        return target
    }
}
