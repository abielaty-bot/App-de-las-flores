package pe.lasflores.controlinterno.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.delay
import pe.lasflores.controlinterno.data.*
import pe.lasflores.controlinterno.domain.CommandCenterRules
import pe.lasflores.controlinterno.domain.JourneyRules
import pe.lasflores.controlinterno.domain.JourneyTask
import pe.lasflores.controlinterno.journey.JourneyAlerts
import pe.lasflores.controlinterno.journey.JourneyEngine
import pe.lasflores.controlinterno.journey.JourneyNavigation
import pe.lasflores.controlinterno.journey.JourneyState

private fun timeLabel(at:Long)=Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
private fun dateLabel(at:Long)=Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
private fun JourneyTask.route()=JourneyNavigation(module,dateIso,sessionSlot,key)
private fun JourneyNotificationEntity.route()=JourneyNavigation(module,dateIso,sessionSlot,taskKey,id)

@Composable
internal fun JourneyDashboardCard(engine:JourneyEngine,onOpen:(JourneyNavigation)->Unit,onCenter:()->Unit){
    val s by engine.state.collectAsState()
    LaunchedEffect(s.dateIso){while(true){delay(60_000);if(LocalDate.now().toString()!=s.dateIso){engine.refresh();break}}}
    val today=s.tasks.filter{it.dateIso==s.dateIso&&JourneyRules.isProgressTask(it)}
    val done=today.count{it.completed};val pending=today.filter{!it.completed}
    val upcoming=pending.minByOrNull{it.dueAtMillis}
    Card(Modifier.fillMaxWidth()){
        Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Text("Mi jornada",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                    Text(s.dateIso+" · Actividades operativas",style=MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick=onCenter){Text("Ver todo")}
            }
            if(!s.loaded)Text("Cargando actividades…")
            else{
                Text(done.toString()+" de "+today.size+" actividades completadas",fontWeight=FontWeight.Medium)
                LinearProgressIndicator(progress={if(today.isEmpty())0f else done.toFloat()/today.size},modifier=Modifier.fillMaxWidth())
                Text(pending.size.toString()+" pendientes de hoy",style=MaterialTheme.typography.bodySmall)
                if(upcoming!=null){
                    HorizontalDivider()
                    Text(if(upcoming.dueAtMillis<System.currentTimeMillis())"Pendiente de atención" else "Próxima actividad",
                        style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.tertiary)
                    Text(upcoming.title,fontWeight=FontWeight.SemiBold)
                    Text(timeLabel(upcoming.dueAtMillis)+" · "+upcoming.detail,style=MaterialTheme.typography.bodySmall)
                    Button(onClick={onOpen(upcoming.route())},modifier=Modifier.fillMaxWidth()){Text("Abrir actividad")}
                }else Text(if(today.isEmpty())"No hay actividades aplicables hoy." else "Actividades de hoy completadas.")
                if(!s.preferences.enabled)Text("Activa los avisos de Android en Notificaciones.",style=MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick=onCenter,modifier=Modifier.fillMaxWidth()){Text("Centro de pendientes y recordatorios")}
            }
        }
    }
}

