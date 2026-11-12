package pe.lasflores.controlinterno.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.lasflores.controlinterno.data.StaffEntity
import pe.lasflores.controlinterno.domain.*
import pe.lasflores.controlinterno.report.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkerProfileScreen(person:StaffEntity,repository:ReportRepository,onBack:()->Unit){
    var monthText by rememberSaveable(person.id){mutableStateOf(YearMonth.now().toString())}
    val month=YearMonth.parse(monthText)
    val selection=ReportSelection(ReportPeriod.MONTH,month.atDay(1),person.id)
    var tab by rememberSaveable(person.id){mutableStateOf("resumen")}
    var data by remember(person.id){mutableStateOf<WorkerProfileData?>(null)}
    var loading by remember{mutableStateOf(true)}
    var error by remember{mutableStateOf("")}
    var refresh by remember{mutableIntStateOf(0)}
    var exporting by remember{mutableStateOf(false)}
    var pendingExport by remember{mutableStateOf<ReportSelection?>(null)}
    var exportMessage by remember{mutableStateOf("")}
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    LaunchedEffect(selection,refresh){
        loading=true;data=null;error=""
        try{data=withContext(Dispatchers.IO){WorkerProfileData.from(repository.snapshot(),selection)}}
        catch(e:CancellationException){throw e}
        catch(e:Exception){error="No se pudo leer la ficha. Reintenta."}
        finally{loading=false}
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")){uri->
        val chosen=pendingExport;pendingExport=null
        if(uri!=null&&chosen!=null)scope.launch{
            exporting=true
            try{
                withContext(Dispatchers.IO){
                    val book=ReportBuilder.build(repository.snapshot(),chosen)
                    val stream=context.contentResolver.openOutputStream(uri)?:error("No se pudo crear el Excel.")
                    stream.use{ExcelWriter().write(book,it)}
                }
                exportMessage="Reporte individual guardado."
            }catch(e:CancellationException){throw e}
            catch(e:Exception){exportMessage="Error al exportar el reporte."}
            finally{exporting=false}
        }
    }
    fun rate(n:Int?)=n?.let{"$it%"}?:"Sin datos"
    val snapshot=data
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{
            TextButton(onClick=onBack){Text("Volver a Personal")}
            Text("Ficha integral",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                    Text(person.fullName,style=MaterialTheme.typography.titleLarge)
                    Text(UniformRoleRules.label(person.role)+(if(person.category>0)" · Categoría "+person.category else ""))
                    Text((if(person.isActive)"Activo" else "Inactivo")+" · "+StaffScheduleRules.scheduleLabel(person),style=MaterialTheme.typography.bodySmall)
                    Text("Perfil: "+StaffScheduleRules.profileLabel(person.uniformProfile),style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        item{
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy",Locale("es","PE"))),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(7.dp)){
                OutlinedButton(onClick={monthText=month.minusMonths(1).toString()}){Text("‹ Mes anterior")}
                OutlinedButton(onClick={monthText=month.plusMonths(1).toString()}){Text("Mes siguiente ›")}
                TextButton(onClick={monthText=YearMonth.now().toString()}){Text("Este mes")}
                TextButton(onClick={refresh++}){Text("Actualizar")}
            }
            SectionControls(listOf("resumen" to "Resumen","uniformes" to "Uniformes","briefings" to "Briefings","celulares" to "Celulares",
                "funciones" to "Funciones","incidentes" to "Incidentes","materiales" to "Materiales"),tab,{tab=it})
            Button(onClick={pendingExport=selection;picker.launch("Las_Flores_Ficha_"+month+".xlsx")},enabled=!exporting&&!loading&&snapshot!=null,modifier=Modifier.fillMaxWidth()){Text(if(exporting)"Exportando…" else "Exportar reporte individual Excel")}
            if(exportMessage.isNotBlank())Text(exportMessage,style=MaterialTheme.typography.bodySmall)
        }
        if(loading)item{CircularProgressIndicator();Text("Consultando registros guardados…")}
        if(error.isNotBlank())item{Text(error,color=MaterialTheme.colorScheme.error);OutlinedButton(onClick={refresh++}){Text("Reintentar")}}
        if(snapshot!=null && !loading)when(tab){
            "resumen"->{
                item{
                    Text("Indicadores del período",style=MaterialTheme.typography.titleMedium)
                    Text("Los porcentajes solo consideran registros evaluados. Sin datos no equivale a incumplimiento.",style=MaterialTheme.typography.bodySmall)
                }
                item{WorkerMetric("Uniformes",rate(snapshot.uniformPercent),"${snapshot.conforming} conformes de ${snapshot.finalized.size} finalizadas · ${snapshot.inspections.size-snapshot.finalized.size} en revisión"){tab="uniformes"}}
                item{WorkerMetric("Briefings",rate(WorkerProfileData.percent(snapshot.briefingStats.communicated,snapshot.briefingStats.registered)),
                    "${snapshot.briefingStats.attended} asistencias · ${snapshot.briefingStats.pending} pendientes de informar · ${snapshot.briefingStats.missed} faltas confirmadas"){tab="briefings"}}
                item{WorkerMetric("Celulares",rate(snapshot.phoneStats.percent),"${snapshot.phoneStats.yes} entregó · ${snapshot.phoneStats.no} no entregó · ${snapshot.phoneStats.na} no aplica · ${snapshot.phoneStats.pending} sin revisar"){tab="celulares"}}
                item{WorkerMetric("Funciones",rate(snapshot.functionStats.rate),"${snapshot.functionStats.ok} cumplidas · ${snapshot.functionStats.bad} incumplidas · ${snapshot.functionStats.pending} pendientes"){tab="funciones"}}
                item{WorkerMetric("Incidentes asignados",snapshot.incidents.count{it.status!=IncidentRules.ANULADO}.toString(),"${snapshot.incidentOpen} pendientes · asignación de seguimiento, no atribución de culpa"){tab="incidentes"}}
                item{WorkerMetric("Materiales",snapshot.currentDeliveryUnits.toString(),"Unidades entregadas vigentes en este período"){tab="materiales"}}
            }
            "uniformes"->{
                item{Text("Inspecciones de uniforme",style=MaterialTheme.typography.titleMedium);if(snapshot.inspections.isEmpty())Text("Sin inspecciones en este período.")}
                items(snapshot.inspections,key={it.id}){i->
                    val ans=snapshot.answers.filter{it.inspectionId==i.id}
                    val result=UniformDaySnapshot(i.dateIso,answers=ans,events=snapshot.events.filter{it.inspectionId==i.id}).statusFor(i)
                    WorkerHistoryRow(i.dateIso,result,if(i.workflow=="FINALIZED")"Resultado inicial: "+(i.initialResult?:"Sin resultado") else "Revisión no finalizada",
                        ans.filter{it.status==UniformRules.BAD}.map{it.labelSnapshot+(if(it.correctedAtMillis!=null)" · Corregido" else "")})
                }
            }
            "briefings"->{
                item{Text("Asistencia y comunicación",style=MaterialTheme.typography.titleMedium);if(snapshot.briefings.isEmpty())Text("Sin registros en este período.")}
                items(snapshot.briefings,key={it.id}){a->WorkerHistoryRow(a.dateIso,BriefingRules.label(a.presence),BriefingRules.communicationLabel(a),listOfNotNull(a.communicationNote.takeIf{it.isNotBlank()}))}
            }
            "celulares"->{
                item{Text("Entrega de celulares",style=MaterialTheme.typography.titleMedium);if(snapshot.phones.isEmpty())Text("Sin registros en este período.")}
                items(snapshot.phones,key={it.dateIso}){a->WorkerHistoryRow(a.dateIso,PhoneRules.label(a.status),a.reason,listOfNotNull(a.note.takeIf{it.isNotBlank()}))}
            }
            "funciones"->{
                item{Text("Funciones asignadas",style=MaterialTheme.typography.titleMedium);if(snapshot.functions.isEmpty())Text("Sin funciones en este período.")}
                items(snapshot.functions,key={it.id}){a->WorkerHistoryRow(a.dateIso,a.taskLabelSnapshot, a.areaSnapshot+" · "+FunctionRules.resultName(a),listOf(a.note,a.correctionNote).filter{it.isNotBlank()})}
            }
            "incidentes"->{
                item{Text("Incidentes con seguimiento asignado",style=MaterialTheme.typography.titleMedium);Text("No significa que el trabajador haya originado el incidente.",style=MaterialTheme.typography.bodySmall);if(snapshot.incidents.isEmpty())Text("Sin incidentes asignados en este período.")}
                items(snapshot.incidents,key={it.id}){a->WorkerHistoryRow(a.occurredDate,a.code+" · "+a.productOrProcess,IncidentRules.statusName(a.status)+" · "+IncidentRules.priorityName(a.priority),listOf(a.immediateAction))}
            }
            "materiales"->{
                item{Text("Entregas de materiales",style=MaterialTheme.typography.titleMedium);if(snapshot.deliveries.isEmpty())Text("Sin entregas en este período.")}
                items(snapshot.deliveries,key={it.id}){a->WorkerHistoryRow(a.dateIso,a.material+" · "+a.quantity,a.status,listOf(a.reason,a.closingNote).filter{it.isNotBlank()})}
            }
        }
    }
}

@Composable
private fun WorkerMetric(title:String,value:String,detail:String,onClick:()->Unit){
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){
        Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Text(title,modifier=Modifier.weight(1f),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
                Text(value,style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.primary)
            }
            Text(detail,style=MaterialTheme.typography.bodySmall)
            TextButton(onClick=onClick){Text("Ver historial ›")}
        }
    }
}
@Composable
private fun WorkerHistoryRow(date:String,title:String,detail:String,notes:List<String>){
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
            Text(date,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.labelMedium)
            Text(title,fontWeight=FontWeight.Bold)
            if(detail.isNotBlank())Text(detail,style=MaterialTheme.typography.bodyMedium)
            notes.filter{it.isNotBlank()}.forEach{Text(it,style=MaterialTheme.typography.bodySmall)}
        }
    }
}
