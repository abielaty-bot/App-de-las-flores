package pe.lasflores.controlinterno.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import pe.lasflores.controlinterno.R
import pe.lasflores.controlinterno.data.BriefingAttendanceEntity
import pe.lasflores.controlinterno.data.FunctionCheckEntity
import pe.lasflores.controlinterno.data.IncidentEntity
import pe.lasflores.controlinterno.data.StaffDayOverrideEntity
import pe.lasflores.controlinterno.data.StaffEntity
import pe.lasflores.controlinterno.data.StaffVacationEntity
import pe.lasflores.controlinterno.data.UniformInspectionEntity
import pe.lasflores.controlinterno.domain.StaffScheduleRules

/** Los indicadores nunca deducen faltas a partir de registros ausentes. */
internal data class DashboardStats(
    val scheduled:Int,
    val totalActive:Int,
    val uniformObserved:Int,
    val uniformReviewed:Int,
    val briefingRecorded:Int?,
    val briefingPending:Int?,
    val functionPending:Int,
    val functionBad:Int,
    val functionReviewed:Int,
    val incidentOpen:Int
) {
    companion object {
        fun calculate(
            today:String,
            people:List<StaffEntity>,
            vacations:List<StaffVacationEntity>,
            overrides:List<StaffDayOverrideEntity>,
            inspections:List<UniformInspectionEntity>,
            briefingDay:String,
            briefings:List<BriefingAttendanceEntity>,
            functions:List<FunctionCheckEntity>,
            incidents:List<IncidentEntity>
        ):DashboardStats {
            val selectedInspections=inspections.filter {
                it.dateIso==today && it.workflow=="FINALIZED"
            }
            val b=if(briefingDay==today)briefings.filter { it.dateIso==today } else null
            val f=functions.filter { it.dateIso==today && it.voidedAtMillis==null }
            return DashboardStats(
                scheduled=people.count {
                    StaffScheduleRules.isScheduled(
                        StaffScheduleRules.status(it,today,vacations,overrides)
                    )
                },
                totalActive=people.count{it.isActive},
                uniformObserved=selectedInspections.count{it.initialResult=="OBSERVADO"},
                uniformReviewed=selectedInspections.size,
                briefingRecorded=b?.size,
                briefingPending=b?.count{it.presence=="MESA" && it.informedAtMillis==null},
                functionPending=f.count{it.result=="PENDING"},
                functionBad=f.count{it.result=="BAD"},
                functionReviewed=f.count{it.result=="OK"||it.result=="BAD"},
                incidentOpen=incidents.count{
                    it.status!="cerrado" && it.status!="anulado"
                }
            )
        }
    }
}