@Composable
internal fun JourneyScreen(engine:JourneyEngine,onOpen:(JourneyNavigation)->Unit,onUpdates:()->Unit){
    val s by engine.state.collectAsState()
    var section by rememberSaveable{mutableStateOf("today")}
    var filter by rememberSaveable{mutableStateOf("NOW")}
    var edit by remember{mutableStateOf<JourneyReminderEntity?>(null)}
    var adding by remember{mutableStateOf(false)}
    var removing by remember{mutableStateOf<JourneyReminderEntity?>(null)}
    var now by remember{mutableStateOf(System.currentTimeMillis())}
    LaunchedEffect(s.dateIso){while(true){now=System.currentTimeMillis();if(LocalDate.now().toString()!=s.dateIso)engine.refresh();delay(30_000)}}
    Column(Modifier.fillMaxSize()){
        SectionControls(listOf("today" to "Centro de Mando","reminders" to "Recordatorios","history" to "Historial","settings" to "Ajustes"),section,{section=it})
        if(s.error.isNotBlank())Text("Error: "+s.error,modifier=Modifier.padding(10.dp),color=MaterialTheme.colorScheme.error)
        when(section){
            "today"->{
                val today=s.tasks.filter{JourneyRules.isProgressTask(it)}
                val occurrences=s.occurrences.associateBy{it.taskKey}
                val lanes=today.groupBy{CommandCenterRules.lane(it,occurrences[it.key],now).name}
                val visible=lanes[filter].orEmpty().sortedWith(compareBy<JourneyTask>{CommandCenterRules.priority(it,now)}.thenBy{it.dueAtMillis})
                val radar=CommandCenterRules.radar(today,now)
                LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    item{
                        Card(colors=CardDefaults.cardColors(containerColor=androidx.compose.ui.graphics.Color(0xFF381F2B)),modifier=Modifier.fillMaxWidth()){
                            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                                Text("LAS FLORES · OPERACIONES",color=androidx.compose.ui.graphics.Color(0xFFE5CDA7),style=MaterialTheme.typography.labelMedium)
                                Text("Centro de Mando",color=androidx.compose.ui.graphics.Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                                Text(if(lanes["NOW"].isNullOrEmpty())"Sin actividades vencidas" else "Tu atención es necesaria",color=androidx.compose.ui.graphics.Color.White)
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                                    listOf("NOW" to "Intervenir","NEXT" to "Próximas","FOLLOWUP" to "Completadas").forEach{(key,label)->
                                        Column{Text(lanes[key].orEmpty().size.toString(),color=androidx.compose.ui.graphics.Color.White,style=MaterialTheme.typography.headlineMedium);Text(label,color=androidx.compose.ui.graphics.Color(0xFFE5CDA7),style=MaterialTheme.typography.labelSmall)}
                                    }
                                }
                            }
                        }
                        if(!s.loaded)Text("Cargando…")
                    }
                    item{
                        SectionControls(listOf("NOW" to "Ahora","NEXT" to "Próximas","FOLLOWUP" to "Seguimiento"),filter,{filter=it})
                        Text(when(filter){"NOW"->"Requieren tu atención";"NEXT"->"Tu agenda operativa";else->"Actividades completadas"},style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    }
                    if(radar.isNotEmpty())item{
                        Card(Modifier.fillMaxWidth()){
                            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                                Text("Radar · próximos 60 minutos",fontWeight=FontWeight.Bold)
                                radar.take(4).forEach{task->TextButton(onClick={onOpen(task.route())}){Text(timeLabel(task.dueAtMillis)+" · "+task.title)}}
                            }
                        }
                    }
                    items(visible,key={it.key}){task->TaskCard(task,occurrences[task.key],now,engine,onOpen)}
                    if(visible.isEmpty()&&s.loaded)item{Text("No hay actividades en esta categoría.")}
                    item{TextButton(onClick={section="reminders"}){Text("Crear actividad personalizada")}}
                }
            }
            "reminders"->{
                LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
                    item{
                        Text("Recordatorios personalizados",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                        Text("Puedes definir horarios, repetición, días, anticipación, prioridad y módulo de destino.")
                        Button(onClick={adding=true},modifier=Modifier.fillMaxWidth()){Text("+ Nuevo recordatorio")}
                        OutlinedButton(onClick=engine::templates,modifier=Modifier.fillMaxWidth()){Text("Agregar plantillas recomendadas")}
                        Text("Plantillas: abastecimiento, supervisión, manteles, personal del fin de semana, horarios, ventas y respaldo. Puedes editarlas.",style=MaterialTheme.typography.bodySmall)
                    }
                    items(s.reminders.sortedBy{it.time},key={it.id}){r->
                        Card(Modifier.fillMaxWidth()){
                            Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                                Row(verticalAlignment=Alignment.CenterVertically){
                                    Column(Modifier.weight(1f)){
                                        Text(r.title,fontWeight=FontWeight.Bold)
                                        Text(r.time+" · "+recurrenceLabel(r),style=MaterialTheme.typography.bodySmall)
                                        Text("Desde "+r.startDateIso+" · "+if(r.workdaysOnly)"Solo días laborales" else "Incluye descansos",
                                            style=MaterialTheme.typography.labelSmall)
                                    }
                                    Switch(checked=r.enabled,onCheckedChange={engine.saveReminder(r.copy(enabled=it))})
                                }
                                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                                    OutlinedButton(onClick={edit=r}){Text("Editar")}
                                    TextButton(onClick={removing=r}){Text("Eliminar")}
                                }
                            }
                        }
                    }
                }
            }
            "history"->{
                LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
                    item{
                        Text("Bandeja de notificaciones",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                        Text(s.notifications.count{it.readAtMillis==null}.toString()+" avisos sin leer")
                        TextButton(onClick=engine::readAll){Text("Marcar todo como leído")}
                        Text("Solo se guardan avisos emitidos por Las Flores. Eliminar un aviso de Android no completa la actividad.",style=MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick=onUpdates){Text("Consultar avisos de actualizaciones")}
                    }
                    items(s.notifications,key={it.id}){log->
                        Card(Modifier.fillMaxWidth()){
                            Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                                Text(log.title,fontWeight=FontWeight.Bold)
                                Text(dateLabel(log.atMillis)+" · "+if(log.readAtMillis==null)"Sin leer" else "Leído",style=MaterialTheme.typography.labelSmall)
                                Text(log.body)
                                Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){
                                    OutlinedButton(onClick={engine.read(log.id);onOpen(log.route())}){Text("Abrir")}
                                    if(log.readAtMillis==null)TextButton(onClick={engine.read(log.id)}){Text("Marcar leído")}
                                }
                            }
                        }
                    }
                    if(s.notifications.isEmpty())item{Text("Todavía no hay notificaciones registradas.")}
                }
            }
            else->JourneyPreferencesEditor(engine,s.preferences)
        }
    }
    if(adding||edit!=null)ReminderDialog(edit,onDismiss={adding=false;edit=null},onSave={engine.saveReminder(it);adding=false;edit=null})
    removing?.let{r->AlertDialog(onDismissRequest={removing=null},title={Text("Eliminar recordatorio")},
        text={Text("Se cancelarán sus avisos futuros. El historial de avisos ya emitidos se conserva.")},
        confirmButton={Button(onClick={engine.deleteReminder(r.id);removing=null}){Text("Eliminar")}},
        dismissButton={TextButton(onClick={removing=null}){Text("Cancelar")}})}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskCard(task:JourneyTask,occurrence:JourneyOccurrenceEntity?,now:Long,engine:JourneyEngine,onOpen:(JourneyNavigation)->Unit){
    Card(Modifier.fillMaxWidth()){
        Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            val status=when{
                task.completed->"Completada"
                occurrence?.snoozedUntilMillis?.let{it>now}==true->"Pospuesta hasta "+occurrence?.snoozedUntilMillis?.let(::timeLabel).orEmpty()
                task.dueAtMillis<now->"Vencida"
                else->"Próxima"
            }
            Text(task.title,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
            Text(task.dateIso+" · "+timeLabel(task.dueAtMillis)+" · "+status,
                color=if(task.completed)semanticForeground("success") else if(task.dueAtMillis<now)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                style=MaterialTheme.typography.labelMedium)
            Text(task.detail,style=MaterialTheme.typography.bodySmall)
            Text(if(task.manual)"Actividad manual" else "Vinculada a registros del módulo",style=MaterialTheme.typography.labelSmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                OutlinedButton(onClick={onOpen(task.route())}){Text("Abrir")}
                if(task.manual)Button(onClick={engine.complete(task.key,!task.completed)}){Text(if(task.completed)"Reabrir" else "Completar")}
            }
            if(!task.completed&&task.notify){
                Text("Posponer aviso",style=MaterialTheme.typography.labelSmall)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(5.dp)){
                    listOf(5,10,15,30).forEach{mins->TextButton(onClick={engine.snooze(task.key,mins)}){Text(mins.toString()+" min")}}
                }
            }
        }
    }
}
private fun recurrenceLabel(r:JourneyReminderEntity)=when(r.recurrence){
    "DAILY"->"Diario";"WEEKLY"->"Semanal · "+listOf(1 to "L",2 to "M",3 to "X",4 to "J",5 to "V",6 to "S",0 to "D").filter{r.daysMask and (1 shl it.first)!=0}.joinToString(" "){it.second}
    "MONTHLY"->"Mensual";else->"Una vez"
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DaysPicker(mask:Int,onChange:(Int)->Unit){
    FlowRow(horizontalArrangement=Arrangement.spacedBy(5.dp)){
        listOf(1 to "L",2 to "M",3 to "X",4 to "J",5 to "V",6 to "S",0 to "D").forEach{(d,label)->
            FilterChip(selected=mask and (1 shl d)!=0,onClick={onChange(mask xor (1 shl d))},label={Text(label)})
        }
    }
}
@Composable
private fun DateButton(date:String,onChange:(String)->Unit){
    val context=LocalContext.current
    val d=runCatching{LocalDate.parse(date)}.getOrDefault(LocalDate.now())
    OutlinedButton(onClick={DatePickerDialog(context,{_,y,m,day->onChange(LocalDate.of(y,m+1,day).toString())},
        d.year,d.monthValue-1,d.dayOfMonth).show()},modifier=Modifier.fillMaxWidth()){Text("Fecha · "+date)}
}
@Composable
private fun ReminderDialog(existing:JourneyReminderEntity?,onDismiss:()->Unit,onSave:(JourneyReminderEntity)->Unit){
    var item by remember(existing?.id){mutableStateOf(existing?:JourneyReminderEntity(UUID.randomUUID().toString(),"",LocalDate.now().toString(),
        "12:00","ONCE",123,0,true,"NORMAL","inicio",true,0))}
    val repeats=listOf("ONCE" to "Una sola vez","DAILY" to "Todos los días","WEEKLY" to "Días específicos","MONTHLY" to "Cada mes")
    val modules=listOf("inicio" to "Inicio","personal" to "Personal","briefings" to "Briefings","uniformes" to "Uniformes",
        "celulares" to "Celulares","funciones" to "Funciones","operativo" to "Control operativo","incidentes" to "Incidentes","reportes" to "Reportes","backups" to "Respaldos","notifications" to "Notificaciones")
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(existing==null)"Nuevo recordatorio" else "Editar recordatorio")},
        text={Column(Modifier.heightIn(max=520.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(7.dp)){
            OutlinedTextField(item.title,{item=item.copy(title=it.take(140))},label={Text("Actividad")},modifier=Modifier.fillMaxWidth())
            DateButton(item.startDateIso){item=item.copy(startDateIso=it)}
            BriefingTimeButton("Hora",item.time,{item=item.copy(time=it)})
            CompactChoice("Repetición",repeats.firstOrNull{it.first==item.recurrence}?.second.orEmpty(),repeats,{item=item.copy(recurrence=it)})
            if(item.recurrence=="WEEKLY")DaysPicker(item.daysMask){item=item.copy(daysMask=it)}
            if(item.recurrence=="MONTHLY")Text("Se repite el mismo día del mes. Los meses sin ese día se omiten.",style=MaterialTheme.typography.bodySmall)
            CompactChoice("Avisar antes",if(item.leadMinutes==0)"A la hora exacta" else item.leadMinutes.toString()+" min",
                listOf(0,5,10,15,30,60).map{it.toString() to if(it==0)"A la hora exacta" else it.toString()+" min"},{item=item.copy(leadMinutes=it.toInt())})
            CompactChoice("Abrir módulo",modules.firstOrNull{it.first==item.module}?.second.orEmpty(),modules,{item=item.copy(module=it)})
            CompactChoice("Prioridad",if(item.priority=="HIGH")"Alta" else "Normal",listOf("NORMAL" to "Normal","HIGH" to "Alta"),{item=item.copy(priority=it)})
            Row(verticalAlignment=Alignment.CenterVertically){
                Checkbox(checked=item.workdaysOnly,onCheckedChange={item=item.copy(workdaysOnly=it)})
                Text("Solo mis días laborales")
            }
            Text("La actividad se completará manualmente; abrir un módulo no la da por realizada.",style=MaterialTheme.typography.bodySmall)
        }},
        confirmButton={Button(enabled=JourneyRules.validReminder(item),onClick={onSave(item.copy(title=item.title.trim()))}){Text("Guardar")}},
        dismissButton={TextButton(onClick=onDismiss){Text("Cancelar")}})
}
@Composable
private fun Toggle(label:String,value:Boolean,onChange:(Boolean)->Unit){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        Text(label,modifier=Modifier.weight(1f))
        Switch(checked=value,onCheckedChange=onChange)
    }
}
@Composable
private fun JourneyPreferencesEditor(engine:JourneyEngine,saved:JourneyPreferencesEntity){
    val context=LocalContext.current
    var p by remember(saved){mutableStateOf(saved)}
    var permission by remember{mutableStateOf(JourneyAlerts.allowed(context))}
    var exact by remember{mutableStateOf(JourneyAlerts.exactAllowed(context))}
    val request=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){
        permission=JourneyAlerts.allowed(context);engine.refresh()
    }
    val settings=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){
        permission=JourneyAlerts.allowed(context);exact=JourneyAlerts.exactAllowed(context);engine.refresh()
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("Configuración de jornada",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        Card(Modifier.fillMaxWidth()){
            Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
                Toggle("Activar notificaciones operativas",p.enabled){p=p.copy(enabled=it)}
                Text(if(permission)"Android permite notificaciones." else "Falta el permiso de notificaciones.")
                if(!permission)OutlinedButton(onClick={
                    if(Build.VERSION.SDK_INT>=33 && androidx.core.content.ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                        request.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else settings.launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))
                }){Text("Permitir notificaciones")}
                Text(if(exact)"Alarmas puntuales autorizadas." else "Alarmas aproximadas: Android puede retrasar los avisos.")
                if(!exact&&Build.VERSION.SDK_INT>=31)OutlinedButton(onClick={
                    settings.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+context.packageName)))
                }){Text("Permitir alarmas puntuales")}
                val power=context.getSystemService(android.os.PowerManager::class.java)
                Text(if(power.isIgnoringBatteryOptimizations(context.packageName))"Batería: sin optimización para Las Flores." else "Batería: optimización del sistema activa; puede retrasar avisos.",style=MaterialTheme.typography.bodySmall)
                Text("Privacidad: detalles operativos ocultos en la pantalla bloqueada.",style=MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick=engine::test){Text("Enviar notificación de prueba")}
                TextButton(onClick={settings.launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))}){Text("Sonido y canales de Android")}
                Text("Las alarmas dependen de permisos, batería y del sistema. Forzar la detención de la aplicación puede impedir avisos.",style=MaterialTheme.typography.bodySmall)
            }
        }
        Card(Modifier.fillMaxWidth()){
            Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
                Text("Mi horario laboral",fontWeight=FontWeight.Bold)
                BriefingTimeButton("Entrada",p.startTime,{p=p.copy(startTime=it)})
                BriefingTimeButton("Salida",p.endTime,{p=p.copy(endTime=it)})
                Text("Días de trabajo")
                DaysPicker(p.workingDaysMask){p=p.copy(workingDaysMask=it)}
                Toggle("Horario silencioso",p.quietEnabled){p=p.copy(quietEnabled=it)}
                if(p.quietEnabled){
                    BriefingTimeButton("Inicio silencioso",p.quietStart,{p=p.copy(quietStart=it)})
                    BriefingTimeButton("Fin silencioso",p.quietEnd,{p=p.copy(quietEnd=it)})
                    Text("Los avisos prioritarios mantienen su canal propio.",style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        Card(Modifier.fillMaxWidth()){
            Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
                Text("Avisos vinculados a módulos",fontWeight=FontWeight.Bold)
                Toggle("Briefings y comunicación presencial",p.briefingAlerts){p=p.copy(briefingAlerts=it)}
                CompactChoice("Preparar antes del briefing",p.briefingLead.toString()+" min",
                    listOf(0,5,10,15,30,60).map{it.toString() to it.toString()+" min"},{p=p.copy(briefingLead=it.toInt())})
                BriefingTimeButton("Revisar por informar",p.informTime,{p=p.copy(informTime=it)})
                Toggle("Control de uniformes",p.uniformAlerts){p=p.copy(uniformAlerts=it)}
                BriefingTimeButton("Hora de revisión",p.uniformsTime,{p=p.copy(uniformsTime=it)})
                Toggle("Control de celulares",p.phoneAlerts){p=p.copy(phoneAlerts=it)}
                BriefingTimeButton("Hora de celulares",p.phoneTime,{p=p.copy(phoneTime=it)})
                Toggle("Verificar funciones pendientes",p.functionAlerts){p=p.copy(functionAlerts=it)}
                BriefingTimeButton("Hora de funciones",p.functionsTime,{p=p.copy(functionsTime=it)})
                Toggle("Incidentes prioritarios",p.incidentAlerts){p=p.copy(incidentAlerts=it)}
                Toggle("Resumen de pendientes",p.summaryAlerts){p=p.copy(summaryAlerts=it)}
                BriefingTimeButton("Hora de resumen",p.summaryTime,{p=p.copy(summaryTime=it)})
            }
        }
        Button(enabled=JourneyRules.validPreferences(p)&&p!=saved,onClick={engine.savePreferences(p)},modifier=Modifier.fillMaxWidth()){Text("Guardar configuración")}
    }
}
