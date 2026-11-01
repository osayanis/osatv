package fr.osalabs.osatv.ui

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.Text
import fr.osalabs.osatv.R
import fr.osalabs.osatv.data.Weather
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Paysages fournis avec l'app (photos Unsplash via picsum.photos, voir README). */
private val PHOTOS = intArrayOf(
    R.drawable.shelf_01, R.drawable.shelf_02, R.drawable.shelf_03, R.drawable.shelf_04, R.drawable.shelf_05,
    R.drawable.shelf_06, R.drawable.shelf_07, R.drawable.shelf_08, R.drawable.shelf_09, R.drawable.shelf_10,
)
const val PHOTO_COUNT = 10

private class Photo(val sharp: ImageBitmap, val blurred: ImageBitmap)

private suspend fun loadPhoto(resources: Resources, index: Int): Photo = withContext(Dispatchers.IO) {
    val bitmap = BitmapFactory.decodeResource(resources, PHOTOS[index])
    // Flou sans Modifier.blur (Android 12+) : on réduit l'image à 32 × 18 puis on l'agrandit avec lissage
    val tiny = Bitmap.createScaledBitmap(bitmap, 32, 18, true)
    val blurred = Bitmap.createScaledBitmap(tiny, 320, 180, true)
    Photo(bitmap.asImageBitmap(), blurred.asImageBitmap())
}

/**
 * L'image du haut, en plein écran. [sharpness] passe de 1 (tout en haut) à 0 (dans la grille) :
 * l'image devient alors floue et sombre. [kenBurns] active un zoom très lent (économiseur).
 *
 * Cette télé a une puce graphique modeste : une couche à alpha 0 n'est pas dessinée,
 * d'où des alphas complémentaires plutôt que des couches empilées en permanence.
 */
@Composable
fun Shelf(index: Int, sharpness: () -> Float, kenBurns: Boolean) {
    val resources = LocalContext.current.resources
    // La photo est décodée avant le fondu, sinon on verrait un écran noir entre deux images
    val current by produceState<Photo?>(null, index) { value = loadPhoto(resources, index) }

    Crossfade(targetState = current, animationSpec = tween(1600), label = "shelf") { photo ->
        val zoom = remember { Animatable(1f) }
        LaunchedEffect(photo, kenBurns) {
            if (kenBurns) zoom.animateTo(1.12f, tween(30_000, easing = LinearEasing))
        }
        if (photo != null) {
            Box(Modifier.fillMaxSize()) {
                Image(
                    photo.blurred,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - sharpness() },
                    contentScale = ContentScale.Crop,
                    // Assombri au dessin plutôt que par une couche noire de plus
                    colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.45f), BlendMode.SrcAtop),
                )
                Image(
                    photo.sharp,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = sharpness()
                            scaleX = zoom.value
                            scaleY = zoom.value
                        },
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
    // Voile en haut pour l'heure, en bas pour les noms sous les icônes
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = sharpness() }) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(90.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.3f), Color.Transparent)))
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
        )
    }
}

/** Passe à la photo suivante toutes les [periodMs] millisecondes. */
@Composable
fun rememberPhotoIndex(periodMs: Long = 20_000L): Int {
    val index by produceState((0 until PHOTO_COUNT).random()) {
        while (true) {
            delay(periodMs)
            value = (value + 1) % PHOTO_COUNT
        }
    }
    return index
}

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH)
private val DateFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)
private val SoftShadow = Shadow(Color.Black.copy(alpha = 0.5f), blurRadius = 12f)

@Composable
private fun rememberNow(): LocalDateTime {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    return now
}

/** Petite heure en haut à droite, comme sur l'économiseur de l'Apple TV. */
@Composable
fun Clock(modifier: Modifier = Modifier) {
    Text(
        rememberNow().format(TimeFormat),
        modifier = modifier.padding(top = 28.dp, end = Osa.side),
        style = LocalTextStyle.current.merge(TextStyle(shadow = SoftShadow)),
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

/** Heure, date et météo en grand pour l'économiseur d'écran. */
@Composable
fun AmbientClock(weather: Weather?, modifier: Modifier = Modifier) {
    val now = rememberNow()
    val style = LocalTextStyle.current.merge(TextStyle(shadow = SoftShadow))
    Column(modifier.padding(start = 56.dp, bottom = 48.dp), horizontalAlignment = Alignment.Start) {
        Text(now.format(TimeFormat), style = style, fontSize = 64.sp, fontWeight = FontWeight.Bold, letterSpacing = (-2).sp)
        val date = now.format(DateFormat).replaceFirstChar { it.uppercase() }
        val line = if (weather != null) "$date   ${weather.symbol} ${weather.temperature}°  ${weather.city}" else date
        Text(line, style = style, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.85f))
    }
}
