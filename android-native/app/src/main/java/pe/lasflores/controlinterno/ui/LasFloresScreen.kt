package pe.lasflores.controlinterno.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.lasflores.controlinterno.data.StaffEntity
import pe.lasflores.controlinterno.data.OfflineBackupManager
import pe.lasflores.controlinterno.domain.STAFF_ROLES
import pe.lasflores.controlinterno.domain.StaffName
import pe.lasflores.controlinterno.update.UpdateScreen
import pe.lasflores.controlinterno.update.UpdateViewModel

private val green = Color(0xFF17584A)
private val lightBackground = Color(0xFFF7F9F7)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LasFloresScreen(vm: StaffViewModel, uniformVm: UniformViewModel, briefingVm: BriefingViewModel, updateVm: UpdateViewModel, backupManager: OfflineBackupManager) {
    var screen by rememberSaveable { mutableStateOf("inicio") }
    val people by vm.staff.collectAsState()
    val saveStatus by vm.saveState.collectAsState()
    val uniformSaveStatus by uniformVm.saveStatus.collectAsState()
    val briefingStatus by briefingVm.message.collectAsState()

    MaterialTheme(colorScheme = androidx.compose.material3.lightColorScheme(
        primary = green, background = lightBackground, surface = Color.White
    )) {
        Scaffold(
            topBar = {
                TopAppBar(title = {
                    Column {
                        Text("LAS FLORES", fontWeight = FontWeight.Bold)
                        Text("Control Interno · Android", style = MaterialTheme.typography.labelSmall)
                    }
                })
            },
            bottomBar = {
                Surface(shadowElevation = 6.dp) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TextButton(onClick = { screen = "inicio" }) { Text("Inicio") }
                        TextButton(onClick = { screen = "personal" }) { Text("Personal") }
                        TextButton(onClick = { screen = "uniformes" }) { Text("Uniformes") }
                        TextButton(onClick = { screen = "briefings" }) { Text("Briefings") }
                        TextButton(onClick = { screen = "backups" }) { Text("Respaldos") }
                        TextButton(onClick = { screen = "updates" }) { Text("Actualizar") }
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Text(
                    when(screen) { "uniformes" -> uniformSaveStatus; "briefings" -> briefingStatus; else -> saveStatus },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if ((when(screen) { "uniformes" -> uniformSaveStatus; "briefings" -> briefingStatus; else -> saveStatus }).startsWith("Error")) MaterialTheme.colorScheme.error else green
                )
                when (screen) {
                    "personal" -> StaffManagementScreen(people, vm)
                    "uniformes" -> UniformScreen(people, uniformVm, vm)
                    "briefings" -> BriefingScreen(briefingVm, vm)
                    "updates" -> UpdateScreen(updateVm)
                    "backups" -> BackupScreen(backupManager)
                    else -> HomeScreen(people, onOpenStaff = { screen = "personal" },
                        onOpenUniforms = { screen = "uniformes" },
                        onOpenUpdates = { screen = "updates" }, onOpenBackups = { screen = "backups" },
                        onOpenBriefings = { screen = "briefings" })
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(people: List<StaffEntity>, onOpenStaff: () -> Unit, onOpenUniforms: () -> Unit, onOpenUpdates: () -> Unit, onOpenBackups: () -> Unit, onOpenBriefings: () -> Unit) {
    val modules = listOf(
        "Personal" to "Disponible · ${people.count { it.isActive }} activos",
        "Briefings" to "Disponible · Asistencia, temas e historial",
        "Uniformes" to "Disponible · Borradores y correcciones",
        "Funciones" to "Próxima etapa",
        "Incidentes" to "Próxima etapa",
        "Reportes" to "Próxima etapa",
        "Respaldos" to "Disponible · Exportación y restauración JSON",
        "Actualizaciones" to "Consultar GitHub Releases desde Las Flores"
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Panel principal", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Personal, Uniformes y Briefings disponibles. Los demás módulos se añadirán progresivamente.",
                style = MaterialTheme.typography.bodyMedium)
        }
        items(modules) { (title, description) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                onClick = { when(title) { "Personal" -> onOpenStaff(); "Uniformes" -> onOpenUniforms(); "Briefings" -> onOpenBriefings(); "Actualizaciones" -> onOpenUpdates(); "Respaldos" -> onOpenBackups() } }
            ) {
                Row(Modifier.padding(18.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(title, fontWeight = FontWeight.Bold)
                        Text(description, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(if (title == "Personal" || title == "Uniformes" || title == "Briefings" || title == "Actualizaciones" || title == "Respaldos") "Abrir" else "Pendiente", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun StaffScreen(people: List<StaffEntity>, vm: StaffViewModel) {
    var fullName by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf("Mozo") }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Personal", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Los trabajadores se guardan en la base de datos interna. No se crean archivos por persona.",
                style = MaterialTheme.typography.bodySmall)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nuevo trabajador", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it.take(120) },
                        label = { Text("Nombre y apellidos") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Puesto", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(STAFF_ROLES) { item ->
                            FilterChip(selected = role == item, onClick = { role = item }, label = { Text(item) })
                        }
                    }
                    Button(
                        onClick = {
                            vm.add(fullName, role)
                            fullName = ""
                        },
                        enabled = StaffName.isValid(fullName)
                    ) { Text("Agregar trabajador") }
                }
            }
        }
        item {
            Text("Registrados (${people.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(people, key = { it.id }) { person ->
            StaffCard(person, vm)
        }
        if (people.isEmpty()) item {
            Text("Aún no hay trabajadores. Agrégalos arriba.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StaffCard(person: StaffEntity, vm: StaffViewModel) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(person.fullName, fontWeight = FontWeight.Bold)
                    Text("${person.role} · ${if (person.isActive) "Activo" else "Inactivo"}",
                        style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = { vm.changeActive(person.id, !person.isActive) }) {
                    Text(if (person.isActive) "Desactivar" else "Activar")
                }
            }
            HorizontalDivider()
            TextButton(onClick = { menuExpanded = !menuExpanded }) {
                Text(if (menuExpanded) "Ocultar puestos" else "Cambiar puesto")
            }
            if (menuExpanded) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(STAFF_ROLES) { newRole ->
                        FilterChip(selected = person.role == newRole,
                            onClick = {
                                vm.changeRole(person.id, newRole)
                                menuExpanded = false
                            }, label = { Text(newRole) })
                    }
                }
            }
        }
    }
}
