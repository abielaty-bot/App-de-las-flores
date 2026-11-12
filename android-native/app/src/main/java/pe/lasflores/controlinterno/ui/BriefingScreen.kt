package pe.lasflores.controlinterno.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import pe.lasflores.controlinterno.data.BriefingAttendanceEntity
import pe.lasflores.controlinterno.data.BriefingSessionEntity
import pe.lasflores.controlinterno.data.BriefingPreferencesEntity
import pe.lasflores.controlinterno.data.StaffEntity
import pe.lasflores.controlinterno.domain.BriefingRules
import pe.lasflores.controlinterno.domain.StaffScheduleRules

@Composable
fun BriefingScreen(vm:BriefingViewModel, staffVm:StaffViewModel) {
    val people by staffVm.staff.collectAsState()
    val vacations by staffVm.vacations.collectAsState()
    val overrides by staffVm.overrides.collectAsState()
    val date by vm.day.collectAsState()
    val loadedRecords by vm.dayRecords.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val dayPlans by vm.dayPlans.collectAsState()
    val prefs by vm.preferences.collectAsState()
    val pending by vm.pending.collectAsState()
    val history by vm.history.collectAsState()
    val events by vm.events.collectAsState()
    val message by vm.message.collectAsState()
    var section by rememberSaveable { mutableStateOf("registro") }
    var selected by rememberSaveable(date) { mutableStateOf<Int?>(null) }
    val daily=sessions.filter{it.dateIso==date}.sortedWith(compareBy({it.scheduledTime},{it.groupHour}))
    val records=loadedRecords.filter{it.dateIso==date}
    val statuses=remember(people,vacations,overrides,date) {
        people.associate{it.id to StaffScheduleRules.status(it,date,vacations,overrides)}
    }
    val scheduled=people.filter{StaffScheduleRules.isScheduled(statuses[it.id].orEmpty())}
    val noBriefing=dayPlans.any{it.dateIso==date && it.status=="NO_REALIZADA"}
    LaunchedEffect(date,daily.map{it.groupHour}) {
        if(selected==null || daily.none{it.groupHour==selected})
            selected=daily.firstOrNull{it.status!="CANCELADO"}?.groupHour ?: daily.firstOrNull()?.groupHour
    }
    Column(Modifier.fillMaxSize()) {
        SectionControls(listOf("registro" to "Registro","pendientes" to "Por informar",
            "historial" to "Historial","estadisticas" to "Estadísticas"),section,{section=it})
        Column(Modifier.padding(horizontal=14.dp,vertical=3.dp)) {
            BriefingDateControls(date,vm)
            Text(if(ErrorReportText.isError(message))ErrorReportText.compact(message) else message,
                style=MaterialTheme.typography.labelSmall,
                color=if(ErrorReportText.isError(message))MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
        when(section) {
            "pendientes" -> BriefingPendingPanel(pending,sessions,date,vm)
            "historial" -> BriefingHistoryPanel(history,sessions,dayPlans,events,date)
            "estadisticas" -> BriefingStatisticsPanel(history,sessions,dayPlans,people,date)
            else -> BriefingRegisterPanel(date,daily,records,scheduled,people,selected,
                {selected=it},noBriefing,prefs?:BriefingPreferencesEntity(),vm,message=="Guardando…")
        }
    }
}

@Composable
private fun BriefingDateControls(date:String,vm:BriefingViewModel) {
    val context=LocalContext.current
    val d=LocalDate.parse(date)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp),
        verticalAlignment=Alignment.CenterVertically) {
        OutlinedButton(onClick={vm.nextDay(-1)}){Text("‹")}
        OutlinedButton(onClick={
            DatePickerDialog(context,{_,year,month,day->
                vm.setDay(LocalDate.of(year,month+1,day).toString())
            },d.year,d.monthValue-1,d.dayOfMonth).show()
        },modifier=Modifier.weight(1f)){
            Text(d.format(DateTimeFormatter.ofPattern("d MMM yyyy",Locale("es","PE"))))
        }
        OutlinedButton(onClick={vm.nextDay(1)}){Text("›")}
        TextButton(onClick={vm.setDay(LocalDate.now().toString())}){Text("Hoy")}
    }
}

