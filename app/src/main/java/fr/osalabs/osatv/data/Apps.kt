package fr.osalabs.osatv.data

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

data class TvApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val banner: ImageBitmap?,
    val icon: ImageBitmap,
    val accent: Color,
)

/** Apps placées en tête (le dock) au premier lancement, si elles sont installées. */
val PREFERRED_APPS = listOf(
    "com.netflix.ninja",
    "com.google.android.youtube.tv",
    "com.amazon.amazonvideo.livingroom",
    "com.disney.disneyplus",
    "com.canal.android.canal",
    "com.spotify.tv.android",
    "de.eye_interactive.atvl.applemusic",
    "tv.molotov.app",
    "fr.francetv.pluzz",
    "com.plexapp.android",
    "deezer.android.tv",
    "tv.twitch.android.app",
    "com.tiktok.tv",
    "tv.wuaki.apptv",
    "com.tcl.tv",
)

class AppRepository(private val context: Context) {
    private val pm: PackageManager = context.packageManager
    private val prefs = context.getSharedPreferences("osatv", Context.MODE_PRIVATE)

    suspend fun loadApps(): List<TvApp> = withContext(Dispatchers.IO) {
        val seen = HashSet<String>()
        val activities = ArrayList<ActivityInfo>()
        // Les apps TV d'abord : leur bannière 16:9 est prioritaire sur l'icône mobile
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (info in pm.queryIntentActivities(query, 0)) {
                val activity = info.activityInfo
                if (activity.packageName != context.packageName && seen.add(activity.packageName)) {
                    activities += activity
                }
            }
        }
        // Chargement des images en parallèle : en série, ça prend ~4 s sur cette télé
        val apps = activities.map { activity ->
            async {
                runCatching { load(activity) }
                    .onFailure { Log.w(TAG, "App ignorée : ${activity.packageName}", it) }
                    .getOrNull()
            }
        }.awaitAll().filterNotNull()
        Log.i(TAG, "${apps.size} apps chargées")
        apps.sortedBy { it.label.lowercase() }
    }

    private fun load(activity: ActivityInfo): TvApp {
        val icon = activity.loadIcon(pm).toBitmap(160, 160)
        val banner = activity.loadBanner(pm)?.toBannerBitmap()
        val accent = Bitmap.createScaledBitmap(banner ?: icon, 1, 1, true).getPixel(0, 0)
        return TvApp(
            packageName = activity.packageName,
            activityName = activity.name,
            label = activity.loadLabel(pm).toString(),
            banner = banner?.asImageBitmap(),
            icon = icon.asImageBitmap(),
            accent = Color(accent),
        )
    }

    private fun Drawable.toBannerBitmap(): Bitmap {
        val width = intrinsicWidth.takeIf { it > 0 }?.coerceAtMost(480) ?: 320
        return toBitmap(width, width * 9 / 16)
    }

    fun launch(app: TvApp) {
        val intent = pm.getLeanbackLaunchIntentForPackage(app.packageName)
            ?: pm.getLaunchIntentForPackage(app.packageName)
            ?: Intent(Intent.ACTION_MAIN).setClassName(app.packageName, app.activityName)
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Ordre des icônes de l'accueil (clés de [fr.osalabs.osatv.HomeItem]). */
    fun savedOrder(): List<String>? =
        prefs.getString(KEY_ORDER, null)?.split('|')?.filter { it.isNotEmpty() }

    fun saveOrder(keys: List<String>) {
        prefs.edit().putString(KEY_ORDER, keys.joinToString("|")).apply()
    }

    private companion object {
        const val KEY_ORDER = "order"
        const val TAG = "OsaTV"
    }
}
