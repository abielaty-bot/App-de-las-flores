package pe.lasflores.controlinterno.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.lasflores.controlinterno.data.BackupPreview
import pe.lasflores.controlinterno.data.OfflineBackupManager

/** Selector de documentos de Android: el usuario conserva el archivo fuera de la app. */
@Composable
fun BackupScreen(manager: OfflineBackupManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("Sin respaldos realizados en esta sesión.") }
    var working by remember { mutableStateOf(false) }
    var pendingJson by remember { mutableStateOf<String?>(null) }
    var pendingPreview by remember { mutableStateOf<BackupPreview?>(null) }

    val createFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            working = true
            try {
                val json = withContext(Dispatchers.IO) { manager.exportJson() }
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri)
                        ?: error("Android no pudo abrir el archivo de destino.")
                    output.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                }
                message = "Respaldo completo guardado. Consérvalo también fuera de la tablet."
            } catch (error: Exception) {
                message = "Error al exportar: " + (error.message ?: "verifica el almacenamiento")
            } finally {
                working = false
            }
        }
    }
    val openFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            working = true
            try {
                val text = withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: error("No se pudo abrir el archivo seleccionado.")
                    input.use { stream ->
                        ByteArrayOutputStream().use { output ->
                            val chunk = ByteArray(8192)
                            var total = 0
                            while (true) {
                                val count = stream.read(chunk)
                                if (count < 0) break
                                total += count
                                require(total <= 30 * 1024 * 1024) { "El respaldo supera 30 MB." }
                                output.write(chunk, 0, count)
                            }
                            output.toString(Charsets.UTF_8.name())
                        }
                    }
                }
                val summary = withContext(Dispatchers.IO) { manager.preview(text) }
                pendingJson = text
                pendingPreview = summary
                message = "Respaldo validado. Confirma los datos antes de restaurarlo."
            } catch (error: Exception) {
                pendingJson = null
                pendingPreview = null
                message = "No se importó ningún dato: " + (error.message ?: "archivo inválido")
            } finally {
                working = false
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Guardado y respaldos", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text("La base de datos se guarda automáticamente. El respaldo JSON permite recuperar todos los módulos Android disponibles.",
                style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Exportar copia de seguridad", fontWeight = FontWeight.Bold)
                    Text("Incluye personal, programación, vacaciones, inspecciones, respuestas, rectificaciones y eventos de uniformes.",
                        style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = {
                            createFile.launch("Las_Flores_Android_" + LocalDate.now().toString() + ".json")
                        },
                        enabled = !working, modifier = Modifier.fillMaxWidth()
                    ) { Text("Descargar respaldo JSON") }
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Restaurar copia Android", fontWeight = FontWeight.Bold)
                    Text("Primero se validará el archivo y verás cuántos registros contiene. La restauración sustituye los datos actuales y crea una copia interna previa.",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { openFile.launch(arrayOf("application/json", "*/*")) },
                        enabled = !working, modifier = Modifier.fillMaxWidth()) {
                        Text("Seleccionar respaldo")
                    }
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Recuperación local", fontWeight = FontWeight.Bold)
                    Text("Después de una restauración puedes recuperar el respaldo anterior guardado dentro de la app. Descarga también copias externas.",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(
                        enabled = !working,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            working = true
                            scope.launch {
                                try {
                                    val text = withContext(Dispatchers.IO) {
                                        manager.recoveryJson() ?: error("No existe un respaldo interno anterior.")
                                    }
                                    val summary = withContext(Dispatchers.IO) { manager.preview(text) }
                                    pendingJson = text
                                    pendingPreview = summary
                                    message = "Copia interna validada. Confirma su restauración."
                                } catch (error: Exception) {
                                    message = "Error al recuperar copia: " + (error.message ?: "no disponible")
                                } finally {
                                    working = false
                                }
                            }
                        }
                    ) { Text("Seleccionar copia interna anterior") }
                }
            }
        }
        item {
            Text(message, color = if (message.startsWith("Error") || message.startsWith("No se importó"))
                MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium)
            if (working) Text("Procesando archivo…")
            Text("Los respaldos de la aplicación web aún no se restauran aquí: su estructura es distinta. Mantén el archivo web original hasta completar la migración de todos los módulos.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
    if (pendingJson != null && pendingPreview != null) {
        val p = pendingPreview!!
        AlertDialog(
            onDismissRequest = { pendingJson = null; pendingPreview = null },
            title = { Text("Confirmar restauración") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Trabajadores: " + p.people)
                    Text("Vacaciones: " + p.vacations)
                    Text("Cambios diarios: " + p.dailyChanges)
                    Text("Inspecciones: " + p.inspections)
                    Text("Respuestas: " + p.answers)
                    Text("Eventos de auditoría: " + p.events)
                    Text("Se reemplazará la información actual. Se guardará una copia interna previa por seguridad.")
                }
            },
            confirmButton = {
                Button(enabled = !working, onClick = {
                    val fileContent = pendingJson ?: return@Button
                    working = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { manager.restore(fileContent) }
                            message = "Restauración finalizada correctamente."
                            pendingJson = null
                            pendingPreview = null
                        } catch (error: Exception) {
                            message = "Error al restaurar: " + (error.message ?: "transacción cancelada")
                        } finally {
                            working = false
                        }
                    }
                }) { Text("Restaurar datos") }
            },
            dismissButton = {
                TextButton(onClick = { pendingJson = null; pendingPreview = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