@Composable
internal fun BriefingTimeButton(label:String,time:String,onPick:(String)->Unit,enabled:Boolean=true) {
    val context=LocalContext.current
    val parsed=runCatching{LocalTime.parse(time)}.getOrDefault(LocalTime.of(11,0))
    OutlinedButton(enabled=enabled,onClick={
        TimePickerDialog(context,{_,h,m->
            onPick(String.format(Locale.ROOT,"%02d:%02d",h,m))
        },parsed.hour,parsed.minute,true).show()
    },modifier=Modifier.fillMaxWidth()){Text(label+" · "+time)}
}

@Composable
internal fun BriefingDefaultTimesCard(vm:BriefingViewModel) {
    val saved by vm.preferences.collectAsState()
    var first by remember(saved){mutableStateOf(saved?.firstTime?:"11:00")}
    var second by remember(saved){mutableStateOf(saved?.secondTime?:"12:00")}
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
            Text("Horarios sugeridos de briefing",fontWeight=FontWeight.Bold)
            Text("Solo se aplican a sesiones nuevas. No cambian sesiones anteriores ni obligan a realizar dos.",style=MaterialTheme.typography.bodySmall)
            BriefingTimeButton("Primera sesión",first,{first=it})
            BriefingTimeButton("Segunda sesión",second,{second=it})
            Button(onClick={vm.savePreferences(first,second)},
                enabled=first!=(saved?.firstTime?:"11:00") || second!=(saved?.secondTime?:"12:00")){
                Text("Guardar horarios")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BriefingRegisterPanel(
    date:String,daily:List<BriefingSessionEntity>,records:List<BriefingAttendanceEntity>,
    scheduled:List<StaffEntity>,people:List<StaffEntity>,selected:Int?,select:(Int)->Unit,
    noBriefing:Boolean,prefs:BriefingPreferencesEntity,vm:BriefingViewModel,busy:Boolean
) {
    val session=daily.firstOrNull{it.groupHour==selected}
    val live=daily.filter{it.status!="CANCELADO"}
    val active=BriefingRules.activeRecords(records,daily)
    val stats=BriefingRules.stats(active)
    val uniqueAttended=active.filter{it.presence==BriefingRules.ASISTIO}.map{it.staffId}.distinct().size
    val sessionRecords=records.filter{it.groupHour==session?.groupHour}
    val byStaff=sessionRecords.associateBy{it.staffId}
    val participants=session?.let(BriefingRules::participants).orEmpty()
    val selectedPeople=people.filter{it.id in participants}.sortedBy{it.fullName.lowercase()}
    var editing by remember(date){mutableStateOf(false)}
    var editingSession by remember(date){mutableStateOf<BriefingSessionEntity?>(null)}
    var noDayDialog by remember(date){mutableStateOf(false)}
    var cancelSession by remember(date){mutableStateOf<BriefingSessionEntity?>(null)}
    var finishSession by remember(date){mutableStateOf<BriefingSessionEntity?>(null)}
    var confirmMark by remember(date,selected){mutableStateOf<Pair<String,String?>?>(null)}
    var communicate by remember(date,selected){mutableStateOf<BriefingAttendanceEntity?>(null)}
    var topics by rememberSaveable(date,selected,session?.topics){mutableStateOf(session?.topics.orEmpty())}

    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(14.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {
            Card(Modifier.fillMaxWidth()){
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                    Text("Briefings del día",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    Text(when {
                        noBriefing -> "No se realizó briefing"
                        daily.isEmpty() -> "Día sin definir · aún no hay sesiones"
                        else -> live.count{it.status=="REALIZADO"}.toString()+" realizadas · "+
                            live.count{it.status=="PROGRAMADO"}.toString()+" programadas · "+
                            live.count{it.status=="EN_CURSO"}.toString()+" en curso"
                    },color=MaterialTheme.colorScheme.primary)
                    if(live.any{it.status=="HISTORICO"})Text("Sesiones históricas: "+live.count{it.status=="HISTORICO"}+
                        " · no se conoce su hora real ni su cierre.",style=MaterialTheme.typography.bodySmall)
                    Text("Programados y apoyos: "+scheduled.size+" · Participantes únicos: "+uniqueAttended)
                    Text("Asistencias: "+stats.attended+" · Por informar: "+stats.pending+" · No asistieron: "+stats.missed)
                    Text("Cada persona cuenta una vez como participante único. Las sesiones canceladas no generan faltas.",
                        style=MaterialTheme.typography.bodySmall)
                    Button(enabled=!busy,onClick={editingSession=null;editing=true},modifier=Modifier.fillMaxWidth()){
                        Text("+ Agregar briefing")
                    }
                    if(live.isEmpty()) OutlinedButton(enabled=!busy,onClick={if(noBriefing)vm.noBriefing(date,false) else noDayDialog=true},
                        modifier=Modifier.fillMaxWidth()){
                        Text(if(noBriefing)"Volver a dejar sin definir" else "Hoy no se realizó briefing")
                    }
                }
            }
        }
        if(daily.isNotEmpty())item{
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                daily.forEachIndexed{index,s->
                    FilterChip(selected=s.groupHour==selected,onClick={select(s.groupHour)},
                        label={Text((index+1).toString()+". "+BriefingRules.timeLabel(s)+
                            if(s.status=="CANCELADO")" · Cancelado" else "")})
                }
            }
        }
        if(session!=null){
            item{
                Card(Modifier.fillMaxWidth()){
                    Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                        Text("Sesión · "+BriefingRules.timeLabel(session),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                        Text(BriefingRules.stateLabel(session.status)+" · Hora programada: "+session.scheduledTime.ifBlank{BriefingRules.legacyTime(session.groupHour)})
                        if(session.actualTime!=null)Text("Hora real: "+session.actualTime)
                        Text("Convocados: "+participants.size+" · Asistió: "+sessionRecords.count{it.presence==BriefingRules.ASISTIO}+
                            " · Sin registrar: "+(participants.size-sessionRecords.size).coerceAtLeast(0))
                        if(session.status=="HISTORICO")Text("Convocados históricos: únicamente se conocen los trabajadores registrados. Puedes añadir otros si corresponde.",style=MaterialTheme.typography.bodySmall)
                        if(session.status!="CANCELADO"){
                            OutlinedButton(enabled=!busy,onClick={editingSession=session;editing=true},modifier=Modifier.fillMaxWidth()){
                                Text("Editar horario y participantes")
                            }
                            if(session.status=="PROGRAMADO")Button(enabled=!busy,onClick={vm.changeState(date,session.groupHour,"EN_CURSO")}){
                                Text("Iniciar briefing")
                            }
                            OutlinedButton(enabled=!busy,onClick={finishSession=session}){
                                Text(if(session.status=="REALIZADO")"Editar hora real" else "Marcar realizado")
                            }
                            TextButton(enabled=!busy,onClick={cancelSession=session}){Text("Cancelar sesión")}
                        } else {
                            Text("Sesión cancelada. Sus registros se conservan como historial y no cuentan en las estadísticas.",
                                style=MaterialTheme.typography.bodySmall)
                            Button(enabled=!busy,onClick={vm.changeState(date,session.groupHour,
                                if(sessionRecords.isEmpty())"PROGRAMADO" else "EN_CURSO")}){
                                Text("Reactivar sesión")
                            }
                        }
                    }
                }
            }
            items(selectedPeople,key={it.id}){worker->
                val previous=byStaff[worker.id]
                Card(Modifier.fillMaxWidth()){
                    Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                        Text(worker.fullName,fontWeight=FontWeight.Bold)
                        Text(previous?.presence?.let(BriefingRules::label)?:"Sin registrar",
                            style=MaterialTheme.typography.bodySmall,
                            color=if(previous?.presence==BriefingRules.FALTA)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        if(session.status!="CANCELADO"){
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(5.dp)){
                                listOf(BriefingRules.ASISTIO to "Asistió",BriefingRules.MESA to "En mesa",BriefingRules.FALTA to "No asistió").forEach{(code,label)->
                                    val category=when(code){BriefingRules.ASISTIO->"success";BriefingRules.FALTA->"danger";else->"warning"}
                                    FilterChip(selected=previous?.presence==code,enabled=!busy,
                                        onClick={if(previous?.presence!=code)confirmMark=worker.id to code},
                                        label={Text(label)},colors=FilterChipDefaults.filterChipColors(
                                            selectedContainerColor=semanticBackground(category),selectedLabelColor=semanticForeground(category)))
                                }
                            }
                            if(previous!=null)TextButton(enabled=!busy,onClick={confirmMark=worker.id to null}){Text("Quitar registro")}
                        }
                        if(previous?.presence==BriefingRules.MESA){
                            Text(BriefingRules.communicationLabel(previous)+
                                if(previous.communicationResponsible.isNotBlank())" · "+previous.communicationResponsible else "",
                                style=MaterialTheme.typography.bodySmall)
                            if(session.status!="CANCELADO")OutlinedButton(enabled=!busy,onClick={communicate=previous}){
                                Text("Registrar / corregir comunicación")
                            }
                        }
                    }
                }
            }
            if(selectedPeople.isEmpty())item{Text("No hay convocados. Usa Editar horario y participantes.")}
            item{
                Card(Modifier.fillMaxWidth()){
                    Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                        Text("Indicaciones de esta sesión",fontWeight=FontWeight.Bold)
                        OutlinedTextField(value=topics,onValueChange={topics=it.take(3000)},
                            enabled=session.status!="CANCELADO"&&!busy,label={Text("Temas tratados")},
                            minLines=3,modifier=Modifier.fillMaxWidth())
                        Button(enabled=session.status!="CANCELADO"&&!busy&&topics.trim()!=session.topics,
                            onClick={vm.saveTopics(date,session.groupHour,topics)}){Text("Guardar temas")}
                    }
                }
            }
        }
    }
    if(editing) {
        val old=editingSession
        val already=live.flatMap{BriefingRules.participants(it)}.toSet()
        val eligible=scheduled.filter{it.id !in already}.map{it.id}.toSet()
        val initial=old?.let(BriefingRules::participants) ?: if(live.isEmpty())scheduled.map{it.id}.toSet() else eligible
        val locked=if(old==null)emptySet() else records.filter{it.groupHour==old.groupHour}.map{it.staffId}.toSet()
        val suggested=if(live.isEmpty())prefs.firstTime else prefs.secondTime
        SessionEditDialog(old,suggested,people.filter{it.id in scheduled.map{p->p.id}.toSet()||it.id in initial},
            initial,locked,onDismiss={editing=false},onSave={time,ids->
                vm.saveSession(date,old?.groupHour,time,ids);editing=false
            })
    }
    if(noDayDialog)AlertDialog(onDismissRequest={noDayDialog=false},title={Text("¿No hubo briefing?")},
        text={Text("Quedará registrado que este día no se realizó briefing. No se generarán inasistencias.")},
        confirmButton={Button(onClick={vm.noBriefing(date,true);noDayDialog=false}){Text("Confirmar")}},
        dismissButton={TextButton(onClick={noDayDialog=false}){Text("Cancelar")}})
    cancelSession?.let{s->
        AlertDialog(onDismissRequest={cancelSession=null},title={Text("Cancelar sesión")},
            text={Text("Se conservarán sus temas, asistencias y auditoría, pero no contará en las estadísticas ni generará avisos.")},
            confirmButton={Button(onClick={vm.changeState(date,s.groupHour,"CANCELADO");cancelSession=null}){Text("Confirmar")}},
            dismissButton={TextButton(onClick={cancelSession=null}){Text("Volver")}})
    }
    finishSession?.let{s->
        var actual by remember(s.groupHour){mutableStateOf(s.actualTime ?: if(date==LocalDate.now().toString())
            LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) else s.scheduledTime)}
        AlertDialog(onDismissRequest={finishSession=null},title={Text("Hora real del briefing")},
            text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text("Confirma la hora en que se realizó. Las asistencias sin marcar permanecen sin registrar.")
                BriefingTimeButton("Hora real",actual,{actual=it})
            }},
            confirmButton={Button(enabled=BriefingRules.validTime(actual),onClick={vm.changeState(date,s.groupHour,"REALIZADO",actual);finishSession=null}){Text("Guardar")}},
            dismissButton={TextButton(onClick={finishSession=null}){Text("Cancelar")}})
    }
    confirmMark?.let{(id,presence)->
        val name=people.firstOrNull{it.id==id}?.fullName.orEmpty()
        AlertDialog(onDismissRequest={confirmMark=null},title={Text("Confirmar asistencia")},
            text={Text(name+": "+(presence?.let(BriefingRules::label)?:"Quitar registro")+
                ". El cambio quedará en el historial.")},
            confirmButton={Button(onClick={session?.let{vm.mark(date,it.groupHour,id,presence)};confirmMark=null}){Text("Confirmar")}},
            dismissButton={TextButton(onClick={confirmMark=null}){Text("Cancelar")}})
    }
    communicate?.let{record->
        BriefingCommunicationDialog(record,session?.let(BriefingRules::timeLabel).orEmpty(),vm,{communicate=null})
    }
}

