package pe.lasflores.controlinterno.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pe.lasflores.controlinterno.data.OfflineBackupManager
import pe.lasflores.controlinterno.report.ReportRepository
import pe.lasflores.controlinterno.update.UpdateScreen
import pe.lasflores.controlinterno.update.UpdateViewModel

/** Raíz visual: no modifica ningún repositorio, función de negocio ni base de datos. */
@Composable
fun LasFloresScreen(
    vm:StaffViewModel,
    uniformVm:UniformViewModel,
    briefingVm:BriefingViewModel,
    functionVm:FunctionViewModel,
    incidentVm:IncidentViewModel,
    updateVm:UpdateViewModel,
    backupManager:OfflineBackupManager,
    reportRepository:ReportRepository
) {
    var screen by rememberSaveable{mutableStateOf("inicio")}
    val holder=rememberSaveableStateHolder()
    val people by vm.staff.collectAsState()
    val personalStatus by vm.saveState.collectAsState()
    val uniformStatus by uniformVm.saveStatus.collectAsState()
    val functionStatus by functionVm.message.collectAsState()
    val incidentStatus by incidentVm.message.collectAsState()

    MaterialTheme(
        colorScheme=LasFloresColors,
        typography=LasFloresTypography,
        shapes=LasFloresShapes
    ) {
        LasFloresAppShell(screen=screen,onNavigate={screen=it}) {
            val message=when(screen) {
                "personal"->personalStatus
                "uniformes"->uniformStatus
                "funciones"->functionStatus
                "incidentes"->incidentStatus
                else->""
            }
            Column(Modifier.fillMaxSize()) {
                if(message.startsWith("Error") || message=="Guardando…") {
                    Text(
                        message,modifier=Modifier.fillMaxWidth().padding(
                            horizontal=16.dp,vertical=5.dp
                        ),
                        color=if(message.startsWith("Error"))
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                        style=MaterialTheme.typography.labelMedium
                    )
                }
                holder.SaveableStateProvider(screen) {
                    when(screen) {
                        "personal"->StaffManagementScreen(people,vm)
                        "uniformes"->UniformScreen(people,uniformVm,vm)
                        "briefings"->BriefingScreen(briefingVm,vm)
                        "funciones"->FunctionScreen(functionVm,vm)
                        "incidentes"->IncidentScreen(incidentVm,vm)
                        "reportes"->ReportScreen(reportRepository,vm)
                        "backups"->BackupScreen(backupManager)
                        "updates"->UpdateScreen(updateVm)
                        else->DashboardScreen(people,vm,uniformVm,
                            briefingVm,functionVm,incidentVm){screen=it}
                    }
                }
            }
        }
    }
}
