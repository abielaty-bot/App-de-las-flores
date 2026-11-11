package pe.lasflores.controlinterno.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Configuración local de aspecto. No altera registros ni base de datos. */
@Composable
internal fun SettingsScreen(
    mode: LasFloresThemeMode,
    darkTheme: Boolean,
    onModeChange: (LasFloresThemeMode) -> Unit,
    onOpenBackups: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Configuración", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        Text("Personaliza la aplicación sin modificar los controles ni la información guardada.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text("Apariencia", style = MaterialTheme.typography.titleLarge)
                Text("Elige cómo se muestran todas las pantallas de Las Flores.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.selectableGroup()) {
                    listOf(
                        Triple(LasFloresThemeMode.AUTOMATIC, "Automático",
                            "Sigue el modo claro u oscuro de Android."),
                        Triple(LasFloresThemeMode.LIGHT, "Claro",
                            "Fondo marfil y acentos borgoña."),
                        Triple(LasFloresThemeMode.DARK, "Oscuro · Borgoña Nocturno",
                            "Fondo ciruela, tarjetas carbón y detalles champán.")
                    ).forEach { (item, label, description) ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .selectable(
                                    selected = mode == item,
                                    onClick = { onModeChange(item) },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            RadioButton(
                                selected = mode == item,
                                onClick = null
                            )
                            Column(Modifier.weight(1f)) {
                                Text(label, fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyLarge)
                                Text(description, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (item != LasFloresThemeMode.DARK) HorizontalDivider()
                    }
                }
                Text(
                    if (darkTheme) "Aspecto activo: oscuro" else "Aspecto activo: claro",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Identidad visual", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        LasFloresPalette.Burgundy,
                        LasFloresPalette.Champagne,
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background
                    ).forEach { swatch ->
                        androidx.compose.foundation.layout.Box(
                            Modifier.size(38.dp)
                                .background(swatch, RoundedCornerShape(10.dp))
                        )
                    }
                }
                Text("Los estados operativos mantienen verde, ámbar y rojo, con etiquetas para evitar confusiones.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Datos y respaldos", style = MaterialTheme.typography.titleMedium)
                Text("La apariencia se guarda por separado y no afecta los datos de personal, briefings, uniformes, funciones o incidentes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(
                    onClick = onOpenBackups,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Abrir respaldos") }
            }
        }
    }
}
