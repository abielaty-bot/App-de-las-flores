package pe.lasflores.controlinterno.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import pe.lasflores.controlinterno.data.StaffEntity
import pe.lasflores.controlinterno.data.StaffVacationEntity
import pe.lasflores.controlinterno.domain.STAFF_ROLES
import pe.lasflores.controlinterno.domain.StaffName
import pe.lasflores.controlinterno.domain.StaffScheduleRules

/** Ficha única de personal: programación diaria, descansos, apoyos y vacaciones. */
@Composable
fun StaffManagementScreen(people: List<StaffEntity>, vm: StaffViewModel) {
    val vacations by vm.vacations.collectAsState()
    val overrides by vm.overrides.collectAsState()
    var dateIso by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<StaffEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var vacationPerson by remember { mutableStateOf<StaffEntity?>(null) }
    var dayPerson by remember { mutableStateOf<StaffEntity?>(null) }
    val visible = people.filter { it.fullName.contains(query, ignoreCase = true) }
    val working = people.count {
        StaffScheduleRules.isScheduled(StaffScheduleRules.status(it, dateIso, vacations, overrides))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)
    ) {
        item {
            DayControls(
                date = dateIso,
                onPrevious = { dateIso = LocalDate.parse(dateIso).minusDays(1).toString() },
                onNext = { dateIso = LocalDate.parse(dateIso).plusDays(1).toString() },
                onToday = { dateIso = LocalDate.now().toString() }
            )
            Text("Activos: " + people.count { it.isActive } +
                " · Programados y apoyos: " + working,
                style = MaterialTheme.typography.bodyMedium)
        }
        item {
            OutlinedTextField(value = query, onValueChange = { query = it },
                label = { Text("Buscar trabajador") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                Text("+ Agregar trabajador")
            }
        }
        items(visible, key = { it.id }) { person ->
            val status = StaffScheduleRules.status(person, dateIso, vacations, overrides)
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(person.fullName, fontWeight = FontWeight.Bold)
                    Text(person.role +
                        (if (person.category > 0) " · Categoría " + person.category else "") +
                        (if (person.briefingGroup > 0) " · Briefing " + person.briefingGroup + ":00" else ""),
                        style = MaterialTheme.typography.bodySmall)
                    Text("Situación: " + StaffScheduleRules.label(status),
                        color = if (StaffScheduleRules.isScheduled(status))
                            MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        OutlinedButton(onClick = { editing = person }) { Text("Editar") }
                        OutlinedButton(onClick = { dayPerson = person }) { Text("Situación") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { vacationPerson = person }) { Text("Vacaciones") }
                        TextButton(onClick = { vm.changeActive(person.id, !person.isActive) }) {
                            Text(if (person.isActive) "Desactivar" else "Reactivar")
                        }
                    }
                }
            }
        }
        if (visible.isEmpty()) item { Text("No se encontraron trabajadores.") }
    }

    if (adding || editing != null) {
        StaffProfileDialog(editing, people,
            onDismiss = { adding = false; editing = null },
            onSave = { name, role, category, group, mode, rest ->
                val selected = editing
                if (selected == null) vm.addDetailed(name, role, category, group, mode, rest)
                else vm.updateDetails(selected.id, name, role, category, group, mode, rest)
                adding = false
                editing = null
            })
    }
    vacationPerson?.let { person ->
        StaffVacationDialog(person, vacations.filter { it.staffId == person.id },
            onDismiss = { vacationPerson = null },
            onAdd = { from, to, note -> vm.addVacation(person.id, from, to, note) },
            onRemove = { id -> vm.removeVacation(id) })
    }
    dayPerson?.let { person ->
        val selected = overrides.firstOrNull { it.staffId == person.id && it.dateIso == dateIso }?.status
        DayStatusDialog(person.fullName, dateIso, selected,
            onDismiss = { dayPerson = null },
            onSave = { status -> vm.overrideDay(person.id, dateIso, status); dayPerson = null })
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun StaffProfileDialog(
    person: StaffEntity?, existing: List<StaffEntity>,
    onDismiss: () -> Unit,
    onSave: (String, String, Int, Int, String, Int) -> Unit
) {
    var name by remember(person?.id) { mutableStateOf(person?.fullName.orEmpty()) }
    var role by remember(person?.id) { mutableStateOf(person?.role ?: "Mozo") }
    var category by remember(person?.id) { mutableStateOf(person?.category ?: 0) }
    var group by remember(person?.id) { mutableStateOf(person?.briefingGroup ?: 0) }
    var mode by remember(person?.id) { mutableStateOf(person?.scheduleMode ?: "regular") }
    var rest by remember(person?.id) { mutableStateOf(person?.restDaysMask ?: 0) }
    val duplicate = existing.any {
        it.id != person?.id && StaffName.normalize(it.fullName).equals(StaffName.normalize(name), true)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (person == null) "Agregar trabajador" else "Editar trabajador") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(120) },
                    label = { Text("Nombre completo") }, modifier = Modifier.fillMaxWidth())
                if (duplicate) Text("Este nombre ya está registrado.",
                    color = MaterialTheme.colorScheme.error)
                Text("Puesto", fontWeight = FontWeight.SemiBold)
                STAFF_ROLES.forEach { option ->
                    FilterChip(selected = role == option, onClick = { role = option },
                        label = { Text(option) })
                }
                Text("Categoría", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0..4).forEach { value ->
                        FilterChip(selected = category == value, onClick = { category = value },
                            label = { Text(if (value == 0) "—" else value.toString()) })
                    }
                }
                Text("Grupo habitual de briefing")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 11, 12).forEach { value ->
                        FilterChip(selected = group == value, onClick = { group = value },
                            label = { Text(if (value == 0) "Sin grupo" else value.toString() + ":00") })
                    }
                }
                Text("Programación habitual")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = mode == "regular", onClick = { mode = "regular" },
                        label = { Text("Regular") })
                    FilterChip(selected = mode == "weekend", onClick = { mode = "weekend" },
                        label = { Text("Apoyo fin de semana") })
                }
                if (mode == "regular") {
                    Text("Días de descanso")
                    listOf(1 to "Lunes", 2 to "Martes", 3 to "Miércoles", 4 to "Jueves",
                        5 to "Viernes", 6 to "Sábado", 0 to "Domingo").forEach { (day, label) ->
                        FilterChip(selected = rest and (1 shl day) != 0,
                            onClick = { rest = rest xor (1 shl day) },
                            label = { Text(label) })
                    }
                }
                Text("Los cambios habituales se reflejan en la programación. Las inspecciones ya realizadas no se borran.",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(enabled = StaffName.isValid(name) && !duplicate,
                onClick = { onSave(name, role, category, group, mode, rest) }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun StaffVacationDialog(
    person: StaffEntity,
    vacations: List<StaffVacationEntity>,
    onDismiss: () -> Unit,
    onAdd: (String, String, String) -> Unit,
    onRemove: (String) -> Unit
) {
    var from by remember(person.id) { mutableStateOf(LocalDate.now().toString()) }
    var to by remember(person.id) { mutableStateOf(LocalDate.now().toString()) }
    var note by remember(person.id) { mutableStateOf("") }
    var removalId by remember { mutableStateOf<String?>(null) }
    val valid = runCatching {
        val a = StaffScheduleRules.parseDate(from)
        val b = StaffScheduleRules.parseDate(to)
        !b.isBefore(a) && java.time.temporal.ChronoUnit.DAYS.between(a, b) <= 365
    }.getOrDefault(false)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Vacaciones · " + person.fullName) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Períodos registrados", fontWeight = FontWeight.Bold)
                if (vacations.isEmpty()) Text("Sin vacaciones registradas")
                vacations.forEach { item ->
                    Text(item.startDateIso + " — " + item.endDateIso)
                    Text(item.note, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { removalId = item.id }) { Text("Quitar período") }
                }
                OutlinedTextField(value = from, onValueChange = { from = it.take(10) },
                    label = { Text("Inicio: AAAA-MM-DD") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = to, onValueChange = { to = it.take(10) },
                    label = { Text("Fin: AAAA-MM-DD") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = note, onValueChange = { note = it.take(300) },
                    label = { Text("Observación opcional") }, modifier = Modifier.fillMaxWidth())
                Button(enabled = valid, onClick = { onAdd(from, to, note) }) {
                    Text("Guardar vacaciones")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
    if (removalId != null) AlertDialog(
        onDismissRequest = { removalId = null },
        title = { Text("¿Quitar este período?") },
        text = { Text("La eliminación modificará la programación de esas fechas.") },
        confirmButton = {
            Button(onClick = { removalId?.let(onRemove); removalId = null }) { Text("Quitar") }
        },
        dismissButton = { TextButton(onClick = { removalId = null }) { Text("Cancelar") } }
    )
}

@Composable
private fun DayStatusDialog(
    name: String, dateIso: String, current: String?,
    onDismiss: () -> Unit, onSave: (String?) -> Unit
) {
    var selected by remember(name, dateIso) { mutableStateOf(current ?: "auto") }
    val options = listOf("auto" to "Según horario", "working" to "Trabaja",
        "rest" to "Descanso", "vacation" to "Vacaciones",
        "absent" to "Falta", "leave" to "Permiso", "support" to "Apoyo")
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Situación · " + name) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                Text(dateIso)
                options.forEach { (value, label) ->
                    FilterChip(selected = selected == value, onClick = { selected = value },
                        label = { Text(label) })
                }
                Text("Los días sin inspección no se califican automáticamente como faltas.",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selected.takeUnless { it == "auto" }) }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}
