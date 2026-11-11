package pe.lasflores.controlinterno.report

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import pe.lasflores.controlinterno.data.*

enum class ReportPeriod(val label: String) { DAY("Diario"), WEEK("Semanal"), MONTH("Mensual") }

data class ReportSelection(val period: ReportPeriod, val anchor: LocalDate, val staffId: String? = null) {
    val start: LocalDate get() = when(period) {
        ReportPeriod.DAY -> anchor
        ReportPeriod.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        ReportPeriod.MONTH -> YearMonth.from(anchor).atDay(1)
    }
    val end: LocalDate get() = when(period) {
        ReportPeriod.DAY -> anchor
        ReportPeriod.WEEK -> start.plusDays(6)
        ReportPeriod.MONTH -> YearMonth.from(anchor).atEndOfMonth()
    }
    val subtitle: String get() = start.toString() + " a " + end.toString()
    fun shift(delta: Long): ReportSelection = copy(anchor = when(period) {
        ReportPeriod.DAY -> anchor.plusDays(delta)
        ReportPeriod.WEEK -> anchor.plusWeeks(delta)
        ReportPeriod.MONTH -> anchor.plusMonths(delta)
    })
    fun fileName(): String = "Las_Flores_Reporte_" + period.name + "_" +
        start.toString() + "_" + end.toString() + ".xlsx"
    fun includes(day: String): Boolean = day >= start.toString() && day <= end.toString()
    fun matches(id: String): Boolean = staffId == null || id == staffId
}

data class ReportSnapshot(
    val staff: List<StaffEntity>,
    val inspections: List<UniformInspectionEntity>,
    val uniformAnswers: List<UniformAnswerEntity>,
    val uniformEvents: List<UniformEventEntity>,
    val activityDays: List<DailyActivityEntity>,
    val briefingAttendance: List<BriefingAttendanceEntity>,
    val briefingSessions: List<BriefingSessionEntity>,
    val functions: List<FunctionCheckEntity>,
    val evidence: List<ReportEvidenceMeta>,
    val incidents: List<IncidentEntity>,
    val incidentActions: List<IncidentActionEntity>,
    val deliveries: List<MaterialDeliveryEntity> = emptyList(),
    val vacations: List<StaffVacationEntity> = emptyList(),
    val overrides: List<StaffDayOverrideEntity> = emptyList(),
    val inspectionSnapshots: List<InspectionRequirementEntity> = emptyList()
)

data class ReportCounts(
    val uniformsFinalized: Int, val uniformsObserved: Int,
    val briefingRegistered: Int, val briefingPendingInformation: Int,
    val dutiesAssigned: Int, val dutiesEvaluated: Int, val dutiesOk: Int,
    val dutiesBad: Int, val dutiesPending: Int, val dutiesCorrected: Int,
    val incidentsRegistered: Int, val incidentsOpen: Int, val incidentsClosed: Int,
    val deliveriesRegistered: Int = 0, val deliveryUnits: Int = 0
) {
    val dutiesPercent: Int? get() = if(dutiesEvaluated == 0) null
        else (dutiesOk * 100.0 / dutiesEvaluated).toInt()
}

object ReportBuilder {
    fun counts(snapshot: ReportSnapshot, select: ReportSelection): ReportCounts {
        val i=snapshot.inspections.filter {
            select.includes(it.dateIso) && select.matches(it.staffId) && it.workflow=="FINALIZED"
        }
        val b=snapshot.briefingAttendance.filter {
            select.includes(it.dateIso) && select.matches(it.staffId)
        }
        val f=snapshot.functions.filter {
            select.includes(it.dateIso) && select.matches(it.staffId) && it.voidedAtMillis==null
        }
        val inc=snapshot.incidents.filter {
            select.includes(it.occurredDate) && (select.staffId == null || it.assignedStaffId==select.staffId) &&
                it.status!="anulado"
        }
        return ReportCounts(i.size,i.count{it.initialResult=="OBSERVADO"},
            b.size,b.count{it.presence=="MESA" && it.informedAtMillis==null},
            f.size,f.count{it.result=="OK"||it.result=="BAD"},
            f.count{it.result=="OK"},f.count{it.result=="BAD"},
            f.count{it.result=="PENDING"},f.count{it.result=="BAD" && it.correctedAtMillis!=null},
            inc.size,inc.count{it.status!="cerrado"},inc.count{it.status=="cerrado"},
            snapshot.deliveries.count{select.includes(it.dateIso)&&select.matches(it.staffId)},
            snapshot.deliveries.filter{select.includes(it.dateIso)&&select.matches(it.staffId)&&
                it.status!="ANULADA"}.sumOf{it.quantity})
    }

