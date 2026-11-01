package fr.osalabs.osatv.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Une image prête à dessiner, avec sa version floue (fond de la grille). */
class LoadedImage(val sharp: ImageBitmap, val blurred: ImageBitmap)

/**
 * Télécharge les images (séries, pochettes), les réduit à la taille de l'écran
 * et les garde sur le disque : au démarrage suivant, tout s'affiche sans réseau.
 */
object Images {
    private lateinit var dir: File
    private val memory = LruCache<String, LoadedImage>(12)
    private val lock = Mutex()

    fun init(context: Context) {
        dir = File(context.cacheDir, "images").apply { mkdirs() }
    }

    suspend fun load(url: String, maxWidth: Int = 1280): LoadedImage? = withContext(Dispatchers.IO) {
        memory.get(url)?.let { return@withContext it }
        lock.withLock {
            memory.get(url)?.let { return@withLock it }
            runCatching {
                val file = File(dir, sha1(url) + ".jpg")
                if (!file.exists()) download(url, file, maxWidth)
                val bitmap = BitmapFactory.decodeFile(file.path) ?: error("image illisible")
                bitmap.prepareToDraw() // envoi à la puce graphique en avance, sans bloquer l'affichage
                LoadedImage(bitmap.asImageBitmap(), blur(bitmap).asImageBitmap()).also { memory.put(url, it) }
            }.onFailure { Log.w("OsaTV", "Image non chargée : $url", it) }.getOrNull()
        }
    }

    private fun download(url: String, target: File, maxWidth: Int) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("User-Agent", "OsaTV")
        val bytes = try {
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
        // Décodage réduit : une image 4K pèserait 33 Mo en mémoire sur une télé qui en a 1,4 Go
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("image illisible")
        val scaled = if (decoded.width > maxWidth) {
            Bitmap.createScaledBitmap(decoded, maxWidth, decoded.height * maxWidth / decoded.width, true)
        } else {
            decoded
        }
        val temp = File(target.path + ".tmp")
        temp.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        temp.renameTo(target)
    }

    /** Flou sans Modifier.blur (Android 12+) : réduction extrême puis agrandissement lissé. */
    fun blur(bitmap: Bitmap): Bitmap {
        val tiny = Bitmap.createScaledBitmap(bitmap, 32, 18, true)
        return Bitmap.createScaledBitmap(tiny, 320, 180, true)
    }

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
