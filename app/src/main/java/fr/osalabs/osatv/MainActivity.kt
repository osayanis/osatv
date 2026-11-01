package fr.osalabs.osatv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import fr.osalabs.osatv.data.Cleanup
import fr.osalabs.osatv.data.Images
import fr.osalabs.osatv.data.Showcase
import fr.osalabs.osatv.data.TvApp
import fr.osalabs.osatv.data.TvSource
import fr.osalabs.osatv.data.openSource as openTvSource
import fr.osalabs.osatv.ui.Feedback
import fr.osalabs.osatv.ui.HomeActions
import fr.osalabs.osatv.ui.HomeScreen
import fr.osalabs.osatv.ui.OsaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), HomeActions {
    private lateinit var state: HomeState
    private var userActed = false
    private var reclaimed = false

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            lifecycleScope.launch { state.reload() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Images.init(applicationContext)
        Showcase.init(applicationContext)
        Feedback.init(applicationContext)
        state = HomeState(applicationContext)
        setContent {
            OsaTheme {
                HomeScreen(state, this)
            }
        }
        lifecycleScope.launch { state.reload() }
        // Au cas où le démarrage a été manqué (première installation, récepteur coupé)
        lifecycleScope.launch { Cleanup.runOncePerBoot(applicationContext) }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Bouton Home pressé alors qu'on est déjà sur l'accueil
        state.resetFocus()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        userActed = true
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        super.onPause()
        // Au démarrage à froid, la TCL ouvre d'office la dernière source (TV, HDMI, AV) par-dessus l'accueil,
        // sans réglage pour l'en empêcher : si personne n'a encore touché la télécommande, on reprend la main.
        // Dès la pause, la source n'a pas encore eu le temps de s'afficher (elle met ~2 s).
        if (!userActed && !reclaimed && SystemClock.elapsedRealtime() < COLD_BOOT_WINDOW_MS) {
            reclaimed = true
            Handler(Looper.getMainLooper()).postDelayed({
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }, 500)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }

    override fun launch(app: TvApp) = attempt { state.repository.launch(app) }

    override fun openSource(source: TvSource) = attempt { openTvSource(this, source) }

    override fun openSettings() = attempt {
        startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun openAppInfo(app: TvApp) = attempt {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    override fun uninstall(app: TvApp) = attempt {
        startActivity(
            Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private companion object {
        /** La TCL bascule sur la source ~2 s après l'accueil, lui-même affiché ~1 min après la mise sous tension. */
        const val COLD_BOOT_WINDOW_MS = 180_000L
    }

    private fun attempt(block: () -> Unit) {
        runCatching(block).onFailure {
            Toast.makeText(this, "Impossible d'ouvrir ça sur cette télé", Toast.LENGTH_SHORT).show()
        }
    }
}