@Composable
private fun SessionEditDialog(old:BriefingSessionEntity?,suggested:String,people:List<StaffEntity>,
    initial:Set<String>,locked:Set<String>,onDismiss:()->Unit,onSave:(String,Set<String>)->Unit) {
    var time by remember(old?.groupHour){mutableStateOf(old?.scheduledTime?.takeIf(BriefingRules::validTime)?:suggested)}
    var selected by remember(old?.groupHour){mutableStateOf(initial)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(old==null)"Nuevo briefing" else "Editar sesión")},
        text={Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),
            verticalArrangement=Arrangement.spacedBy(6.dp)){
            BriefingTimeButton("Hora programada",time,{time=it})
            Text("Convocados · "+selected.size,fontWeight=FontWeight.Bold)
            Text("Selecciona el grupo o a todos los trabajadores programados. Cada sesión conserva sus participantes.",style=MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){
                TextButton(onClick={selected=people.map{it.id}.toSet()+locked}){Text("Todos")}
                TextButton(onClick={selected=locked}){Text("Limpiar")}
            }
            Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){
                TextButton(onClick={selected=people.filter{it.briefingGroup==11}.map{it.id}.toSet()+locked}){Text("Grupo habitual 1")}
                TextButton(onClick={selected=people.filter{it.briefingGroup==12}.map{it.id}.toSet()+locked}){Text("Grupo habitual 2")}
            }
            people.forEach{p->
                Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()){
                    Checkbox(checked=p.id in selected,enabled=p.id !in locked,onCheckedChange={checked->
                        selected=if(checked)selected+p.id else selected-p.id
                    })
                    Column{
                        Text(p.fullName)
                        if(p.id in locked)Text("Asistencia registrada",style=MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }},
        confirmButton={Button(enabled=BriefingRules.validTime(time),onClick={onSave(time,selected)}){Text("Guardar sesión")}},
        dismissButton={TextButton(onClick=onDismiss){Text("Cancelar")}})
}
