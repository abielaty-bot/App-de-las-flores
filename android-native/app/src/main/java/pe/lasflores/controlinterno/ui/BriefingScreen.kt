package pe.lasflores.controlinterno.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.LaunchedEffect
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
import pe.lasflores.controlinterno.data.BriefingAttendanceEntity
import pe.lasflores.controlinterno.data.StaffEntity
import pe.lasflores.controlinterno.domain.BriefingRules
import pe.lasflores.controlinterno.domain.StaffScheduleRules

@Composable
fun BriefingScreen(vm: BriefingViewModel, staffVm: StaffViewModel) {
    val staff by staffVm.staff.collectAsState()
    val vacations by staffVm.vacations.collectAsState()
    val overrides by staffVm.overrides.collectAsState()
    val date by vm.day.collectAsState()
    val group by vm.group.collectAsState()
    val loadedRecords by vm.dayRecords.collectAsState()
    val records = loadedRecords.filter { it.dateIso == date }
    val session by vm.session.collectAsState()
    val pending by vm.pending.collectAsState()
    val history by vm.history.collectAsState()
    val message by vm.message.collectAsState()
    var section by rememberSaveable { mutableStateOf("registro") }
    var showAll by rememberSaveable { mutableStateOf(false) }

    val status = remember(staff, vacations, overrides, date) {
        staff.associate { it.id to StaffScheduleRules.status(it, date, vacations, overrides) }
    }
    val currentRecord = remember(records) { records.associateBy { it.staffId } }
    val scheduledIds = scheduledStaffIds(staff, status)
    val scheduled = staff.filter { it.id in scheduledIds }
    val visible = scheduled.filter { person ->
        val record = currentRecord[person.id]
        showAll || record?.groupHour == group || (record == null &&
            (person.briefingGroup == 0 || person.briefingGroup == group))
    }
    val stats = BriefingRules.stats(records)
    val sessionTopics = session?.takeIf { it.dateIso == date && it.groupHour == group }?.topics.orEmpty()
    Column(Modifier.fillMaxSize()) {
        LazyRow(contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(listOf(
                "registro" to "Registro",
                "pendientes" to "Por informar",
                "historial" to "Historial",
                "estadisticas" to "Estadísticas"
            )) { (key, text) ->
                FilterChip(selected = section == key, onClick = { section = key },
                    label = { Text(text) })
            }
        }
        Text(message, modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp),
            color = if (message.startsWith("Error")) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall)
        when(section) {
            "pendientes" -> BriefingPendingPanel(pending, vm)
            "historial" -> BriefingHistoryPanel(history, staff)
            "estadisticas" -> BriefingStatisticsPanel(history, staff)
            else -> BriefingRegisterPanel(
                date, group, visible, status, currentRecord, records, scheduled.size,
                stats.pending, stats.attended, stats.missed, sessionTopics,
                session?.dateIso == date && session?.groupHour == group, vm,
                showAll, onShowAll = { showAll = !showAll }
            )
        }
    }
}

