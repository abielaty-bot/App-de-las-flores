package pe.lasflores.controlinterno.domain

import pe.lasflores.controlinterno.data.*

object PhoneRules{
    const val YES="YES"
    const val VOLUNTARY="VOLUNTARY"
    const val REMINDED="REMINDED"
    const val NO="NO"
    const val NA="NA"
    const val PENDING="PENDING"
    val STATUSES=setOf(YES,VOLUNTARY,REMINDED,NO,NA,PENDING)
    val REASONS=listOf("No trajo celular","Autorización de Administración")
    fun label(status:String)=when(status){
        YES->"Entrega · modalidad desconocida";VOLUNTARY->"Voluntaria";REMINDED->"Con recordatorio";NO->"No entregó";NA->"No aplica";else->"Sin revisar"
    }
    data class Audit(val at:Long,val from:String,val to:String)
    fun audit(raw:String):List<Audit> = if(raw.isBlank())emptyList() else raw.lines().map{line->val f=line.split('|');require(f.size==3);Audit(f[0].toLong(),f[1],f[2]).also{require(it.at>=0&&it.from in STATUSES&&it.to in STATUSES)}}.also{events->require(events.zipWithNext().all{(a,b)->a.to==b.from&&a.at<=b.at})}
    fun valid(row:PhoneCheckEntity):Boolean=runCatching{
        StaffScheduleRules.parseDate(row.dateIso)
        row.staffId.isNotBlank()&&StaffName.isValid(row.nameSnapshot)&&row.roleSnapshot in STAFF_ROLES&&
            row.status in STATUSES&&audit(row.auditTrail).let{it.isEmpty()||it.last().to==row.status}&&row.note.length<=500&&row.updatedAtMillis>=0&&
            (if(row.status==NA)row.reason in REASONS else row.reason.isEmpty())&&
            (row.status!=PENDING||row.note.isEmpty())
    }.getOrDefault(false)
    data class Counts(val yes:Int=0,val no:Int=0,val na:Int=0,val pending:Int=0,val voluntary:Int=0,val reminded:Int=0,val unknown:Int=0){
        val initialPercent:Int? get()=if(voluntary+reminded+no==0)null else (100L*voluntary/(voluntary+reminded+no)).toInt()
        val total get()=yes+no+na+pending
        val reviewed get()=yes+no+na
        val percent:Int? get()=if(yes+no==0)null else (100L*yes/(yes+no)).toInt()
    }
    fun counts(rows:List<PhoneCheckEntity>)=Counts(rows.count{it.status in setOf(YES,VOLUNTARY,REMINDED)},rows.count{it.status==NO},rows.count{it.status==NA},rows.count{it.status==PENDING},rows.count{it.status==VOLUNTARY},rows.count{it.status==REMINDED},rows.count{it.status==YES})
    /** Los registros ya marcados sobreviven cambios de horario. Los pendientes de hoy
     * se ajustan al personal programado; las jornadas anteriores quedan congeladas. */
    fun roster(day:String,people:List<StaffEntity>,vacations:List<StaffVacationEntity>,
        overrides:List<StaffDayOverrideEntity>,stored:List<PhoneCheckEntity>,sync:Boolean):List<PhoneCheckEntity>{
        val old=stored.associateBy{it.staffId}
        if(stored.isNotEmpty()&&!sync)return stored.sortedBy{it.nameSnapshot.lowercase()}
        val eligible=people.filter{StaffScheduleRules.isScheduled(StaffScheduleRules.status(it,day,vacations,overrides))}
        val result=eligible.map{p->old[p.id]?:PhoneCheckEntity(day,p.id,p.fullName,p.role,PENDING,"","",0)}
        return (result+stored.filter{(it.status!=PENDING||it.auditTrail.isNotBlank())&&it.staffId !in result.map{r->r.staffId}.toSet()})
            .sortedBy{it.nameSnapshot.lowercase()}
    }
    /** La lista activa solo incluye jornadas programadas. Los registros de personas fuera
     * de turno se conservan por separado para auditoría, sin contarlos como pendientes. */
    data class DayRoster(
        val scheduled:List<PhoneCheckEntity>,
        val outsideSchedule:List<PhoneCheckEntity>
    )
    fun rosterGroups(day:String,people:List<StaffEntity>,vacations:List<StaffVacationEntity>,
        overrides:List<StaffDayOverrideEntity>,stored:List<PhoneCheckEntity>,sync:Boolean):DayRoster{
        val scheduledIds=people.filter{
            StaffScheduleRules.isScheduled(StaffScheduleRules.status(it,day,vacations,overrides))
        }.map{it.id}.toSet()
        val combined=roster(day,people,vacations,overrides,stored,sync)
        return DayRoster(
            scheduled=combined.filter{it.staffId in scheduledIds},
            outsideSchedule=combined.filter{
                it.staffId !in scheduledIds &&
                    (it.status!=PENDING || it.auditTrail.isNotBlank() ||
                        it.updatedAtMillis>0 || it.note.isNotBlank() || it.reason.isNotBlank())
            }
        )
    }
    fun toggle(old:PhoneCheckEntity,status:String,reason:String="",note:String="",now:Long):PhoneCheckEntity{
        require(status in STATUSES)
        val priorFailure=old.status in setOf(NO,REMINDED)||audit(old.auditTrail).any{it.from in setOf(NO,REMINDED)||it.to in setOf(NO,REMINDED)}
        val next=when { status==VOLUNTARY && priorFailure -> REMINDED; old.status==status -> PENDING; else -> status }
        val trail=if(next==old.status)old.auditTrail else listOf(old.auditTrail,"$now|${old.status}|$next").filter{it.isNotBlank()}.joinToString("\n")
        return old.copy(status=next,reason=if(next==NA)reason else "",note=if(next==PENDING)"" else note.trim(),
            updatedAtMillis=now,auditTrail=trail).also{require(valid(it)){"Revisa el motivo y la observación."}}
    }
}
