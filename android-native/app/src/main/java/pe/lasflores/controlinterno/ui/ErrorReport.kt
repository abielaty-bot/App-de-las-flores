package pe.lasflores.controlinterno.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Copia información de diagnóstico suficiente para reproducir un error,
 * pero nunca el contenido del respaldo JSON ni registros del personal.
 */
internal object ErrorReportText {
    private val jsonDump = Regex("""\s+of\s+[\{\[]""", RegexOption.IGNORE_CASE)
    private val rawObject = Regex("""[\{\[]\s*["'](?:app|staff|inspections|formatVersion)["']\s*:""")
    fun compact(raw: String): String {
        val head = raw.trim().lineSequence().firstOrNull().orEmpty()
        val index = listOfNotNull(
            jsonDump.find(head)?.range?.first,
            rawObject.find(head)?.range?.first
        ).minOrNull()
        val candidate = (if (index != null) head.take(index) else head).trim()
        return candidate.take(380).ifBlank { "Error sin descripción disponible" }
    }

    fun isError(message: String): Boolean {
        val value = message.trim()
        return value.startsWith("Error", ignoreCase = true) ||
            value.startsWith("No se importó", ignoreCase = true) ||
            value.startsWith("No se pudo", ignoreCase = true)
    }
}

internal data class ErrorReport(
    val module: String,
    val detail: String,
    val timestampMillis: Long
) {
    fun clipboardText(version: String): String {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(timestampMillis))
        return listOf(
            "Las Flores – Control Interno",
            "Versión Android: $version",
            "Módulo: $module",
            "Fecha y hora: $date",
            "Error: $detail",
            "Datos personales y contenido JSON: omitidos por seguridad."
        ).joinToString("\n")
    }
}

/** Memoria solo en la sesión, no envía nada a servidores ni guarda datos sensibles. */
internal class ErrorReportCenter {
    var latest by mutableStateOf<ErrorReport?>(null)
        private set

    fun register(module: String, rawMessage: String) {
        val detail = ErrorReportText.compact(rawMessage)
        if (latest?.module == module && latest?.detail == detail) return
        latest = ErrorReport(module, detail, System.currentTimeMillis())
    }

    fun dismiss() { latest = null }
}

internal val LocalErrorReporter = staticCompositionLocalOf<(String, String) -> Unit> {
    { _, _ -> }
}

@Composable
internal fun CopyableErrorCard(report: ErrorReport, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val version = androidx.compose.runtime.remember(context) {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Desconocida"
    }
    Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Error detectado · " + report.module,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error)
            Text(report.detail, maxLines = 3, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                        as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(
                        "Error Las Flores", report.clipboardText(version)
                    ))
                }) { Text("Copiar error") }
                TextButton(onClick = onDismiss) { Text("Cerrar") }
            }
        }
    }
}
