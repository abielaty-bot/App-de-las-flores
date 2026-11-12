package pe.lasflores.controlinterno.update

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import pe.lasflores.controlinterno.ui.ErrorReportText
import pe.lasflores.controlinterno.ui.LocalErrorReporter

@Composable
fun UpdateScreen(vm:UpdateViewModel){
    val ui by vm.state.collectAsState()
    val context=LocalContext.current
    val reportError=LocalErrorReporter.current
    var allowed by remember{mutableStateOf(UpdateNotifications.allowed(context))}
    val request=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){allowed=UpdateNotifications.allowed(context)}
    val settings=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){allowed=UpdateNotifications.allowed(context)}
    LaunchedEffect(ui.status,ui.error){if(ui.error)reportError("Actualizaciones",ui.status)}
    val installed=remember(context){context.packageManager.getPackageInfo(context.packageName,0).versionName?:"Desconocida"}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{
            Text("Centro de actualizaciones",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            Text("Versión instalada · "+installed,style=MaterialTheme.typography.bodyMedium)
        }
        item{
            Card(Modifier.fillMaxWidth()){
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
                    Text("Estado de la actualización",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    Text(if(ui.error)ErrorReportText.compact(ui.status) else ui.status,
                        color=if(ui.error)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    if(ui.phase==UpdatePhase.CHECKING || ui.phase==UpdatePhase.INSTALL_CHECK)LinearProgressIndicator(Modifier.fillMaxWidth())
                    Button(onClick=vm::checkForUpdates,enabled=!ui.busy,modifier=Modifier.fillMaxWidth()){Text("Buscar actualizaciones")}
                }
            }
        }
        ui.release?.let{release->
            item{
                Card(Modifier.fillMaxWidth()){
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
                        Text("Versión "+UpdatePolicy.versionNameForCode(release.versionCode),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                        Text("Tamaño · "+UpdateFormat.bytes(release.sizeBytes))
                        if(release.publishedAt.isNotBlank())Text("Publicada · "+formatPublished(release.publishedAt),style=MaterialTheme.typography.bodySmall)
                        if(release.description.isNotBlank()){
                            HorizontalDivider()
                            Text("Novedades",fontWeight=FontWeight.SemiBold)
                            Text(release.description,style=MaterialTheme.typography.bodySmall)
                        }
                        if(!ui.busy&&ui.downloadedFile==null){
                            Button(onClick=vm::download,modifier=Modifier.fillMaxWidth()){
                                Text(if(ui.progress.bytes>0)"Reanudar / reintentar descarga" else if(ui.error)"Reintentar descarga" else "Descargar actualización")
                            }
                        }
                        if(!ui.busy&&ui.downloadedFile!=null){
                            Button(onClick={vm.install{file->openPackageInstaller(context,file)}},modifier=Modifier.fillMaxWidth()){
                                Text("Instalar actualización")
                            }
                            Text("Si Android pide permiso para instalar desde Las Flores, concédelo y vuelve a pulsar Instalar.",style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if(ui.phase in setOf(UpdatePhase.DOWNLOADING,UpdatePhase.VERIFYING_HASH,UpdatePhase.VERIFYING_PACKAGE,
                UpdatePhase.PREPARING,UpdatePhase.CANCELLING,UpdatePhase.CANCELLED,UpdatePhase.ERROR,UpdatePhase.READY,UpdatePhase.INSTALL_CHECK)
                || ui.progress.bytes>0)item{ProgressPanel(ui,vm)}
        }
        item{
            Card(Modifier.fillMaxWidth()){
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
                    Text("Notificaciones",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    Text(if(allowed)"Notificaciones de descarga habilitadas por Android." else "Permite las notificaciones para ver el progreso fuera de Las Flores.")
                    if(!allowed)OutlinedButton(onClick={
                        if(Build.VERSION.SDK_INT>=33 &&
                            androidx.core.content.ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
                            request.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else settings.launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))
                    }){Text("Permitir notificaciones")}
                    OutlinedButton(onClick={
                        settings.launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))
                    }){Text("Configuración de avisos de Android")}
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        Column(Modifier.weight(1f)){
                            Text("Avisar de nuevas versiones",fontWeight=FontWeight.Medium)
                            Text("Consulta periódica, aproximadamente diaria. No descarga ni instala automáticamente.",style=MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked=ui.automaticChecks,onCheckedChange=vm::setAutomaticChecks)
                    }
                }
            }
        }
        item{
            Card(Modifier.fillMaxWidth()){
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
                    Text("Historial de versiones instaladas",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    Text("Versiones detectadas desde que existe este registro. Una descarga no cuenta como instalación.",style=MaterialTheme.typography.bodySmall)
                    ui.history.forEach{item->
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                            Text(item.name,fontWeight=FontWeight.Medium)
                            Text(formatInstant(item.installedAt),style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item{
            Text("Antes de instalar, Las Flores crea y verifica una copia interna. Si falla, no abre el instalador. Conserva también copias externas. Las APK se verifican por SHA-256, paquete, versión y certificado. No desinstales Las Flores.",
                style=MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ProgressPanel(ui:UpdateUiState,vm:UpdateViewModel){
    val p=ui.progress
    val active=ui.phase==UpdatePhase.DOWNLOADING
    val verifying=ui.phase in setOf(UpdatePhase.VERIFYING_HASH,UpdatePhase.VERIFYING_PACKAGE,UpdatePhase.PREPARING,UpdatePhase.INSTALL_CHECK)
    val cancellable=ui.phase in setOf(UpdatePhase.DOWNLOADING,UpdatePhase.VERIFYING_HASH,UpdatePhase.VERIFYING_PACKAGE,UpdatePhase.PREPARING)
    Card(Modifier.fillMaxWidth()){
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text("Progreso de descarga",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            Text(p.percent.toString()+"%",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
            LinearProgressIndicator(progress={if(p.total>0)(p.bytes.toDouble()/p.total).coerceIn(0.0,1.0).toFloat() else 0f},modifier=Modifier.fillMaxWidth())
            Text(UpdateFormat.bytes(p.bytes)+" de "+UpdateFormat.bytes(p.total))
            if(verifying){
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Archivo descargado. Aún se está verificando; 100% no significa instalado.",style=MaterialTheme.typography.bodySmall)
            }
            val metrics=listOf(
                "Restante por descargar" to UpdateFormat.bytes(p.remaining),
                "Velocidad" to if(active&&p.bytesPerSecond>0)UpdateFormat.bytes(p.bytesPerSecond.toLong())+"/s" else "—",
                "Tiempo transcurrido" to UpdateFormat.duration(p.elapsedMillis/1000),
                "Tiempo restante estimado" to if(active)UpdateFormat.duration(p.etaSeconds) else if(p.remaining==0L)"0 s" else "—")
            BoxWithConstraints(Modifier.fillMaxWidth()){
                val columns=if(maxWidth>=360.dp)2 else 1
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                    metrics.chunked(columns).forEach{line->
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            line.forEach{(label,value)->
                                Card(Modifier.weight(1f)){
                                    Column(Modifier.padding(10.dp)){
                                        Text(label,style=MaterialTheme.typography.labelMedium)
                                        Text(value,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Text(ui.status,style=MaterialTheme.typography.bodySmall)
            if(cancellable||(!ui.busy&&ui.downloadedFile==null&&p.bytes>0))OutlinedButton(onClick=vm::cancel,modifier=Modifier.fillMaxWidth()){
                Text("Cancelar descarga y descartar parte")
            }
            if(active)Text("Puedes seguir usando otros módulos mientras descarga. El tiempo restante es aproximado y se ajusta con la conexión.",style=MaterialTheme.typography.bodySmall)
        }
    }
}

private fun formatInstant(value:Long):String=runCatching{
    Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm",Locale("es","PE")))
}.getOrDefault("Sin fecha")
private fun formatPublished(value:String):String=runCatching{formatInstant(Instant.parse(value).toEpochMilli())}.getOrDefault(value.take(10))

private fun openPackageInstaller(context:Context,file:File){
    check(file.isFile){"La descarga ya no está disponible."}
    if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()){
        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply{
            data=Uri.parse("package:"+context.packageName);addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        return
    }
    val uri=FileProvider.getUriForFile(context,context.packageName+".updatefiles",file)
    context.startActivity(Intent(Intent.ACTION_VIEW).apply{
        setDataAndType(uri,"application/vnd.android.package-archive")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    })
}
