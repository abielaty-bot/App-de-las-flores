package pe.lasflores.controlinterno

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import pe.lasflores.controlinterno.ui.LasFloresScreen
import pe.lasflores.controlinterno.ui.StaffViewModel
import pe.lasflores.controlinterno.ui.UniformViewModel
import pe.lasflores.controlinterno.ui.BriefingViewModel
import pe.lasflores.controlinterno.ui.FunctionViewModel
import pe.lasflores.controlinterno.ui.IncidentViewModel
import pe.lasflores.controlinterno.update.UpdateViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as LasFloresApp
        val staffVm = ViewModelProvider(this, StaffViewModel.Factory(app.staffRepository))[StaffViewModel::class.java]
        val uniformVm = ViewModelProvider(this, UniformViewModel.Factory(app.uniformRepository, app.materialRepository))[UniformViewModel::class.java]
        val briefingVm = ViewModelProvider(this, BriefingViewModel.Factory(app.briefingRepository))[BriefingViewModel::class.java]
        val functionVm = ViewModelProvider(this, FunctionViewModel.Factory(app.functionRepository))[FunctionViewModel::class.java]
        val incidentVm = ViewModelProvider(this, IncidentViewModel.Factory(app.incidentRepository))[IncidentViewModel::class.java]
        val updateVm = ViewModelProvider(this, UpdateViewModel.Factory(application))[UpdateViewModel::class.java]
        setContent { LasFloresScreen(staffVm, uniformVm, briefingVm, functionVm, incidentVm, updateVm, app.backupManager, app.reportRepository) }
    }
}
