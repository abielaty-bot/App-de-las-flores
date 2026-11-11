package pe.lasflores.controlinterno.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import pe.lasflores.controlinterno.data.IncidentEntity
import pe.lasflores.controlinterno.data.IncidentTypeEntity

object IncidentRules {
    const val PRODUCTO="producto"
    const val OPERATIVO="operativo"
    const val REGISTRADO="registrado"
    const val EN_ATENCION="en-atencion"
    const val VERIFICACION="verificacion"
    const val CERRADO="cerrado"
    const val ANULADO="anulado"
    val CATEGORIES=setOf(PRODUCTO,OPERATIVO)
    val PRIORITIES=setOf("baja","media","alta","critica")
    val STATUSES=setOf(REGISTRADO,EN_ATENCION,VERIFICACION,CERRADO,ANULADO)
    val ACTIONS=setOf("correccion","comunicacion","investigacion","prevencion","verificacion")
    val AREAS=listOf("Cocina","Azafate","Barra","Repostería","Salón/Mozos",
        "Recepción","Caja/POS","Logística/Almacén","Otra")
    val IMPACTS=listOf("No determinado","Antes de servir",
        "En mesa / cliente afectado","Después del consumo",
        "Servicio interrumpido o retrasado","Otro")

    fun categoryName(v:String)=when(v){PRODUCTO->"Alimentos y bebidas";OPERATIVO->"Operaciones y servicio";else->v}
    fun statusName(v:String)=when(v) {
        REGISTRADO->"Registrado";EN_ATENCION->"En atención"
        VERIFICACION->"Pendiente de verificación";CERRADO->"Cerrado"
        ANULADO->"Anulado";else->v
    }
    fun priorityName(v:String)=when(v){
        "baja"->"Baja";"media"->"Media";"alta"->"Alta";"critica"->"Crítica";else->v
    }
    fun actionName(v:String)=when(v){
        "correccion"->"Corrección";"comunicacion"->"Comunicación"
        "investigacion"->"Investigación";"prevencion"->"Prevención"
        "verificacion"->"Verificación";"reapertura"->"Reapertura"
        "anulacion"->"Anulación";else->v
    }
    fun validDateTime(day:String,time:String):Boolean=runCatching {
        LocalDate.parse(day).toString()==day &&
            LocalTime.parse(time,DateTimeFormatter.ofPattern("HH:mm"))
                .format(DateTimeFormatter.ofPattern("HH:mm"))==time
    }.getOrDefault(false)

    fun canClose(oldStatus:String,nextStatus:String,kind:String):Boolean =
        nextStatus != CERRADO || oldStatus == CERRADO || kind=="verificacion"

    fun isOpen(item:IncidentEntity) = item.status !in setOf(CERRADO,ANULADO)
    fun nextCode(year:String,existingCodes:Collection<String>):String {
        require(Regex("""\d{4}""").matches(year)){"Año incorrecto."}
        val prefix="INC-$year-"
        val seq=(existingCodes.mapNotNull { if(it.startsWith(prefix))
            it.removePrefix(prefix).toIntOrNull() else null }.maxOrNull()?:0)+1
        return prefix+seq.toString().padStart(4,'0')
    }

    fun seedTypes():List<IncidentTypeEntity> {
        val data=listOf(
            Triple(PRODUCTO,"inc-p-coccion","Cocción incorrecta"),
            Triple(PRODUCTO,"inc-p-cuerpo","Cuerpo extraño (pelo, insecto u otro)"),
            Triple(PRODUCTO,"inc-p-sabor","Sabor fuera de estándar"),
            Triple(PRODUCTO,"inc-p-temperatura","Temperatura incorrecta"),
            Triple(PRODUCTO,"inc-p-presentacion","Presentación deficiente"),
            Triple(PRODUCTO,"inc-p-producto","Producto equivocado o defectuoso"),
            Triple(OPERATIVO,"inc-o-demora","Demora excesiva"),
            Triple(OPERATIVO,"inc-o-comanda","Comanda incorrecta o extraviada"),
            Triple(OPERATIVO,"inc-o-equipo","Falla de equipo o POS"),
            Triple(OPERATIVO,"inc-o-stock","Producto no disponible sin aviso"),
            Triple(OPERATIVO,"inc-o-comunicacion","Falla de coordinación"),
            Triple(OPERATIVO,"inc-o-atencion","Incidente de atención al cliente")
        )
        return data.map { (category,id,label) ->
            IncidentTypeEntity(id,category,label,true,0L,0L)
        }
    }

    data class Stats(
        val total:Int, val products:Int, val operations:Int, val pending:Int,
        val closed:Int, val highPriorityOpen:Int
    )
    /** Los anulados quedan en historial, pero se excluyen del análisis. */
    fun stats(items:Collection<IncidentEntity>):Stats {
        val active=items.filter { it.status!=ANULADO }
        return Stats(
            active.size,active.count { it.category==PRODUCTO },
            active.count { it.category==OPERATIVO },
            active.count(::isOpen),active.count { it.status==CERRADO },
            active.count { isOpen(it) && it.priority in setOf("alta","critica") }
        )
    }
}
