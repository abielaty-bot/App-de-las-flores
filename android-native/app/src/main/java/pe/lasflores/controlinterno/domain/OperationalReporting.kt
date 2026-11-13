package pe.lasflores.controlinterno.domain

import java.time.LocalDate
import pe.lasflores.controlinterno.data.OperationalInstanceEntity

object OperationalReporting {
    fun periodRows(rows:List<OperationalInstanceEntity>,date:LocalDate,mode:String):List<OperationalInstanceEntity>{
        val start=when(mode){"Semanal"->date.minusDays((date.dayOfWeek.value-1).toLong());"Mensual"->date.withDayOfMonth(1);else->date}
        val end=when(mode){"Semanal"->start.plusDays(6);"Mensual"->start.plusMonths(1).minusDays(1);else->date}
        return rows.filter{r->
            if(r.periodKey.length==7)return@filter mode=="Mensual" && r.periodKey==date.toString().take(7)
            val d=runCatching{LocalDate.parse(r.periodKey.removeSuffix("-W"))}.getOrNull()?:return@filter false
            !d.isBefore(start) && !d.isAfter(end) && (!r.periodKey.endsWith("-W") || mode!="Diaria")
        }
    }
    data class Metrics(val total:Int,val reviewed:Int,val evaluated:Int,val conform:Int,val failures:Int,val verified:Int,val attributable:Int){
        val initial:Int? get()=if(evaluated==0)null else 100*conform/evaluated
        val final:Int? get()=if(evaluated==0)null else 100*(conform+verified)/evaluated
    }
    fun metrics(rows:List<OperationalInstanceEntity>)=Metrics(rows.size,rows.count{it.initialResult!="UNREVIEWED"},rows.count{it.initialResult in setOf("CONFORMING","NONCONFORMING")},rows.count{it.initialResult=="CONFORMING"},rows.count{it.initialResult=="NONCONFORMING"},rows.count{it.initialResult=="NONCONFORMING" && it.correction=="VERIFIED"},rows.count{it.initialResult=="NONCONFORMING" && it.attributable})
}
