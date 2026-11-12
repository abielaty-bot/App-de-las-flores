package pe.lasflores.controlinterno.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import pe.lasflores.controlinterno.data.BriefingAttendanceEntity
import pe.lasflores.controlinterno.data.BriefingSessionEntity
import pe.lasflores.controlinterno.data.BriefingEventEntity
import pe.lasflores.controlinterno.data.DailyActivityEntity
import pe.lasflores.controlinterno.data.StaffEntity
import pe.lasflores.controlinterno.domain.BriefingRules

private fun communicationDate(value:Long?) = value?.let{
    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
}.orEmpty()

@Composable
internal fun BriefingCommunicationDialog(record:BriefingAttendanceEntity,time:String,vm:BriefingViewModel,onDismiss:()->Unit) {
    var state by remember(record.id){mutableStateOf(if(record.communicationState=="INFORMADO_HISTORICO")"" else record.communicationState)}
    var actor by remember(record.id){mutableStateOf(record.communicationResponsible.ifBlank{record.informedBy.orEmpty()})}
    var note by remember(record.id){mutableStateOf(record.communicationNote)}
    val options=listOf("PERSONAL" to "Informado personalmente",
        "OTRO_RESPONSABLE" to "Informado por otro responsable",
        "PENDIENTE_CONFIRMAR" to "Pendiente de confirmar",
        "PENDIENTE" to "Pendiente de informar")
    AlertDialog(onDismissRequest=onDismiss,title={Text("Comunicación presencial")},
        text={Column(Modifier.heightIn(max=450.dp).verticalScroll(rememberScrollState()),
            verticalArrangement=Arrangement.spacedBy(5.dp)){
            Text(record.staffNameSnapshot+" · "+record.dateIso+" · "+time,fontWeight=FontWeight.Bold)
            Text("Asistencia original: "+BriefingRules.label(record.presence)+". No se modificará.",style=MaterialTheme.typography.bodySmall)
            if(record.communicationState=="INFORMADO_HISTORICO")Text("Registro anterior: informado por "+record.informedBy.orEmpty()+". Selecciona el método si necesitas corregirlo.")
            options.forEach{(code,label)->
                Row(verticalAlignment=Alignment.CenterVertically){
                    RadioButton(selected=state==code,onClick={state=code})
                    TextButton(onClick={state=code}){Text(label)}
                }
            }
            OutlinedTextField(value=actor,onValueChange={actor=it.take(100)},
                label={Text(if(state=="PENDIENTE_CONFIRMAR")"Responsable asignado" else "Quién comunicó / responsable")},
                modifier=Modifier.fillMaxWidth())
            OutlinedTextField(value=note,onValueChange={note=it.take(500)},label={Text("Observación opcional")},
                minLines=2,modifier=Modifier.fillMaxWidth())
            Text("Solo comunicación cara a cara. Se guarda fecha, hora y auditoría.",style=MaterialTheme.typography.bodySmall)
        }},
        confirmButton={Button(enabled=state.isNotBlank()&&(state=="PENDIENTE"||actor.trim().length>=2),
            onClick={vm.inform(record.id,state,actor,note);onDismiss()}){Text("Guardar")}},
        dismissButton={TextButton(onClick=onDismiss){Text("Cancelar")}})
}

