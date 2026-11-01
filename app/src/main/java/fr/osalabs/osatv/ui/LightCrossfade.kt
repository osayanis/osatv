package fr.osalabs.osatv.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Comme [androidx.compose.animation.Crossfade], mais l'opacité est appliquée à chaque dessin
 * ([CompositingStrategy.ModulateAlpha]) au lieu de passer par un tampon plein écran :
 * sur la puce graphique de la télé, c'est ce tampon qui faisait saccader les fondus.
 */
@Composable
fun <T> LightCrossfade(
    targetState: T,
    modifier: Modifier = Modifier,
    durationMillis: Int = 600,
    content: @Composable (T) -> Unit,
) {
    val transition = updateTransition(targetState, label = "fondu")
    val visible = remember { mutableStateListOf(targetState) }
    if (targetState !in visible) visible.add(targetState)
    if (transition.currentState == transition.targetState) {
        visible.removeAll { it != transition.targetState }
    }
    Box(modifier) {
        visible.forEach { state ->
            key(state) {
                val alpha by transition.animateFloat({ tween(durationMillis) }, label = "opacité") { if (it == state) 1f else 0f }
                Box(
                    Modifier.graphicsLayer {
                        this.alpha = alpha
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
                ) {
                    content(state)
                }
            }
        }
    }
}
