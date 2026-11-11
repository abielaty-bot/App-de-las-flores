package pe.lasflores.controlinterno.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
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
    // Borgoña Nocturno: marca, superficies, textos y tres estados accesibles.
    val NightBackground = Color(0xFF171519)
    val NightSurface = Color(0xFF242026)
    val NightElevated = Color(0xFF302831)
    val NightBurgundy = Color(0xFF873650)
    val NightChampagne = Color(0xFFDCB98A)
    val NightText = Color(0xFFF7F1EE)
    val NightBlush = Color(0xFFD8A0B1)
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

/** M3 usa un borgoña aclarado como color de contenido táctil: es legible sobre carbón.
 * El borgoña de marca más intenso permanece en encabezados y superficies destacadas.
 */
internal val LasFloresDarkColors = darkColorScheme(
    primary = LasFloresPalette.NightBlush,
    onPrimary = Color(0xFF24151D),
    primaryContainer = LasFloresPalette.NightBurgundy,
    onPrimaryContainer = Color.White,
    secondary = LasFloresPalette.NightChampagne,
    onSecondary = Color(0xFF2B2026),
    secondaryContainer = LasFloresPalette.NightBurgundy,
    onSecondaryContainer = Color.White,
    tertiary = LasFloresPalette.NightChampagne,
    onTertiary = Color(0xFF2B2026),
    tertiaryContainer = Color(0xFF493824),
    onTertiaryContainer = Color(0xFFFFE4B3),
    background = LasFloresPalette.NightBackground,
    onBackground = LasFloresPalette.NightText,
    surface = LasFloresPalette.NightSurface,
    onSurface = LasFloresPalette.NightText,
    surfaceVariant = LasFloresPalette.NightElevated,
    onSurfaceVariant = Color(0xFFCDBFC4),
    outline = Color(0xFFB29EA8),
    outlineVariant = Color(0xFF55454E),
    error = Color(0xFFFF9AA2),
    onError = Color(0xFF491C25),
    errorContainer = Color(0xFF4B2833),
    onErrorContainer = Color(0xFFFFCDD3),
    surfaceContainerLowest = Color(0xFF171519),
    surfaceContainerLow = LasFloresPalette.NightSurface,
    surfaceContainer = Color(0xFF29232B),
    surfaceContainerHigh = LasFloresPalette.NightElevated,
    surfaceContainerHighest = Color(0xFF3B313B)
)

/** La semántica no depende únicamente del color: cada chip mantiene su texto. */
internal val LocalLasFloresDark = staticCompositionLocalOf { false }

@Composable
internal fun semanticBackground(kind: String): Color {
    val dark = LocalLasFloresDark.current
    return if(dark) when(kind) {
        "success" -> Color(0xFF254333)
        "danger" -> Color(0xFF4B2833)
        "warning" -> Color(0xFF493824)
        "corrected" -> Color(0xFF463626)
        else -> LasFloresPalette.NightElevated
    } else when(kind) {
        "success" -> Color(0xFFE5F2EA)
        "danger" -> Color(0xFFFCE9E6)
        "warning" -> Color(0xFFF7ECD7)
        "corrected" -> Color(0xFFF3E6D5)
        else -> LasFloresPalette.Ivory
    }
}

@Composable
internal fun semanticForeground(kind: String): Color {
    val dark = LocalLasFloresDark.current
    return if(dark) when(kind) {
        "success" -> Color(0xFF7DD2A2)
        "danger" -> Color(0xFFFF9AA2)
        "warning", "corrected" -> Color(0xFFF2C578)
        else -> Color(0xFFCDBFC4)
    } else when(kind) {
        "success" -> LasFloresPalette.Success
        "danger" -> LasFloresPalette.Danger
        "warning" -> LasFloresPalette.Warning
        "corrected" -> Color(0xFF76582F)
        else -> LasFloresPalette.Muted
    }
}

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

/** Pestañas en una sola fila: no consumen tres filas en tablets verticales. */
@Composable
internal fun SectionControls(
    sections: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(sections, key = { it.first }) { (id, label) ->
            FilterChip(
                selected = selected == id,
                onClick = { onSelect(id) },
                label = { Text(label) }
            )
        }
    }
}
