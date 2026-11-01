package fr.osalabs.osatv.ui

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
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
import kotlinx.coroutines.launch

private val TileShape = RoundedCornerShape(14.dp)
private val ShadowPad = 22.dp
private var shadowBitmap: ImageBitmap? = null

/** Ombre floue dessinée une seule fois, en logiciel (BlurMaskFilter), puis réutilisée par toutes les icônes. */
@Composable
private fun tileShadow(): ImageBitmap {
    shadowBitmap?.let { return it }
    val density = LocalDensity.current
    return with(density) {
        val pad = ShadowPad.toPx()
        val width = (Osa.tileWidth.toPx() + pad * 2).toInt()
        val height = (Osa.tileHeight.toPx() + pad * 2).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(170, 0, 0, 0)
            maskFilter = BlurMaskFilter(pad * 0.7f, BlurMaskFilter.Blur.NORMAL)
        }
        val radius = 14.dp.toPx()
        Canvas(bitmap).drawRoundRect(RectF(pad, pad, width - pad, height - pad), radius, radius, paint)
        bitmap.asImageBitmap().also { shadowBitmap = it }
    }
}
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
    // Inclinaison à l'arrivée du focus et rebond quand on bute sur le bord de la grille
    val tilt = remember { Animatable(0f) }
    val bounce = remember { Animatable(0f) }
    var tiltAxisX by remember { mutableStateOf(false) }
    var bounceAxisX by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val wobble = if (jiggle) {
        val transition = rememberInfiniteTransition(label = "jiggle")
        transition.animateFloat(-1.6f, 1.6f, infiniteRepeatable(tween(120), RepeatMode.Reverse), label = "angle").value
    } else {
        0f
    }

    Column(Modifier.width(Osa.tileWidth), horizontalAlignment = Alignment.CenterHorizontally) {
      Box(Modifier.size(Osa.tileWidth, Osa.tileHeight), contentAlignment = Alignment.Center) {
        // Ombre pré-calculée : une ombre « réelle » (elevation) serait recalculée à chaque image
        // pendant l'inclinaison 3D, ce que la puce graphique de la télé ne suit pas
        Image(
            tileShadow(),
            contentDescription = null,
            modifier = Modifier
                .requiredSize(Osa.tileWidth + ShadowPad * 2, Osa.tileHeight + ShadowPad * 2)
                .graphicsLayer {
                    val scale = 1f + 0.17f * lift
                    scaleX = scale
                    scaleY = scale
                    translationY = 8.dp.toPx() * lift
                    alpha = lift
                },
        )
        Box(
            Modifier
                .size(Osa.tileWidth, Osa.tileHeight)
                .focusRequester(focusRequester)
                .onFocusChanged {
                    if (it.isFocused && !focused) {
                        val direction = Feedback.lastDirection
                        if (direction != null) {
                            tiltAxisX = direction == FocusDirection.Up || direction == FocusDirection.Down
                            val start = when (direction) {
                                FocusDirection.Right, FocusDirection.Down -> 11f
                                else -> -11f
                            }
                            scope.launch {
                                tilt.snapTo(start)
                                tilt.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 420f))
                            }
                        }
                    }
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }
                .onKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    val direction = when (native.keyCode) {
                        NativeKeyEvent.KEYCODE_DPAD_LEFT -> FocusDirection.Left
                        NativeKeyEvent.KEYCODE_DPAD_RIGHT -> FocusDirection.Right
                        NativeKeyEvent.KEYCODE_DPAD_UP -> FocusDirection.Up
                        NativeKeyEvent.KEYCODE_DPAD_DOWN -> FocusDirection.Down
                        else -> null
                    }
                    if (direction != null) {
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent true
                        Feedback.lastDirection = direction
                        if (focusManager.moveFocus(direction)) {
                            Feedback.tick()
                        } else if (native.repeatCount == 0) {
                            Feedback.bump()
                            bounceAxisX = direction == FocusDirection.Left || direction == FocusDirection.Right
                            val sign = if (direction == FocusDirection.Left || direction == FocusDirection.Up) -1f else 1f
                            scope.launch {
                                bounce.animateTo(sign, tween(70))
                                bounce.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 900f))
                            }
                        }
                        return@onKeyEvent true
                    }
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
                            if (!longPressed) {
                                Feedback.select()
                                onClick()
                            }
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
                    val push = 7.dp.toPx() * bounce.value
                    translationX = if (bounceAxisX) push else 0f
                    translationY = -4.dp.toPx() * lift + if (bounceAxisX) 0f else push
                    if (tiltAxisX) rotationX = -tilt.value else rotationY = tilt.value
                    cameraDistance = 9f * density
                    rotationZ = wobble
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