    fun build(snapshot:ReportSnapshot, select:ReportSelection):List<ExcelSheet> {
        val staffNames=snapshot.staff.associate{it.id to it.fullName}
        val period=select.subtitle + " · " +
            (select.staffId?.let{"Trabajador: "+(staffNames[it]?:"Histórico")} ?: "Todo el equipo")
        fun sheet(name:String,description:String,headers:List<String>,rows:List<List<Any?>>) =
            ExcelSheet(name,"LAS FLORES · "+name,period+" · "+description,headers,rows)
        val inspections=snapshot.inspections.filter{select.includes(it.dateIso) && select.matches(it.staffId)}
            .sortedWith(compareBy({it.dateIso},{staffNames[it.staffId]?:it.staffId}))
        val answers=snapshot.uniformAnswers.groupBy{it.inspectionId}
        val uniformEvents=snapshot.uniformEvents.groupBy{it.inspectionId}
        fun uniformStatus(i:UniformInspectionEntity):String {
            if(i.workflow!="FINALIZED")return "En revisión"
            val rectified=uniformEvents[i.id].orEmpty().any{it.action=="RECTIFICAR"}
            if(i.initialResult=="CONFORME")return if(rectified)"Rectificado" else "Conforme"
            val bad=answers[i.id].orEmpty().filter{it.status=="BAD"}
            if(bad.isEmpty())return if(rectified)"Rectificado" else "Corregido"
            val corrected=bad.count{it.correctedAtMillis!=null}
            return when{
                corrected==bad.size->"Corregido"
                corrected>0||rectified->"Corrección parcial"
                else->"Observado"
            }
        }

        val uniformRows=inspections.map{i->
            val checks=answers[i.id].orEmpty()
            listOf(i.dateIso,staffNames[i.staffId]?:i.staffId,i.roleSnapshot,
                if(i.workflow=="FINALIZED")"Finalizada" else "En revisión",
                i.initialResult?:"Sin resultado",uniformStatus(i),
                checks.size,checks.count{it.status=="BAD"},checks.count{it.correctedAtMillis!=null},
                uniformEvents[i.id].orEmpty().count{it.action=="RECTIFICAR"})
        }
        val briefings=snapshot.briefingAttendance.filter{select.includes(it.dateIso)&&select.matches(it.staffId)}
            .sortedWith(compareBy({it.dateIso},{it.groupHour},{it.staffNameSnapshot}))
        val briefRows=briefings.map{b->
            listOf(b.dateIso,b.staffNameSnapshot,b.groupHour.toString()+":00",
                when(b.presence){"ASISTIO"->"Asistió";"MESA"->"Atendiendo mesa";"FALTA"->"No asistió al briefing";else->b.presence},
                if(b.presence=="ASISTIO")"Presencial"
                    else if(b.informedAtMillis!=null)"Informado posteriormente"
                    else if(b.presence=="MESA")"Pendiente de informar" else "No comunicado",
                b.informedBy.orEmpty(),b.categorySnapshot)
        }
        val topics= if(select.staffId==null) snapshot.briefingSessions.filter{select.includes(it.dateIso)}
            .sortedWith(compareBy({it.dateIso},{it.groupHour})) else emptyList()
        val topicRows=topics.map{listOf(it.dateIso,it.groupHour.toString()+":00",it.topics)}
        val functions=snapshot.functions.filter{select.includes(it.dateIso)&&select.matches(it.staffId)}
            .sortedWith(compareBy({it.dateIso},{it.areaSnapshot},{it.staffNameSnapshot}))
        val photos=snapshot.evidence.groupBy{it.checkId}
        val functionRows=functions.map{f->
            val e=photos[f.id].orEmpty()
            listOf(f.dateIso,f.staffNameSnapshot,f.areaSnapshot,f.taskLabelSnapshot,
                if(f.periodicitySnapshot=="MENSUAL")"Mensual" else "Diaria",
                when { f.voidedAtMillis!=null->"Anulada"; f.result=="OK"->"Cumplió";
                    f.result=="BAD"->"No cumplió"; else->"Pendiente" },
                if(f.correctedAtMillis!=null)"Sí" else "No",
                f.note,f.correctionNote,e.size,
                if(e.any{it.groupSentAtMillis!=null})"Envío declarado" else "No confirmado")
        }
        val functionById=functions.associateBy{it.id}
        val photoRows=snapshot.evidence.filter{it.checkId in functionById}.map{e->
            val f=functionById.getValue(e.checkId)
            listOf(f.dateIso,f.staffNameSnapshot,f.taskLabelSnapshot,e.description,
                if(e.groupSentAtMillis!=null)"Declarado enviado" else "No confirmado",
                e.groupSentBy.orEmpty(),"Foto no incluida en XLSX; permanece en respaldo JSON")
        }
        val incidents=snapshot.incidents.filter{
            select.includes(it.occurredDate) &&
                (select.staffId==null||it.assignedStaffId==select.staffId)
        }.sortedWith(compareBy({it.occurredDate},{it.occurredTime}))
        val incidentRows=incidents.map{i->
            listOf(i.code,i.occurredDate,i.occurredTime,
                if(i.category=="producto")"Alimentos y bebidas" else "Operaciones y servicio",
                i.productOrProcess,i.typeLabelSnapshot,i.detectedArea,i.originArea,i.confirmedOrigin,
                i.priority,i.status,i.assignedStaffNameSnapshot,i.reporter,
                i.description,i.impact,i.immediateAction,i.closedBy,i.voidReason)
        }
        val incidentById=incidents.associateBy{it.id}
        val actions=snapshot.incidentActions.filter{it.incidentId in incidentById}.map{a->
            val i=incidentById.getValue(a.incidentId)
            listOf(i.code,i.occurredDate,a.kind,a.actor,a.statusBefore,a.statusAfter,
                a.description,a.confirmedOrigin)
        }
        val validFunctions=functions.filter{it.voidedAtMillis==null}
        val validIncidents=incidents.filter{it.status!="anulado"}
        val finalInspections=inspections.filter{it.workflow=="FINALIZED"}
        val metrics=counts(snapshot,select)
        val workRate=metrics.dutiesPercent?.toString()?.plus("%")?:"Sin evaluaciones"
        val summaryRows=listOf(
            listOf("Período",select.subtitle,"Fecha de los registros"),
            listOf("Trabajadores",select.staffId?.let{staffNames[it]?:it}?:"Todo el equipo","Los filtros se aplican a los responsables registrados"),
            listOf("Uniformes: inspecciones finalizadas",metrics.uniformsFinalized,"No incluye borradores"),
            listOf("Uniformes: observadas inicialmente",metrics.uniformsObserved,"Las correcciones no eliminan el resultado inicial"),
            listOf("Uniformes: días sin revisión",if(select.staffId==null)
                snapshot.activityDays.count{select.includes(it.dateIso)&&it.module=="uniformes" && it.status=="NO_REALIZADA"} else 0,
                "Solo aplica a reportes de todo el equipo; estado según registro manual"),
            listOf("Briefings: registros",metrics.briefingRegistered,"Sin registrar no significa inasistencia"),
            listOf("Briefings: asistencias presenciales",briefings.count{it.presence=="ASISTIO"},"Personal que participó presencialmente"),
            listOf("Briefings: por informar",metrics.briefingPendingInformation,"Atendiendo mesa sin comunicación posterior"),
            listOf("Funciones: asignadas vigentes",metrics.dutiesAssigned,"Excluye anuladas"),
            listOf("Funciones: evaluadas",metrics.dutiesEvaluated,"Cumplió + No cumplió"),
            listOf("Funciones: cumplimiento",workRate,"Cumplió / Evaluadas; las pendientes no cuentan"),
            listOf("Funciones: incumplimientos",metrics.dutiesBad,"Incluye incumplimientos corregidos posteriormente"),
            listOf("Funciones: pendientes",metrics.dutiesPending,"Sin evaluación"),
            listOf("Funciones: corregidas",metrics.dutiesCorrected,"No cambia el resultado original"),
            listOf("Funciones: envío de fotos declarado",validFunctions.count{photos[it.id].orEmpty().any{p->p.groupSentAtMillis!=null}},"Confirmación manual; no es comprobación de WhatsApp"),
            listOf("Incidentes: registrados vigentes",metrics.incidentsRegistered,"No incluye anulados"),
            listOf("Incidentes: abiertos",metrics.incidentsOpen,"Según estado actual"),
            listOf("Incidentes: cerrados",metrics.incidentsClosed,"Con verificación registrada"),
            listOf("Incidentes: alta/crítica abiertos",validIncidents.count{it.status!="cerrado"&&it.priority in setOf("alta","critica")},"Pendientes prioritarios")
        ) + ReportExtras.summaryMetrics(snapshot,select)
        val presentIds= (finalInspections.map{it.staffId}+briefings.map{it.staffId}+
            validFunctions.map{it.staffId}+validIncidents.map{it.assignedStaffId})
            .filter{it.isNotBlank()}.toSet()
        val ids=if(select.staffId!=null)setOf(select.staffId) else
            (snapshot.staff.filter{it.isActive}.map{it.id}.toSet()+presentIds)
        val personnelRows=ids.sortedBy{staffNames[it]?:it}.map{id->
            val u=finalInspections.filter{it.staffId==id}
            val b=briefings.filter{it.staffId==id}
            val f=validFunctions.filter{it.staffId==id}
            val evaluated=f.count{it.result=="OK"||it.result=="BAD"}
            val ok=f.count{it.result=="OK"}
            listOf(staffNames[id] ?: b.firstOrNull()?.staffNameSnapshot ?:
                f.firstOrNull()?.staffNameSnapshot ?: id,
                u.size,u.count{it.initialResult=="OBSERVADO"},
                b.size,b.count{it.presence=="ASISTIO"},
                b.count{it.presence=="MESA"&&it.informedAtMillis==null},
                f.size,evaluated,ok,f.count{it.result=="BAD"},
                if(evaluated==0)"Sin evaluaciones" else ((ok*100.0/evaluated).toInt()).toString()+"%",
                f.count{it.result=="PENDING"},
                validIncidents.count{it.assignedStaffId==id&&it.status!="cerrado"},
                snapshot.deliveries.count{it.staffId==id&&select.includes(it.dateIso)},
                snapshot.deliveries.filter{it.staffId==id&&select.includes(it.dateIso)&&
                    it.status!="ANULADA"}.sumOf{it.quantity})
        }
        return listOf(
            sheet("Resumen","Indicadores con reglas explícitas",
                listOf("Indicador","Valor","Interpretación"),summaryRows),
            sheet("Por trabajador","El porcentaje solo incluye funciones evaluadas",
                listOf("Trabajador","Uniformes revisados","Uniformes observados","Briefings registrados",
                    "Asistió","Por informar","Funciones asignadas","Evaluadas","Cumplió","No cumplió",
                    "% cumplimiento","Pendientes","Incidentes abiertos a cargo",
                    "Entregas período","Unidades entregadas no anuladas"),personnelRows),
            sheet("Uniformes","Estado inicial vs. corrección posterior",
                listOf("Fecha","Trabajador","Puesto","Revisión","Resultado inicial","Estado actual",
                    "Requisitos revisados","Incumplimientos marcados","Correcciones","Rectificaciones"),uniformRows),
            sheet("Briefings","Sin registrar no se trata como falta",
                listOf("Fecha","Trabajador","Grupo","Asistencia","Comunicación","Informó","Categoría"),briefRows),
            sheet("Temas briefing","Indicaciones registradas para el equipo",
                listOf("Fecha","Grupo","Temas comunicados"),topicRows),
            sheet("Funciones","Pendientes y anulaciones excluidas del porcentaje",
                listOf("Fecha","Trabajador","Área","Función","Frecuencia","Resultado","Corrigió",
                    "Observación","Detalle corrección","Fotos","Envío al grupo"),functionRows),
            sheet("Evidencias","Metadatos solamente; fotografías en respaldo JSON",
                listOf("Fecha","Trabajador","Función","Descripción de foto","Envío declarado",
                    "Responsable","Nota"),photoRows),
            sheet("Incidentes","El área de detección no prueba el origen",
                listOf("Código","Fecha","Hora","Categoría","Producto/proceso","Tipo",
                    "Área detectada","Origen probable","Origen confirmado","Prioridad","Estado",
                    "Responsable","Registró","Hechos","Impacto","Acción inmediata","Cerrado por",
                    "Motivo anulación"),incidentRows),
            sheet("Acciones incidentes","Historial vinculado a casos del período seleccionado",
                listOf("Código","Fecha incidente","Acción","Ejecutó","Estado anterior",
                    "Estado posterior","Descripción","Origen verificado"),actions)
        ) + ReportExtras.build(snapshot,select)
    }
}
