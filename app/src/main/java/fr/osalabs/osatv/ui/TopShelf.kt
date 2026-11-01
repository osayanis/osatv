package fr.osalabs.osatv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.Text
import fr.osalabs.osatv.data.Album
import fr.osalabs.osatv.data.Feature
import fr.osalabs.osatv.data.Images
import fr.osalabs.osatv.data.LoadedImage
import fr.osalabs.osatv.data.ShelfSource
import fr.osalabs.osatv.data.Showcase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TextShadow = Shadow(Color.Black.copy(alpha = 0.55f), blurRadius = 16f)

@Composable
private fun Eyebrow(text: String) {
    Text(
        text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 3.sp,
        color = Color.White.copy(alpha = 0.75f),
        style = LocalTextStyle.current.merge(TextStyle(shadow = TextShadow)),
    )
}

/** Carrousel de séries en plein écran, qui défile tout seul : le « Top Shelf » des apps vidéo. */
@Composable
fun SeriesShelf(source: ShelfSource.Series, onReady: () -> Unit) {
    val features by produceState(emptyList<Feature>(), source) { value = Showcase.series(source) }
    var page by remember(source) { mutableIntStateOf(0) }
    LaunchedEffect(features) {
        while (features.size > 1) {
            delay(8_000)
            page = (page + 1) % features.size
        }
    }
    val feature = features.getOrNull(page)
    // L'image est téléchargée avant le fondu ; la suivante est préparée pendant qu'on regarde celle-ci
    val shown by produceState<Pair<Feature, LoadedImage>?>(null, feature) {
        val current = feature ?: return@produceState
        Images.load(current.imageUrl)?.let {
            value = current to it
            onReady()
        }
        launch { features.getOrNull((page + 1) % features.size)?.let { Images.load(it.imageUrl) } }
    }

    Box(Modifier.fillMaxSize()) {
        LightCrossfade(targetState = shown, durationMillis = 900) { pair ->
            if (pair != null) {
                val (show, image) = pair
                Box(Modifier.fillMaxSize()) {
                    Image(image.sharp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    // Dégradé limité à la partie gauche : plein écran, il coûterait un passage de plus sur tout l'écran
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.62f)
                            .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
                    )
                    Column(Modifier.padding(start = Osa.side, top = 92.dp).width(480.dp)) {
                        Eyebrow("${source.brand}  ·  À la une")
                        Spacer(Modifier.height(8.dp))
                        Text(
                            show.title,
                            fontSize = 42.sp,
                            lineHeight = 44.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-1).sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = LocalTextStyle.current.merge(TextStyle(shadow = TextShadow)),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(show.meta, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.85f))
                        show.badge?.let {
                            Spacer(Modifier.height(14.dp))
                            Text(
                                it,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White)
                                    .padding(horizontal = 12.dp, vertical = 5.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                            )
                        }
                    }
                }
            }
        }
        if (features.size > 1 && shown != null) {
            PageDots(count = features.size, current = page, Modifier.padding(start = Osa.side, top = 298.dp))
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            val alpha by animateFloatAsState(if (i == current) 1f else 0.35f, label = "dot")
            Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White.copy(alpha = alpha)))
        }
    }
}

/** Les albums du moment (classement Deezer) : le « Top Shelf » des apps de musique. */
@Composable
fun AlbumsShelf(onReady: () -> Unit) {
    val albums by produceState(emptyList<Album>()) { value = Showcase.topAlbums().take(5) }
    val covers by produceState(emptyList<Pair<Album, LoadedImage>>(), albums) {
        val loaded = ArrayList<Pair<Album, LoadedImage>>()
        for (album in albums) {
            Images.load(album.coverUrl, maxWidth = 400)?.let { loaded += album to it }
            value = loaded.toList()
        }
    }
    if (covers.isEmpty()) return
    LaunchedEffect(Unit) { onReady() }

    Box(Modifier.fillMaxSize()) {
        // Fond : la première pochette, floutée et assombrie
        Image(
            covers.first().second.blurred,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.15f), BlendMode.SrcAtop),
        )
        Column(Modifier.padding(start = Osa.side, top = 64.dp)) {
            Eyebrow("Top albums  ·  En ce moment")
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(Osa.gap)) {
                covers.forEach { (album, cover) ->
                    Column(Modifier.width(Osa.tileWidth)) {
                        Image(
                            cover.sharp,
                            album.title,
                            Modifier
                                .size(Osa.tileWidth)
                                .shadow(18.dp, RoundedCornerShape(10.dp))
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(album.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(album.artist, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
