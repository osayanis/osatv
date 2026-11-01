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
import fr.osalabs.osatv.HomeItem

/** Menu ouvert par un appui long sur OK, comme le menu contextuel de tvOS. */
@Composable
fun ItemMenu(
    item: HomeItem,
    onDismiss: () -> Unit,
    onMove: () -> Unit,
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
                .width(320.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xF22C2C2E))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.padding(start = 6.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (item is HomeItem.App) {
                    Image(item.app.icon, null, Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)))
                    Spacer(Modifier.width(10.dp))
                }
                Text(item.label, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(6.dp))
            MenuItem("Déplacer", Modifier.focusRequester(first), onMove)
            if (item is HomeItem.App) {
                MenuItem("Infos sur l'app", onClick = onInfo)
                MenuItem("Supprimer l'app", onClick = onUninstall, destructive = true)
            }
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
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = if (destructive) Color(0xFFFF453A) else Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = if (destructive) Color(0xFFD70015) else Color.Black,
        ),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