@Composable
private fun BriefingRegisterPanel(
    day: String, group: Int, visible: List<StaffEntity>, status: Map<String,String>,
    allRecords: Map<String,BriefingAttendanceEntity>,
    dailyRecords: List<BriefingAttendanceEntity>, scheduled: Int,
    pending: Int, attended: Int, absent: Int,
    topics: String, sessionLoaded: Boolean, vm: BriefingViewModel,
    showAll: Boolean, onShowAll: () -> Unit
) {
    var editedTopics by rememberSaveable(day, group) { mutableStateOf(topics) }
    var topicConfirm by remember { mutableStateOf(false) }
    var pendingPresence by remember { mutableStateOf<Pair<String,String?>?>(null) }
    var pendingInform by remember { mutableStateOf<BriefingAttendanceEntity?>(null) }
    var pendingUndo by remember { mutableStateOf<BriefingAttendanceEntity?>(null) }
    var actor by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(day,group,topics,sessionLoaded) {
        editedTopics = topics
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item {
            Text("Briefings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Control diario por grupos de 11:00 a. m. y 12:00 p. m.",
                style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.nextDay(-1) }) { Text("‹") }
                Text(day, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { vm.nextDay(1) }) { Text("›") }
                TextButton(onClick = { vm.setDay(java.time.LocalDate.now().toString()) }) { Text("Hoy") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = group == 11, onClick = { vm.setGroup(11) },
                    label = { Text("11:00 a. m.") })
                FilterChip(selected = group == 12, onClick = { vm.setGroup(12) },
                    label = { Text("12:00 p. m.") })
            }
        }
        item {
            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Resumen del día · Ambos grupos", fontWeight = FontWeight.Bold)
                    Text("Programados y apoyos: " + scheduled)
                    Text("Asistieron: " + attended + " · Por informar: " + pending)
                    Text("No asistieron al briefing: " + absent)
                    Text("Sin registrar: " + (scheduled - dailyRecords.size).coerceAtLeast(0))
                    Text("Sin registrar no significa falta.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Grupo " + group + ":00", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = onShowAll) {
                    Text(if (showAll) "Solo este grupo" else "Mostrar otros grupos")
                }
            }
            Text("Registra cada trabajador. «Atendiendo mesa» no es una falta.",
                style = MaterialTheme.typography.bodySmall)
        }
        items(visible, key = { it.id }) { worker ->
            val previous = allRecords[worker.id]
            Card {
                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(worker.fullName, fontWeight = FontWeight.Bold)
                    Text(StaffScheduleRules.label(status[worker.id].orEmpty()) +
                        " · Grupo habitual " +
                        (if (worker.briefingGroup == 0) "sin asignar"
                         else worker.briefingGroup.toString() + ":00"),
                        style = MaterialTheme.typography.bodySmall)
                    Text(previous?.presence?.let(BriefingRules::label) ?: "Sin registrar",
                        color = if (previous?.presence == BriefingRules.FALTA)
                            MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf(
                            BriefingRules.ASISTIO to "Asistió",
                            BriefingRules.MESA to "Atendiendo mesa",
                            BriefingRules.FALTA to "No asistió",
                            "LIMPIAR" to "Quitar registro"
                        )) { (code, title) ->
                            FilterChip(
                                selected = previous?.presence == code,
                                onClick = {
                                    val requested = if (code == "LIMPIAR") null else code
                                    if (requested != previous?.presence)
                                        pendingPresence = worker.id to requested
                                },
                                label = { Text(title) }
                            )
                        }
                    }
                    if (previous?.presence == BriefingRules.MESA) {
                        Text(if (previous.informedAtMillis != null)
                            "Comunicación recibida · " + (previous.informedBy ?: "")
                            else "Comunicación posterior pendiente",
                            style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = {
                            actor = ""
                            if (previous.informedAtMillis == null) pendingInform = previous
                            else pendingUndo = previous
                        }) {
                            Text(if (previous.informedAtMillis == null)
                                "Marcar como informado" else "Revertir comunicación")
                        }
                    }
                }
            }
        }
        if (visible.isEmpty()) item {
            Text("No hay trabajadores programados para este grupo. Puedes cambiar la asignación desde Personal.",
                style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Indicaciones del briefing", fontWeight = FontWeight.Bold)
                    Text("Temas guardados por fecha y grupo; máximo 3000 caracteres.",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = editedTopics, onValueChange = {
                        editedTopics = it.take(3000)
                    }, label = { Text("Temas comunicados") }, minLines = 4,
                        modifier = Modifier.fillMaxWidth())
                    Button(
                        enabled = editedTopics.trim() != topics,
                        onClick = {
                            if (editedTopics.isBlank()) topicConfirm = true
                            else vm.saveTopics(editedTopics)
                        }) { Text("Guardar temas") }
                }
            }
        }
    }
    pendingPresence?.let { (staffId, presence) ->
        val name = visible.firstOrNull { it.id == staffId }?.fullName.orEmpty()
        AlertDialog(
            onDismissRequest = { pendingPresence = null },
            title = { Text("Confirmar asistencia") },
            text = {
                val previous = allRecords[staffId]
                val moved = previous != null && previous.groupHour != group && presence != null
                Text(name + ": " + (presence?.let(BriefingRules::label) ?: "Quitar registro") +
                    (if (moved) ". Cambiarás su registro del grupo " +
                        previous!!.groupHour + ":00 al grupo " + group + ":00."
                    else "") + " El cambio quedará en el historial.")
            },
            confirmButton = {
                Button(onClick = { vm.mark(staffId, presence); pendingPresence = null }) {
                    Text("Confirmar")
                }
            },
            dismissButton = { TextButton(onClick = { pendingPresence = null }) { Text("Cancelar") } }
        )
    }
    if (topicConfirm) AlertDialog(
        onDismissRequest = { topicConfirm = false },
        title = { Text("¿Borrar temas de este grupo?") },
        text = { Text("Se guardará un evento de auditoría.") },
        confirmButton = {
            Button(onClick = { vm.saveTopics(""); topicConfirm = false }) { Text("Borrar temas") }
        },
        dismissButton = { TextButton(onClick = { topicConfirm = false }) { Text("Cancelar") } }
    )
    val informedRecord = pendingInform ?: pendingUndo
    if (informedRecord != null) AlertDialog(
        onDismissRequest = { pendingInform = null; pendingUndo = null },
        title = { Text(if (pendingInform != null) "Comunicación posterior"
            else "Revertir comunicación") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(informedRecord.staffNameSnapshot + " · " + informedRecord.dateIso)
                OutlinedTextField(value = actor, onValueChange = { actor = it.take(100) },
                    label = { Text("Nombre del responsable") },
                    modifier = Modifier.fillMaxWidth())
                Text("Este cambio quedará en el historial.",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(enabled = actor.trim().length >= 2, onClick = {
                vm.inform(informedRecord.id, actor, pendingInform != null)
                pendingInform = null
                pendingUndo = null
            }) { Text("Confirmar") }
        },
        dismissButton = {
            TextButton(onClick = { pendingInform = null; pendingUndo = null }) {
                Text("Cancelar")
            }
        }
    )
}


/** Mantiene un único origen para el cómputo de programación diaria. */
private fun scheduledStaffIds(
    people: List<StaffEntity>,
    statuses: Map<String,String>
): Set<String> = people.filter {
    StaffScheduleRules.isScheduled(statuses[it.id].orEmpty())
}.map { it.id }.toSet()
