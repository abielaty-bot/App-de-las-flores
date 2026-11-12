package pe.lasflores.controlinterno

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import pe.lasflores.controlinterno.journey.JourneyAlerts
import pe.lasflores.controlinterno.journey.JourneyNavigation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import pe.lasflores.controlinterno.update.UpdateNotifications
import pe.lasflores.controlinterno.update.UpdateCheckJob
import pe.lasflores.controlinterno.update.UpdateStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import pe.lasflores.controlinterno.ui.LasFloresScreen
import pe.lasflores.controlinterno.ui.StaffViewModel
import pe.lasflores.controlinterno.ui.UniformViewModel
import pe.lasflores.controlinterno.ui.BriefingViewModel
import pe.lasflores.controlinterno.ui.FunctionViewModel
import pe.lasflores.controlinterno.ui.IncidentViewModel
import pe.lasflores.controlinterno.ui.PhoneViewModel
import pe.lasflores.controlinterno.update.UpdateViewModel

class MainActivity : ComponentActivity() {
    private var openUpdates by mutableIntStateOf(0)
    private var journeyRoute by mutableStateOf<JourneyNavigation?>(null)
    override fun onResume(){super.onResume();(application as LasFloresApp).journey.refresh();(application as LasFloresApp).checkDailyBackup()}
    override fun onStop(){super.onStop();if(!isChangingConfigurations)(application as LasFloresApp).checkDailyBackup(true)}
    override fun onNewIntent(intent:Intent){
        super.onNewIntent(intent)
        setIntent(intent)
        if(intent.action==UpdateNotifications.OPEN)openUpdates++
        JourneyAlerts.route(intent)?.let{journeyRoute=it}
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_LasFlores)
        super.onCreate(savedInstanceState)
        if(intent.action==UpdateNotifications.OPEN)openUpdates++
        JourneyAlerts.route(intent)?.let{journeyRoute=it}
        UpdateCheckJob.configure(this,UpdateStore(this).automaticChecks)
        val app = application as LasFloresApp
        val staffVm = ViewModelProvider(this, StaffViewModel.Factory(app.staffRepository))[StaffViewModel::class.java]
        val uniformVm = ViewModelProvider(this, UniformViewModel.Factory(app.uniformRepository, app.materialRepository))[UniformViewModel::class.java]
        val briefingVm = ViewModelProvider(this, BriefingViewModel.Factory(app.briefingRepository))[BriefingViewModel::class.java]
        val functionVm = ViewModelProvider(this, FunctionViewModel.Factory(app.functionRepository))[FunctionViewModel::class.java]
        val incidentVm = ViewModelProvider(this, IncidentViewModel.Factory(app.incidentRepository))[IncidentViewModel::class.java]
        val updateVm = ViewModelProvider(this, UpdateViewModel.Factory(application))[UpdateViewModel::class.java]
        val phoneVm=ViewModelProvider(this,PhoneViewModel.Factory(app.phoneRepository))[PhoneViewModel::class.java]
        setContent { LasFloresScreen(staffVm, uniformVm, briefingVm, functionVm, incidentVm, updateVm, app.backupManager, app.reportRepository, openUpdates,app.journey,journeyRoute,phoneVm) }
    }
}
