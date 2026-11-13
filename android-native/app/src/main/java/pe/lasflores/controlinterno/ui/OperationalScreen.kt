package pe.lasflores.controlinterno.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
    var filter by rememberSaveable{mutableStateOf("")}
    var pendingOnly by rememberSaveable{mutableStateOf(false)}
    var message by remember{mutableStateOf("")}
    var busy by remember{mutableStateOf(false)}
    var selected by remember{mutableStateOf<OperationalInstanceEntity?>(null)}
    var edit by remember{mutableStateOf<OperationalTemplateEntity?>(null)}
    var people by remember{mutableStateOf(emptyList<StaffEntity>())}
    val period=when(mode){"Semanal"->date.minusDays((date.dayOfWeek.value-1).toLong()).toString()+"-W";"Mensual"->dateIso.take(7);else->dateIso}
    val rows by remember(period){repo.observe(period)}.collectAsState(initial=emptyList())
    val templates by remember{repo.catalog.observe()}.collectAsState(initial=emptyList())
    fun run(action:suspend ()->Unit){if(busy)return;busy=true;scope.launch{
        try{action();message="Guardado automáticamente"}catch(e:CancellationException){throw e}catch(e:Exception){message="Error: "+(e.message?:"No se pudo guardar")}finally{busy=false}
    }}
    LaunchedEffect(dateIso){try{repo.catalog.initialize();people=repo.available(date)}catch(e:CancellationException){throw e}catch(e:Exception){message="Error: "+e.message}}
    val visible=rows.filter{(!pendingOnly || it.initialResult=="UNREVIEWED" || it.correction in setOf("PENDING","REPORTED","REOPENED")) && (filter.isBlank() || listOf(it.locationSnapshot,it.staffNameSnapshot,it.labelSnapshot).any{s->s.contains(filter,true)})}
    val reviewed=rows.count{it.initialResult!="UNREVIEWED"}
    val conform=rows.count{it.initialResult=="CONFORMING"}
    val evaluated=rows.count{it.initialResult in setOf("CONFORMING","NONCONFORMING")}
    Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            TextButton(onClick={dateIso=date.minusDays(1).toString()}){Text("‹ Anterior")}
            Text(dateIso,style=MaterialTheme.typography.titleMedium)
            TextButton(onClick={dateIso=date.plusDays(1).toString()}){Text("Siguiente ›")}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
            listOf("Diaria","Semanal","Mensual","Catálogo").forEach{m->FilterChip(selected=mode==m,onClick={mode=m},label={Text(m)})}
        }
        if(mode!="Catálogo"){
            Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){
                Text("Supervisión operativa",style=MaterialTheme.typography.titleLarge)
                Text("Revisados: $reviewed / ${rows.size} · Sin asignar: ${rows.count{it.staffNameSnapshot=="Sin asignar"}}")
                Text("Cumplimiento inicial: "+if(evaluated==0)"Sin datos" else "${100*conform/evaluated}%")
                Text("Correcciones abiertas: ${rows.count{it.correction in setOf("PENDING","REPORTED","REOPENED")}}")
                LinearProgressIndicator(progress={if(rows.isEmpty())0f else reviewed.toFloat()/rows.size},modifier=Modifier.fillMaxWidth())
            }}
            Button(onClick={run{repo.generate(date)}},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Preparar checklist de la jornada")}
            OutlinedTextField(filter,{filter=it},label={Text("Buscar trabajador, zona o función")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            FilterChip(selected=pendingOnly,onClick={pendingOnly=!pendingOnly},label={Text("Solo pendientes")})
        }
        if(message.isNotBlank())Text(message,color=if(message.startsWith("Error"))MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){
            if(mode=="Catálogo")items(templates,key={it.id}){t->
                Card(onClick={edit=t},modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){
                    Text(t.label,style=MaterialTheme.typography.titleMedium)
                    Text("${t.groupName} · ${t.location} · ${t.frequency}")
                    Text("Estándar: ${t.standard} · v${t.version}")
                    Text(if(t.active)"Activa" else "Desactivada")
                }}
            }else items(visible,key={it.id}){row->
                Card(onClick={selected=row},modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){
                    Text(row.locationSnapshot+" · "+row.staffNameSnapshot,style=MaterialTheme.typography.labelMedium)
                    Text(row.labelSnapshot,style=MaterialTheme.typography.titleMedium)
                    Text("Estándar: "+row.standardSnapshot)
                    Text(label(row.initialResult)+" · "+label(row.correction),color=if(row.initialResult=="NONCONFORMING"||row.initialResult=="BLOCKED")MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }}
            }
        }
    }
    selected?.let{row->
        var note by remember(row.id){mutableStateOf(row.note)}
        var attributable by remember(row.id){mutableStateOf(row.attributable)}
        var assigning by remember(row.id){mutableStateOf(false)}
        AlertDialog(onDismissRequest={selected=null},title={Text(row.labelSnapshot)},text={
            Column(Modifier.heightIn(max=480.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text(row.standardSnapshot)
                Text("Responsable: "+row.staffNameSnapshot)
                OutlinedTextField(note,{note=it},label={Text("Observación / motivo")})
                if(row.initialResult=="UNREVIEWED"||row.correction in setOf("NONE","PENDING")){
                    Row{Checkbox(attributable,{attributable=it});Text("Incumplimiento atribuible")}
                    listOf("CONFORMING","NONCONFORMING","BLOCKED","NOT_APPLICABLE","UNREVIEWED").forEach{v->
                        OutlinedButton(onClick={run{repo.review(row.id,v,note,attributable);selected=null}},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(label(v))}
                    }
                }
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
            OutlinedTextField(label,{label=it},label={Text("Función")})
            OutlinedTextField(standard,{standard=it},label={Text("Estándar")})
            Row{Checkbox(active,{active=it});Text("Activa")}
        }},confirmButton={Button(enabled=label.isNotBlank()&&standard.isNotBlank()&&!busy,onClick={run{repo.catalog.edit(t.copy(label=label,standard=standard,active=active));edit=null}}){Text("Guardar")}},dismissButton={TextButton(onClick={edit=null}){Text("Cancelar")}})
    }
}
private fun label(s:String)=when(s){"UNREVIEWED"->"Sin revisar";"CONFORMING"->"Conforme";"NONCONFORMING"->"No conforme";"BLOCKED"->"Bloqueado";"NOT_APPLICABLE"->"No aplica";"PENDING"->"Corrección pendiente";"REPORTED"->"Corrección reportada";"VERIFIED"->"Subsanado";"REOPENED"->"Reabrir";"NONE"->"Sin corrección";else->s}
