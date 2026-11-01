package fr.osalabs.osatv.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Animation de démarrage : le logo apparaît, un reflet traverse « OsaTV » au son du carillon,
 * puis l'écran s'ouvre sur l'accueil. Elle couvre le chargement des apps (~3 s sur cette télé).
 */
@Composable
fun Intro(ready: Boolean, onDone: () -> Unit) {
    val appear = remember { Animatable(0f) }
    val sweep = remember { Animatable(-0.4f) }
    val exit = remember { Animatable(0f) }
    val isReady = rememberUpdatedState(ready)

    LaunchedEffect(Unit) {
        Feedback.chime()
        launch { appear.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
        launch { sweep.animateTo(1.4f, tween(1600, delayMillis = 500, easing = LinearEasing)) }
        delay(1900)
        snapshotFlow { isReady.value }.first { it }
        exit.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        onDone()
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - exit.value }
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.graphicsLayer {
                alpha = appear.value
                val scale = 0.86f + 0.14f * appear.value + 0.25f * exit.value
                scaleX = scale
                scaleY = scale
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Canvas(Modifier.size(84.dp, 60.dp)) {
                val stroke = 5.dp.toPx()
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke - 10.dp.toPx()),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(stroke),
                )
                val cx = size.width / 2
                val cy = (size.height - 10.dp.toPx()) / 2
                val r = 11.dp.toPx()
                drawPath(
                    Path().apply {
                        moveTo(cx - r * 0.7f, cy - r)
                        lineTo(cx + r, cy)
                        lineTo(cx - r * 0.7f, cy + r)
                        close()
                    },
                    Color.White,
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.6f),
                    topLeft = Offset(size.width * 0.32f, size.height - 4.dp.toPx()),
                    size = Size(size.width * 0.36f, 4.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
            Spacer(Modifier.height(18.dp))
            // Reflet qui traverse le texte
            val shine = Brush.linearGradient(
                0f to Color(0xFFB8B8C0),
                0.42f to Color(0xFFB8B8C0),
                0.5f to Color.White,
                0.58f to Color(0xFFB8B8C0),
                1f to Color(0xFFB8B8C0),
                start = Offset(600f * sweep.value - 300f, 0f),
                end = Offset(600f * sweep.value + 300f, 120f),
            )
            Text(
                "OsaTV",
                style = LocalTextStyle.current.merge(TextStyle(brush = shine)),
                fontSize = 52.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1.5).sp,
            )
        }
    }
}
