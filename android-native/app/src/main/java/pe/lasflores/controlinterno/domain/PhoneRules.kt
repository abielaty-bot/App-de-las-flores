package pe.lasflores.controlinterno.domain

import pe.lasflores.controlinterno.data.*

object PhoneRules{
    const val YES="YES"
    const val NO="NO"
    const val NA="NA"
    const val PENDING="PENDING"
    val STATUSES=setOf(YES,NO,NA,PENDING)
    val REASONS=listOf("No trajo celular","Autorización de Administración")
    fun label(status:String)=when(status){
        YES->"Entregó";NO->"No entregó";NA->"No aplica";else->"Sin revisar"
    }
    fun valid(row:PhoneCheckEntity):Boolean=runCatching{
        StaffScheduleRules.parseDate(row.dateIso)
        row.staffId.isNotBlank()&&StaffName.isValid(row.nameSnapshot)&&row.roleSnapshot in STAFF_ROLES&&
            row.status in STATUSES&&row.note.length<=500&&row.updatedAtMillis>=0&&
            (if(row.status==NA)row.reason in REASONS else row.reason.isEmpty())&&
            (row.status!=PENDING||row.note.isEmpty())
    }.getOrDefault(false)
    data class Counts(val yes:Int=0,val no:Int=0,val na:Int=0,val pending:Int=0){
        val total get()=yes+no+na+pending
        val reviewed get()=yes+no+na
        val percent:Int? get()=if(yes+no==0)null else (100L*yes/(yes+no)).toInt()
    }
    fun counts(rows:List<PhoneCheckEntity>)=Counts(rows.count{it.status==YES},rows.count{it.status==NO},rows.count{it.status==NA},rows.count{it.status==PENDING})
    /** Los registros ya marcados sobreviven cambios de horario. Los pendientes de hoy
     * se ajustan al personal programado; las jornadas anteriores quedan congeladas. */
    fun roster(day:String,people:List<StaffEntity>,vacations:List<StaffVacationEntity>,
        overrides:List<StaffDayOverrideEntity>,stored:List<PhoneCheckEntity>,sync:Boolean):List<PhoneCheckEntity>{
        val old=stored.associateBy{it.staffId}
        if(stored.isNotEmpty()&&!sync)return stored.sortedBy{it.nameSnapshot.lowercase()}
        val eligible=people.filter{StaffScheduleRules.isScheduled(StaffScheduleRules.status(it,day,vacations,overrides))}
        val result=eligible.map{p->old[p.id]?:PhoneCheckEntity(day,p.id,p.fullName,p.role,PENDING,"","",0)}
        return (result+stored.filter{it.status!=PENDING&&it.staffId !in result.map{r->r.staffId}.toSet()})
            .sortedBy{it.nameSnapshot.lowercase()}
    }
    fun toggle(old:PhoneCheckEntity,status:String,reason:String="",note:String="",now:Long):PhoneCheckEntity{
        require(status in STATUSES)
        val next=if(old.status==status)PENDING else status
        return old.copy(status=next,reason=if(next==NA)reason else "",note=if(next==PENDING)"" else note.trim(),
            updatedAtMillis=now).also{require(valid(it)){"Revisa el motivo y la observación."}}
    }
}
