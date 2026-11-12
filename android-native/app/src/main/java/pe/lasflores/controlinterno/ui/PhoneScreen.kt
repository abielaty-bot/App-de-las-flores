package pe.lasflores.controlinterno.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.lasflores.controlinterno.data.*
import pe.lasflores.controlinterno.domain.PhoneRules
import pe.lasflores.controlinterno.report.*

private fun phoneKind(status:String)=when(status){
    PhoneRules.YES->"success";PhoneRules.NO->"danger";PhoneRules.PENDING->"warning";else->"neutral"
}
private fun dayTitle(day:String)=LocalDate.parse(day).format(DateTimeFormatter.ofPattern("d 'de' MMMM",Locale("es","PE")))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhoneScreen(people:List<StaffEntity>,staffVm:StaffViewModel,vm:PhoneViewModel){
    val date by vm.date.collectAsState()
    val checks by vm.checks.collectAsState()
    val days by vm.days.collectAsState()
    val vacations by staffVm.vacations.collectAsState()
    val overrides by staffVm.overrides.collectAsState()
    val message by vm.message.collectAsState()
    val reportError=LocalErrorReporter.current
    LaunchedEffect(message){if(ErrorReportText.isError(message))reportError("Celulares",message)}
    var tab by rememberSaveable{mutableStateOf("registro")}
    var filter by rememberSaveable{mutableStateOf("all")}
    var search by rememberSaveable{mutableStateOf("")}
    var monthText by rememberSaveable{mutableStateOf(YearMonth.now().toString())}
    var periodName by rememberSaveable{mutableStateOf("MONTH")}
    var detailId by rememberSaveable{mutableStateOf("")}
    var exceptionId by remember(date){mutableStateOf("")}
    var noteId by remember(date){mutableStateOf("")}
    var noReviewDialog by remember(date){mutableStateOf(false)}
    var reason by remember(date){mutableStateOf("")}
    var note by remember(date){mutableStateOf("")}
    var exportMessage by remember{mutableStateOf("")}
    var exporting by remember{mutableStateOf(false)}
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var exportSheets by remember{mutableStateOf<List<ExcelSheet>>(emptyList())}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")){uri->
        val sheets=exportSheets;exportSheets=emptyList()
        if(uri!=null&&sheets.isNotEmpty())scope.launch{
            exporting=true
            try{
                withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.use{ExcelWriter().write(sheets,it)}
                    ?:error("Android no pudo crear el archivo Excel.")}
                exportMessage="Reporte de celulares guardado."
            }catch(e:Exception){exportMessage="Error al exportar: "+(e.message?:"Error de almacenamiento");reportError("Celulares",exportMessage)}
            finally{exporting=false}
        }
    }
    val dayRecords=checks.filter{it.dateIso==date}
    val noReview=days.firstOrNull{it.dateIso==date}
    val today=LocalDate.now().toString()
    val future=date>today
    val rows=PhoneRules.roster(date,people,vacations,overrides,dayRecords,date==today)
    val counts=PhoneRules.counts(rows)
    val visible=rows.filter{(filter=="all"||it.status==filter)&&it.nameSnapshot.contains(search,true)}
    val busy=message=="Guardando…"
    val month=YearMonth.parse(monthText)
    val period=ReportPeriod.valueOf(periodName)
    val selection=ReportSelection(period,if(period==ReportPeriod.MONTH)month.atDay(1) else LocalDate.parse(date))
    val excluded=days.filter{it.status=="NO_REALIZADA"}.map{it.dateIso}.toSet()
    val monthRows=checks.filter{selection.includes(it.dateIso)&&it.dateIso !in excluded}
    val totals=PhoneRules.counts(monthRows)
    val ranked=monthRows.groupBy{it.staffId}.values.sortedWith(compareByDescending<List<PhoneCheckEntity>>{PhoneRules.counts(it).percent?:-1}
        .thenByDescending{PhoneRules.counts(it).yes}.thenBy{it.first().nameSnapshot})
    Column(Modifier.fillMaxSize()){
        SectionControls(listOf("registro" to "Registro","historial" to "Historial","stats" to "Estadísticas"),tab,{tab=it;detailId=""})
        when(tab){
            "registro"->LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
                item{
                    Text("Control de celulares",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                    Text("Verifica la entrega en Administración.",style=MaterialTheme.typography.bodySmall)
                    DayControls(date,{vm.setDate(LocalDate.parse(date).minusDays(1).toString())},
                        {vm.setDate(LocalDate.parse(date).plusDays(1).toString())},{vm.setDate(today)},vm::setDate)
                    Text(message,style=MaterialTheme.typography.labelSmall,color=if(message.startsWith("Error"))MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
                item{
                    if(noReview!=null){
                        Card(Modifier.fillMaxWidth()){
                            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                                Text("No hubo revisión",fontWeight=FontWeight.Bold)
                                Text(noReview.reason)
                                OutlinedButton(enabled=!busy,onClick=vm::reopen){Text("Reabrir jornada")}
                            }
                        }
                    }else PhoneSummary(counts)
                }
                if(noReview==null){
                    item{
                        OutlinedTextField(value=search,onValueChange={search=it},label={Text("Buscar trabajador")},singleLine=true,modifier=Modifier.fillMaxWidth())
                        LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                            items(listOf("all" to "Todos",PhoneRules.PENDING to "Pendientes",PhoneRules.YES to "Entregaron",PhoneRules.NO to "No entregaron",PhoneRules.NA to "N/A")){(v,l)->
                                FilterChip(selected=filter==v,onClick={filter=v},label={Text(l)})
                            }
                        }
                        Text("PERSONAL PROGRAMADO · "+rows.size,style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.SemiBold)
                        if(future)Text("Fecha futura: no se permite registrar respuestas.",style=MaterialTheme.typography.bodySmall)
                        if(date<today&&dayRecords.isEmpty())Text("Sin control registrado. La lista es una estimación de la programación; verifica antes de registrar.",style=MaterialTheme.typography.bodySmall)
                        if(date==today&&dayRecords.isNotEmpty())TextButton(onClick=vm::sync,enabled=!busy){Text("Actualizar personal programado")}
                    }
                    items(visible,key={it.staffId}){r->
                        PhoneWorkerCard(r,!future&&!busy,onMark={status->
                            if(status==PhoneRules.NA&&r.status!=PhoneRules.NA){exceptionId=r.staffId;reason=PhoneRules.REASONS.first();note=""}
                            else vm.mark(r.staffId,status)
                        },onNote={noteId=r.staffId;note=r.note})
                    }
                    if(visible.isEmpty())item{Text("No hay trabajadores con este filtro.")}
                    item{
                        if(counts.reviewed==0&&!future)TextButton(enabled=!busy,onClick={reason="";noReviewDialog=true}){Text("No hubo revisión")}
                        Text("Retirar el celular al salir no cambia el resultado. Los pendientes no se consideran incumplimientos.",style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
            "historial"->{
                val recorded=(checks.map{it.dateIso}+days.map{it.dateIso}).distinct().sortedDescending()
                LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
                    item{Text("Historial de controles",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
                    items(recorded,key={it}){d->
                        val c=PhoneRules.counts(checks.filter{it.dateIso==d})
                        val skip=days.firstOrNull{it.dateIso==d}
                        Card(onClick={vm.setDate(d);tab="registro";filter="all";search=""},modifier=Modifier.fillMaxWidth()){
                            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                                    Text(dayTitle(d),fontWeight=FontWeight.Bold);Text(d,style=MaterialTheme.typography.labelSmall)
                                }
                                if(skip!=null)Text("No hubo revisión · "+skip.reason)
                                else{
                                    Text(c.reviewed.toString()+" de "+c.total+" revisados")
                                    FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                                        Text(c.yes.toString()+" entregaron",color=semanticForeground("success"),style=MaterialTheme.typography.labelMedium)
                                        Text(c.no.toString()+" no entregaron",color=semanticForeground("danger"),style=MaterialTheme.typography.labelMedium)
                                        Text(c.na.toString()+" N/A · "+c.pending+" pendientes",style=MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                    if(recorded.isEmpty())item{Text("Todavía no existen controles registrados.")}
                }
            }
            else->LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                item{
                    Text("Estadísticas",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                    SectionControls(listOf("DAY" to "Día","WEEK" to "Semana","MONTH" to "Mes"),periodName,{periodName=it;detailId=""})
                    if(period==ReportPeriod.MONTH){
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
                            OutlinedButton(onClick={monthText=month.minusMonths(1).toString()}){Text("‹")}
                            Text(month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy",Locale("es","PE"))),fontWeight=FontWeight.SemiBold)
                            OutlinedButton(onClick={monthText=month.plusMonths(1).toString()}){Text("›")}
                        }
                    }else DayControls(date,{vm.setDate(LocalDate.parse(date).minusDays(if(period==ReportPeriod.WEEK)7 else 1).toString())},
                        {vm.setDate(LocalDate.parse(date).plusDays(if(period==ReportPeriod.WEEK)7 else 1).toString())},{vm.setDate(today)},vm::setDate)
                    Text(selection.subtitle,style=MaterialTheme.typography.bodySmall)
                    Text("Jornadas sin revisión: "+days.count{selection.includes(it.dateIso)&&it.status=="NO_REALIZADA"},style=MaterialTheme.typography.bodySmall)
                }
                item{
                    Card(Modifier.fillMaxWidth()){
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                            Text("CUMPLIMIENTO GENERAL",style=MaterialTheme.typography.labelMedium)
                            Text(totals.percent?.let{it.toString()+"%"}?:"Sin evaluación",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
                            LinearProgressIndicator(progress={(totals.percent?:0)/100f},modifier=Modifier.fillMaxWidth(),color=semanticForeground("success"))
                            PhoneMetrics(totals)
                            Text("Sí ÷ (Sí + No). No aplica, sin revisar y jornadas sin revisión no cuentan en el porcentaje.",style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item{Text("Cumplimiento por trabajador",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)}
                items(ranked,key={it.first().staffId}){group->
                    val latest=group.maxBy{it.dateIso};val c=PhoneRules.counts(group)
                    Card(onClick={detailId=if(detailId==latest.staffId)"" else latest.staffId},modifier=Modifier.fillMaxWidth()){
                        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                                Text(latest.nameSnapshot,fontWeight=FontWeight.Bold)
                                Text(c.percent?.let{it.toString()+"%"}?:"Sin evaluación",color=semanticForeground(if(c.no>0)"warning" else "success"),fontWeight=FontWeight.Bold)
                            }
                            LinearProgressIndicator(progress={(c.percent?:0)/100f},modifier=Modifier.fillMaxWidth(),
                                color=semanticForeground(if(c.no>0)"warning" else "success"))
                            Text(c.yes.toString()+" entregas · "+c.no+" incumplimientos · "+c.na+" N/A · "+c.pending+" pendientes",style=MaterialTheme.typography.bodySmall)
                            if(detailId==latest.staffId){
                                HorizontalDivider()
                                Text("Fechas de incumplimiento",fontWeight=FontWeight.SemiBold)
                                val failures=group.filter{it.status==PhoneRules.NO}.sortedByDescending{it.dateIso}
                                if(failures.isEmpty())Text("No hay incumplimientos registrados.")
                                failures.forEach{r->TextButton(onClick={vm.setDate(r.dateIso);tab="registro";search=r.nameSnapshot;filter="all"}){Text(r.dateIso+if(r.note.isNotBlank())" · "+r.note else "")}}
                            }else Text("Toca para ver fechas de incumplimiento",style=MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if(ranked.isEmpty())item{Text("No hay registros en este período.")}
                item{
                    Button(enabled=!exporting,onClick={
                        exportSheets=PhoneReports.build(checks,days,selection)
                        picker.launch("Las_Flores_Celulares_"+selection.start+"_"+selection.end+".xlsx")
                    },modifier=Modifier.fillMaxWidth()){Text(if(exporting)"Exportando…" else "Exportar reporte Excel")}
                    if(exportMessage.isNotBlank())Text(exportMessage,style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if(exceptionId.isNotBlank())AlertDialog(onDismissRequest={exceptionId=""},title={Text("No aplica")},
        text={Column(verticalArrangement=Arrangement.spacedBy(7.dp)){
            PhoneRules.REASONS.forEach{r->Row(verticalAlignment=Alignment.CenterVertically){RadioButton(selected=reason==r,onClick={reason=r});Text(r)}}
            OutlinedTextField(note,{note=it.take(500)},label={Text("Observación (opcional)")})
        }},confirmButton={Button(onClick={vm.mark(exceptionId,PhoneRules.NA,reason,note);exceptionId=""}){Text("Guardar")}},
        dismissButton={TextButton(onClick={exceptionId=""}){Text("Cancelar")}})
    if(noteId.isNotBlank())AlertDialog(onDismissRequest={noteId=""},title={Text("Observación")},
        text={OutlinedTextField(note,{note=it.take(500)},label={Text("Detalle opcional")})},
        confirmButton={Button(onClick={vm.saveNote(noteId,note);noteId=""}){Text("Guardar")}},
        dismissButton={TextButton(onClick={noteId=""}){Text("Cancelar")}})
    if(noReviewDialog)AlertDialog(onDismissRequest={noReviewDialog=false},title={Text("No hubo revisión")},
        text={OutlinedTextField(reason,{reason=it.take(500)},label={Text("Motivo")})},
        confirmButton={Button(enabled=reason.trim().length>=3,onClick={vm.noReview(reason);noReviewDialog=false}){Text("Confirmar")}},
        dismissButton={TextButton(onClick={noReviewDialog=false}){Text("Cancelar")}})
}

@Composable
private fun PhoneSummary(c:PhoneRules.Counts){
    Card(Modifier.fillMaxWidth()){
        Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
                Column{
                    Text("PROGRESO DE REVISIÓN",style=MaterialTheme.typography.labelMedium)
                    Text(c.reviewed.toString()+" / "+c.total,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
                }
                Text(if(c.total==0)"0%" else (100*c.reviewed/c.total).toString()+"%",style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.tertiary)
            }
            LinearProgressIndicator(progress={if(c.total==0)0f else c.reviewed.toFloat()/c.total},modifier=Modifier.fillMaxWidth())
            PhoneMetrics(c)
        }
    }
}
@Composable
private fun PhoneMetrics(c:PhoneRules.Counts){
    val metrics=listOf(Triple("Entregaron",c.yes,"success"),Triple("No entregaron",c.no,"danger"),
        Triple("Pendientes",c.pending,"warning"),Triple("No aplica",c.na,"neutral"))
    Column(verticalArrangement=Arrangement.spacedBy(7.dp)){
        metrics.chunked(2).forEach{line->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)){
            line.forEach{(label,num,kind)->
                Surface(Modifier.weight(1f),shape=MaterialTheme.shapes.medium,color=semanticBackground(kind)){
                    Column(Modifier.padding(10.dp)){
                        Text(label,style=MaterialTheme.typography.labelSmall,color=semanticForeground(kind))
                        Text(num.toString(),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                    }
                }
            }
        }}
    }
}
@Composable
private fun PhoneWorkerCard(r:PhoneCheckEntity,enabled:Boolean,onMark:(String)->Unit,onNote:()->Unit){
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){
                Box(Modifier.size(38.dp).background(MaterialTheme.colorScheme.surfaceVariant,CircleShape),contentAlignment=Alignment.Center){
                    Text(r.nameSnapshot.take(1).uppercase(),fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.tertiary)
                }
                Column(Modifier.weight(1f)){
                    Text(r.nameSnapshot,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
                    Text(r.roleSnapshot,style=MaterialTheme.typography.bodySmall)
                }
                Text(PhoneRules.label(r.status),color=semanticForeground(phoneKind(r.status)),style=MaterialTheme.typography.labelSmall)
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                listOf(PhoneRules.YES to "Sí",PhoneRules.NO to "No",PhoneRules.NA to "N/A").forEach{(status,label)->
                    FilterChip(selected=r.status==status,onClick={onMark(status)},enabled=enabled,modifier=Modifier.weight(1f),
                        label={Text(label)},colors=FilterChipDefaults.filterChipColors(
                            selectedContainerColor=semanticBackground(phoneKind(status)),selectedLabelColor=semanticForeground(phoneKind(status))))
                }
            }
            if(r.reason.isNotBlank())Text(r.reason,style=MaterialTheme.typography.bodySmall)
            if(r.note.isNotBlank())Text(r.note,style=MaterialTheme.typography.bodySmall)
            if(r.status!=PhoneRules.PENDING)TextButton(onClick=onNote,enabled=enabled,contentPadding=PaddingValues(0.dp)){Text("Observación",style=MaterialTheme.typography.labelMedium)}
        }
    }
}
