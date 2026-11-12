package pe.lasflores.controlinterno.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.LinearProgressIndicator
import pe.lasflores.controlinterno.domain.STAFF_ROLES
import pe.lasflores.controlinterno.domain.UniformRoleRules
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.lasflores.controlinterno.data.StaffEntity

/** Consulta del historial sin alterar ni eliminar el resultado inicial. */
@Composable
fun UniformHistoryAdmin(people: List<StaffEntity>, vm: UniformViewModel) {
    val history by vm.history.collectAsState()
    val audit by vm.auditHistory.collectAsState()
    var nameFilter by rememberSaveable { mutableStateOf("") }
    var dateFilter by rememberSaveable { mutableStateOf("") }
    var roleFilter by rememberSaveable { mutableStateOf("all") }
    var showAudits by rememberSaveable { mutableStateOf(true) }
    val names = remember(people) { people.associate { it.id to it.fullName } }
    val base = history.filter {
        (nameFilter.isBlank() ||
            (names[it.staffId] ?: it.staffId).contains(nameFilter, ignoreCase = true)) &&
        (dateFilter.isBlank() || it.dateIso.startsWith(dateFilter.trim()))
    }
    val visible=base.filter{roleFilter=="all"||it.roleSnapshot==roleFilter}
    val stats=UniformRoleRules.stats(visible)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Historial de uniformes", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text("El resultado inicial y los eventos de corrección se conservan por separado.",
                style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value = nameFilter, onValueChange = { nameFilter = it.take(100) },
                label = { Text("Buscar trabajador") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = dateFilter, onValueChange = { dateFilter = it.take(10) },
                label = { Text("Fecha o mes (AAAA-MM-DD)") }, modifier = Modifier.fillMaxWidth())
            LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                items(listOf("all" to "Todos")+STAFF_ROLES.map{it to UniformRoleRules.label(it)}){(code,label)->
                    FilterChip(selected=roleFilter==code,onClick={roleFilter=code},label={Text(label)})
                }
            }
            FilterChip(selected = showAudits, onClick = { showAudits = !showAudits },
                label = { Text("Mostrar eventos de auditoría") })
            Text(visible.size.toString() + " inspecciones encontradas")
        }
        if(stats.isNotEmpty())item{
            Card(Modifier.fillMaxWidth()){
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
                    Text("Conformidad inicial por puesto",fontWeight=FontWeight.Bold)
                    Text("Conformes ÷ (conformes + observadas). Borradores excluidos. Las correcciones no modifican el resultado inicial.",style=MaterialTheme.typography.bodySmall)
                    stats.forEach{s->
                        Text(UniformRoleRules.label(s.role)+" · "+(s.percent?.let{it.toString()+"%"}?:"Sin evaluación"),fontWeight=FontWeight.SemiBold)
                        LinearProgressIndicator(progress={(s.percent?:0)/100f},modifier=Modifier.fillMaxWidth())
                        Text(s.conforming.toString()+" conformes · "+s.observed+" observadas · "+s.drafts+" borradores",style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        items(visible, key = { it.id }) { record ->
            val events = audit.filter { it.inspectionId == record.id }
            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(names[record.staffId] ?: record.staffId, fontWeight = FontWeight.Bold)
                    Text(record.dateIso + " · " + record.roleSnapshot)
                    Text("Resultado inicial: " + (record.initialResult ?: "Borrador"))
                    Text(if (record.workflow == "FINALIZED") "Finalizada" else "En revisión",
                        color = MaterialTheme.colorScheme.primary)
                    if (showAudits) {
                        events.forEach { event ->
                            val action = when (event.action) {
                                "CORREGIR" -> "Corrección verificada"
                                "RECTIFICAR" -> "Error de marcación rectificado"
                                "FINALIZAR" -> "Revisión finalizada"
                                else -> event.action
                            }
                            Text("• " + action +
                                (if (event.actor.isNotBlank()) " · " + event.actor else "") +
                                (if (event.note.isNotBlank()) " · " + event.note else ""),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        if (visible.isEmpty()) item { Text("No hay inspecciones para esos filtros.") }
    }
}
