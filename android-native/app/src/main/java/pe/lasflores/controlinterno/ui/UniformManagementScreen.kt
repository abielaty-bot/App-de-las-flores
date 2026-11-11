package pe.lasflores.controlinterno.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.lasflores.controlinterno.data.MaterialDeliveryEntity
import pe.lasflores.controlinterno.data.StaffEntity
import pe.lasflores.controlinterno.data.UniformRequirementEntity
import pe.lasflores.controlinterno.domain.STAFF_ROLES

@Composable
fun UniformScreen(people: List<StaffEntity>, vm: UniformViewModel, staffVm: StaffViewModel) {
    var section by rememberSaveable { mutableStateOf("inspecciones") }
    Column(Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(listOf(
                "inspecciones" to "Inspecciones",
                "requisitos" to "Configurar requisitos",
                "historial" to "Historial",
                "entregas" to "Entregas de materiales"
            )) { (key, title) ->
                FilterChip(selected = section == key,
                    onClick = { section = key }, label = { Text(title) })
            }
        }
        when(section) {
            "requisitos" -> RequirementsAdmin(vm)
            "historial" -> UniformHistoryAdmin(people, vm)
            "entregas" -> MaterialsAdmin(people, vm)
            else -> UniformInspectionScreen(people, vm, staffVm)
        }
    }
}

@Composable
private fun RequirementsAdmin(vm: UniformViewModel) {
    val catalog by vm.catalog.collectAsState()
    var editing by remember { mutableStateOf<UniformRequirementEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Configurar requisitos", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text("Los cambios se aplican a inspecciones nuevas. Los requisitos y resultados anteriores se conservan.",
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { adding = true }) { Text("+ Nuevo requisito") }
        }
        items(catalog, key = { it.id }) { req ->
            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(req.label, fontWeight = FontWeight.Bold)
                    Text(req.category + (if (req.minQty > 0) " · Mínimo: " + req.minQty else ""),
                        style = MaterialTheme.typography.bodySmall)
                    Text(STAFF_ROLES.filter { req.rolesCsv.contains("|" + it + "|") }.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall)
                    Text(if (req.isActive) "Activo" else "Desactivado",
                        color = if (req.isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { editing = req }) { Text("Editar") }
                        OutlinedButton(onClick = { vm.setRequirementActive(req.id, !req.isActive) }) {
                            Text(if (req.isActive) "Desactivar" else "Activar")
                        }
                    }
                }
            }
        }
    }
    if (adding || editing != null) {
        RequirementDialog(editing,
            onDismiss = { adding = false; editing = null },
            onSave = { id, label, group, qty, roles ->
                vm.saveRequirement(id, label, group, qty, roles)
                adding = false
                editing = null
            })
    }
}

@Composable
private fun RequirementDialog(
    existing: UniformRequirementEntity?,
    onDismiss: () -> Unit,
    onSave: (String?, String, String, Int, Set<String>) -> Unit
) {
    var label by remember(existing?.id) { mutableStateOf(existing?.label.orEmpty()) }
    var category by remember(existing?.id) { mutableStateOf(existing?.category ?: "Implementos") }
    var qty by remember(existing?.id) { mutableStateOf((existing?.minQty ?: 0).toString()) }
    var roles by remember(existing?.id) {
        mutableStateOf(STAFF_ROLES.filter {
            existing?.rolesCsv?.contains("|" + it + "|") ?: (it == "Mozo")
        }.toSet())
    }
    val groups = listOf("Uniforme", "Presentación", "Implementos", "Otro")
    val validQty = qty.toIntOrNull()
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Nuevo requisito" else "Editar requisito") },
        text = {
            Column(Modifier.heightIn(max = 490.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = label, onValueChange = { label = it.take(100) },
                    label = { Text("Nombre del requisito") }, modifier = Modifier.fillMaxWidth())
                Text("Grupo", fontWeight = FontWeight.Bold)
                groups.forEach { group ->
                    FilterChip(selected = category == group, onClick = { category = group },
                        label = { Text(group) })
                }
                OutlinedTextField(value = qty, onValueChange = { qty = it.filter(Char::isDigit).take(3) },
                    label = { Text("Cantidad mínima (0 si no aplica)") }, modifier = Modifier.fillMaxWidth())
                Text("Puestos a los que corresponde", fontWeight = FontWeight.Bold)
                STAFF_ROLES.forEach { role ->
                    FilterChip(selected = role in roles, onClick = {
                        roles = if (role in roles) roles - role else roles + role
                    }, label = { Text(role) })
                }
                Text("Los controles ya iniciados conservan su lista original de requisitos.",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(enabled = label.trim().length in 3..100 &&
                validQty != null && validQty in 0..100 && roles.isNotEmpty(),
                onClick = { onSave(existing?.id, label, category, validQty ?: 0, roles) }) {
                Text("Guardar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun MaterialsAdmin(people: List<StaffEntity>, vm: UniformViewModel) {
    val deliveries by vm.deliveries.collectAsState()
    val dateIso by vm.dateIso.collectAsState()
    val active = people.filter { it.isActive }
    var adding by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf<MaterialDeliveryEntity?>(null) }
    val current = deliveries.count { it.status == "ENTREGADA" }
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Entrega de materiales", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text("Vigentes: " + current + " · Devueltas: " +
                deliveries.count { it.status == "DEVUELTA" } + " · Anuladas: " +
                deliveries.count { it.status == "ANULADA" },
                style = MaterialTheme.typography.bodyMedium)
            Text("El historial conserva las entregas aunque se devuelvan o anulen.",
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Button(enabled = active.isNotEmpty(), onClick = { adding = true }) {
                Text("+ Registrar entrega")
            }
        }
        items(deliveries, key = { it.id }) { item ->
            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.staffNameSnapshot, fontWeight = FontWeight.Bold)
                    Text(item.material + " · " + item.quantity + " unidad(es) · " + item.dateIso)
                    Text("Motivo: " + item.reason + " · Entregado por: " + item.deliveredBy,
                        style = MaterialTheme.typography.bodySmall)
                    Text(when(item.status) {
                        "ENTREGADA" -> "Entrega vigente"
                        "DEVUELTA" -> "Devuelta"
                        else -> "Anulada"
                    }, color = if (item.status == "ENTREGADA") MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (item.closingNote.isNotBlank()) Text("Observación: " + item.closingNote,
                        style = MaterialTheme.typography.bodySmall)
                    if (item.status == "ENTREGADA")
                        OutlinedButton(onClick = { closing = item }) {
                            Text("Devolución / anulación")
                        }
                }
            }
        }
        if (deliveries.isEmpty()) item { Text("Todavía no hay entregas de materiales.") }
    }
    if (adding) MaterialForm(active, dateIso, onDismiss = { adding = false },
        onSave = { id, material, quantity, reason, actor ->
            vm.deliver(id, material, quantity, reason, actor)
            adding = false
        })
    closing?.let { item ->
        MaterialCloseDialog(item, onDismiss = { closing = null },
            onSave = { type, note ->
                vm.closeDelivery(item.id, type, note)
                closing = null
            })
    }
}

