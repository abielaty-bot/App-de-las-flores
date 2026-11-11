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
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal object LasFloresPalette {
    val Burgundy = Color(0xFF672638)
    val DeepPlum = Color(0xFF351C27)
    val Champagne = Color(0xFFC49A55)
    val Ivory = Color(0xFFF8F5F0)
    val Charcoal = Color(0xFF25212A)
    val Success = Color(0xFF216B48)
    val Warning = Color(0xFF9A6210)
    val Danger = Color(0xFFB3261E)
    val Muted = Color(0xFF75666C)
}

/** Tokens cromáticos compartidos; el champán no se usa para texto pequeño sobre blanco. */
internal val LasFloresColors = lightColorScheme(
    primary = LasFloresPalette.Burgundy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF2DCE2),
    onPrimaryContainer = Color(0xFF421622),
    secondary = Color(0xFF74563A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF5E9D7),
    onSecondaryContainer = Color(0xFF432A16),
    tertiary = Color(0xFF78602D),
    onTertiary = Color.White,
    background = LasFloresPalette.Ivory,
    onBackground = LasFloresPalette.Charcoal,
    surface = Color(0xFFFFFDFC),
    onSurface = LasFloresPalette.Charcoal,
    surfaceVariant = Color(0xFFF4EAE4),
    onSurfaceVariant = Color(0xFF61575A),
    outline = Color(0xFF867A7C),
    outlineVariant = Color(0xFFE5D7D2),
    error = LasFloresPalette.Danger,
    onError = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFBF8),
    surfaceContainer = Color(0xFFF6EEE8),
    surfaceContainerHigh = Color(0xFFF0E5DF),
    surfaceContainerHighest = Color(0xFFECE0DA)
)

internal val LasFloresShapes = Shapes(
    extraSmall = RoundedCornerShape(7.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(15.dp),
    large = RoundedCornerShape(21.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

internal val LasFloresTypography = Typography(
    headlineSmall = TextStyle(
        fontSize = 23.sp, fontWeight = FontWeight.Bold, lineHeight = 29.sp
    ),
    titleLarge = TextStyle(
        fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 27.sp
    ),
    titleMedium = TextStyle(
        fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 23.sp
    ),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
