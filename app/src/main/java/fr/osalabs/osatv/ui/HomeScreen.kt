package fr.osalabs.osatv.ui

import android.view.KeyEvent as NativeKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import fr.osalabs.osatv.HomeItem
import fr.osalabs.osatv.HomeState
import fr.osalabs.osatv.data.TvApp
import fr.osalabs.osatv.data.TvSource
import fr.osalabs.osatv.data.fetchWeather
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest

interface HomeActions {
    fun launch(app: TvApp)
    fun openSource(source: TvSource)
    fun openSettings()
    fun openAppInfo(app: TvApp)
    fun uninstall(app: TvApp)
}

/** Sans touche pressée pendant ce temps, l'accueil laisse place au paysage (économiseur). */
private const val AMBIENT_DELAY_MS = 3 * 60_000L

/** Position de la rangée sélectionnée quand on est descendu dans la grille. */
private val FocusedRowTop = 70.dp

@Composable
fun HomeScreen(state: HomeState, actions: HomeActions) {
    val items = state.items
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    fun requester(key: String) = requesters.getOrPut(key) { FocusRequester() }

    var focusedRow by remember { mutableIntStateOf(0) }
    var menuFor by remember { mutableStateOf<HomeItem?>(null) }
    var ambient by remember { mutableStateOf(false) }
    val keyPresses = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    // Touches dont on a avalé l'appui : on avale aussi le relâchement, sinon l'icône s'ouvrirait
    val swallowedKeyUps = remember { mutableSetOf<Int>() }

    // Défilement piloté à la main, comme tvOS : la rangée du dock laisse voir l'image,
    // les rangées suivantes remontent toutes au même endroit
    val target = if (focusedRow == 0) 0.dp else Osa.shelfHeight + Osa.rowPitch * focusedRow - FocusedRowTop
    val scroll by animateDpAsState(target, tween(380, easing = FastOutSlowInEasing), label = "scroll")
    val gridAlpha by animateFloatAsState(if (ambient) 0f else 1f, tween(900), label = "grid")
    val photoIndex = rememberPhotoIndex()

    LaunchedEffect(state.resetTick, items.isNotEmpty()) {
        if (items.isEmpty()) return@LaunchedEffect
        state.movingKey = null
        ambient = false
        focusedRow = 0
        withFrameNanos { }
        runCatching { requester(items.first().key).requestFocus() }
    }
    // Après un déplacement, l'icône change de rangée : on lui rend le focus
    LaunchedEffect(items, state.movingKey) {
        val moving = state.movingKey ?: return@LaunchedEffect
        withFrameNanos { }
        runCatching { requester(moving).requestFocus() }
    }
    LaunchedEffect(Unit) {
        keyPresses.emit(Unit)
        keyPresses.collectLatest {
            delay(AMBIENT_DELAY_MS)
            ambient = true
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            fetchWeather()?.let { state.weather = it }
            delay(30 * 60_000L)
        }
    }
    LifecycleResumeEffect(Unit) {
        // Retour depuis une app : jamais directement sur l'économiseur
        ambient = false
        keyPresses.tryEmit(Unit)
        onPauseOrDispose { }
    }
    BackHandler(enabled = state.movingKey == null) { state.resetFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                val code = event.nativeKeyEvent.keyCode
                if (event.type == KeyEventType.KeyUp) return@onPreviewKeyEvent swallowedKeyUps.remove(code)
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                keyPresses.tryEmit(Unit)
                if (ambient) {
                    // La première touche ne fait que réveiller l'écran
                    ambient = false
                    swallowedKeyUps += code
                    return@onPreviewKeyEvent true
                }
                if (state.movingKey == null) return@onPreviewKeyEvent false
                when (code) {
                    NativeKeyEvent.KEYCODE_DPAD_LEFT -> state.moveBy(-1)
                    NativeKeyEvent.KEYCODE_DPAD_RIGHT -> state.moveBy(1)
                    NativeKeyEvent.KEYCODE_DPAD_UP -> state.moveBy(-Osa.COLUMNS)
                    NativeKeyEvent.KEYCODE_DPAD_DOWN -> state.moveBy(Osa.COLUMNS)
                    NativeKeyEvent.KEYCODE_DPAD_CENTER, NativeKeyEvent.KEYCODE_ENTER,
                    NativeKeyEvent.KEYCODE_BACK -> state.movingKey = null
                    else -> return@onPreviewKeyEvent false
                }
                swallowedKeyUps += code
                true
            },
    ) {
        Shelf(
            photoIndex,
            sharpness = { if (ambient) 1f else 1f - (scroll / Osa.rowPitch).coerceIn(0f, 1f) },
            kenBurns = ambient,
        )

        Column(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .graphicsLayer {
                    translationY = -scroll.toPx()
                    alpha = gridAlpha
                }
                .padding(top = Osa.shelfHeight),
        ) {
            items.chunked(Osa.COLUMNS).forEachIndexed { row, rowItems ->
                Row(
                    Modifier
                        .padding(horizontal = Osa.side)
                        .height(Osa.rowPitch),
                    horizontalArrangement = Arrangement.spacedBy(Osa.gap),
                ) {
                    rowItems.forEach { item ->
                        key(item.key) {
                            HomeTile(
                                item = item,
                                focusRequester = requester(item.key),
                                jiggle = state.movingKey == item.key,
                                onFocused = { focusedRow = row },
                                onClick = { open(item, actions) },
                                onLongClick = { menuFor = item },
                            )
                        }
                    }
                }
            }
        }

        Clock(
            Modifier
                .align(Alignment.TopEnd)
                .graphicsLayer { alpha = gridAlpha * (1f - (scroll / Osa.rowPitch).coerceIn(0f, 1f)) }
        )
        if (ambient) {
            AmbientClock(state.weather, Modifier.align(Alignment.BottomStart))
        }

        menuFor?.let { item ->
            ItemMenu(
                item = item,
                onDismiss = { menuFor = null },
                onMove = {
                    menuFor = null
                    state.movingKey = item.key
                },
                onInfo = {
                    menuFor = null
                    (item as? HomeItem.App)?.let { actions.openAppInfo(it.app) }
                },
                onUninstall = {
                    menuFor = null
                    (item as? HomeItem.App)?.let { actions.uninstall(it.app) }
                },
            )
        }
    }
}

private fun open(item: HomeItem, actions: HomeActions) {
    when (item) {
        is HomeItem.App -> actions.launch(item.app)
        is HomeItem.Input -> actions.openSource(item.source)
        HomeItem.Settings -> actions.openSettings()
    }
}
