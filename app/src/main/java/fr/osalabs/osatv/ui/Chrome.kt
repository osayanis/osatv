package fr.osalabs.osatv.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import fr.osalabs.osatv.data.Weather
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Ce que le grand bandeau du haut met en avant : l'élément sélectionné. */
data class HeroItem(
    val eyebrow: String,
    val title: String,
    val hint: String,
    val backdrop: ImageBitmap?,
    val icon: ImageBitmap?,
)

/** Fond plein écran : l'image de l'élément sélectionné, très floue et assombrie. */
@Composable
fun Backdrop(item: HeroItem?) {
    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = item?.backdrop, animationSpec = tween(450), label = "backdrop") { image ->
            if (image != null) {
                Image(
                    image,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 0.7f },
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.1f),
                        0.45f to Color.Black.copy(alpha = 0.5f),
                        0.8f to Color.Black,
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.55f), 0.6f to Color.Transparent))
        )
    }
}

@Composable
fun Hero(item: HeroItem?) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(248.dp)
            .padding(start = Osa.side, end = 260.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            item?.icon?.let {
                Image(it, null, Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                (item?.eyebrow ?: "OsaTV").uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
                color = Osa.muted,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item?.title ?: "Bienvenue",
            fontSize = 44.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-1).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(item?.hint ?: "", fontSize = 13.sp, color = Osa.muted, maxLines = 1)
    }
}

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH)
private val DateFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)

/** Heure, date et météo en haut à droite. */
@Composable
fun StatusBar(weather: Weather?, modifier: Modifier = Modifier) {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    Column(modifier.padding(top = 26.dp, end = Osa.side), horizontalAlignment = Alignment.End) {
        Text(now.format(TimeFormat), fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp)
        val date = now.format(DateFormat).replaceFirstChar { it.uppercase() }
        val line = if (weather != null) "$date  ·  ${weather.symbol} ${weather.temperature}°  ${weather.city}" else date
        Text(line, fontSize = 13.sp, color = Osa.muted)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = Osa.side, top = 18.dp, bottom = 2.dp),
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White.copy(alpha = 0.85f),
    )
}
