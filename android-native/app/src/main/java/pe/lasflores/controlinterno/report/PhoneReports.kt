package pe.lasflores.controlinterno.report

import pe.lasflores.controlinterno.data.*
import pe.lasflores.controlinterno.domain.PhoneRules

object PhoneReports{
    fun build(checks:List<PhoneCheckEntity>,days:List<DailyActivityEntity>,selection:ReportSelection,includeEmpty:Boolean=true):List<ExcelSheet>{
        val noReview=days.filter{it.module=="celulares"&&it.status=="NO_REALIZADA"&&selection.includes(it.dateIso)}
        val excluded=noReview.map{it.dateIso}.toSet()
        val rows=checks.filter{selection.includes(it.dateIso)&&selection.matches(it.staffId)&&it.dateIso !in excluded}
        if(!includeEmpty&&rows.isEmpty()&&noReview.isEmpty())return emptyList()
        val summary=rows.groupBy{it.staffId}.map{(_,items)->
            val latest=items.maxBy{it.dateIso}
            val counts=PhoneRules.counts(items)
            listOf(latest.nameSnapshot,latest.roleSnapshot,counts.yes,counts.no,counts.na,counts.pending,
                counts.reviewed,counts.percent?.let{it.toString()+"%"}?:"Sin evaluación",
                items.filter{it.status==PhoneRules.NO}.map{it.dateIso}.sorted().joinToString(", "))
        }.sortedBy{it[0].toString()}
        val detail=rows.sortedWith(compareBy<PhoneCheckEntity>{it.dateIso}.thenBy{it.nameSnapshot}).map{
            listOf(it.dateIso,it.nameSnapshot,it.roleSnapshot,PhoneRules.label(it.status),it.reason,it.note)
        }+if(selection.staffId==null)noReview.sortedBy{it.dateIso}.map{
            listOf(it.dateIso,"","","No hubo revisión","",it.reason)
        }else emptyList()
        return listOf(
            ExcelSheet("Celulares resumen","Control de celulares",selection.subtitle+" · Sí/(Sí + No); pendientes y N/A excluidos",
                listOf("Trabajador","Puesto","Sí","No","No aplica","Sin revisar","Revisados","Cumplimiento","Fechas de incumplimiento"),summary),
            ExcelSheet("Celulares detalle","Control diario de celulares",selection.subtitle+" · No acredita custodia o devolución",
                listOf("Fecha","Trabajador","Puesto","Resultado","Motivo","Observación"),detail))
    }
}
