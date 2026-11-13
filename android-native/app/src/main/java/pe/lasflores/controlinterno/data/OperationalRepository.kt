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
                if(t.frequency.contains("Mié",true) && day !in 3..5)continue
                if(t.deadline.contains("Mié/Jue/Vie",true) && day !in 3..5)continue
                val target=when {
                    t.groupName=="Abastecimiento OF-1" && day==3 -> "Edith"
                    t.groupName=="Abastecimiento OF-2" && day==4 -> "Edith"
                    else -> t.titular
                }
                val person=people.filter{norm(it.fullName).startsWith(norm(target)+" ") || norm(it.fullName)==norm(target)}.singleOrNull()
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
        require(old.correction=="NONE" || old.correction=="PENDING"){"La revisión tiene una corrección en curso"}
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
