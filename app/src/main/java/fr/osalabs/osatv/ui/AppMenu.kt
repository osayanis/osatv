package fr.osalabs.osatv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import fr.osalabs.osatv.data.TvApp

/** Menu ouvert par un appui long sur OK. */
@Composable
fun AppMenu(
    app: TvApp,
    isFavorite: Boolean,
    favoriteIndex: Int,
    favoriteCount: Int,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMove: (Int) -> Unit,
    onInfo: () -> Unit,
    onUninstall: () -> Unit,
) {
    val first = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { first.requestFocus() }
        }
        Column(
            Modifier
                .width(340.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Osa.card)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(app.icon, null, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
                Spacer(Modifier.width(12.dp))
                Text(app.label, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            MenuItem("Ouvrir", Modifier.focusRequester(first), onOpen)
            MenuItem(if (isFavorite) "Retirer des favoris" else "Ajouter aux favoris", onClick = onToggleFavorite)
            if (isFavorite && favoriteIndex > 0) MenuItem("Déplacer vers la gauche", onClick = { onMove(-1) })
            if (isFavorite && favoriteIndex in 0 until favoriteCount - 1) {
                MenuItem("Déplacer vers la droite", onClick = { onMove(1) })
            }
            MenuItem("Infos sur l'app", onClick = onInfo)
            MenuItem("Désinstaller", onClick = onUninstall, destructive = true)
        }
    }
}

@Composable
private fun MenuItem(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Osa.faint,
            contentColor = if (destructive) Color(0xFFFF6961) else Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = if (destructive) Color(0xFFD70015) else Color.Black,
        ),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
