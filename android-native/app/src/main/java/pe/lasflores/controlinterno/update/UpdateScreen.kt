package pe.lasflores.controlinterno.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File

@Composable
fun UpdateScreen(vm: UpdateViewModel) {
    val ui by vm.state.collectAsState()
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Text("Actualizaciones", style = MaterialTheme.typography.headlineSmall) }
        item { Text("Descarga e instala nuevas versiones directamente desde Las Flores, sin archivos ZIP ni carpetas manuales.") }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Estado", style = MaterialTheme.typography.titleMedium)
                    Text(ui.status, color = if (ui.error) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface)
                    Button(onClick = { vm.checkForUpdates() }, enabled = !ui.busy) {
                        Text("Buscar actualizaciones")
                    }
                }
            }
        }
        ui.release?.let { release ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(release.title, style = MaterialTheme.typography.titleLarge)
                        Text("Versión de compilación ${release.versionCode}")
                        if (release.description.isNotBlank()) Text(release.description)
                        Button(onClick = { vm.download() },
                            enabled = !ui.busy && ui.downloadedFile == null) {
                            Text(if (ui.busy) "Descargando…" else "Descargar actualización")
                        }
                        if (ui.downloadedFile != null) {
                            Button(onClick = { openPackageInstaller(context, ui.downloadedFile!!) }) {
                                Text("Instalar actualización")
                            }
                            Text("Android puede pedirte autorización para instalar desde Las Flores y confirmar la actualización.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(4.dp))
            Text("Los datos registrados permanecen en la base local durante una actualización compatible y firmada. El respaldo de datos se incorporará por separado.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** El sistema Android, no Las Flores, autoriza finalmente la instalación. */
private fun openPackageInstaller(context: Context, file: File) {
    if (!file.isFile) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        !context.packageManager.canRequestPackageInstalls()) {
        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        return // Al volver, pulsa Instalar actualización nuevamente.
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.updatefiles", file)
    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    })
}
