package pe.lasflores.controlinterno.ui

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pe.lasflores.controlinterno.report.ExcelWriter
import pe.lasflores.controlinterno.report.ExcelSheet
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import pe.lasflores.controlinterno.LasFloresApp
import pe.lasflores.controlinterno.data.*
import java.time.LocalDate

@Composable
internal fun OperationalScreen() {
    val context=LocalContext.current
    val repo=remember(context){(context.applicationContext as LasFloresApp).operationalRepository}
    val scope=rememberCoroutineScope()
    var dateIso by rememberSaveable{mutableStateOf(LocalDate.now().toString())}
    val date=LocalDate.parse(dateIso)
    var mode by rememberSaveable{mutableStateOf("Diaria")}
    var section by rememberSaveable{mutableStateOf("Inicio")}
    var groupByZone by rememberSaveable{mutableStateOf(false)}
    var owner by rememberSaveable{mutableStateOf("")}
    var filter by rememberSaveable{mutableStateOf("")}
    var pendingOnly by rememberSaveable{mutableStateOf(false)}
    var message by remember{mutableStateOf("")}
    var busy by remember{mutableStateOf(false)}
    var failureOnly by remember{mutableStateOf(false)}
    var selected by remember{mutableStateOf<OperationalInstanceEntity?>(null)}
    var edit by remember{mutableStateOf<OperationalTemplateEntity?>(null)}
    var people by remember{mutableStateOf(emptyList<StaffEntity>())}
    val period=when(mode){"Semanal"->date.minusDays((date.dayOfWeek.value-1).toLong()).toString()+"-W";"Mensual"->dateIso.take(7);else->dateIso}
    val rows by remember(period){repo.observe(period)}.collectAsState(initial=emptyList())
    val templates by remember{repo.catalog.observe()}.collectAsState(initial=emptyList())
    fun run(action:suspend ()->Unit){if(busy)return;busy=true;scope.launch{
        try{action();message="Guardado automáticamente"}catch(e:CancellationException){throw e}catch(e:Exception){message="Error: "+(e.message?:"No se pudo guardar")}finally{busy=false}
    }}
    var photoId by rememberSaveable{mutableStateOf<String?>(null)}
    val photo=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->
        val id=photoId
        photoId=null
        if(uri!=null && id!=null)run{
            val encoded=withContext(Dispatchers.IO){
                val resolver=context.contentResolver
                val bounds=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true}
                resolver.openInputStream(uri)?.use{android.graphics.BitmapFactory.decodeStream(it,null,bounds)}
                require(bounds.outWidth>0 && bounds.outHeight>0){"Imagen inválida"}
                var sample=1
                while(maxOf(bounds.outWidth,bounds.outHeight)/sample>1200)sample*=2
                val bitmap=resolver.openInputStream(uri)?.use{android.graphics.BitmapFactory.decodeStream(it,null,android.graphics.BitmapFactory.Options().apply{inSampleSize=sample})}?:error("No se pudo leer la foto")
                val bytes=java.io.ByteArrayOutputStream()
                try{bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,75,bytes)}finally{bitmap.recycle()}
                android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP)
            }
            repo.evidence(id,encoded)
        }
    }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")){uri->
        if(uri!=null)run{
            withContext(Dispatchers.IO){
                val start=when(mode){"Semanal"->date.minusDays((date.dayOfWeek.value-1).toLong());"Mensual"->date.withDayOfMonth(1);else->date}
                val end=when(mode){"Semanal"->start.plusDays(6);"Mensual"->start.plusMonths(1).minusDays(1);else->date}
                val reportRows=repo.all().filter{r->
                    val d=runCatching{LocalDate.parse(r.periodKey.removeSuffix("-W"))}.getOrNull()
                    r.periodKey==period || (d!=null && !d.isBefore(start) && !d.isAfter(end) && (mode=="Mensual" || !r.periodKey.endsWith("-W")))
                }
                val output=context.contentResolver.openOutputStream(uri)?:error("No se pudo crear Excel")
                output.use{ExcelWriter().write(listOf(ExcelSheet("Control operativo","Control operativo Las Flores",period,
                    listOf("Período","Zona","Responsable","Función","Estándar","Resultado inicial","Corrección","Observación","Atribuible"),
                    reportRows.map{listOf(it.periodKey,it.locationSnapshot,it.staffNameSnapshot,it.labelSnapshot,it.standardSnapshot,label(it.initialResult),label(it.correction),it.note,it.attributable)})),it)}
            }
        }
    }
    LaunchedEffect(dateIso){try{repo.catalog.initialize();people=repo.available(date)}catch(e:CancellationException){throw e}catch(e:Exception){message="Error: "+e.message}}
    val openStates=setOf("PENDING","REPORTED","REOPENED")
    val reviewed=rows.count{it.initialResult!="UNREVIEWED"}
    val conform=rows.count{it.initialResult=="CONFORMING"}
    val evaluated=rows.count{it.initialResult in setOf("CONFORMING","NONCONFORMING")}
    val groups=rows.groupBy{if(groupByZone)it.locationSnapshot else it.staffNameSnapshot}.toSortedMap()
    val activeOwner=owner.takeIf{it in groups}?:groups.keys.firstOrNull().orEmpty()
    val visible=rows.filter{(section!="Seguimiento" || it.correction in openStates) &&
        (section!="Revisión" || (if(groupByZone)it.locationSnapshot else it.staffNameSnapshot)==activeOwner) &&
        (!pendingOnly || it.initialResult=="UNREVIEWED" || it.correction in openStates) &&
        (filter.isBlank() || listOf(it.locationSnapshot,it.staffNameSnapshot,it.labelSnapshot).any{s->s.contains(filter,true)})}
    val burgundy=Color(0xFF381F2B)
    val champagne=Color(0xFFE5CDA7)
    Column(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
            TextButton(onClick={dateIso=date.minusDays(1).toString()}){Text("‹ Anterior")}
            Text(dateIso,style=MaterialTheme.typography.titleMedium)
            TextButton(onClick={dateIso=date.plusDays(1).toString()}){Text("Siguiente ›")}
        }
        LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
            items(listOf("Inicio","Revisión","Seguimiento","Reportes","Catálogo")){m->FilterChip(selected=section==m,onClick={section=m;filter="";pendingOnly=false},label={Text(m)})}
        }
        if(section!="Catálogo"){
            LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                items(listOf("Diaria","Semanal","Mensual")){m->FilterChip(selected=mode==m,onClick={mode=m;owner=""},label={Text(m)})}
            }
        }
        if(message.isNotBlank())Text(message,style=MaterialTheme.typography.labelMedium,color=if(message.startsWith("Error"))MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        if(section=="Revisión"){
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                FilterChip(selected=!groupByZone,onClick={groupByZone=false;owner=""},label={Text("Por trabajador")})
                FilterChip(selected=groupByZone,onClick={groupByZone=true;owner=""},label={Text("Por zona")})
            }
            OutlinedTextField(filter,{filter=it},label={Text("Buscar función")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            FilterChip(selected=pendingOnly,onClick={pendingOnly=!pendingOnly},label={Text("Solo pendientes")})
        }
        if(section=="Seguimiento")OutlinedTextField(filter,{filter=it},label={Text("Buscar corrección")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){
            if(section=="Inicio" || section=="Reportes"){
                item{
                    Card(colors=CardDefaults.cardColors(containerColor=burgundy),modifier=Modifier.fillMaxWidth()){
                        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                            Text("LAS FLORES · OPERACIONES",color=champagne,style=MaterialTheme.typography.labelMedium)
                            Text(if(section=="Inicio")"Supervisión de la jornada" else "Inteligencia operativa",color=Color.White,style=MaterialTheme.typography.headlineSmall)
                            Text("$reviewed / ${rows.size} revisados",color=Color.White,style=MaterialTheme.typography.headlineMedium)
                            LinearProgressIndicator(progress={if(rows.isEmpty())0f else reviewed.toFloat()/rows.size},modifier=Modifier.fillMaxWidth(),color=champagne,trackColor=Color(0xFF76505D))
                            Text("Conformes: $conform · Pendientes: ${rows.size-reviewed}",color=champagne)
                        }
                    }
                }
                item{
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        OperationalMetric("Sin asignar",rows.count{it.staffNameSnapshot=="Sin asignar"}.toString(),Modifier.weight(1f))
                        OperationalMetric("Por corregir",rows.count{it.correction in openStates}.toString(),Modifier.weight(1f))
                    }
                }
                item{
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        OperationalMetric("Conformidad inicial",if(evaluated==0)"—" else "${100*conform/evaluated}%",Modifier.weight(1f))
                        OperationalMetric("Conformidad final",if(evaluated==0)"—" else "${100*rows.count{it.initialResult=="CONFORMING" || (it.initialResult=="NONCONFORMING" && it.correction=="VERIFIED")}/evaluated}%",Modifier.weight(1f))
                    }
                }
                item{
                    if(section=="Inicio"){
                        Button(onClick={run{repo.generate(date)}},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(if(rows.isEmpty())"Preparar jornada" else "Preparar controles faltantes")}
                        OutlinedButton(onClick={section="Revisión"},modifier=Modifier.fillMaxWidth()){Text("Continuar inspección")}
                    }else Button(onClick={export.launch("Las_Flores_Control_"+period+".xlsx")},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Exportar período a Excel")}
                    Text("Estaciones de trabajo",style=MaterialTheme.typography.titleMedium)
                }
                items(groups.entries.toList(),key={it.key}){g->
                    val done=g.value.count{it.initialResult!="UNREVIEWED"}
                    Card(onClick={owner=g.key;section="Revisión";filter=""},modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){
                        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(g.key,style=MaterialTheme.typography.titleMedium);Text("$done / ${g.value.size}",color=MaterialTheme.colorScheme.primary)}
                            Text(g.value.map{it.locationSnapshot}.distinct().joinToString(" · "),style=MaterialTheme.typography.bodySmall)
                            LinearProgressIndicator(progress={done.toFloat()/g.value.size},modifier=Modifier.fillMaxWidth())
                        }
                    }
                }
            }else if(section=="Catálogo"){
                items(templates,key={it.id}){t->
                    Card(onClick={edit=t},modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){
                        Text(t.label,style=MaterialTheme.typography.titleMedium)
                        Text("${t.groupName} · ${t.location} · ${t.frequency}")
                        if(t.standard!=t.label)Text(t.standard)
                        Text(if(t.active)"Activa" else "Desactivada")
                    }}
                }
            }else{
                if(section=="Revisión")item{
                    val assigned=groups[activeOwner].orEmpty()
                    Card(colors=CardDefaults.cardColors(containerColor=burgundy),modifier=Modifier.fillMaxWidth()){
                        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                            Text("ESTACIÓN DE INSPECCIÓN",color=champagne,style=MaterialTheme.typography.labelMedium)
                            Text(activeOwner.ifBlank{"Sin controles"},color=Color.White,style=MaterialTheme.typography.headlineSmall)
                            Text("${assigned.count{it.initialResult!="UNREVIEWED"}} / ${assigned.size} revisados",color=champagne)
                            LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                                items(groups.keys.toList()){g->FilterChip(selected=activeOwner==g,onClick={owner=g},label={Text(g)},colors=FilterChipDefaults.filterChipColors(containerColor=Color.White,selectedContainerColor=champagne,labelColor=burgundy,selectedLabelColor=burgundy))}
                            }
                        }
                    }
                }
                if(visible.isEmpty())item{Text("No hay controles para mostrar",modifier=Modifier.padding(16.dp))}
                items(visible,key={it.id}){row->
                    Card(modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){
                        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                            Row(verticalAlignment=Alignment.CenterVertically){
                                Column(Modifier.weight(1f)){
                                    Text(row.labelSnapshot,style=MaterialTheme.typography.titleMedium)
                                    Text(if(section=="Seguimiento")row.staffNameSnapshot+" · "+row.locationSnapshot else row.locationSnapshot,style=MaterialTheme.typography.labelSmall)
                                }
                                TextButton(onClick={failureOnly=false;selected=row}){Text("Detalle")}
                            }
                            if(row.initialResult=="UNREVIEWED"){
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                    Button(onClick={run{repo.review(row.id,"CONFORMING","")}},enabled=!busy,modifier=Modifier.weight(1f).heightIn(min=48.dp),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF317253),contentColor=Color.White)){Text("✓ Cumple")}
                                    OutlinedButton(onClick={failureOnly=true;selected=row},enabled=!busy,modifier=Modifier.weight(1f).heightIn(min=48.dp)){Text("✕ No cumple",color=MaterialTheme.colorScheme.error)}
                                }
                            }else{
                                Text(label(row.initialResult)+(if(row.correction=="NONE")"" else " · "+label(row.correction)),color=if(row.initialResult=="CONFORMING")Color(0xFF317253) else MaterialTheme.colorScheme.error,style=MaterialTheme.typography.labelLarge)
                                if(row.note.isNotBlank())Text(row.note,style=MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        if(section=="Revisión" && groups.isNotEmpty())Button(onClick={val keys=groups.keys.toList();owner=keys[(keys.indexOf(activeOwner)+1)%keys.size];filter=""},modifier=Modifier.fillMaxWidth()){Text("Siguiente estación ›")}
    }
    selected?.let{row->
        var linkedSummary by remember(row.id){mutableStateOf<String?>(null)}
        LaunchedEffect(row.id,dateIso){linkedSummary=listOfNotNull(repo.linkedSummary(row.templateId,date),repo.relatedFunctions(row,date)).joinToString("\n").ifBlank{null}}
        val history by remember(row.id){repo.events(row.id)}.collectAsState(initial=emptyList())
        var note by remember(row.id){mutableStateOf(row.note)}
        var attributable by remember(row.id){mutableStateOf(row.attributable)}
        var assigning by remember(row.id){mutableStateOf(false)}
        AlertDialog(onDismissRequest={selected=null},title={Text(row.labelSnapshot)},text={
            Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text(row.standardSnapshot)
                linkedSummary?.let{Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)}
                Text("Responsable: "+row.staffNameSnapshot)
                TextButton(onClick={photoId=row.id;photo.launch("image/*")},enabled=!busy){Text("Adjuntar foto")}
                Text("Evidencias: "+history.count{it.action=="PHOTO"})
                history.filter{it.action=="PHOTO"}.forEach{e->
                    val bitmap=remember(e.id){runCatching{val bytes=android.util.Base64.decode(e.afterValue,android.util.Base64.DEFAULT);android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size)?.asImageBitmap()}.getOrNull()}
                    bitmap?.let{Image(it,contentDescription="Evidencia de la función",modifier=Modifier.fillMaxWidth().heightIn(max=200.dp))}
                }
                history.filter{it.action!="PHOTO"}.takeLast(5).forEach{e->Text(label(e.beforeValue)+" → "+label(e.afterValue)+" · "+e.note,style=MaterialTheme.typography.bodySmall)}
                OutlinedTextField(note,{note=it},label={Text("Observación / motivo")})
                if(row.initialResult=="UNREVIEWED"){
                    Row{Checkbox(attributable,{attributable=it});Text("Incumplimiento atribuible")}
                    (if(failureOnly)listOf("NONCONFORMING","BLOCKED","NOT_APPLICABLE") else listOf("CONFORMING","NONCONFORMING","BLOCKED","NOT_APPLICABLE")).forEach{v->
                        OutlinedButton(onClick={run{repo.review(row.id,v,note,attributable);selected=null}},enabled=!busy && (v=="CONFORMING" || note.isNotBlank()),modifier=Modifier.fillMaxWidth()){Text(label(v))}
                    }
                }
                if(row.initialResult!="UNREVIEWED" && row.correction in setOf("NONE","PENDING")) TextButton(onClick={run{repo.review(row.id,"UNREVIEWED",note);selected=null}},enabled=!busy && note.isNotBlank()){Text("Anular revisión con motivo")}
                val next=when(row.correction){"PENDING","REOPENED"->listOf("REPORTED");"REPORTED"->listOf("VERIFIED","REOPENED");"VERIFIED"->listOf("REOPENED");else->emptyList()}
                next.forEach{v->Button(onClick={run{repo.correct(row.id,v,note);selected=null}},enabled=!busy){Text(label(v))}}
                if(row.initialResult=="UNREVIEWED")TextButton(onClick={assigning=true}){Text("Asignar responsable")}
            }
        },confirmButton={TextButton(onClick={selected=null}){Text("Cerrar")}})
        if(assigning)AlertDialog(onDismissRequest={assigning=false},title={Text("Personal disponible")},text={
            LazyColumn(Modifier.heightIn(max=320.dp)){items(people,key={it.id}){person->TextButton(onClick={run{repo.assign(row.id,person,date);assigning=false;selected=null}}){Text(person.fullName)}}}
        },confirmButton={TextButton(onClick={assigning=false}){Text("Cancelar")}})
    }
    edit?.let{t->
        var standard by remember(t.id){mutableStateOf(t.standard)}
        var label by remember(t.id){mutableStateOf(t.label)}
        var active by remember(t.id){mutableStateOf(t.active)}
        AlertDialog(onDismissRequest={edit=null},title={Text("Editar función")},text={Column{
            Text(t.validation,style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(label,{label=it},label={Text("Función")})
            OutlinedTextField(standard,{standard=it},label={Text("Estándar")})
            Row{Checkbox(active,{active=it});Text("Activa")}
        }},confirmButton={Button(enabled=label.isNotBlank()&&standard.isNotBlank()&&!busy,onClick={run{repo.catalog.edit(t.copy(label=label,standard=standard,active=active));edit=null}}){Text("Guardar")}},dismissButton={TextButton(onClick={edit=null}){Text("Cancelar")}})
    }
}
@Composable
private fun OperationalMetric(title:String,value:String,modifier:Modifier=Modifier){
    Card(modifier,colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
            Text(title,style=MaterialTheme.typography.labelMedium)
            Text(value,style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.primary)
        }
    }
}
private fun label(s:String)=when(s){"UNREVIEWED"->"Sin revisar";"CONFORMING"->"Conforme";"NONCONFORMING"->"No conforme";"BLOCKED"->"Bloqueado";"NOT_APPLICABLE"->"No aplica";"PENDING"->"Corrección pendiente";"REPORTED"->"Corrección reportada";"VERIFIED"->"Subsanado";"REOPENED"->"Reabrir";"NONE"->"Sin corrección";else->s}
