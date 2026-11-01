package fr.osalabs.osatv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.osalabs.osatv.data.AppRepository
import fr.osalabs.osatv.data.TvApp
import fr.osalabs.osatv.data.TvSource
import fr.osalabs.osatv.data.Weather
import fr.osalabs.osatv.data.loadSources

class HomeState(private val context: Context) {
    val repository = AppRepository(context)

    var apps by mutableStateOf<List<TvApp>>(emptyList())
        private set
    var favorites by mutableStateOf<List<String>>(emptyList())
        private set
    var sources by mutableStateOf<List<TvSource>>(emptyList())
        private set
    var weather by mutableStateOf<Weather?>(null)

    /** Incrémenté pour ramener le focus sur le premier favori (bouton Home, Retour). */
    var resetTick by mutableIntStateOf(0)
        private set

    val favoriteApps: List<TvApp>
        get() = favorites.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }

    suspend fun reload() {
        val loaded = repository.loadApps()
        apps = loaded
        favorites = repository.savedFavorites() ?: repository.defaultFavorites(loaded).also {
            repository.saveFavorites(it)
        }
        sources = loadSources(context)
    }

    fun resetFocus() {
        resetTick++
    }

    fun isFavorite(app: TvApp) = app.packageName in favorites

    fun toggleFavorite(app: TvApp) {
        update(if (isFavorite(app)) favorites - app.packageName else favorites + app.packageName)
    }

    /** Déplace un favori parmi ceux réellement affichés. */
    fun move(app: TvApp, delta: Int) {
        val visible = favoriteApps.map { it.packageName }.toMutableList()
        val from = visible.indexOf(app.packageName)
        val to = from + delta
        if (from < 0 || to !in visible.indices) return
        visible.add(to, visible.removeAt(from))
        update(visible + favorites.filter { it !in visible })
    }

    private fun update(list: List<String>) {
        favorites = list
        repository.saveFavorites(list)
    }
}
