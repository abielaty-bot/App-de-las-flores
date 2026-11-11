package pe.lasflores.controlinterno

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import pe.lasflores.controlinterno.ui.LasFloresScreen
import pe.lasflores.controlinterno.ui.StaffViewModel
import pe.lasflores.controlinterno.ui.UniformViewModel
import pe.lasflores.controlinterno.update.UpdateViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as LasFloresApp
        val staffVm = ViewModelProvider(this, StaffViewModel.Factory(app.staffRepository))[StaffViewModel::class.java]
        val uniformVm = ViewModelProvider(this, UniformViewModel.Factory(app.uniformRepository))[UniformViewModel::class.java]
        val updateVm = ViewModelProvider(this, UpdateViewModel.Factory(application))[UpdateViewModel::class.java]
        setContent { LasFloresScreen(staffVm, uniformVm, updateVm, app.backupManager) }
    }
}
