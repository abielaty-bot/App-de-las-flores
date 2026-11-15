package pe.lasflores.controlinterno.domain

import pe.lasflores.controlinterno.data.*
import pe.lasflores.controlinterno.report.ExcelSheet

object UniformIntelligence {
    data class Metric(val total:Int,val ok:Int,val observed:Int,val corrected:Int,val drafts:Int,val rectified:Int=0){
        val rate:Double? get()=percent(ok,total)
        val finalRate:Double? get()=percent(ok+corrected+rectified,total)
    }
    data class Detail(val inspection:UniformInspectionEntity,val requirementId:String,val label:String,val initial:String,val corrected:Boolean,val rectified:Boolean,val correctedAt:Long?=null)
    data class Requirement(val id:String,val label:String,val ok:Int,val bad:Int,val corrected:Int,val na:Int=0){
        val rate get()=percent(ok,ok+bad)
    }
    fun percent(n:Int,d:Int):Double?=if(d==0)null else 100.0*n/d
    fun label(v:Double?):String=v?.let{String.format(java.util.Locale.US,"%.1f%%",it)}?:"Sin datos"
    fun rows(s:UniformDaySnapshot,from:String,to:String,staffId:String?=null)=s.inspections.filter{
        it.dateIso in from..to && (staffId==null||it.staffId==staffId)
    }.sortedByDescending{it.dateIso}
    fun details(s:UniformDaySnapshot,rows:List<UniformInspectionEntity>):List<Detail>{
        val answers=s.answers.groupBy{it.inspectionId}
        val snapshots=s.requirements.groupBy{it.inspectionId}
        val events=s.events.groupBy{it.inspectionId to it.requirementId}
        return rows.filter{it.workflow=="FINALIZED"}.flatMap{i->
            val reqs=snapshots[i.id].orEmpty().associateBy{it.requirementId}
            answers[i.id].orEmpty().mapNotNull{a->
                val req=reqs[a.requirementId]
                if(req?.applicable==false)return@mapNotNull Detail(i,a.requirementId,req.labelSnapshot,UniformRules.NA,false,false)
                val ev=events[i.id to a.requirementId].orEmpty()
                val rect=ev.filter{it.action=="RECTIFICAR"}.minByOrNull{it.atMillis}
                val initial=rect?.fromStatus?:a.status
                if(!UniformRules.evaluated(initial)&&initial!=UniformRules.NA)return@mapNotNull null
                Detail(i,a.requirementId,req?.labelSnapshot?:a.labelSnapshot,initial,
                    initial==UniformRules.BAD && a.correctedAtMillis!=null && rect==null,rect!=null,a.correctedAtMillis)
            }
        }
    }
    fun metric(s:UniformDaySnapshot,rows:List<UniformInspectionEntity>):Metric{
        val final=rows.filter{it.workflow=="FINALIZED" && it.initialResult in setOf(UniformRules.CONFORME,UniformRules.OBSERVADO)}
        val ok=final.count{it.initialResult==UniformRules.CONFORME}
        val details=details(s,final).groupBy{it.inspection.id}
        val corrected=final.count{i->i.initialResult==UniformRules.OBSERVADO && details[i.id].orEmpty().filter{it.initial==UniformRules.BAD}.let{it.isNotEmpty()&&it.all{d->d.corrected}}}
        val rectified=final.count{i->i.initialResult==UniformRules.OBSERVADO && details[i.id].orEmpty().filter{it.initial==UniformRules.BAD}.let{it.isNotEmpty()&&it.any{d->d.rectified}&&it.all{d->d.corrected||d.rectified}}}
        return Metric(final.size,ok,final.size-ok,corrected,rows.count{it.workflow!="FINALIZED"},rectified)
    }
    fun requirements(details:List<Detail>)=details.groupBy{it.requirementId}.map{(id,items)->
        Requirement(id,items.maxBy{it.inspection.dateIso}.label,items.count{it.initial==UniformRules.OK},items.count{it.initial==UniformRules.BAD},items.count{it.corrected},items.count{it.initial==UniformRules.NA})
    }.sortedWith(compareByDescending<Requirement>{it.bad}.thenBy{it.label})
    fun dayStatus(s:UniformDaySnapshot,staffId:String,date:String):String {
        val i=s.inspections.firstOrNull{it.staffId==staffId&&it.dateIso==date}
        return if(i!=null)s.statusFor(i) else if(s.activityDays.any{it.dateIso==date&&it.module=="uniformes"&&it.status=="NO_REALIZADA"}) "No hubo revisión" else "Sin registro"
    }
    fun streak(rows:List<UniformInspectionEntity>):Int = rows.filter{it.workflow=="FINALIZED"}.sortedByDescending{it.dateIso}.takeWhile{it.initialResult==UniformRules.CONFORME}.size
    fun export(s:UniformDaySnapshot,rows:List<UniformInspectionEntity>,names:Map<String,String>,period:String):List<ExcelSheet>{
        val summary=rows.groupBy{it.staffId}.map{(id,items)->val m=metric(s,items);listOf(names[id]?:id,m.total,m.ok,m.observed,m.corrected,m.rectified,m.drafts,label(m.rate),label(m.finalRate))}
        val details=details(s,rows)
        val req=details.groupBy{it.inspection.staffId}.flatMap{(id,items)->requirements(items).map{listOf(names[id]?:id,it.label,it.ok,it.bad,it.corrected,it.na,label(it.rate))}}
        return listOf(
            ExcelSheet("Resumen","Uniform Intelligence",period,listOf("Trabajador","Finalizadas","Conformes","Observadas","Corregidas","Rectificadas","Borradores","Inicial","Final"),summary),
            ExcelSheet("Requisitos","Cumplimiento histórico",period,listOf("Trabajador","Requisito","Cumple","Incumple","Corregido","No aplica","Inicial"),req),
            ExcelSheet("Inspecciones","Historial",period,listOf("Fecha","Trabajador","Puesto","Estado","Resultado inicial"),rows.map{listOf(it.dateIso,names[it.staffId]?:it.staffId,it.roleSnapshot,it.workflow,it.initialResult)}),
            ExcelSheet("Detalle","Resultados por requisito",period,listOf("Fecha","Trabajador","Requisito","Inicial","Corregido","Rectificado"),details.map{listOf(it.inspection.dateIso,names[it.inspection.staffId]?:it.inspection.staffId,it.label,it.initial,if(it.corrected)"Sí" else "No",if(it.rectified)"Sí" else "No")})
        )
    }
}
