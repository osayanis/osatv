package fr.osalabs.osatv.ui

import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.Text
import fr.osalabs.osatv.HomeItem

private val TileShape = RoundedCornerShape(14.dp)
private val LabelShadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 10f)

/**
 * Icône façon tvOS : au focus elle grossit, se soulève avec une ombre portée
 * et un reflet ; le nom n'apparaît que sous l'icône sélectionnée.
 */
@Composable
fun HomeTile(
    item: HomeItem,
    focusRequester: FocusRequester,
    jiggle: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var longPressed by remember { mutableStateOf(false) }
    val lift by animateFloatAsState(
        if (focused) 1f else 0f,
        spring(dampingRatio = 0.6f, stiffness = 420f),
        label = "lift",
    )
    val labelAlpha by animateFloatAsState(if (focused && !jiggle) 1f else 0f, tween(160), label = "label")
    val wobble = if (jiggle) {
        val transition = rememberInfiniteTransition(label = "jiggle")
        transition.animateFloat(-1.6f, 1.6f, infiniteRepeatable(tween(120), RepeatMode.Reverse), label = "angle").value
    } else {
        0f
    }

    Column(Modifier.width(Osa.tileWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(Osa.tileWidth, Osa.tileHeight)
                .focusRequester(focusRequester)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }
                .onKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    val isSelect = native.keyCode == NativeKeyEvent.KEYCODE_DPAD_CENTER ||
                        native.keyCode == NativeKeyEvent.KEYCODE_ENTER ||
                        native.keyCode == NativeKeyEvent.KEYCODE_NUMPAD_ENTER
                    if (!isSelect) return@onKeyEvent false
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            if (native.repeatCount == 0) longPressed = false
                            // Appui long : la télécommande répète la touche tant qu'on la maintient
                            if (!longPressed && (native.isLongPress || native.repeatCount >= 8)) {
                                longPressed = true
                                onLongClick()
                            }
                        }
                        KeyEventType.KeyUp -> {
                            if (!longPressed) onClick()
                            longPressed = false
                        }
                    }
                    true
                }
                .focusable()
                .graphicsLayer {
                    val scale = 1f + 0.17f * lift
                    scaleX = scale
                    scaleY = scale
                    translationY = -4.dp.toPx() * lift
                    rotationZ = wobble
                    shadowElevation = 26.dp.toPx() * lift
                    shape = TileShape
                    clip = true
                },
        ) {
            TileArt(item)
            // Reflet en diagonale, seulement sur l'icône sélectionnée
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = lift }
                    .background(
                        Brush.linearGradient(
                            0f to Color.White.copy(alpha = 0.32f),
                            0.45f to Color.White.copy(alpha = 0.04f),
                            1f to Color.Transparent,
                            start = Offset.Zero,
                            end = Offset.Infinite,
                        )
                    )
            )
        }
        Text(
            text = item.label,
            modifier = Modifier
                // Plus large que l'icône, sans élargir la colonne (et donc l'espacement)
                .requiredWidth(Osa.tileWidth + 40.dp)
                .padding(top = 16.dp)
                .graphicsLayer {
                    alpha = labelAlpha
                    translationY = 6.dp.toPx() * lift
                },
            style = LocalTextStyle.current.merge(TextStyle(shadow = LabelShadow)),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TileArt(item: HomeItem) {
    when (item) {
        is HomeItem.App -> {
            val app = item.app
            if (app.banner != null) {
                Image(app.banner, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                // App sans bannière TV : son icône sur un fond tiré de sa couleur
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(lerp(app.accent, Color.White, 0.12f), lerp(app.accent, Color.Black, 0.45f))
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(app.icon, app.label, Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)))
                }
            }
        }

        is HomeItem.Input -> Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF3A3A3C), Color(0xFF1C1C1E)))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                item.label,
                modifier = Modifier.padding(horizontal = 10.dp),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        HomeItem.Settings -> Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFFE5E5EA), Color(0xFF9A9AA1)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Settings, contentDescription = null, Modifier.size(54.dp), tint = Color(0xFF55555C))
        }
    }
}
