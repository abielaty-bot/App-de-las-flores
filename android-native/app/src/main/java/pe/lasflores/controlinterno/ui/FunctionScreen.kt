package pe.lasflores.controlinterno.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import pe.lasflores.controlinterno.data.FunctionCheckEntity
import pe.lasflores.controlinterno.data.FunctionTaskEntity
import pe.lasflores.controlinterno.data.FunctionEvidenceEntity
import pe.lasflores.controlinterno.data.StaffEntity
import pe.lasflores.controlinterno.domain.FunctionRules
import pe.lasflores.controlinterno.domain.StaffScheduleRules

@Composable
fun FunctionScreen(vm:FunctionViewModel,staffVm:StaffViewModel) {
    val staff by staffVm.staff.collectAsState()
    val vacations by staffVm.vacations.collectAsState()
    val overrides by staffVm.overrides.collectAsState()
    val tasks by vm.tasks.collectAsState()
    val checks by vm.checks.collectAsState()
    val evidence by vm.evidence.collectAsState()
    val events by vm.events.collectAsState()
    val day by vm.date.collectAsState()
    val message by vm.message.collectAsState()
    var tab by rememberSaveable { mutableStateOf("diario") }
    Column(Modifier.fillMaxSize()) {
        LazyRow(contentPadding=PaddingValues(horizontal=12.dp,vertical=3.dp),
            horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            items(listOf("diario" to "Control diario", "mensual" to "Mensual",
                "catalogo" to "Catálogo", "historial" to "Historial")) { (id,title) ->
                FilterChip(selected=tab==id,onClick={tab=id},label={Text(title)})
            }
        }
        Text(message, modifier=Modifier.padding(horizontal=16.dp,vertical=2.dp),
            style=MaterialTheme.typography.labelSmall,
            color=if(message.startsWith("Error")) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary)
        when(tab) {
            "catalogo" -> FunctionCatalogPanel(vm,tasks)
            "mensual" -> FunctionMonthlyPanel(checks,evidence,staff)
            "historial" -> FunctionHistoryPanel(checks,evidence,events,staff)
            else -> FunctionDailyPanel(vm,day,tasks,checks,evidence,staff,vacations,overrides)
        }
    }
}

