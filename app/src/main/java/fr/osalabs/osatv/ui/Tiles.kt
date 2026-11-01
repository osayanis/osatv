package fr.osalabs.osatv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import fr.osalabs.osatv.data.TvApp

private val TileShape = RoundedCornerShape(12.dp)

/**
 * Carte 16:9 façon tvOS : elle grossit et s'éclaire au focus,
 * le nom n'apparaît que sous la carte sélectionnée.
 */
@Composable
private fun Tile(
    label: String,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    width: Dp = Osa.tileWidth,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val labelAlpha by animateFloatAsState(if (focused) 1f else 0f, tween(180), label = "label")

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier
                .size(width, width * 9 / 16)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                },
            shape = ClickableSurfaceDefaults.shape(TileShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Osa.card,
                focusedContainerColor = Osa.card,
            ),
            glow = ClickableSurfaceDefaults.glow(
                focusedGlow = Glow(Color.White.copy(alpha = 0.28f), 14.dp),
            ),
        ) {
            content()
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = label,
            modifier = Modifier
                .width(width)
                .graphicsLayer { alpha = labelAlpha },
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun AppTile(
    app: TvApp,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Tile(app.label, onFocused, onClick, modifier, onLongClick) {
        if (app.banner != null) {
            Image(app.banner, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            // App sans bannière TV : son icône sur un fond tiré de sa couleur
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(lerp(app.accent, Color.White, 0.15f), lerp(app.accent, Color.Black, 0.55f))
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Image(app.icon, app.label, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)))
            }
        }
    }
}

@Composable
fun SourceTile(label: String, onFocused: () -> Unit, onClick: () -> Unit) {
    Tile(label, onFocused, onClick) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(Color(0xFF3A3A4E), Color(0xFF15151C)))),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ENTRÉE", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp, color = Osa.muted)
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun SettingsTile(onFocused: () -> Unit, onClick: () -> Unit) {
    Tile("Réglages", onFocused, onClick) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(Color(0xFF8E8E93), Color(0xFF48484A)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Settings, contentDescription = null, Modifier.size(40.dp), tint = Color.White)
        }
    }
}
