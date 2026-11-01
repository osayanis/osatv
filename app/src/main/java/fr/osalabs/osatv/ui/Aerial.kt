package fr.osalabs.osatv.ui

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * « Earth Views: 4K Extended Cut » (NASA, 2021, domaine public) : 52 minutes de vues
 * de la Terre filmées depuis la Station spatiale internationale. Version 720p (~2,2 Mb/s).
 */
private const val AERIAL_URL = "https://images-assets.nasa.gov/video/" +
    "jsc2021m000138_4K_Earth_Views_Extended_Cut_for_Earth_Day_%202021_210422-4KMP4/" +
    "jsc2021m000138_4K_Earth_Views_Extended_Cut_for_Earth_Day_%202021_210422-4KMP4~medium.mp4"
private const val AERIAL_LENGTH_S = 3100

/**
 * Vidéo plein écran dans une SurfaceView : elle passe par le plan vidéo matériel de la télé,
 * donc elle ne coûte presque rien à la puce graphique, contrairement à une animation Compose.
 */
@Composable
fun AerialVideo(play: Boolean, onRendering: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val callback = rememberUpdatedState(onRendering)
    val player = remember { AerialPlayer { callback.value(it) } }
    SideEffect { player.wanted = play }
    AndroidView(
        factory = { context -> SurfaceView(context).apply { holder.addCallback(player) } },
        modifier = modifier,
    )
}

private class AerialPlayer(private val onRendering: (Boolean) -> Unit) : SurfaceHolder.Callback {
    private var player: MediaPlayer? = null
    private var prepared = false

    var wanted = false
        set(value) {
            field = value
            sync()
        }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val media = MediaPlayer()
        player = media
        runCatching {
            media.setDisplay(holder)
            media.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            media.setVolume(0f, 0f)
            media.isLooping = true
            media.setOnPreparedListener {
                prepared = true
                // Chaque retour sur l'accueil reprend à un endroit différent du film
                it.seekTo((0 until AERIAL_LENGTH_S).random() * 1000L, MediaPlayer.SEEK_CLOSEST_SYNC)
                sync()
            }
            media.setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) onRendering(true)
                false
            }
            media.setOnErrorListener { _, what, extra ->
                Log.w("OsaTV", "Vidéo de l'espace indisponible ($what/$extra)")
                onRendering(false)
                true
            }
            media.setDataSource(AERIAL_URL)
            media.prepareAsync()
        }.onFailure { Log.w("OsaTV", "Vidéo de l'espace impossible", it) }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        player?.release()
        player = null
        prepared = false
        onRendering(false)
    }

    private fun sync() {
        val media = player ?: return
        if (!prepared) return
        runCatching {
            if (wanted && !media.isPlaying) media.start()
            if (!wanted && media.isPlaying) media.pause()
        }
    }
}