@Composable
internal fun DashboardScreen(
    people:List<StaffEntity>,
    staffVm:StaffViewModel,
    uniformVm:UniformViewModel,
    briefingVm:BriefingViewModel,
    functionVm:FunctionViewModel,
    incidentVm:IncidentViewModel,
    onOpen:(String)->Unit
) {
    val vacations by staffVm.vacations.collectAsState()
    val overrides by staffVm.overrides.collectAsState()
    val inspections by uniformVm.history.collectAsState()
    val briefingDate by briefingVm.day.collectAsState()
    val attendance by briefingVm.history.collectAsState()
    val briefingSessions by briefingVm.sessions.collectAsState()
    val checks by functionVm.checks.collectAsState()
    val incidents by incidentVm.incidents.collectAsState()
    val today=LocalDate.now().toString()
    val stats=remember(today,people,vacations,overrides,inspections,attendance,briefingSessions,checks,incidents) {
        DashboardStats.calculate(today,people,vacations,overrides,inspections,
            today,pe.lasflores.controlinterno.domain.BriefingRules.activeRecords(attendance,briefingSessions),checks,incidents)
    }
    val label=LocalDate.parse(today).format(
        DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM",Locale("es","PE"))
    ).replaceFirstChar{it.uppercaseChar()}

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding=PaddingValues(horizontal=14.dp,vertical=14.dp),
        verticalArrangement=Arrangement.spacedBy(13.dp)
    ) {
        item {
            Box(
                Modifier.fillMaxWidth()
                    .background(
                        Brush.linearGradient(listOf(
                            LasFloresPalette.DeepPlum,LasFloresPalette.Burgundy
                        )),
                        RoundedCornerShape(22.dp)
                    ).padding(18.dp)
            ) {
                Row(verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    Image(painterResource(R.drawable.las_flores_tulip),
                        contentDescription=null,modifier=Modifier.size(72.dp))
                    Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Text("LAS FLORES",
                            style=MaterialTheme.typography.titleLarge,
                            color=Color.White,fontWeight=FontWeight.Bold)
                        Text("Control interno · Supervisión diaria",
                            style=MaterialTheme.typography.bodyMedium,
                            color=Color(0xFFF2D9CC))
                        Text(label,color=Color(0xFFFFE2B4),
                            style=MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        item {
            Text("Resumen operativo",
                style=MaterialTheme.typography.titleLarge)
            Text("Información registrada en esta tablet; no incluye datos de ejemplo.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                DashboardMetric("Personal","Programados hoy",
                    stats.scheduled.toString(),"Activos: "+stats.totalActive,
                    "personal",onOpen,Modifier.weight(1f))
                DashboardMetric("Briefings","Por informar",
                    stats.briefingPending?.toString()?:"—",
                    if(stats.briefingRecorded==null)"Consulta la fecha en Briefings"
                    else "Registros: "+stats.briefingRecorded,
                    "briefings",onOpen,Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                DashboardMetric("Uniformes","Observados hoy",
                    stats.uniformObserved.toString(),
                    "Revisiones finalizadas: "+stats.uniformReviewed,
                    "uniformes",onOpen,Modifier.weight(1f))
                DashboardMetric("Incidentes","Casos abiertos",
                    stats.incidentOpen.toString(),
                    "Incluye pendientes anteriores",
                    "incidentes",onOpen,Modifier.weight(1f))
            }
        }
        item {
            Card(
                modifier=Modifier.fillMaxWidth(),
                colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
                onClick={onOpen("funciones")}
            ) {
                Column(Modifier.padding(16.dp),
                    verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("Funciones · Hoy",
                        style=MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement=Arrangement.spacedBy(18.dp),
                        verticalAlignment=Alignment.CenterVertically) {
                        MetricNumber(stats.functionReviewed,"Evaluadas")
                        MetricNumber(stats.functionPending,"Pendientes")
                        MetricNumber(stats.functionBad,"No cumplió")
                    }
                    Text("Solo se cuentan las funciones asignadas y no anuladas.",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Text("Acciones rápidas",
                style=MaterialTheme.typography.titleLarge)
        }
        item {
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                DashboardAction("Pasar lista","briefings",onOpen,Modifier.weight(1f))
                DashboardAction("Revisar uniformes","uniformes",onOpen,Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                DashboardAction("Revisar funciones","funciones",onOpen,Modifier.weight(1f))
                DashboardAction("Generar Excel","reportes",onOpen,Modifier.weight(1f))
            }
        }
        item {
            Text("El resultado inicial de una inspección no desaparece al corregirse. "+
                "Los trabajadores sin revisión no cuentan automáticamente como incumplimiento.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DashboardMetric(
    section:String,title:String,value:String,detail:String,
    target:String,onOpen:(String)->Unit,modifier:Modifier=Modifier
) {
    Card(
        modifier=modifier,
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
        onClick={onOpen(target)}
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp),
            verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text(section.uppercase(),style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.tertiary)
            Text(value,style=MaterialTheme.typography.headlineLarge,
                fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface)
            Text(title,style=MaterialTheme.typography.labelLarge)
            Text(detail,style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DashboardAction(label:String,target:String,onOpen:(String)->Unit,modifier:Modifier=Modifier) {
    Card(
        modifier=modifier,
        colors=CardDefaults.cardColors(
            containerColor=MaterialTheme.colorScheme.secondaryContainer
        ),
        onClick={onOpen(target)}
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal=13.dp,vertical=18.dp),
            contentAlignment=Alignment.Center) {
            Text(label,style=MaterialTheme.typography.labelLarge,
                color=MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun MetricNumber(value:Int,label:String) {
    Column {
        Text(value.toString(),style=MaterialTheme.typography.headlineSmall,
            fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
        Text(label,style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
