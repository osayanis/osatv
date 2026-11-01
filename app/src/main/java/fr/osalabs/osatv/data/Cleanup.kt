package fr.osalabs.osatv.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Allègement automatique : désactive les apps système inutiles à chaque démarrage de la télé,
 * via [LocalAdb]. Nécessaire parce que la TCL réactive certaines apps (Chromecast) à chaque redémarrage.
 * Même liste que scripts/alleger.sh.
 */
object Cleanup {
    val PACKAGES = listOf(
        "com.tcl.bi",
        "com.tcl.tvweishi",
        "com.tcl.esticker",
        "com.tcl.appmarket2",
        "com.tcl.copydatatotv",
        "com.google.android.videos",
        "com.google.android.music",
        "com.google.android.play.games",
        "com.google.android.tvrecommendations",
        "com.google.android.leanbacklauncher",
        "com.google.android.leanbacklauncher.partnercustomizer",
        "com.google.android.leanbacklauncher.partnerwidget",
        "com.google.android.leanbacklauncher.recommendations",
        "com.google.android.backdrop",
        "com.google.android.feedback",
        "com.google.android.tv.bugreportsender",
        "com.android.gallery3d",
        "com.android.printspooler",
        "com.android.wallpaper.livepicker",
        "com.google.android.apps.mediashell",
    )

    private const val TAG = "OsaTV"
    private val lock = Mutex()

    /** Une seule fois par démarrage de la télé ; réessaie tant qu'adb n'est pas prêt. */
    suspend fun runOncePerBoot(context: Context) = withContext(Dispatchers.IO) {
        lock.withLock {
            val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
            val prefs = context.getSharedPreferences("cleanup", Context.MODE_PRIVATE)
            if (boot != -1 && prefs.getInt("boot", -2) == boot) return@withLock
            val adb = LocalAdb(LocalAdb.keys(context))
            repeat(4) { attempt ->
                val done = runCatching { clean(adb) }
                    .onFailure { Log.w(TAG, "Allègement impossible (essai ${attempt + 1})", it) }
                    .isSuccess
                if (done) {
                    prefs.edit().putInt("boot", boot).apply()
                    return@withLock
                }
                delay(10_000)
            }
        }
    }

    private fun clean(adb: LocalAdb) {
        // Un seul appel pour savoir quoi faire : chaque commande pm prend près d'une seconde sur cette télé
        val enabled = adb.shell("pm list packages -e").lineSequence()
            .map { it.trim().removePrefix("package:") }
            .toSet()
        val todo = PACKAGES.filter { it in enabled }
        if (todo.isEmpty()) return
        // Une app désactivée peut rester en mémoire : on l'arrête aussi
        adb.shell(todo.joinToString("; ") { "pm disable-user --user 0 $it; am force-stop $it" })
        Log.i(TAG, "Allègement : ${todo.joinToString()} désactivé(s)")
    }
}

/** Lance l'allègement dès la fin du démarrage, même si la télé s'ouvre sur une source et pas sur OsaTV. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val finished = AtomicBoolean(false)
        val finish = { if (finished.compareAndSet(false, true)) pending.finish() }
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                Cleanup.runOncePerBoot(context.applicationContext)
            } finally {
                finish()
            }
        }
        // Android coupe un récepteur au bout de 60 s : on rend la main avant, le travail continue
        scope.launch {
            delay(45_000)
            finish()
        }
    }
}
