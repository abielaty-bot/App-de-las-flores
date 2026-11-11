package pe.lasflores.controlinterno.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import pe.lasflores.controlinterno.data.MaterialDeliveryEntity

/** Las alertas señalan posibles reposiciones repetidas; no atribuyen responsabilidad. */
object MaterialRules {
    private fun key(value:String) = value.trim().replace(Regex("\\s+")," ").lowercase()
    fun validDay(value:String):Boolean = runCatching {
        val date=LocalDate.parse(value)
        date.toString()==value && !date.isAfter(LocalDate.now())
    }.getOrDefault(false)

    fun recent(
        rows:Collection<MaterialDeliveryEntity>,
        staffId:String, material:String, dateIso:String
    ):List<MaterialDeliveryEntity> {
        val day=LocalDate.parse(dateIso)
        return rows.filter { row ->
            val date=runCatching{LocalDate.parse(row.dateIso)}.getOrNull()
            row.staffId==staffId && key(row.material)==key(material) &&
                row.status!="ANULADA" && date!=null &&
                ChronoUnit.DAYS.between(date,day) in 0L..30L
        }.sortedByDescending { it.dateIso }
    }

    data class MaterialBalance(val staffId:String,val staffName:String,
        val material:String,val units:Int,val deliveries:Int)

    /** Solo cuentas vigentes: una devolución completa retira todo el lote del balance. */
    fun balances(rows:Collection<MaterialDeliveryEntity>):List<MaterialBalance> =
        rows.filter{it.status=="ENTREGADA"}
            .groupBy { it.staffId to it.material }
            .map { (key,items) ->
                MaterialBalance(key.first,items.first().staffNameSnapshot,key.second,
                    items.sumOf{it.quantity},items.size)
            }.sortedWith(compareBy({it.staffName},{it.material}))

    fun byPeriod(rows:Collection<MaterialDeliveryEntity>,
        start:String,end:String,staffId:String?=null):List<MaterialDeliveryEntity> =
        rows.filter { it.dateIso>=start && it.dateIso<=end &&
            (staffId==null||it.staffId==staffId) }
            .sortedWith(compareBy({it.dateIso},{it.staffNameSnapshot},{it.material}))
}
