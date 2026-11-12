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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    var migrationMode by remember { mutableStateOf<String?>(null) }
    var existingRecords by remember { mutableStateOf(0) }
    var replacementAcknowledged by remember { mutableStateOf(false) }
    val reportError = LocalErrorReporter.current
    // Las excepciones JSON pueden incluir TODO el archivo después de «of {».
    // No se muestra ni se copia su contenido; solo la descripción útil del fallo.
    LaunchedEffect(message) {
        if (ErrorReportText.isError(message)) reportError("Respaldos", message)
    }

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
                message = "Error al exportar: " + ErrorReportText.compact(error.message ?: "verifica el almacenamiento")
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
                val current = withContext(Dispatchers.IO) {
                    manager.preview(manager.exportJson())
                }
                migrationMode = manager.webMigrationMode(text)
                existingRecords = current.people + current.inspections +
                    current.briefings + current.functionChecks +
                    current.incidents + current.deliveries + current.reminders + current.journeyOccurrences + current.journeyNotifications + current.phoneChecks
                replacementAcknowledged = false
                pendingJson = text
                pendingPreview = summary
                message = if (migrationMode != null)
                    "Transferencia Web convertida y validada. Revisa el contenido antes de confirmar."
                else "Respaldo Android validado. Confirma los datos antes de restaurarlo."
            } catch (error: Exception) {
                pendingJson = null
                pendingPreview = null
                migrationMode = null
                message = "No se importó ningún dato: " + ErrorReportText.compact(error.message ?: "archivo inválido")
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
                    Text("Incluye personal, programación, vacaciones, inspecciones, requisitos, uniformes, briefings, funciones e incidentes con todas sus acciones de seguimiento.",
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
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Centro de migración Web / Android", fontWeight = FontWeight.Bold)
                    Text("1. En Las Flores Web abre Respaldos → Centro de migración. " +
                        "2. Analiza tus datos y descarga el JSON compatible con Android. " +
                        "3. Selecciónalo aquí para comprobar los registros antes de importarlos.",
                        style = MaterialTheme.typography.bodySmall)
                    Text("El respaldo Web original se incorpora íntegro al archivo migrado " +
                        "y se conserva en los siguientes respaldos Android.",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(
                        onClick = { openFile.launch(arrayOf("application/json", "*/*")) },
                        enabled = !working, modifier = Modifier.fillMaxWidth()
                    ) { Text("Seleccionar transferencia Web convertida") }
                    Text("Un JSON Web antiguo sin convertir será rechazado. " +
                        "No borres el archivo original ni la versión web hasta comprobar los resultados.",
                        style = MaterialTheme.typography.bodySmall)
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
                                    message = "Error al recuperar copia: " + ErrorReportText.compact(error.message ?: "no disponible")
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
            Text("El Centro de migración admite archivos Web convertidos y validados. Los JSON Web sin convertir se mantienen protegidos: conviértelos primero en la web.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
    if (pendingJson != null && pendingPreview != null) {
        val p = pendingPreview!!
        AlertDialog(
            onDismissRequest = { pendingJson = null; pendingPreview = null },
            title = { Text(if (migrationMode != null) "Confirmar transferencia Web" else "Confirmar restauración") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (migrationMode != null) {
                        Text("Origen: Las Flores Web · " +
                            (if (migrationMode == "merge") "Fusión estricta" else "Reemplazo"))
                        Text("La copia Web íntegra se mantendrá en los respaldos Android.")
                    }
                    Text("Trabajadores: " + p.people)
                    Text("Vacaciones: " + p.vacations)
                    Text("Cambios diarios: " + p.dailyChanges)
                    Text("Inspecciones: " + p.inspections)
                    Text("Respuestas: " + p.answers)
                    Text("Eventos de auditoría: " + p.events)
                    Text("Requisitos configurables: " + p.requirements)
                    Text("Entregas de materiales: " + p.deliveries)
                    Text("Asistencias a briefings: " + p.briefings)
                    Text("Sesiones con temas: " + p.briefingSessions)
                    Text("Eventos de briefing: " + p.briefingEvents)
                    Text("Tareas de funciones: " + p.functionTasks)
                    Text("Asignaciones: " + p.functionChecks)
                    Text("Fotos de evidencia: " + p.functionEvidence)
                    Text("Eventos de funciones: " + p.functionEvents)
                    Text("Tipos de incidentes: " + p.incidentTypes)
                    Text("Incidentes: " + p.incidents)
                    Text("Acciones de seguimiento: " + p.incidentActions)
                    Text("Recordatorios: "+p.reminders+" · Actividades: "+p.journeyOccurrences+" · Avisos: "+p.journeyNotifications)
                    if(p.formatVersion<8)Text("Esta copia es anterior a los recordatorios; se restaurará sin ellos.")
                    Text("Controles de celulares: "+p.phoneChecks)
                    if(p.formatVersion<9)Text("Esta copia es anterior a Celulares; no contiene sus registros.")
                    if(p.formatVersion<10)Text("Esta copia conserva los puestos originales; no convierte Apoyo en Runner.")
                    if (p.formatVersion < 5)
                        Text("Este archivo es anterior a Incidentes. Si lo restauras, reemplazará los incidentes que hayas registrado en esta tablet.",
                            color = MaterialTheme.colorScheme.error)
                    if (p.formatVersion < 4)
                        Text("Respaldo anterior a Funciones. Restaurarlo reemplazará todos los registros actuales de Funciones.",
                            color = MaterialTheme.colorScheme.error)
                    if (p.formatVersion < 3)
                        Text("Este archivo es anterior a Briefings. Restaurarlo reemplazará los briefings actuales.",
                            color = MaterialTheme.colorScheme.error)
                    Text("Se reemplazará la información actual por el contenido del archivo seleccionado. Se guardará una copia interna previa por seguridad.")
                    if (existingRecords > 0) {
                        Text("Esta tablet ya contiene $existingRecords registros principales. " +
                            "Comprueba que estén incluidos en el archivo seleccionado.",
                            color = MaterialTheme.colorScheme.error)
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = replacementAcknowledged,
                                onCheckedChange = { replacementAcknowledged = it }
                            )
                            Text("Revisé el archivo y autorizo reemplazar los datos actuales.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(enabled = !working && (existingRecords == 0 || replacementAcknowledged), onClick = {
                    val fileContent = pendingJson ?: return@Button
                    working = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { manager.restore(fileContent) }
                            message = "Restauración finalizada correctamente."
                            pendingJson = null
                            pendingPreview = null
                            migrationMode = null
                            replacementAcknowledged = false
                        } catch (error: Exception) {
                            message = "Error al restaurar: " + ErrorReportText.compact(error.message ?: "transacción cancelada")
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
