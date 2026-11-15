package pe.lasflores.controlinterno.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import pe.lasflores.controlinterno.data.*
import pe.lasflores.controlinterno.domain.*
import pe.lasflores.controlinterno.report.*

@Composable
fun UniformIntelligenceScreen(people:List<StaffEntity>,vm:UniformViewModel){
    val history by vm.history.collectAsState()
    val audit by vm.auditHistory.collectAsState()
    var snapshot by remember{mutableStateOf<UniformDaySnapshot?>(null)}
    var error by remember{mutableStateOf("")}
    var reload by remember{mutableIntStateOf(0)}
    LaunchedEffect(history,audit,reload){
        try{snapshot=withContext(Dispatchers.IO){vm.intelligence()};error=""}
        catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(e:Exception){error=e.message?:"No se pudo cargar el historial"}
    }
    val s=snapshot
    if(s==null){Column(Modifier.padding(16.dp)){Text(if(error.isBlank())"Cargando historial…" else error);if(error.isNotBlank())Button(onClick={reload++}){Text("Reintentar")}};return}
    var worker by rememberSaveable{mutableStateOf("")}
    var query by rememberSaveable{mutableStateOf("")}
    var period by rememberSaveable{mutableStateOf("Mes")}
    var anchor by rememberSaveable{mutableStateOf(LocalDate.now().toString())}
    var tab by rememberSaveable{mutableStateOf("Resumen")}
    var customStart by rememberSaveable{mutableStateOf(LocalDate.now().withDayOfMonth(1).toString())}
    var customEnd by rememberSaveable{mutableStateOf(LocalDate.now().toString())}
    var selectedDay by rememberSaveable{mutableStateOf("")}
    var expanded by rememberSaveable{mutableStateOf("")}
    val date=LocalDate.parse(anchor)
    val start=when(period){"Semana"->date.minusDays((date.dayOfWeek.value-1).toLong());"Mes"->date.withDayOfMonth(1);"Año"->date.withDayOfYear(1);"Todo"->LocalDate.of(1900,1,1);else->runCatching{LocalDate.parse(customStart)}.getOrNull()}
    val end=when(period){"Semana"->start?.plusDays(6);"Mes"->YearMonth.from(date).atEndOfMonth();"Año"->date.withMonth(12).withDayOfMonth(31);"Todo"->LocalDate.of(9999,12,31);else->runCatching{LocalDate.parse(customEnd)}.getOrNull()}
    val valid=start!=null&&end!=null&&start<=end
    val all=remember(s,start,end){if(valid)UniformIntelligence.rows(s,start.toString(),end.toString())else emptyList()}
    val rows=remember(all,worker){all.filter{worker.isEmpty()||it.staffId==worker}}
    val metric=remember(s,rows){UniformIntelligence.metric(s,rows)}
    val detail=remember(s,rows){UniformIntelligence.details(s,rows)}
    val reqs=remember(detail){UniformIntelligence.requirements(detail)}
    val names=people.associate{it.id to it.fullName}
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var exportSheets by remember{mutableStateOf<List<ExcelSheet>>(emptyList())}
    var message by remember{mutableStateOf("")}
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")){uri->
        val sheets=exportSheets
        if(uri!=null&&sheets.isNotEmpty())scope.launch{
            try{withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.use{ExcelWriter().write(sheets,it)}?:error("No se pudo abrir el archivo")};message="Excel guardado"}
            catch(e:Exception){message="Error al exportar: "+e.message}
        }
    }
    fun move(delta:Long){anchor=when(period){"Semana"->date.plusWeeks(delta);"Mes"->date.plusMonths(delta);"Año"->date.plusYears(delta);else->date}.toString();selectedDay=""}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{
            if(worker.isNotEmpty())TextButton(onClick={worker="";tab="Resumen";selectedDay=""}){Text("‹ Todos los trabajadores")}
            Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF321D28)),modifier=Modifier.fillMaxWidth()){
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text("UNIFORM INTELLIGENCE",color=Color(0xFFE8D1B1),style=MaterialTheme.typography.labelMedium)
                    Text(if(worker.isEmpty())"Presentación del equipo" else names[worker]?:worker,color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                    Text(UniformIntelligence.label(metric.rate),color=Color.White,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
                    Text("Conformidad inicial · "+metric.ok+" de "+metric.total+" inspecciones",color=Color(0xFFE8D1B1))
                }
            }
            LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){items(listOf("Semana","Mes","Año","Todo","Personalizado")){p->FilterChip(selected=period==p,onClick={period=p;selectedDay=""},label={Text(p)})}}
            if(period=="Personalizado"){
                OutlinedTextField(customStart,{customStart=it.take(10)},label={Text("Desde AAAA-MM-DD")},modifier=Modifier.fillMaxWidth())
                OutlinedTextField(customEnd,{customEnd=it.take(10)},label={Text("Hasta AAAA-MM-DD")},modifier=Modifier.fillMaxWidth())
            }else if(period!="Todo")Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
                TextButton(onClick={move(-1)}){Text("‹")}
                Text(start.toString()+" — "+end.toString(),style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={move(1)}){Text("›")}
            }
            if(!valid)Text("Rango de fechas inválido",color=MaterialTheme.colorScheme.error)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                IntelligenceMetric("Finalizadas",metric.total.toString(),Modifier.weight(1f))
                IntelligenceMetric("Conformes",metric.ok.toString(),Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                IntelligenceMetric("Observadas",metric.observed.toString(),Modifier.weight(1f))
                IntelligenceMetric("Corregidas",metric.corrected.toString(),Modifier.weight(1f))
            }
            Text("Rectificadas: "+metric.rectified+" · Borradores: "+metric.drafts+" · Conformidad final: "+UniformIntelligence.label(metric.finalRate),style=MaterialTheme.typography.bodySmall)
            if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
        }
        if(worker.isEmpty()){
            item{OutlinedTextField(query,{query=it},label={Text("Buscar trabajador")},modifier=Modifier.fillMaxWidth())}
            val ids=(people.map{it.id}+s.inspections.map{it.staffId}).distinct().filter{(names[it]?:it).contains(query,true)}.sortedBy{names[it]?:it}
            items(ids,key={it}){id->
                val m=UniformIntelligence.metric(s,all.filter{it.staffId==id})
                Card(onClick={worker=id;tab="Resumen";selectedDay=""},modifier=Modifier.fillMaxWidth()){
                    Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){
                        Column(Modifier.weight(1f)){Text(names[id]?:id,fontWeight=FontWeight.Bold);Text(m.total.toString()+" inspecciones · "+m.observed+" observadas",style=MaterialTheme.typography.bodySmall)}
                        Text(UniformIntelligence.label(m.rate),fontWeight=FontWeight.Bold);Text("  ›")
                    }
                }
            }
        }else{
            item{LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){items(listOf("Resumen","Requisitos","Calendario","Historial")){t->FilterChip(selected=tab==t,onClick={tab=t},label={Text(t)})}}}
            if(tab=="Resumen"){
                item{
                    Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                        Text("Racha de inspecciones conformes: "+UniformIntelligence.streak(rows),fontWeight=FontWeight.Bold)
                        val times=detail.filter{it.corrected&&it.correctedAt!=null}.mapNotNull{d->s.events.filter{it.inspectionId==d.inspection.id&&it.requirementId==d.requirementId&&it.action=="CORREGIR"}.minByOrNull{it.atMillis}?.let{(it.atMillis-d.inspection.createdAtMillis).coerceAtLeast(0)/60000.0}}
                        Text("Tiempo medio desde inicio de inspección hasta corrección: "+if(times.isEmpty())"Sin datos" else "%.0f min".format(times.average()))
                        Text("Evolución mensual",fontWeight=FontWeight.Bold)
                        val months=rows.groupBy{it.dateIso.take(7)}.toSortedMap().map{(month,items)->month to UniformIntelligence.metric(s,items)}
                        if(months.isEmpty())Text("Sin inspecciones en el período")
                        IntelligenceTrend(months)
                        months.forEach{(month,m)->IntelligenceBar(month+" · Inicial",m.rate);IntelligenceBar("Final",m.finalRate)}
                        Text("Comparación por puesto histórico",fontWeight=FontWeight.Bold)
                        rows.map{it.roleSnapshot}.distinct().forEach{role->
                            val own=UniformIntelligence.metric(s,rows.filter{it.roleSnapshot==role})
                            val peers=UniformIntelligence.metric(s,all.filter{it.roleSnapshot==role&&it.staffId!=worker})
                            IntelligenceBar(role+" · Trabajador",own.rate)
                            IntelligenceBar(role+" · Compañeros",peers.rate)
                        }
                        Text("Prioridad de mejora",fontWeight=FontWeight.Bold)
                        Text(reqs.firstOrNull{it.bad>0}?.let{it.label+" · "+it.bad+" incumplimientos"}?:"Sin incumplimientos registrados")
                    }}
                }
            }
            if(tab=="Requisitos")items(reqs,key={it.id}){q->
                Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                    IntelligenceBar(q.label,q.rate)
                    Text(q.ok.toString()+" cumplen · "+q.bad+" incumplen · "+q.corrected+" corregidos · "+q.na+" no aplican",style=MaterialTheme.typography.bodySmall)
                    if(q.bad>0)Text(detail.filter{it.requirementId==q.id&&it.initial==UniformRules.BAD}.map{it.inspection.dateIso}.distinct().sorted().joinToString(", "),style=MaterialTheme.typography.bodySmall)
                }}
            }
            if(tab=="Calendario")item{
                val month=YearMonth.from(date)
                Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                        TextButton(onClick={anchor=date.minusMonths(1).toString();selectedDay=""}){Text("‹")}
                        Text(month.toString(),fontWeight=FontWeight.Bold)
                        TextButton(onClick={anchor=date.plusMonths(1).toString();selectedDay=""}){Text("›")}
                    }
                    Row(Modifier.fillMaxWidth()){listOf("L","M","X","J","V","S","D").forEach{Text(it,Modifier.weight(1f),style=MaterialTheme.typography.labelSmall)}}
                    val offset=month.atDay(1).dayOfWeek.value-1
                    (0 until offset+month.lengthOfMonth()).chunked(7).forEach{week->
                        Row(Modifier.fillMaxWidth()){week.forEach{index->
                            val d=index-offset+1
                            if(d<=0)Spacer(Modifier.weight(1f))else{
                                val iso=month.atDay(d).toString()
                                val i=s.inspections.firstOrNull{it.staffId==worker&&it.dateIso==iso}
                                val status=UniformIntelligence.dayStatus(s,worker,iso)
                                val color=when(status){"Conforme"->semanticForeground("success");"Corregido","Rectificado"->MaterialTheme.colorScheme.primary;"En revisión","Corrección parcial"->MaterialTheme.colorScheme.tertiary;"Observado"->MaterialTheme.colorScheme.error;else->MaterialTheme.colorScheme.onSurfaceVariant}
                                TextButton(onClick={selectedDay=iso},modifier=Modifier.weight(1f),contentPadding=PaddingValues(0.dp)){Text(d.toString(),color=color,fontWeight=if(selectedDay==iso)FontWeight.Bold else FontWeight.Normal)}
                            }
                        };repeat(7-week.size){Spacer(Modifier.weight(1f))}}
                    }
                    Text("Verde: conforme · Rojo: observado · Gris: sin registro/no hubo revisión · Dorado: pendiente · Primario: corregido/rectificado",style=MaterialTheme.typography.bodySmall)
                    if(selectedDay.isNotBlank()){Text(selectedDay,fontWeight=FontWeight.Bold);Text(UniformIntelligence.dayStatus(s,worker,selectedDay));s.inspections.filter{it.staffId==worker&&it.dateIso==selectedDay}.forEach{inspection->UniformIntelligence.details(s,listOf(inspection)).forEach{d->Text(d.label+" · "+d.initial+if(d.corrected)" · Corregido" else if(d.rectified)" · Rectificado" else "")}}}
                }}
            }
            if(tab=="Historial")items(rows,key={it.id}){i->
                Card(onClick={expanded=if(expanded==i.id)"" else i.id},modifier=Modifier.fillMaxWidth()){
                    Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                        Text(i.dateIso+" · "+i.roleSnapshot,fontWeight=FontWeight.Bold)
                        Text(i.initialResult?:i.workflow)
                        if(expanded==i.id){
                            detail.filter{it.inspection.id==i.id}.forEach{d->Text(d.label+" · "+d.initial+if(d.corrected)" · Corregido" else if(d.rectified)" · Rectificado" else "",style=MaterialTheme.typography.bodySmall)}
                            s.events.filter{it.inspectionId==i.id}.sortedBy{it.atMillis}.forEach{e->Text(java.time.Instant.ofEpochMilli(e.atMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().toString()+" · "+e.action+" · "+e.actor+" · "+e.note,style=MaterialTheme.typography.bodySmall)}
                        }else Text("Ver detalle ›",color=MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        item{
            Button(enabled=valid&&rows.isNotEmpty(),onClick={
                exportSheets=UniformIntelligence.export(s,rows,names,start.toString()+" — "+end.toString())
                launcher.launch("Uniform_Intelligence.xlsx")
            },modifier=Modifier.fillMaxWidth()){Text("Exportar Excel")}
            if(message.isNotBlank())Text(message)
        }
    }
}
@Composable private fun IntelligenceMetric(label:String,value:String,modifier:Modifier){
    Card(modifier){Column(Modifier.padding(12.dp)){Text(label,style=MaterialTheme.typography.labelMedium);Text(value,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)}}
}
@Composable private fun IntelligenceBar(label:String,value:Double?){
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall);Text(UniformIntelligence.label(value),fontWeight=FontWeight.Bold)}
    val color=MaterialTheme.colorScheme.primary
    val track=MaterialTheme.colorScheme.surfaceVariant
    Canvas(Modifier.fillMaxWidth().height(8.dp)){
        drawLine(track,Offset(0f,size.height/2),Offset(size.width,size.height/2),strokeWidth=size.height)
        if(value!=null)drawLine(color,Offset(0f,size.height/2),Offset(size.width*(value/100).toFloat(),size.height/2),strokeWidth=size.height)
    }
}

@Composable private fun IntelligenceTrend(months:List<Pair<String,UniformIntelligence.Metric>>){
    if(months.isEmpty())return
    val initial=MaterialTheme.colorScheme.primary
    val final=semanticForeground("success")
    val grid=MaterialTheme.colorScheme.outlineVariant
    Text("Inicial · Final",style=MaterialTheme.typography.labelSmall)
    Canvas(Modifier.fillMaxWidth().height(120.dp)){
        for(n in 0..4){val y=size.height*n/4;drawLine(grid,Offset(0f,y),Offset(size.width,y))}
        fun series(values:List<Double?>,color:Color){
            var previous:Offset?=null
            values.forEachIndexed{index,value->
                if(value==null){previous=null}else{
                    val point=Offset(if(values.size==1)size.width/2 else size.width*index/(values.size-1),size.height*(1-value.toFloat()/100))
                    previous?.let{drawLine(color,it,point,strokeWidth=3.dp.toPx())};drawCircle(color,4.dp.toPx(),point);previous=point
                }
            }
        }
        series(months.map{it.second.rate},initial);series(months.map{it.second.finalRate},final)
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(months.first().first,style=MaterialTheme.typography.labelSmall);Text(months.last().first,style=MaterialTheme.typography.labelSmall)}
}
