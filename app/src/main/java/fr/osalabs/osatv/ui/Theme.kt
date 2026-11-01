package fr.osalabs.osatv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.darkColorScheme
import fr.osalabs.osatv.R

val Inter = FontFamily(
    Font(R.font.inter_400, FontWeight.Normal),
    Font(R.font.inter_500, FontWeight.Medium),
    Font(R.font.inter_600, FontWeight.SemiBold),
    Font(R.font.inter_700, FontWeight.Bold),
    Font(R.font.inter_800, FontWeight.ExtraBold),
)

object Osa {
    val card = Color(0xFF1C1C1E)
    val muted = Color.White.copy(alpha = 0.6f)
    val faint = Color.White.copy(alpha = 0.08f)

    // Grille pensée pour 960 × 540 dp (une télé 720p ou 1080p), proportions de tvOS
    const val COLUMNS = 5
    val side = 57.dp // (960 - 5 × 150 - 4 × 24) / 2 : grille centrée
    val gap = 24.dp
    val tileWidth = 150.dp
    val tileHeight = 90.dp // icônes 5:3 comme sur tvOS
    val rowPitch = 140.dp
    /** Hauteur de l'image du haut avant la première rangée (le dock). */
    val shelfHeight = 348.dp
}

@Composable
fun OsaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color.White,
            onPrimary = Color.Black,
            background = Color.Black,
            surface = Osa.card,
            onSurface = Color.White,
        ),
    ) {
        // Couleur passée par LocalContentColor (et non le style) pour que les Surface puissent la changer au focus
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            ProvideTextStyle(TextStyle(fontFamily = Inter), content)
        }
    }
}
