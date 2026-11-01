package fr.osalabs.osatv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import fr.osalabs.osatv.data.TvApp
import fr.osalabs.osatv.data.TvSource
import fr.osalabs.osatv.data.openSource as openTvSource
import fr.osalabs.osatv.ui.HomeActions
import fr.osalabs.osatv.ui.HomeScreen
import fr.osalabs.osatv.ui.OsaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), HomeActions {
    private lateinit var state: HomeState

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            lifecycleScope.launch { state.reload() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = HomeState(applicationContext)
        setContent {
            OsaTheme {
                HomeScreen(state, this)
            }
        }
        lifecycleScope.launch { state.reload() }

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

    private fun attempt(block: () -> Unit) {
        runCatching(block).onFailure {
            Toast.makeText(this, "Impossible d'ouvrir ça sur cette télé", Toast.LENGTH_SHORT).show()
        }
    }
}
