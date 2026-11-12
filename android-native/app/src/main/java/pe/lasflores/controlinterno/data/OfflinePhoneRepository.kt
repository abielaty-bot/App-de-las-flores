package pe.lasflores.controlinterno.data

import androidx.room3.withWriteTransaction
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import pe.lasflores.controlinterno.domain.PhoneRules
import pe.lasflores.controlinterno.domain.StaffScheduleRules

class OfflinePhoneRepository(private val db:LasFloresDatabase){
    private val dao=db.phoneDao()
    val checks=dao.observeChecks()
    val days=dao.observeDays()
    private fun validDay(date:String){require(!StaffScheduleRules.parseDate(date).isAfter(LocalDate.now())){"No se puede revisar una fecha futura."}}
    private suspend fun roster(date:String,sync:Boolean):List<PhoneCheckEntity>{
        val stored=dao.dayChecks(date)
        return PhoneRules.roster(date,db.staffDao().allStaff(),db.staffDao().allVacations(),db.staffDao().allOverrides(),stored,sync)
    }
    private suspend fun saveRoster(date:String,rows:List<PhoneCheckEntity>){
        val ids=rows.map{it.staffId}.toSet()
        dao.dayChecks(date).filter{it.status==PhoneRules.PENDING&&it.staffId !in ids}.forEach{dao.removePending(date,it.staffId)}
        rows.forEach{dao.save(it)}
    }
    suspend fun mark(date:String,staffId:String,status:String,reason:String="",note:String="")=db.withWriteTransaction{
        validDay(date)
        require(dao.dayStatus(date)==null){"Reabre la jornada antes de registrar."}
        val rows=roster(date,date==LocalDate.now().toString())
        val current=rows.firstOrNull{it.staffId==staffId}?:error("El trabajador no está programado para esta revisión.")
        saveRoster(date,rows)
        dao.save(PhoneRules.toggle(current,status,reason,note,System.currentTimeMillis()))
    }
    suspend fun sync(date:String)=db.withWriteTransaction{
        validDay(date)
        require(dao.dayStatus(date)==null){"Reabre la jornada antes de sincronizar."}
        saveRoster(date,roster(date,true))
    }
    suspend fun noReview(date:String,reason:String)=db.withWriteTransaction{
        validDay(date)
        require(reason.trim().length in 3..500){"Indica el motivo de la jornada sin revisión."}
        require(dao.dayChecks(date).none{it.status!=PhoneRules.PENDING}){"Hay respuestas registradas; no se puede declarar una jornada sin revisión."}
        db.uniformDao().upsertDayStatus(DailyActivityEntity(date,"celulares","NO_REALIZADA",reason.trim(),System.currentTimeMillis()))
    }
    suspend fun reopen(date:String)=db.withWriteTransaction{validDay(date);dao.reopen(date)}
}