@Composable
private fun FunctionDailyPanel(
    vm:FunctionViewModel,day:String,tasks:List<FunctionTaskEntity>,
    checks:List<FunctionCheckEntity>,evidence:List<FunctionEvidenceEntity>,
    people:List<StaffEntity>,
    vacations:List<pe.lasflores.controlinterno.data.StaffVacationEntity>,
    overrides:List<pe.lasflores.controlinterno.data.StaffDayOverrideEntity>
) {
    var area by rememberSaveable { mutableStateOf(FunctionRules.AREAS.first()) }
    var assigning by remember { mutableStateOf<FunctionTaskEntity?>(null) }
    var reviewing by remember { mutableStateOf<FunctionCheckEntity?>(null) }
    var correcting by remember { mutableStateOf<FunctionCheckEntity?>(null) }
    var voiding by remember { mutableStateOf<FunctionCheckEntity?>(null) }
    var photoCheck by remember { mutableStateOf<FunctionCheckEntity?>(null) }
    val dateRecords=checks.filter { it.dateIso==day && it.voidedAtMillis==null }
    val total=FunctionRules.summarize(dateRecords,evidence)
    val dayStaff=remember(people,vacations,overrides,day) {
        people.filter {
            StaffScheduleRules.isScheduled(
                StaffScheduleRules.status(it,day,vacations,overrides)
            )
        }
    }
    val displayTasks=tasks.filter { it.area==area && it.isActive }
    val visible=dateRecords.filter { it.areaSnapshot==area }

    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(14.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {
            Text("Control de funciones",style=MaterialTheme.typography.headlineSmall,
                fontWeight=FontWeight.Bold)
            Text("Asigna tareas y verifica lo realizado; no se evalúa lo no asignado.",
                style=MaterialTheme.typography.bodySmall)
            Row(verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={vm.shiftDate(-1)}) { Text("‹") }
                Text(day,fontWeight=FontWeight.Bold)
                OutlinedButton(onClick={vm.shiftDate(1)}) { Text("›") }
                TextButton(onClick={vm.setDate(LocalDate.now().toString())}) {Text("Hoy")}
            }
        }
        item {
            Card {
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text("Resumen · Todas las áreas",fontWeight=FontWeight.Bold)
                    Text("Asignadas: "+total.assigned+"  ·  Evaluadas: "+total.evaluated)
                    Text("Cumplió: "+total.ok+"  ·  No cumplió: "+total.bad)
                    Text("Pendientes: "+total.pending+"  ·  Corrigieron: "+total.corrected)
                    Text("Sin envío de evidencia confirmado: "+total.missingEvidence,
                        color=MaterialTheme.colorScheme.error)
                    Text("Las tareas corregidas conservan su incumplimiento original.",
                        style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Text("Área",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                items(FunctionRules.AREAS) { value ->
                    FilterChip(selected=area==value,onClick={area=value},label={Text(value)})
                }
            }
            Text("Tareas disponibles · "+area,style=MaterialTheme.typography.bodySmall)
            if (displayTasks.isEmpty()) Text("Sin tareas activas: agrégalas en Catálogo.")
        }
        item {
            LazyRow(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                items(displayTasks,key={it.id}) { task ->
                    OutlinedButton(onClick={assigning=task}) { Text("+ "+task.label) }
                }
            }
        }
        item {
            Text("Asignaciones de hoy ("+visible.size+")",fontWeight=FontWeight.Bold)
            Text("Quien se retira temprano debe completar su función antes de salir. " +
                "Guarda la foto y confirma su envío al grupo de funciones.",
                style=MaterialTheme.typography.bodySmall)
        }
        items(visible,key={it.id}) { item ->
            val photos=evidence.filter { it.checkId==item.id }
            Card {
                Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    Text(item.staffNameSnapshot,fontWeight=FontWeight.Bold)
                    Text(item.taskLabelSnapshot+" · "+
                        (if(item.periodicitySnapshot=="MENSUAL")"Mensual" else "Diaria"),
                        style=MaterialTheme.typography.bodyMedium)
                    Text(FunctionRules.resultName(item),color=when(item.result) {
                        FunctionRules.BAD -> MaterialTheme.colorScheme.error
                        FunctionRules.OK -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    })
                    if(item.note.isNotBlank()) Text("Observación: "+item.note,
                        style=MaterialTheme.typography.bodySmall)
                    if(item.correctedAtMillis!=null)
                        Text("Corrección: "+item.correctionNote,style=MaterialTheme.typography.bodySmall)
                    val sent=photos.any { it.groupSentAtMillis!=null }
                    Text("Evidencias: "+photos.size+" foto(s) · "+
                        (if(sent)"Envío al grupo declarado" else "Envío al grupo pendiente"),
                        style=MaterialTheme.typography.bodySmall)
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                        item {
                            OutlinedButton(onClick={reviewing=item}) {Text("Revisar")}
                        }
                        item {
                            OutlinedButton(onClick={photoCheck=item}) {Text("Evidencias")}
                        }
                        if(item.result==FunctionRules.BAD) item {
                            OutlinedButton(onClick={correcting=item}) {Text("Corrigió")}
                        }
                        item {
                            TextButton(onClick={voiding=item}) {Text("Anular")}
                        }
                    }
                }
            }
        }
        if(visible.isEmpty()) item {
            Text("Sin asignaciones visibles en esta área.",
                style=MaterialTheme.typography.bodySmall)
        }
    }
    assigning?.let { task ->
        FunctionAssignDialog(
            task,day,dayStaff,dateRecords,
            onDismiss={assigning=null},
            onSave={ids->vm.assign(task.id,ids); assigning=null}
        )
    }
    reviewing?.let { item ->
        FunctionReviewDialog(item,onDismiss={reviewing=null},
            onSave={result,note,actor->
                vm.review(item.id,result,note,actor);reviewing=null
            })
    }
    correcting?.let { item ->
        FunctionNoteDialog("Corregir función",item.staffNameSnapshot+" · "+
            item.taskLabelSnapshot,reasonLabel="¿Qué se corrigió?",
            onDismiss={correcting=null},onConfirm={actor,reason->
                vm.correct(item.id,actor,reason);correcting=null
            })
    }
    voiding?.let { item ->
        FunctionNoteDialog("Anular asignación",item.staffNameSnapshot+" · "+
            item.taskLabelSnapshot,reasonLabel="Motivo de anulación",
            onDismiss={voiding=null},onConfirm={actor,reason->
                vm.void(item.id,actor,reason);voiding=null
            })
    }
    photoCheck?.let {item ->
        FunctionEvidenceDialog(item,evidence.filter{it.checkId==item.id},vm,
            onDismiss={photoCheck=null})
    }
}