@Composable
fun BriefingPendingPanel(records:List<BriefingAttendanceEntity>,sessions:List<BriefingSessionEntity>,date:String,vm:BriefingViewModel) {
    var all by rememberSaveable{mutableStateOf(true)}
    var chosen by remember{mutableStateOf<BriefingAttendanceEntity?>(null)}
    val index=sessions.associateBy{it.dateIso to it.groupHour}
    val filtered=records.filter{all||it.dateIso==date}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(14.dp),
        verticalArrangement=Arrangement.spacedBy(9.dp)){
        item{
            Text("Por informar",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Text("Seguimiento presencial · "+filtered.size+" pendientes")
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                FilterChip(selected=all,onClick={all=true},label={Text("Todas las fechas")})
                FilterChip(selected=!all,onClick={all=false},label={Text("Fecha elegida")})
            }
        }
        items(filtered,key={it.id}){item->
            val time=index[item.dateIso to item.groupHour]?.let(BriefingRules::timeLabel)?:BriefingRules.legacyTime(item.groupHour)
            Card(Modifier.fillMaxWidth()){
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                    Text(item.staffNameSnapshot,fontWeight=FontWeight.Bold)
                    Text(item.dateIso+" · "+time)
                    Text(BriefingRules.communicationLabel(item),color=MaterialTheme.colorScheme.primary)
                    if(item.communicationResponsible.isNotBlank())Text("Responsable: "+item.communicationResponsible)
                    if(item.communicationNote.isNotBlank())Text(item.communicationNote,style=MaterialTheme.typography.bodySmall)
                    Button(onClick={chosen=item}){Text("Registrar comunicación")}
                }
            }
        }
        if(filtered.isEmpty())item{Text("No hay comunicaciones pendientes.")}
    }
    chosen?.let{item->
        BriefingCommunicationDialog(item,index[item.dateIso to item.groupHour]?.let(BriefingRules::timeLabel).orEmpty(),vm,{chosen=null})
    }
}

private fun dateInScope(iso:String,date:String,scope:String):Boolean {
    val anchor=LocalDate.parse(date)
    return when(scope){
        "day"->iso==date
        "week"->{val start=anchor.minusDays((anchor.dayOfWeek.value-1).toLong());iso>=start.toString()&&iso<=start.plusDays(6).toString()}
        "month"->iso.startsWith(date.take(7))
        else->true
    }
}

@Composable
private fun PeriodChips(scope:String,select:(String)->Unit,all:Boolean=false) {
    LazyRow(horizontalArrangement=Arrangement.spacedBy(5.dp)){
        val scopes=listOf("day" to "Día","week" to "Semana","month" to "Mes")+(if(all)listOf("all" to "Todo")else emptyList())
        items(scopes){(id,label)->FilterChip(selected=id==scope,onClick={select(id)},label={Text(label)})}
    }
}

