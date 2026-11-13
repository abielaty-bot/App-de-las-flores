package pe.lasflores.controlinterno.data

import android.content.Context
import androidx.room3.withWriteTransaction
import pe.lasflores.controlinterno.domain.StaffScheduleRules
import java.time.LocalDate
import java.util.UUID
import java.text.Normalizer

class OperationalRepository(context:Context, private val db:LasFloresDatabase) {
    private val dao=db.operationalDao()
    val catalog=OperationalCatalogRepository(context,dao)
    fun observe(period:String)=dao.observePeriod(period)
    fun events(id:String)=dao.observeEvents(id)
    suspend fun all()=dao.allInstances()
    fun observeAll()=dao.observeAllInstances()
    /** Read-only references: never manufacture a compliance result from another module. */
    suspend fun linkedSummary(templateId:String,date:LocalDate):String? {
        val day=date.toString()
        return when(templateId){
            "MAI-15-1" -> {
                val rows=db.uniformDao().allInspections().filter{it.dateIso==day}
                val finalized=rows.count{it.workflow=="FINALIZED"}
                "Uniformes: $finalized revisiones finalizadas de ${rows.size} registradas. Confirma la cobertura antes de marcar conforme."
            }
            "MAI-17-1" -> {
                val rows=db.phoneDao().dayChecks(day)
                "Celulares: ${rows.count{it.status=="YES"}} entregados, ${rows.count{it.status=="NO"}} no entregados, ${rows.count{it.status=="PENDING"}} pendientes, ${rows.count{it.status=="NA"}} no aplican."
            }
            "MAI-18-1" -> {
                val sessions=db.briefingDao().sessionsForDay(day)
                "Briefings: ${sessions.size} sesiones registradas. "+sessions.joinToString(" · "){it.scheduledTime+": "+it.status}
            }
            "MAI-16-1" -> {
                val incidents=db.incidentDao().allIncidents().filter{it.occurredDate==day && it.voidedAtMillis==null}
                "Incidentes registrados: ${incidents.size}. "+incidents.joinToString(" · "){it.code+": "+it.status}+". Confirma si existen incidentes aún no registrados."
            }
            else -> null
        }
    }
    suspend fun relatedFunctions(row:OperationalInstanceEntity,date:LocalDate):String? {
        val staffId=row.staffId ?: return null
        val checks=db.functionDao().allChecks().filter{it.dateIso==date.toString() && it.staffId==staffId && it.voidedAtMillis==null}
        if(checks.isEmpty())return null
        return "Funciones registradas del responsable (referencia, no equivalencia): "+checks.joinToString(" · "){it.taskLabelSnapshot+": "+it.result}
    }
    suspend fun evidence(id:String,encoded:String)=db.withWriteTransaction {
        require(dao.instance(id)!=null && encoded.length in 1..2000000)
        event(id,"PHOTO","",encoded,"Evidencia fotográfica")
    }
    suspend fun groupedEvidence(ids:List<String>,encoded:String)=db.withWriteTransaction {
        require(ids.isNotEmpty() && ids.size<=50 && encoded.length in 1..2000000)
        val controls=ids.distinct().map{dao.instance(it)?:error("Control inexistente")}
        require(controls.map{it.periodKey}.distinct().size==1 && controls.map{it.locationSnapshot}.distinct().size==1){"Selecciona controles de la misma zona y período"}
        val group=UUID.randomUUID().toString()
        controls.forEach{event(it.id,"PHOTO",group,encoded,"Evidencia agrupada")}
    }
    suspend fun correctionPlan(id:String,person:StaffEntity?,deadline:String,priority:String,cause:String)=db.withWriteTransaction {
        val old=dao.instance(id)?:error("Control inexistente")
        require(old.correction in setOf("PENDING","REPORTED","REOPENED"))
        require(priority in setOf("NORMAL","URGENT") && cause.isNotBlank())
        require(runCatching{java.time.LocalDateTime.parse(deadline)}.isSuccess){"Plazo inválido"}
        if(person!=null)require(db.staffDao().allStaff().any{it.id==person.id})
        val payload=org.json.JSONObject().put("staffId",person?.id ?: "").put("responsible",person?.fullName ?: "Maître").put("deadline",deadline).put("priority",priority).put("cause",cause).toString()
        event(id,"PLAN","",payload,"Plan de corrección")
    }
    fun period(t:OperationalTemplateEntity,date:LocalDate):String=when(t.frequency){
        "Semanal"->date.minusDays((date.dayOfWeek.value-1).toLong()).toString()+"-W"
        "Mensual"->date.toString().take(7)
        else->date.toString()
    }
    private fun norm(s:String)=Normalizer.normalize(s,Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"").lowercase()
    suspend fun available(date:LocalDate):List<StaffEntity> {
        val staff=db.staffDao()
        val vacations=staff.allVacations()
        val overrides=staff.allOverrides()
        return staff.allStaff().filter{StaffScheduleRules.isScheduled(StaffScheduleRules.status(it,date.toString(),vacations,overrides))}
    }
    suspend fun generate(date:LocalDate) {
        catalog.initialize()
        db.withWriteTransaction {
            val people=available(date)
            for(t in dao.templates().filter{it.active}) {
                val day=date.dayOfWeek.value
                if(day in 3..5 && t.id in setOf("SAL-14-1","SAL-15-1","SAL-16-1","LIM-19-1","LIM-20-1","LIM-21-1","LIM-22-1","LIM-25-1","LIM-26-1","LIM-27-1"))continue
                if(t.frequency.contains("Mié",true) && day !in 3..5)continue
                if(t.deadline.contains("Mié/Jue/Vie",true) && day !in 3..5)continue
                val target=when {
                    t.titular=="Edith" && day in 3..5 -> when(t.id){
                        "OFI-05-1","OFI-11-1","OFI-14-1"->"Leo"
                        "OFI-07-1","OFI-08-1","OFI-09-1","OFI-10-1"->"Álvaro"
                        else->"Dina"
                    }
                    t.groupName=="Abastecimiento OF-1" && day==3 -> "Edith"
                    t.groupName=="Abastecimiento OF-2" && day==4 -> "Edith"
                    else -> t.titular
                }
                val person=people.filter{norm(it.fullName).startsWith(norm(target)+" ") || norm(it.fullName)==norm(target)}.singleOrNull()
                // Keep the canonical office control; the overlapping support template is suppressed below.
                val key=period(t,date)
                dao.insertInstance(OperationalInstanceEntity(t.id+":"+key,key,t.id,t.version,t.label,t.standard,t.location,person?.id,person?.fullName ?: if(target=="Maître")"Maître" else "Sin asignar","UNREVIEWED","NONE","",false,null,System.currentTimeMillis()))
            }
        }
    }
    suspend fun assign(id:String,person:StaffEntity,date:LocalDate) = db.withWriteTransaction {
        require(available(date).any{it.id==person.id})
        val old=dao.instance(id)?:error("Control inexistente")
        require(old.initialResult=="UNREVIEWED"){"No se puede cambiar el responsable de una revisión histórica"}
        dao.saveInstance(old.copy(staffId=person.id,staffNameSnapshot=person.fullName,updatedAtMillis=System.currentTimeMillis()))
        event(id,"ASSIGN",old.staffNameSnapshot,person.fullName,"")
    }
    suspend fun review(id:String,result:String,note:String,attributable:Boolean=false) = db.withWriteTransaction {
        require(result in setOf("UNREVIEWED","CONFORMING","NONCONFORMING","BLOCKED","NOT_APPLICABLE"))
        require(result !in setOf("BLOCKED","NOT_APPLICABLE","NONCONFORMING") || note.isNotBlank())
        val old=dao.instance(id)?:error("Control inexistente")
        require(old.initialResult=="UNREVIEWED" || result=="UNREVIEWED" && old.correction in setOf("NONE","PENDING")){"Conserva el resultado inicial y utiliza el seguimiento de correcciones"}
        require(result!="UNREVIEWED" || note.isNotBlank()){"Indica el motivo de anulación"}
        dao.saveInstance(old.copy(initialResult=result,correction=if(result=="NONCONFORMING"||result=="BLOCKED")"PENDING" else "NONE",note=note,attributable=attributable && result=="NONCONFORMING" && old.staffId!=null,reviewedAtMillis=if(result=="UNREVIEWED")null else System.currentTimeMillis(),updatedAtMillis=System.currentTimeMillis()))
        event(id,"REVIEW",old.initialResult,result,note)
    }
    suspend fun correct(id:String,next:String,note:String)=db.withWriteTransaction {
        val old=dao.instance(id)?:error("Control inexistente")
        require(note.isNotBlank())
        val allowed=when(old.correction){"PENDING","REOPENED"->setOf("REPORTED");"REPORTED"->setOf("VERIFIED","REOPENED");"VERIFIED"->setOf("REOPENED");else->emptySet()}
        require(next in allowed)
        dao.saveInstance(old.copy(correction=next,updatedAtMillis=System.currentTimeMillis()))
        event(id,"CORRECTION",old.correction,next,note)
    }
    private suspend fun event(id:String,action:String,before:String,after:String,note:String) {
        dao.addEvent(OperationalEventEntity(UUID.randomUUID().toString(),id,action,before,after,note,System.currentTimeMillis()))
    }
}
