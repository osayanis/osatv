package fr.osalabs.osatv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.osalabs.osatv.data.AppRepository
import fr.osalabs.osatv.data.PREFERRED_APPS
import fr.osalabs.osatv.data.TvApp
import fr.osalabs.osatv.data.TvSource
import fr.osalabs.osatv.data.Weather
import fr.osalabs.osatv.data.loadSources
import fr.osalabs.osatv.ui.Osa

/** Une icône de l'accueil : une app, une entrée HDMI ou les réglages. */
sealed interface HomeItem {
    val key: String
    val label: String

    data class App(val app: TvApp) : HomeItem {
        override val key = "app:${app.packageName}"
        override val label = app.label
    }

    data class Input(val source: TvSource) : HomeItem {
        override val key = "input:${source.id}"
        override val label = source.label
    }

    data object Settings : HomeItem {
        override val key = "settings"
        override val label = "Réglages"
    }
}

class HomeState(private val context: Context) {
    val repository = AppRepository(context)

    /** Toutes les icônes dans l'ordre choisi ; la première rangée sert de dock. */
    var items by mutableStateOf<List<HomeItem>>(emptyList())
        private set
    var weather by mutableStateOf<Weather?>(null)

    /** Icône en cours de déplacement (mode « tremblement » de tvOS). */
    var movingKey by mutableStateOf<String?>(null)

    /** Incrémenté pour revenir tout en haut, sur la première icône. */
    var resetTick by mutableIntStateOf(0)
        private set

    suspend fun reload() {
        val all = repository.loadApps().map(HomeItem::App) +
            loadSources(context).map(HomeItem::Input) +
            HomeItem.Settings
        val byKey = all.associateBy { it.key }
        val order = repository.savedOrder() ?: defaultOrder(all)
        // Les apps installées depuis la dernière fois s'ajoutent à la fin, comme sur tvOS
        items = order.mapNotNull(byKey::get) + all.filter { it.key !in order }
        repository.saveOrder(items.map { it.key })
    }

    private fun defaultOrder(all: List<HomeItem>): List<String> {
        val keys = all.map { it.key }
        val preferred = PREFERRED_APPS.map { "app:$it" }.filter { it in keys }
        val dock = preferred.take(Osa.COLUMNS)
        val system = all.filter { it !is HomeItem.App }.map { it.key }
        val head = dock + system + preferred.drop(Osa.COLUMNS)
        return head + keys.filter { it !in head }
    }

    fun resetFocus() {
        resetTick++
    }

    fun moveBy(delta: Int) {
        val key = movingKey ?: return
        val list = items.toMutableList()
        val from = list.indexOfFirst { it.key == key }
        if (from < 0) return
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (to == from) return
        list.add(to, list.removeAt(from))
        items = list
        repository.saveOrder(list.map { it.key })
    }
}