@Composable
fun BriefingHistoryPanel(records:List<BriefingAttendanceEntity>,sessions:List<BriefingSessionEntity>,
    plans:List<DailyActivityEntity>,events:List<BriefingEventEntity>,date:String) {
    var scope by rememberSaveable{mutableStateOf("day")}
    var query by rememberSaveable{mutableStateOf("")}
    var audit by rememberSaveable{mutableStateOf(false)}
    val selectedSessions=sessions.filter{dateInScope(it.dateIso,date,scope)}
    val index=selectedSessions.associateBy{it.dateIso to it.groupHour}
    val filtered=records.filter{dateInScope(it.dateIso,date,scope)&&it.staffNameSnapshot.contains(query,true)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{
            Text("Historial de briefings",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            PeriodChips(scope,{scope=it},true)
            OutlinedTextField(value=query,onValueChange={query=it.take(100)},label={Text("Buscar trabajador")},modifier=Modifier.fillMaxWidth())
            TextButton(onClick={audit=!audit}){Text(if(audit)"Ver registros" else "Ver auditoría")}
        }
        if(audit){
            items(events.filter{dateInScope(it.dateIso,date,scope)}.take(200),key={it.id}){event->
                Card(Modifier.fillMaxWidth()){
                    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                        Text(event.dateIso+" · "+event.action,fontWeight=FontWeight.Bold)
                        Text(event.beforeValue+" → "+event.afterValue,style=MaterialTheme.typography.bodySmall)
                        Text(event.actor+" · "+event.details,style=MaterialTheme.typography.bodySmall)
                        Text(communicationDate(event.atMillis),style=MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }else{
            items(plans.filter{dateInScope(it.dateIso,date,scope)},key={it.dateIso}){plan->
                Text(plan.dateIso+" · No se realizó briefing",fontWeight=FontWeight.Medium)
            }
            if(query.isBlank())items(selectedSessions,key={it.dateIso+":"+it.groupHour}){s->
                Card(Modifier.fillMaxWidth()){
                    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                        Text(s.dateIso+" · "+BriefingRules.timeLabel(s),fontWeight=FontWeight.Bold)
                        Text(BriefingRules.stateLabel(s.status)+" · Convocados: "+BriefingRules.participants(s).size)
                        Text("Hora programada: "+s.scheduledTime+" · Real: "+(s.actualTime?:"Sin registrar"),style=MaterialTheme.typography.bodySmall)
                        if(s.topics.isNotBlank())Text(s.topics,style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
            items(filtered,key={it.id}){record->
                val s=index[record.dateIso to record.groupHour]
                Card(Modifier.fillMaxWidth()){
                    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                        Text(record.staffNameSnapshot,fontWeight=FontWeight.Bold)
                        Text(record.dateIso+" · "+(s?.let(BriefingRules::timeLabel)?:BriefingRules.legacyTime(record.groupHour)))
                        Text(BriefingRules.label(record.presence)+" · "+BriefingRules.communicationLabel(record))
                        if(s?.status=="CANCELADO")Text("Sesión cancelada · no cuenta en estadísticas")
                        if(record.communicationResponsible.isNotBlank())Text("Responsable: "+record.communicationResponsible)
                        if(record.communicationNote.isNotBlank())Text(record.communicationNote,style=MaterialTheme.typography.bodySmall)
                        if(record.informedAtMillis!=null)Text("Informado: "+communicationDate(record.informedAtMillis),style=MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if(filtered.isEmpty()&&selectedSessions.isEmpty())item{Text("No hay registros en este período. No implica que se haya cancelado el briefing.")}
        }
    }
}

@Composable
fun BriefingStatisticsPanel(records:List<BriefingAttendanceEntity>,sessions:List<BriefingSessionEntity>,
    plans:List<DailyActivityEntity>,staff:List<StaffEntity>,date:String) {
    var scope by rememberSaveable{mutableStateOf("month")}
    var filter by rememberSaveable{mutableStateOf("todos")}
    val scoped=sessions.filter{dateInScope(it.dateIso,date,scope)}
    val valid=scoped.filter{it.status in setOf("REALIZADO","HISTORICO")}
    val keys=valid.map{it.dateIso to it.groupHour}.toSet()
    val base=records.filter{(it.dateIso to it.groupHour) in keys}
    val data=base.filter{filter=="todos"||it.staffId==filter}
    val stats=BriefingRules.stats(data)
    val people=staff.filter{it.isActive||records.any{a->a.staffId==it.id}}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
        item{
            Text("Estadísticas",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            PeriodChips(scope,{scope=it})
            LazyRow(horizontalArrangement=Arrangement.spacedBy(5.dp)){
                item{FilterChip(selected=filter=="todos",onClick={filter="todos"},label={Text("Todos")})}
                items(people,key={it.id}){p->FilterChip(selected=filter==p.id,onClick={filter=p.id},label={Text(p.fullName)})}
            }
            Card(Modifier.fillMaxWidth()){
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                    Text("Resumen del período",fontWeight=FontWeight.Bold)
                    Text("Sesiones realizadas: "+scoped.count{it.status=="REALIZADO"}+" · Históricas: "+scoped.count{it.status=="HISTORICO"})
                    Text("Días sin briefing: "+plans.count{dateInScope(it.dateIso,date,scope)&&it.status=="NO_REALIZADA"})
                    Text("Sesiones canceladas: "+scoped.count{it.status=="CANCELADO"})
                    Text("Asistencias registradas: "+stats.attended+" / "+stats.registered)
                    Text("Participantes únicos: "+data.filter{it.presence==BriefingRules.ASISTIO}.map{it.staffId}.distinct().size)
                    Text("Participación: "+stats.attendancePercentage+"% · Comunicación: "+stats.communicationPercentage+"%")
                    Text("No asistieron: "+stats.missed+" · Por informar: "+stats.pending)
                    Text("Solo sesiones realizadas o históricas. Los porcentajes usan registros explícitos; sin registrar no es falta.",style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        items(people.filter{filter=="todos"||it.id==filter},key={it.id}){p->
            val result=BriefingRules.stats(base.filter{it.staffId==p.id})
            Card(Modifier.fillMaxWidth()){
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                    Text(p.fullName,fontWeight=FontWeight.Bold)
                    Text("Registros: "+result.registered+" · Asistió: "+result.attended+" · Por informar: "+result.pending)
                    Text("Participación: "+result.attendancePercentage+"% · Comunicación: "+result.communicationPercentage+"%",style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
