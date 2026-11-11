package pe.lasflores.controlinterno.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal val LasFloresColors = lightColorScheme(
    primary = Color(0xFF801D38),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF6DDE1),
    onPrimaryContainer = Color(0xFF501227),
    secondary = Color(0xFF715039),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF2DEC7),
    onSecondaryContainer = Color(0xFF422A1C),
    background = Color(0xFFFAF2E7),
    onBackground = Color(0xFF30201E),
    surface = Color(0xFFFFFAF3),
    onSurface = Color(0xFF30201E),
    surfaceVariant = Color(0xFFF3E4D8),
    onSurfaceVariant = Color(0xFF634F49),
    outline = Color(0xFF836D64),
    outlineVariant = Color(0xFFDFCDC2),
    surfaceContainer = Color(0xFFF6E8DB),
    surfaceContainerHigh = Color(0xFFF0DFD1)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DayControls(date: String, onPrevious: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(onClick = onPrevious, modifier = Modifier.semantics { contentDescription = "Día anterior" }) {
            Text("‹")
        }
        Text(date, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
        OutlinedButton(onClick = onNext, modifier = Modifier.semantics { contentDescription = "Día siguiente" }) {
            Text("›")
        }
        TextButton(onClick = onToday) { Text("Hoy") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SectionControls(sections: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        sections.forEach { (id, label) ->
            FilterChip(selected = selected == id, onClick = { onSelect(id) }, label = { Text(label) })
        }
    }
}
