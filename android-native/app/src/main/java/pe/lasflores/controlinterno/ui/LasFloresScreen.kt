package pe.lasflores.controlinterno.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.lasflores.controlinterno.R
import pe.lasflores.controlinterno.data.OfflineBackupManager
import pe.lasflores.controlinterno.report.ReportRepository
import pe.lasflores.controlinterno.update.UpdateScreen
import pe.lasflores.controlinterno.update.UpdateViewModel

private val mainSections = listOf(
    "inicio" to "Inicio", "personal" to "Personal", "briefings" to "Briefings", "uniformes" to "Uniformes"
)

private val otherSections = listOf(
    "funciones" to "Funciones", "incidentes" to "Incidentes", "reportes" to "Reportes",
    "backups" to "Respaldos", "updates" to "Actualizaciones"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LasFloresScreen(
    vm: StaffViewModel,
    uniformVm: UniformViewModel,
    briefingVm: BriefingViewModel,
    functionVm: FunctionViewModel,
    incidentVm: IncidentViewModel,
    updateVm: UpdateViewModel,
    backupManager: OfflineBackupManager,
    reportRepository: ReportRepository
) {
    var screen by rememberSaveable { mutableStateOf("inicio") }
    val stateHolder = rememberSaveableStateHolder()
    val people by vm.staff.collectAsState()
    val staffStatus by vm.saveState.collectAsState()
    val uniformStatus by uniformVm.saveStatus.collectAsState()
    val functionStatus by functionVm.message.collectAsState()
    val incidentStatus by incidentVm.message.collectAsState()
    BackHandler(enabled = screen != "inicio") { screen = "inicio" }

    MaterialTheme(colorScheme = LasFloresColors) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        val title = (mainSections + otherSections).first { it.first == screen }.second
                        Text(if (screen == "inicio") "Las Flores" else title, fontWeight = FontWeight.Bold)
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            },
            bottomBar = {
                Surface(tonalElevation = 2.dp, shadowElevation = 3.dp) {
                    Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                        SectionControls(mainSections, screen) { screen = it }
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                val status = when (screen) {
                    "personal" -> staffStatus
                    "uniformes" -> uniformStatus
                    "funciones" -> functionStatus
                    "incidentes" -> incidentStatus
                    else -> ""
                }
                if (status.isNotBlank()) {
                    Text(
                        status,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (status.startsWith("Error")) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                }
                stateHolder.SaveableStateProvider(screen) {
                    when (screen) {
                        "personal" -> StaffManagementScreen(people, vm)
                        "uniformes" -> UniformScreen(people, uniformVm, vm)
                        "briefings" -> BriefingScreen(briefingVm, vm)
                        "funciones" -> FunctionScreen(functionVm, vm)
                        "incidentes" -> IncidentScreen(incidentVm, vm)
                        "reportes" -> ReportScreen(reportRepository, vm)
                        "updates" -> UpdateScreen(updateVm)
                        "backups" -> BackupScreen(backupManager)
                        else -> HomeScreen(people.count { it.isActive }) { screen = it }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(activeStaff: Int, onOpen: (String) -> Unit) {
    val descriptions = mapOf(
        "personal" to "$activeStaff trabajadores activos",
        "briefings" to "Asistencia, temas e historial",
        "uniformes" to "Inspecciones y entregas",
        "funciones" to "Asignaciones, evidencias y estadísticas",
        "incidentes" to "Casos y seguimiento",
        "reportes" to "Excel diario, semanal y mensual",
        "backups" to "Exportar y restaurar registros",
        "updates" to "Buscar una nueva versión"
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(painterResource(R.drawable.las_flores_tulip), contentDescription = null, modifier = Modifier.size(64.dp))
                    Column {
                        Text("Las Flores", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Gestión de Personal", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        items(mainSections.drop(1) + otherSections, key = { it.first }) { (id, title) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                onClick = { onOpen(id) }
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(descriptions.getValue(id), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
