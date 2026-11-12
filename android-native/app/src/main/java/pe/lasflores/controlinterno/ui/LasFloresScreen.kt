package pe.lasflores.controlinterno.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import android.graphics.Color as AndroidColor
import androidx.core.view.WindowCompat
import androidx.compose.ui.unit.dp
import pe.lasflores.controlinterno.data.OfflineBackupManager
import pe.lasflores.controlinterno.report.ReportRepository
import pe.lasflores.controlinterno.update.UpdateScreen
import pe.lasflores.controlinterno.update.UpdateViewModel
import pe.lasflores.controlinterno.journey.JourneyEngine
import pe.lasflores.controlinterno.journey.JourneyNavigation

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
    reportRepository:ReportRepository,
    openUpdates:Int=0,
    journey:JourneyEngine,
    journeyRoute:JourneyNavigation?=null,
    phoneVm:PhoneViewModel
) {
    var screen by rememberSaveable{mutableStateOf("inicio")}
    LaunchedEffect(openUpdates){if(openUpdates>0)screen="updates"}
    val journeyState by journey.state.collectAsState()
    var requestedDate by remember{mutableStateOf<JourneyNavigation?>(null)}
    val openJourney:(JourneyNavigation)->Unit={route->
        when(route.module){
            "briefings"->briefingVm.openFromJourney(route.dateIso,route.sessionSlot,route.taskKey.startsWith("inform:"))
            "uniformes"->uniformVm.setDate(route.dateIso)
            "celulares"->phoneVm.setDate(route.dateIso)
            "funciones"->functionVm.setDate(route.dateIso)
            "personal","reportes"->requestedDate=route
        }
        if(route.notificationId.isNotBlank())journey.read(route.notificationId)
        screen=route.module
    }
    LaunchedEffect(journeyRoute?.nonce){journeyRoute?.let(openJourney)}
    val holder=rememberSaveableStateHolder()
    val people by vm.staff.collectAsState()
    val personalStatus by vm.saveState.collectAsState()
    val uniformStatus by uniformVm.saveStatus.collectAsState()
    val functionStatus by functionVm.message.collectAsState()
    val incidentStatus by incidentVm.message.collectAsState()
    val briefingStatus by briefingVm.message.collectAsState()
    val phoneStatus by phoneVm.message.collectAsState()
    val updateStatus by updateVm.state.collectAsState()
    val errorCenter = remember { ErrorReportCenter() }
    val reportError: (String, String) -> Unit = remember(errorCenter) {
        { module, raw -> errorCenter.register(module, raw) }
    }
    LaunchedEffect(personalStatus) {
        if (ErrorReportText.isError(personalStatus)) reportError("Personal", personalStatus)
    }
    LaunchedEffect(uniformStatus) {
        if (ErrorReportText.isError(uniformStatus)) reportError("Uniformes", uniformStatus)
    }
    LaunchedEffect(briefingStatus) {
        if (ErrorReportText.isError(briefingStatus)) reportError("Briefings", briefingStatus)
    }
    LaunchedEffect(functionStatus) {
        if (ErrorReportText.isError(functionStatus)) reportError("Funciones", functionStatus)
    }
    LaunchedEffect(incidentStatus) {
        if (ErrorReportText.isError(incidentStatus)) reportError("Incidentes", incidentStatus)
    }
    LaunchedEffect(phoneStatus) {
        if (ErrorReportText.isError(phoneStatus)) reportError("Celulares", phoneStatus)
    }
    LaunchedEffect(updateStatus.status, updateStatus.error) {
        if (updateStatus.error) reportError("Actualizaciones", updateStatus.status)
    }

    val context = LocalContext.current
    val store = remember(context) { LasFloresThemeStore(context) }
    var themeMode by remember { mutableStateOf(store.read()) }
    val darkTheme = LasFloresThemeRules.isDark(themeMode, isSystemInDarkTheme())
    val activity = context as? Activity

    // Sincroniza iconos de sistema con el tema sin reiniciar ni alterar formularios.
    SideEffect {
        activity?.window?.let { window ->
            @Suppress("DEPRECATION")
            window.statusBarColor = AndroidColor.rgb(53, 28, 39)
            @Suppress("DEPRECATION")
            window.navigationBarColor =
                if(darkTheme) AndroidColor.rgb(23, 21, 25)
                else AndroidColor.rgb(248, 245, 240)
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalLasFloresDark provides darkTheme,
        LocalErrorReporter provides reportError
    ) {
    MaterialTheme(
        colorScheme=if(darkTheme) LasFloresDarkColors else LasFloresColors,
        typography=LasFloresTypography,
        shapes=LasFloresShapes
    ) {
        LasFloresAppShell(screen=screen,onNavigate={screen=it},unreadNotifications=journeyState.notifications.count{it.readAtMillis==null}) {
            val message=when(screen) {
                "personal"->personalStatus
                "uniformes"->uniformStatus
                "funciones"->functionStatus
                "incidentes"->incidentStatus
                "briefings"->briefingStatus
                "celulares"->phoneStatus
                else->""
            }
            Column(Modifier.fillMaxSize()) {
                errorCenter.latest?.let { incident ->
                    CopyableErrorCard(incident, onDismiss = errorCenter::dismiss)
                }
                if(message=="Guardando…" || message=="Guardado automáticamente" || message.startsWith("Error")) {
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
                        "personal"->StaffManagementScreen(people,vm,requestedDate?.takeIf{it.module=="personal"}?.dateIso,{requestedDate=null},reportRepository)
                        "uniformes"->UniformScreen(people,uniformVm,vm)
                        "celulares"->PhoneScreen(people,vm,phoneVm)
                        "briefings"->BriefingScreen(briefingVm,vm)
                        "funciones"->FunctionScreen(functionVm,vm)
                        "incidentes"->IncidentScreen(incidentVm,vm)
                        "reportes"->ReportScreen(reportRepository,vm,requestedDate?.takeIf{it.module=="reportes"}?.dateIso,{requestedDate=null})
                        "backups"->BackupScreen(backupManager)
                        "notifications"->JourneyScreen(journey,openJourney,{screen="updates"})
                        "settings"->SettingsScreen(
                            mode=themeMode,
                            darkTheme=darkTheme,
                            onModeChange={ selected ->
                                themeMode=selected
                                store.write(selected)
                            },
                            onOpenBackups={screen="backups"},
                            briefingVm=briefingVm,
                            onOpenNotifications={screen="notifications"}
                        )
                        "updates"->UpdateScreen(updateVm)
                        else->DashboardScreen(people,vm,uniformVm,
                            briefingVm,functionVm,incidentVm,journey,openJourney){screen=it}
                    }
                }
            }
        }
    }
    }
}
