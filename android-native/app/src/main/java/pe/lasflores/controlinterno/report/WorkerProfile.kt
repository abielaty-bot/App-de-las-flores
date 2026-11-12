package pe.lasflores.controlinterno.report

import pe.lasflores.controlinterno.data.*
import pe.lasflores.controlinterno.domain.*

/** Read-only projection of the existing report snapshot. No synthetic absences. */
data class WorkerProfileData(
    val inspections:List<UniformInspectionEntity>,
    val answers:List<UniformAnswerEntity>,
    val events:List<UniformEventEntity>,
    val briefings:List<BriefingAttendanceEntity>,
    val phones:List<PhoneCheckEntity>,
    val functions:List<FunctionCheckEntity>,
    val incidents:List<IncidentEntity>,
    val deliveries:List<MaterialDeliveryEntity>
){
    val finalized get()=inspections.filter{it.workflow=="FINALIZED"}
    val conforming get()=finalized.count{it.initialResult==UniformRules.CONFORME}
    val uniformPercent:Int? get()=percent(conforming,finalized.size)
    val briefingStats get()=BriefingRules.stats(briefings)
    val phoneStats get()=PhoneRules.counts(phones)
    val functionStats get()=FunctionRules.summarize(functions)
    val incidentOpen get()=incidents.count(IncidentRules::isOpen)
    val currentDeliveryUnits get()=deliveries.filter{it.status=="ENTREGADA"}.sumOf{it.quantity}
    companion object{
        fun percent(ok:Int,total:Int):Int?=if(total==0)null else (100L*ok/total).toInt()
        fun from(s:ReportSnapshot,selection:ReportSelection):WorkerProfileData{
            require(selection.staffId!=null)
            val ins=s.inspections.filter{selection.matches(it.staffId)&&selection.includes(it.dateIso)}.sortedByDescending{it.dateIso}
            val ids=ins.map{it.id}.toSet()
            val excludedPhoneDays=s.activityDays.filter{it.module=="celulares"&&it.status=="NO_REALIZADA"}.map{it.dateIso}.toSet()
            return WorkerProfileData(ins,s.uniformAnswers.filter{it.inspectionId in ids},s.uniformEvents.filter{it.inspectionId in ids},
                BriefingRules.activeRecords(s.briefingAttendance,s.briefingSessions).filter{selection.matches(it.staffId)&&selection.includes(it.dateIso)}.sortedByDescending{it.dateIso},
                s.phoneChecks.filter{selection.matches(it.staffId)&&selection.includes(it.dateIso)&&it.dateIso !in excludedPhoneDays}.sortedByDescending{it.dateIso},
                s.functions.filter{selection.matches(it.staffId)&&selection.includes(it.dateIso)}.sortedByDescending{it.dateIso},
                s.incidents.filter{selection.matches(it.assignedStaffId)&&selection.includes(it.occurredDate)}.sortedByDescending{it.occurredDate},
                s.deliveries.filter{selection.matches(it.staffId)&&selection.includes(it.dateIso)}.sortedByDescending{it.dateIso})
        }
    }
}
