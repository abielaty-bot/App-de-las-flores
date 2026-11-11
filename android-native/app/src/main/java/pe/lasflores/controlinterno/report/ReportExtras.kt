package pe.lasflores.controlinterno.report

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import pe.lasflores.controlinterno.data.*
import pe.lasflores.controlinterno.domain.MaterialRules
import pe.lasflores.controlinterno.domain.StaffScheduleRules

/** Desgloses especializados que complementan las 9 hojas de 4.0006.
 * Ningún campo presentado como causa atribuye culpabilidad automática.
 */
object ReportExtras {
    private val formatter=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private fun timestamp(value:Long?):String = value?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(formatter)
    }.orEmpty()

    fun summaryMetrics(snapshot:ReportSnapshot,sel:ReportSelection):List<List<Any?>> {
        val selected=MaterialRules.byPeriod(snapshot.deliveries,sel.start.toString(),
            sel.end.toString(),sel.staffId)
        val current=MaterialRules.balances(snapshot.deliveries)
            .filter{sel.staffId==null||it.staffId==sel.staffId}
        val finalized=snapshot.inspections.filter{
            sel.includes(it.dateIso)&&sel.matches(it.staffId)&&it.workflow=="FINALIZED"
        }
        val rectified=snapshot.uniformEvents.filter{
            it.action=="RECTIFICAR" && finalized.any{inspection->inspection.id==it.inspectionId}
        }
        return listOf(
            listOf("Entregas: registros del período",selected.size,
                "Incluye entregas devueltas y anuladas para trazabilidad"),
            listOf("Entregas: unidades no anuladas",selected.filter{
                it.status!="ANULADA"}.sumOf{it.quantity},
                "Unidades entregadas durante el período, aun cuando después se devolvieran"),
            listOf("Materiales: unidades actualmente vigentes",current.sumOf{it.units},
                "Balance actual de lotes no devueltos; no equivale a movimiento del período"),
            listOf("Uniformes: rectificaciones registradas",rectified.size,
                "Errores de marcación rectificados, no correcciones de incumplimientos"),
            listOf("Programación exportada","Reconstrucción actual",
                "No es asistencia laboral histórica ni demuestra quién trabajó realmente")
        )
    }

    fun build(snapshot:ReportSnapshot,sel:ReportSelection):List<ExcelSheet> {
        val names=snapshot.staff.associate{it.id to it.fullName}
        val period=sel.subtitle + " · " +
            (sel.staffId?.let{"Trabajador: "+(names[it]?:it)}?:"Todo el equipo")
        fun sheet(name:String,info:String,head:List<String>,rows:List<List<Any?>>) =
            ExcelSheet(name,"LAS FLORES · "+name,period+" · "+info,head,rows)

        val inspections=snapshot.inspections.filter{
            sel.includes(it.dateIso)&&sel.matches(it.staffId)
        }
        val byId=inspections.associateBy{it.id}
        val minQty=snapshot.inspectionSnapshots.associateBy{
            it.inspectionId to it.requirementId
        }
        val implementations=snapshot.uniformAnswers.filter{
            it.inspectionId in byId
        }.sortedWith(compareBy({byId[it.inspectionId]?.dateIso},{it.labelSnapshot}))
        val implementsRows=implementations.map { a ->
            val i=byId.getValue(a.inspectionId)
            listOf(i.dateIso,names[i.staffId]?:i.staffId,i.roleSnapshot,
                a.labelSnapshot,a.groupSnapshot,
                minQty[a.inspectionId to a.requirementId]?.minQtySnapshot?:0,
                a.quantity,""+when(a.status){
                    "OK"->"Cumple";"BAD"->"No cumple";else->"Pendiente"
                },timestamp(a.correctedAtMillis),a.correctedBy.orEmpty(),
                if(a.status=="BAD"&&a.correctedAtMillis!=null)
                    "Incumplimiento original corregido" else "")
        }

        val allEvents=snapshot.uniformEvents.filter{
            it.inspectionId in byId
        }.sortedBy{it.atMillis}
        fun eventRow(e:UniformEventEntity):List<Any?> {
            val i=byId.getValue(e.inspectionId)
            return listOf(i.dateIso,names[i.staffId]?:i.staffId,
                timestamp(e.atMillis),e.requirementId,e.action,
                e.fromStatus.orEmpty(),e.toStatus.orEmpty(),e.actor,e.note)
        }
        val auditRows=allEvents.map(::eventRow)
        val rectifyRows=allEvents.filter{it.action=="RECTIFICAR"}.map(::eventRow)

        // Reconstitución con reglas actuales. Evita afirmar que representa una asistencia histórica.
        val days=generateSequence(sel.start){it.plusDays(1)}
            .takeWhile{!it.isAfter(sel.end)}.toList()
        val workers=snapshot.staff.filter{sel.matches(it.id)}
        val scheduled=days.flatMap {day ->
            workers.map{person ->
                Triple(day.toString(),person,StaffScheduleRules.status(
                    person,day.toString(),snapshot.vacations,snapshot.overrides))
            }
        }
        val inspectionIndex=inspections.associateBy{it.dateIso to it.staffId}
        val noReviewDays=snapshot.activityDays.filter{
            it.module=="uniformes"&&it.status=="NO_REALIZADA"
        }.map{it.dateIso}.toSet()
        val pendingRows=scheduled.filter{StaffScheduleRules.isScheduled(it.third)}
            .mapNotNull{(date,worker,status)->
                val inspection=inspectionIndex[date to worker.id]
                when {
                    inspection?.workflow=="FINALIZED"->null
                    date in noReviewDays->listOf(date,worker.fullName,status,
                        "Jornada declarada sin revisión","No implica incumplimiento", "Según programación actual")
                    inspection!=null->listOf(date,worker.fullName,status,
                        "Inspección iniciada sin finalizar","No implica incumplimiento","Según programación actual")
                    else->listOf(date,worker.fullName,status,
                        "Sin inspección registrada","No implica incumplimiento","Según programación actual")
                }
            }
        val scheduleRows=scheduled.map{(date,person,status)->
            listOf(date,person.fullName,person.role,
                StaffScheduleRules.label(status),if(person.isActive)"Sí" else "No",
                if(snapshot.overrides.any{it.dateIso==date&&it.staffId==person.id})
                    "Excepción manual" else "Regla habitual o vacaciones",
                "Reconstrucción con datos actuales; no es asistencia histórica")
        }

        val selectedDeliveries=MaterialRules.byPeriod(snapshot.deliveries,
            sel.start.toString(),sel.end.toString(),sel.staffId)
        val deliveryRows=selectedDeliveries.map{d->
            listOf(d.dateIso,names[d.staffId]?:d.staffNameSnapshot,
                d.material,d.quantity,d.reason,d.deliveredBy,
                when(d.status){"ENTREGADA"->"Vigente";"DEVUELTA"->"Devuelta";
                    "ANULADA"->"Anulada";else->d.status},
                timestamp(d.closedAtMillis),d.closingNote)
        }
        val balances=MaterialRules.balances(snapshot.deliveries)
            .filter{sel.staffId==null||it.staffId==sel.staffId}
            .associateBy{it.staffId to it.material}
        val byWorkerMaterial=selectedDeliveries.groupBy{it.staffId to it.material}
        val materialRows=(byWorkerMaterial.keys+balances.keys).distinct()
            .sortedWith(compareBy({names[it.first]?:it.first},{it.second}))
            .map { (id,material) ->
                val current=byWorkerMaterial[id to material].orEmpty()
                val active=balances[id to material]
                listOf(names[id]?:current.firstOrNull()?.staffNameSnapshot ?:
                    active?.staffName?:id,material,
                    current.size,current.sumOf{it.quantity},
                    current.filter{it.status!="ANULADA"}.sumOf{it.quantity},
                    current.count{it.status=="DEVUELTA"},
                    current.count{it.status=="ANULADA"},
                    active?.units?:0,
                    "Saldo vigente a la exportación; movimientos según fecha de entrega")
            }

        val failed=snapshot.functions.filter{
            sel.includes(it.dateIso)&&sel.matches(it.staffId)&&
                it.voidedAtMillis==null&&it.result=="BAD"
        }
        val failureRows=failed.groupBy{
            Triple(it.staffId,it.areaSnapshot,it.taskLabelSnapshot)
        }.map{(key,entries)->
            listOf(names[key.first]?:entries.first().staffNameSnapshot,
                key.second,key.third,entries.size,
                entries.count{it.correctedAtMillis!=null},
                entries.size-entries.count{it.correctedAtMillis!=null},
                "Una función corregida conserva su incumplimiento inicial")
        }.sortedWith(compareByDescending<List<Any?>>{it[3] as Int}
            .thenBy{it[0].toString()})

        val incidents=snapshot.incidents.filter{
            sel.includes(it.occurredDate)&&
                (sel.staffId==null||it.assignedStaffId==sel.staffId)&&
                it.status!="anulado"
        }
        fun groupIncidents(by:(IncidentEntity)->String):List<List<Any?>> {
            return incidents.groupBy(by).map{(label,entries)->
                listOf(label,entries.size,
                    entries.count{it.category=="producto"},
                    entries.count{it.category=="operativo"},
                    entries.count{it.status!="cerrado"},
                    entries.count{it.status=="cerrado"},
                    entries.count{it.status!="cerrado"&&it.priority in setOf("alta","critica")})
            }.sortedWith(compareByDescending<List<Any?>>{it[1] as Int}
                .thenBy{it[0].toString()})
        }

        return listOf(
            sheet("Implementos","Requisitos evaluados y correcciones individuales",
                listOf("Fecha","Trabajador","Puesto","Requisito","Grupo","Cantidad mínima",
                    "Cantidad observada","Resultado inicial","Corregido el","Verificado por","Nota"),
                implementsRows),
            sheet("Pendientes uniforme","Programación estimada; ninguna ausencia de revisión es una falta",
                listOf("Fecha","Trabajador","Situación estimada","Revisión",
                    "Interpretación","Origen de la programación"),pendingRows),
            sheet("Rectificaciones","Rectificaciones de errores de marcación, no correcciones reales",
                listOf("Fecha revisión","Trabajador","Fecha acción","Requisito ID",
                    "Acción","Antes","Después","Responsable","Observación"),rectifyRows),
            sheet("Auditoría uniformes","Registro completo de eventos de inspección",
                listOf("Fecha revisión","Trabajador","Fecha acción","Requisito ID",
                    "Acción","Antes","Después","Responsable","Observación"),auditRows),
            sheet("Entregas","Movimientos por fecha de entrega y estado actual de cada lote",
                listOf("Fecha","Trabajador","Material","Cantidad","Motivo","Entregó",
                    "Estado actual","Fecha cierre","Observación cierre"),deliveryRows),
            sheet("Entregas por trabajador",
                "Movimientos en período; saldo vigente independiente del período",
                listOf("Trabajador","Material","Entregas período","Unidades período",
                    "Unidades no anuladas","Lotes devueltos","Lotes anulados",
                    "Unidades vigentes actuales","Aclaración"),materialRows),
            sheet("Programación","Reconstruida con horarios actuales; no acredita asistencia",
                listOf("Fecha","Trabajador","Puesto","Situación calculada",
                    "Activo actualmente","Fuente","Nota"),scheduleRows),
            sheet("Faltas por función","Solo incumplimientos evaluados y no anulados",
                listOf("Trabajador","Área","Función","Incumplimientos","Corregidos",
                    "Aún no corregidos","Criterio"),failureRows),
            sheet("Incidentes por área",
                "Área de detección; no demuestra origen o responsabilidad",
                listOf("Área detectada","Casos","Alimentos/bebidas","Operativos",
                    "Abiertos","Cerrados","Alta/crítica pendiente"),
                groupIncidents{it.detectedArea}),
            sheet("Incidentes por tipo","Clasificación histórica de cada incidente",
                listOf("Tipo","Casos","Alimentos/bebidas","Operativos",
                    "Abiertos","Cerrados","Alta/crítica pendiente"),
                groupIncidents{it.typeLabelSnapshot})
        )
    }
}