@Composable
private fun MaterialForm(
    workers: List<StaffEntity>, date: String, onDismiss: () -> Unit,
    onSave: (String, String, Int, String, String) -> Unit
) {
    var workerId by remember { mutableStateOf(workers.firstOrNull()?.id.orEmpty()) }
    var material by remember { mutableStateOf("Trapo") }
    var otherMaterial by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var reason by remember { mutableStateOf("Dotación inicial") }
    var otherReason by remember { mutableStateOf("") }
    var actor by remember { mutableStateOf("") }
    val materials = listOf("Trapo","Mandil","Camisa","Toca","Otro")
    val reasons = listOf("Dotación inicial","Reposición por desgaste",
        "Pérdida","Daño","Cambio de talla","Otro")
    val finalMaterial = if (material == "Otro") otherMaterial.trim() else material
    val finalReason = if (reason == "Otro") otherReason.trim() else reason
    val quantity = qty.toIntOrNull()
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Registrar entrega · " + date) },
        text = {
            Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Trabajador", fontWeight = FontWeight.Bold)
                workers.forEach { person ->
                    FilterChip(selected = workerId == person.id, onClick = { workerId = person.id },
                        label = { Text(person.fullName) })
                }
                Text("Material", fontWeight = FontWeight.Bold)
                materials.forEach { option ->
                    FilterChip(selected = material == option, onClick = { material = option },
                        label = { Text(option) })
                }
                if (material == "Otro") OutlinedTextField(value = otherMaterial,
                    onValueChange = { otherMaterial = it.take(80) },
                    label = { Text("Nombre del material") })
                OutlinedTextField(value = qty,
                    onValueChange = { qty = it.filter(Char::isDigit).take(3) },
                    label = { Text("Cantidad entregada") })
                Text("Motivo", fontWeight = FontWeight.Bold)
                reasons.forEach { option ->
                    FilterChip(selected = reason == option, onClick = { reason = option },
                        label = { Text(option) })
                }
                if (reason == "Otro") OutlinedTextField(value = otherReason,
                    onValueChange = { otherReason = it.take(120) },
                    label = { Text("Motivo específico") })
                OutlinedTextField(value = actor, onValueChange = { actor = it.take(120) },
                    label = { Text("Responsable de entrega") })
            }
        },
        confirmButton = {
            Button(enabled = workerId.isNotBlank() && finalMaterial.length in 2..80 &&
                quantity != null && quantity in 1..100 && finalReason.length in 3..120 &&
                actor.trim().length in 2..120,
                onClick = { onSave(workerId, finalMaterial, quantity ?: 1, finalReason, actor) }) {
                Text("Guardar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun MaterialCloseDialog(
    item: MaterialDeliveryEntity,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var action by remember { mutableStateOf("DEVUELTA") }
    var note by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(item.material + " · " + item.staffNameSnapshot) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("El registro se conserva en el historial.")
                FilterChip(selected = action == "DEVUELTA",
                    onClick = { action = "DEVUELTA" }, label = { Text("Devuelta") })
                FilterChip(selected = action == "ANULADA",
                    onClick = { action = "ANULADA" }, label = { Text("Anulada") })
                OutlinedTextField(value = note, onValueChange = { note = it.take(300) },
                    label = { Text("Motivo u observación") }, minLines = 2)
            }
        },
        confirmButton = {
            Button(enabled = note.trim().length >= 5,
                onClick = { onSave(action, note) }) { Text("Confirmar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