@Composable
private fun FunctionAssignDialog(
    task:FunctionTaskEntity,day:String,people:List<StaffEntity>,
    existing:List<FunctionCheckEntity>,onDismiss:()->Unit,onSave:(Set<String>)->Unit
) {
    val available=people.filter { p -> existing.none { record ->
        record.staffId==p.id && record.taskId==task.id
    } }
    var selected by remember { mutableStateOf(setOf<String>()) }
    AlertDialog(onDismissRequest=onDismiss,
        title={Text("Asignar · "+task.label)},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(task.area+" · "+(if(task.periodicity=="MENSUAL")"Mensual" else "Diaria")+
                    " · "+day,style=MaterialTheme.typography.bodySmall)
                Text("Selecciona responsables programados. Puedes elegir varios.",
                    style=MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.fillMaxWidth().weight(1f,fill=false),
                    verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    items(available,key={it.id}) { person ->
                        FilterChip(selected=person.id in selected,
                            onClick={selected=if(person.id in selected)selected-person.id
                                else selected+person.id},
                            label={Text(person.fullName)})
                    }
                }
                if(available.isEmpty()) Text("No hay personal disponible para esta función.")
            }
        },
        confirmButton={
            Button(enabled=selected.isNotEmpty(),onClick={onSave(selected)}) {
                Text("Asignar "+selected.size)
            }
        },
        dismissButton={TextButton(onClick=onDismiss){Text("Cancelar")}}
    )
}

@Composable
private fun FunctionReviewDialog(
    check:FunctionCheckEntity,onDismiss:()->Unit,
    onSave:(String,String,String)->Unit
) {
    var result by remember { mutableStateOf(check.result) }
    var note by remember { mutableStateOf(check.note) }
    var actor by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onDismiss,
        title={Text("Evaluar función")},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(check.staffNameSnapshot+" · "+check.taskLabelSnapshot)
                listOf(
                    FunctionRules.PENDING to "Pendiente",
                    FunctionRules.OK to "Cumplió",
                    FunctionRules.BAD to "No cumplió"
                ).forEach { (value,title) ->
                    FilterChip(selected=result==value,onClick={result=value},
                        label={Text(title)})
                }
                OutlinedTextField(value=note,onValueChange={note=it.take(2000)},
                    label={Text("Observación / motivo")},minLines=2,
                    modifier=Modifier.fillMaxWidth())
                OutlinedTextField(value=actor,onValueChange={actor=it.take(100)},
                    label={Text("Quién revisó")},modifier=Modifier.fillMaxWidth())
                Text("La corrección posterior NO convierte un incumplimiento en Cumplió.",
                    style=MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton={
            Button(enabled=actor.trim().length>=2 &&
                (result!=FunctionRules.BAD || note.trim().length>=5) &&
                (check.result!=FunctionRules.BAD || result==FunctionRules.BAD ||
                    note.trim().length>=5),
                onClick={onSave(result,note,actor)}) {Text("Guardar revisión")}
        },
        dismissButton={TextButton(onClick=onDismiss){Text("Cancelar")}}
    )
}

@Composable
private fun FunctionNoteDialog(
    title:String,subtitle:String,reasonLabel:String,
    onDismiss:()->Unit,onConfirm:(String,String)->Unit
) {
    var actor by remember{mutableStateOf("")}
    var reason by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,
        title={Text(title)},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(9.dp)) {
                Text(subtitle)
                OutlinedTextField(value=actor,onValueChange={actor=it.take(100)},
                    label={Text("Responsable")},modifier=Modifier.fillMaxWidth())
                OutlinedTextField(value=reason,onValueChange={reason=it.take(2000)},
                    label={Text(reasonLabel)},modifier=Modifier.fillMaxWidth(),
                    minLines=2)
                Text("El historial de cambios conservará esta acción.",
                    style=MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton={
            Button(enabled=actor.trim().length>=2 && reason.trim().length>=5,
                onClick={onConfirm(actor,reason)}) { Text("Confirmar") }
        },
        dismissButton={TextButton(onClick=onDismiss){Text("Cancelar")}}
    )
}
