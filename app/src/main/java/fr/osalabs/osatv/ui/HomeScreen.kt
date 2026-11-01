package fr.osalabs.osatv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import fr.osalabs.osatv.HomeState
import fr.osalabs.osatv.data.TvApp
import fr.osalabs.osatv.data.TvSource
import fr.osalabs.osatv.data.fetchWeather
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

interface HomeActions {
    fun launch(app: TvApp)
    fun openSource(source: TvSource)
    fun openSettings()
    fun openAppInfo(app: TvApp)
    fun uninstall(app: TvApp)
}

private const val HINT_APP = "OK pour ouvrir  ·  maintiens OK pour les options"

@Composable
fun HomeScreen(state: HomeState, actions: HomeActions) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val firstFavorite = remember { FocusRequester() }
    var hero by remember { mutableStateOf<HeroItem?>(null) }
    var menuFor by remember { mutableStateOf<TvApp?>(null) }

    val favoriteApps = state.favoriteApps
    val hasFavorites = favoriteApps.isNotEmpty()

    // Au démarrage, au bouton Home et au bouton Retour : retour en haut, sur le 1er favori
    LaunchedEffect(state.resetTick, hasFavorites) {
        if (!hasFavorites) return@LaunchedEffect
        listState.scrollToItem(0)
        withFrameNanos { }
        runCatching { firstFavorite.requestFocus() }
    }
    BackHandler { state.resetFocus() }

    LaunchedEffect(Unit) {
        while (true) {
            fetchWeather()?.let { state.weather = it }
            delay(30 * 60_000L)
        }
    }

    fun focusApp(app: TvApp) {
        hero = HeroItem(
            eyebrow = if (state.isFavorite(app)) "Favori" else "Application",
            title = app.label,
            hint = HINT_APP,
            backdrop = app.backdrop,
            icon = app.icon,
        )
    }

    fun scrollToTop() {
        if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
            scope.launch { listState.animateScrollToItem(0) }
        }
    }

    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val topShade by animateFloatAsState(if (scrolled) 1f else 0f, label = "shade")

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Backdrop(hero)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 72.dp),
        ) {
            item(key = "hero") { Hero(hero) }

            item(key = "dock") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Osa.side, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(Osa.gap),
                ) {
                    itemsIndexed(favoriteApps, key = { _, app -> app.packageName }) { index, app ->
                        AppTile(
                            app = app,
                            modifier = if (index == 0) Modifier.focusRequester(firstFavorite) else Modifier,
                            onFocused = {
                                focusApp(app)
                                scrollToTop()
                            },
                            onClick = { actions.launch(app) },
                            onLongClick = { menuFor = app },
                        )
                    }
                }
            }

            item(key = "sources-title") { SectionTitle("Entrées et réglages") }
            item(key = "sources") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Osa.side, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(Osa.gap),
                ) {
                    items(state.sources, key = { it.id }) { source ->
                        SourceTile(
                            label = source.label,
                            onFocused = {
                                hero = HeroItem("Entrée", source.label, "OK pour basculer sur cette entrée", null, null)
                            },
                            onClick = { actions.openSource(source) },
                        )
                    }
                    item(key = "settings") {
                        SettingsTile(
                            onFocused = {
                                hero = HeroItem("Système", "Réglages", "Wi-Fi, image, son, comptes…", null, null)
                            },
                            onClick = actions::openSettings,
                        )
                    }
                }
            }

            item(key = "all-title") { SectionTitle("Toutes les apps") }
            items(state.apps.chunked(Osa.COLUMNS), key = { row -> "row-" + row.first().packageName }) { row ->
                Row(
                    Modifier.padding(horizontal = Osa.side, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(Osa.gap),
                ) {
                    row.forEach { app ->
                        AppTile(
                            app = app,
                            onFocused = { focusApp(app) },
                            onClick = { actions.launch(app) },
                            onLongClick = { menuFor = app },
                        )
                    }
                }
            }
        }

        // Voile en haut quand on descend dans la grille, pour garder l'heure lisible
        Box(
            Modifier
                .fillMaxWidth()
                .height(110.dp)
                .graphicsLayer { alpha = topShade }
                .background(Brush.verticalGradient(listOf(Color.Black, Color.Transparent)))
        )
        StatusBar(state.weather, Modifier.align(Alignment.TopEnd))

        menuFor?.let { app ->
            val index = favoriteApps.indexOf(app)
            AppMenu(
                app = app,
                isFavorite = state.isFavorite(app),
                favoriteIndex = index,
                favoriteCount = favoriteApps.size,
                onDismiss = { menuFor = null },
                onOpen = {
                    menuFor = null
                    actions.launch(app)
                },
                onToggleFavorite = {
                    menuFor = null
                    state.toggleFavorite(app)
                },
                onMove = { delta ->
                    menuFor = null
                    state.move(app, delta)
                },
                onInfo = {
                    menuFor = null
                    actions.openAppInfo(app)
                },
                onUninstall = {
                    menuFor = null
                    actions.uninstall(app)
                },
            )
        }
    }
}
