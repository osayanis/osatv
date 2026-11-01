package fr.osalabs.osatv.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Une série mise en avant en haut de l'écran (données TVmaze, licence CC BY-SA). */
data class Feature(
    val title: String,
    val meta: String,
    val badge: String?,
    val imageUrl: String,
)

/** Un album du classement Deezer. */
data class Album(val title: String, val artist: String, val coverUrl: String)

/** Ce que l'image du haut montre quand une app du dock est sélectionnée, comme le « Top Shelf » de tvOS. */
sealed interface ShelfSource {
    data class Series(val brand: String, val shows: List<Pair<Int, String>>) : ShelfSource
    data object TopAlbums : ShelfSource
}

/** Sélection de séries par plateforme : identifiant TVmaze et titre français. */
private val NETFLIX = ShelfSource.Series(
    "Netflix",
    listOf(
        2993 to "Stranger Things", 53647 to "Mercredi", 43687 to "Squid Game", 55138 to "Arcane",
        28276 to "The Witcher", 50701 to "Lupin", 42966 to "La Chronique des Bridgerton", 3594 to "The Crown",
        17861 to "Dark", 46065 to "One Piece", 2705 to "Narcos", 53277 to "3 Body Problem",
        30386 to "The Umbrella Academy", 305 to "Black Mirror", 56512 to "The Night Agent",
    ),
)
private val PRIME_VIDEO = ShelfSource.Series(
    "Prime Video",
    listOf(
        15299 to "The Boys", 43031 to "Reacher", 33352 to "Les Anneaux de Pouvoir", 49041 to "Fallout",
        37196 to "Invincible", 62998 to "Gen V", 5079 to "Jack Ryan", 28717 to "Good Omens",
        35083 to "La Roue du Temps",
    ),
)
private val DISNEY_PLUS = ShelfSource.Series(
    "Disney+",
    listOf(
        38963 to "The Mandalorian", 41007 to "Loki", 52341 to "Andor", 52343 to "Ahsoka",
        41748 to "WandaVision", 48108 to "Percy Jackson et les Olympiens", 43102 to "Hawkeye",
        63234 to "Daredevil: Born Again", 62271 to "Skeleton Crew",
    ),
)

val SHELF_SOURCES: Map<String, ShelfSource> = mapOf(
    "com.netflix.ninja" to NETFLIX,
    "com.amazon.amazonvideo.livingroom" to PRIME_VIDEO,
    "com.disney.disneyplus" to DISNEY_PLUS,
    "deezer.android.tv" to ShelfSource.TopAlbums,
    "de.eye_interactive.atvl.applemusic" to ShelfSource.TopAlbums,
    "com.apple.android.music" to ShelfSource.TopAlbums,
    "com.spotify.tv.android" to ShelfSource.TopAlbums,
)

private val GENRES = mapOf(
    "Drama" to "Drame", "Science-Fiction" to "Science-fiction", "Horror" to "Horreur", "Crime" to "Policier",
    "Comedy" to "Comédie", "Adventure" to "Aventure", "Fantasy" to "Fantastique", "Mystery" to "Mystère",
    "Supernatural" to "Surnaturel", "Family" to "Famille", "War" to "Guerre", "History" to "Histoire",
    "Music" to "Musique", "Legal" to "Judiciaire", "Medical" to "Médical", "Espionage" to "Espionnage",
    "Anime" to "Animé", "Thriller" to "Thriller", "Action" to "Action", "Romance" to "Romance",
)
private val DAY = DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH)

object Showcase {
    private lateinit var dir: File
    private val series = HashMap<String, List<Feature>>()
    private var albums: List<Album>? = null
    private var albumsAt = 0L
    private val lock = Mutex()

    fun init(context: Context) {
        dir = File(context.filesDir, "showcase").apply { mkdirs() }
    }

