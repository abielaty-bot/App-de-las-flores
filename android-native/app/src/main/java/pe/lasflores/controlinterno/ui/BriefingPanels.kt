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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import pe.lasflores.controlinterno.data.BriefingAttendanceEntity
import pe.lasflores.controlinterno.data.StaffEntity
import pe.lasflores.controlinterno.domain.BriefingRules

@Composable
fun BriefingPendingPanel(records: List<BriefingAttendanceEntity>, vm: BriefingViewModel) {
    var chosen by remember { mutableStateOf<BriefingAttendanceEntity?>(null) }
    var actor by rememberSaveable { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Pendientes de informar", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text(records.size.toString() + " comunicaciones pendientes, incluidas las fechas anteriores.")
            Text("No implica una falta. El trabajador debe recibir las indicaciones personalmente.",
                style = MaterialTheme.typography.bodySmall)
        }
        items(records, key = { it.id }) { item ->
            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.staffNameSnapshot, fontWeight = FontWeight.Bold)
                    Text(item.dateIso + " · Grupo " + item.groupHour + ":00")
                    Text("Registrado atendiendo mesas",
                        style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { actor = ""; chosen = item }) { Text("Ya fue informado") }
                }
            }
        }
        if (records.isEmpty()) item { Text("No hay comunicaciones pendientes.") }
    }
    chosen?.let { item ->
        AlertDialog(
            onDismissRequest = { chosen = null },
            title = { Text("Confirmar comunicación") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.staffNameSnapshot + " · " + item.dateIso)
                    OutlinedTextField(value = actor, onValueChange = { actor = it.take(100) },
                        label = { Text("Nombre de quien informó") },
                        modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(enabled = actor.trim().length >= 2, onClick = {
                    vm.inform(item.id, actor)
                    chosen = null
                }) { Text("Registrar comunicación") }
            },
            dismissButton = { TextButton(onClick = { chosen = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
fun BriefingHistoryPanel(
    records: List<BriefingAttendanceEntity>, staff: List<StaffEntity>
) {
    var query by rememberSaveable { mutableStateOf("") }
    var start by rememberSaveable { mutableStateOf("") }
    var end by rememberSaveable { mutableStateOf("") }
    val names = remember(staff) { staff.associate { it.id to it.fullName } }
    val datesValid = (start.isBlank() || runCatching { LocalDate.parse(start) }.isSuccess) &&
        (end.isBlank() || runCatching { LocalDate.parse(end) }.isSuccess)
    val filtered = if (!datesValid) emptyList() else records.filter {
        (query.isBlank() || (names[it.staffId] ?: it.staffNameSnapshot)
            .contains(query, ignoreCase = true)) &&
        (start.isBlank() || it.dateIso >= start) &&
        (end.isBlank() || it.dateIso <= end)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item {
            Text("Historial de briefings", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            OutlinedTextField(value = query, onValueChange = { query = it.take(100) },
                label = { Text("Buscar trabajador") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = start, onValueChange = { start = it.take(10) },
                label = { Text("Desde · AAAA-MM-DD") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = end, onValueChange = { end = it.take(10) },
                label = { Text("Hasta · AAAA-MM-DD") }, modifier = Modifier.fillMaxWidth())
            if (!datesValid) Text("Introduce fechas válidas (AAAA-MM-DD).",
                color = MaterialTheme.colorScheme.error)
            Text(filtered.size.toString() + " registros",
                style = MaterialTheme.typography.bodySmall)
        }
        items(filtered, key = { it.id }) { item ->
            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(names[item.staffId] ?: item.staffNameSnapshot,
                        fontWeight = FontWeight.Bold)
                    Text(item.dateIso + " · " + item.groupHour + ":00")
                    Text(BriefingRules.label(item.presence))
                    Text(when {
                        item.presence == BriefingRules.ASISTIO -> "Recibió las indicaciones presencialmente"
                        item.informedAtMillis != null -> "Informado posteriormente · " +
                            item.informedBy.orEmpty()
                        item.presence == BriefingRules.MESA -> "Comunicación pendiente"
                        else -> "No participó en el briefing"
                    }, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (filtered.isEmpty() && datesValid) item {
            Text("No hay registros en este período.")
        }
    }
}

@Composable
fun BriefingStatisticsPanel(
    records: List<BriefingAttendanceEntity>, staff: List<StaffEntity>
) {
    var month by rememberSaveable {
        mutableStateOf(LocalDate.now().toString().take(7))
    }
    var personFilter by rememberSaveable { mutableStateOf("todos") }
    val names = remember(staff) { staff.associate { it.id to it.fullName } }
    val monthValid = Regex("""\d{4}-(0[1-9]|1[0-2])""").matches(month)
    val monthData = if (monthValid) records.filter { it.dateIso.startsWith(month) }
        else emptyList()
    val data = monthData.filter { personFilter == "todos" || it.staffId == personFilter }
    val stats = BriefingRules.stats(data)
    val participants = records.map { it.staffId }.toSet()
    val known = staff.filter { it.isActive || it.id in participants }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Estadísticas de briefings", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text("Porcentajes sobre los registros realizados; sin registrar no se considera falta.",
                style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value = month, onValueChange = { month = it.take(7) },
                label = { Text("Mes · AAAA-MM") }, modifier = Modifier.fillMaxWidth())
            if (!monthValid) Text("Mes inválido", color = MaterialTheme.colorScheme.error)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    FilterChip(selected = personFilter == "todos",
                        onClick = { personFilter = "todos" },
                        label = { Text("Todos") })
                }
                items(known, key = { it.id }) { worker ->
                    FilterChip(selected = personFilter == worker.id,
                        onClick = { personFilter = worker.id },
                        label = { Text(worker.fullName) })
                }
            }
            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Resultados de " + month, fontWeight = FontWeight.Bold)
                    Text("Registros realizados: " + stats.registered)
                    Text("Asistencia presencial: " + stats.attended +
                        " (" + stats.attendancePercentage + "%)")
                    Text("Comunicación recibida: " + stats.communicated +
                        " (" + stats.communicationPercentage + "%)")
                    Text("Pendientes de informar: " + stats.pending)
                    Text("No asistieron al briefing: " + stats.missed)
                }
            }
            Text("Resultados por trabajador", fontWeight = FontWeight.Bold)
        }
        val people = if (personFilter == "todos") known else known.filter {
            it.id == personFilter
        }
        items(people, key = { it.id }) { worker ->
            val workerStats = BriefingRules.stats(monthData.filter { it.staffId == worker.id })
            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(names[worker.id] ?: worker.fullName, fontWeight = FontWeight.Bold)
                    Text("Registros: " + workerStats.registered +
                        " · Asistió: " + workerStats.attended)
                    Text("Presencial: " + workerStats.attendancePercentage +
                        "% · Comunicación: " + workerStats.communicationPercentage + "%")
                    Text("Por informar: " + workerStats.pending)
                }
            }
        }
        if (people.isEmpty()) item { Text("Todavía no hay trabajadores registrados.") }
    }
}
