package fr.osalabs.osatv.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.ui.focus.FocusDirection
import fr.osalabs.osatv.R

/** Sons de l'interface (synthétisés, voir README) et dernier sens de navigation. */
object Feedback {
    private var pool: SoundPool? = null
    private var tick = 0
    private var select = 0
    private var bump = 0
    private var chime = 0
    private var lastTick = 0L
    private val loaded = HashSet<Int>()
    private val pending = HashSet<Int>()

    /** Sens de la dernière flèche : l'icône qui reçoit le focus s'incline depuis ce côté. */
    var lastDirection: FocusDirection? = null

    fun init(context: Context) {
        if (pool != null) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attributes).build().apply {
            // Le carillon de démarrage est demandé avant la fin du chargement des sons
            setOnLoadCompleteListener { pool, id, status ->
                if (status != 0) return@setOnLoadCompleteListener
                loaded += id
                if (pending.remove(id)) pool.play(id, 0.55f, 0.55f, 1, 0, 1f)
            }
            tick = load(context, R.raw.tick, 1)
            select = load(context, R.raw.select, 1)
            bump = load(context, R.raw.bump, 1)
            chime = load(context, R.raw.chime, 1)
        }
    }

    fun tick() {
        // Flèche maintenue : pas plus d'un clic toutes les 45 ms
        val now = System.currentTimeMillis()
        if (now - lastTick < 45) return
        lastTick = now
        play(tick, 0.35f)
    }

    fun select() = play(select, 0.45f)
    fun bump() = play(bump, 0.5f)
    fun chime() {
        if (chime in loaded) play(chime, 0.55f) else pending += chime
    }

    private fun play(sound: Int, volume: Float) {
        pool?.play(sound, volume, volume, 1, 0, 1f)
    }
}
