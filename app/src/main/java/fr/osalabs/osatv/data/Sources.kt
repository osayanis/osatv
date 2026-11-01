package fr.osalabs.osatv.data

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.util.Log

/** Une entrée physique de la télé (HDMI, péritel…). */
data class TvSource(val id: String, val label: String)

fun loadSources(context: Context): List<TvSource> = runCatching {
    val manager = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
        ?: return emptyList()
    manager.tvInputList
        // Ports physiques seulement : pas les appareils CEC branchés derrière
        .filter { it.isPassthroughInput && it.parentId == null && !it.isHidden(context) }
        .sortedWith(compareBy({ it.type != TvInputInfo.TYPE_HDMI }, { it.id }))
        .map { input ->
            val label = input.loadCustomLabel(context)?.takeIf { it.isNotBlank() }
                ?: input.loadLabel(context)
            TvSource(input.id, label.toString())
        }
}.onFailure { Log.w("OsaTV", "Entrées illisibles", it) }.getOrDefault(emptyList())

fun openSource(context: Context, source: TvSource) {
    val uri = TvContract.buildChannelUriForPassthroughInput(source.id)
    // Le type explicite est nécessaire : l'app TV de TCL ne s'enregistre que sur ce type MIME
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, TvContract.Channels.CONTENT_ITEM_TYPE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