    /** Séries d'une plateforme : depuis le disque si elles ont moins d'un jour, sinon depuis TVmaze. */
    suspend fun series(source: ShelfSource.Series): List<Feature> = lock.withLock {
        series[source.brand]?.let { return@withLock it }
        withContext(Dispatchers.IO) {
            val file = File(dir, source.brand.filter(Char::isLetterOrDigit) + ".json")
            val saved = runCatching { readFeatures(file) }.getOrNull()
            val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < 86_400_000L
            val features = if (saved != null && fresh) {
                saved
            } else {
                val loaded = source.shows.mapNotNull { (id, title) ->
                    runCatching { loadShow(id, title) }
                        .onFailure { Log.w("OsaTV", "Série $id non chargée", it) }
                        .getOrNull()
                        .also { delay(150) } // TVmaze limite à ~20 requêtes / 10 s
                }
                if (loaded.isNotEmpty()) writeFeatures(file, loaded)
                loaded.ifEmpty { saved.orEmpty() }
            }
            // 8 au plus : au-delà, les points du carrousel deviennent illisibles
            features.shuffled().take(8).also { if (it.isNotEmpty()) series[source.brand] = it }
        }
    }

    private fun readFeatures(file: File): List<Feature> =
        JSONArray(file.readText()).objects().map {
            Feature(it.getString("title"), it.getString("meta"), it.optString("badge").ifEmpty { null }, it.getString("image"))
        }

    private fun writeFeatures(file: File, features: List<Feature>) {
        val json = JSONArray()
        features.forEach {
            json.put(JSONObject().put("title", it.title).put("meta", it.meta).put("badge", it.badge ?: "").put("image", it.imageUrl))
        }
        file.writeText(json.toString())
    }

    suspend fun topAlbums(): List<Album> = lock.withLock {
        val cached = albums
        if (cached != null && System.currentTimeMillis() - albumsAt < 3_600_000L) return@withLock cached
        withContext(Dispatchers.IO) {
            runCatching {
                val data = JSONObject(get("https://api.deezer.com/chart/0/albums?limit=12")).getJSONArray("data")
                List(data.length()) { i ->
                    val album = data.getJSONObject(i)
                    Album(album.getString("title"), album.getJSONObject("artist").getString("name"), album.getString("cover_xl"))
                }
            }.onFailure { Log.w("OsaTV", "Classement Deezer indisponible", it) }
                .getOrNull()
                ?.also {
                    albums = it
                    albumsAt = System.currentTimeMillis()
                }
                ?: cached.orEmpty()
        }
    }

    private fun loadShow(id: Int, title: String): Feature? {
        val show = JSONObject(get("https://api.tvmaze.com/shows/$id?embed%5B%5D=images&embed%5B%5D=nextepisode&embed%5B%5D=previousepisode"))
        val embedded = show.getJSONObject("_embedded")
        val image = embedded.getJSONArray("images").objects()
            .filter { it.getString("type") == "background" }
            .map { it.getJSONObject("resolutions").getJSONObject("original") }
            .filter { it.getInt("width") >= 1280 }
            .maxByOrNull { it.getInt("width").coerceAtMost(2560) }
            ?.getString("url")
            ?: return null

        val genres = show.optJSONArray("genres")?.strings().orEmpty().take(2).map { GENRES[it] ?: it }
        val year = show.optString("premiered").take(4).takeIf { it.length == 4 }
        val rating = show.optJSONObject("rating")?.optDouble("average")?.takeIf { !it.isNaN() }
        val meta = (listOf("Série") + genres + listOfNotNull(year, rating?.let { "★ " + "%.1f".format(Locale.FRENCH, it) }))
            .joinToString("  ·  ")

        val today = LocalDate.now()
        val next = embedded.optJSONObject("nextepisode")?.optString("airdate")?.let(::parseDate)
        val previous = embedded.optJSONObject("previousepisode")?.optString("airdate")?.let(::parseDate)
        val badge = when {
            next != null && !next.isBefore(today) -> "Nouvel épisode le ${next.format(DAY)}"
            previous != null && previous.isAfter(today.minusDays(21)) -> "Nouvel épisode disponible"
            else -> null
        }
        return Feature(title, meta, badge, image)
    }

    private fun parseDate(text: String): LocalDate? = runCatching { LocalDate.parse(text) }.getOrNull()

    private fun JSONArray.objects() = List(length()) { getJSONObject(it) }
    private fun JSONArray.strings() = List(length()) { getString(it) }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("User-Agent", "OsaTV")
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
