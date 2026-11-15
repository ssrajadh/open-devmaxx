package io.github.ssrajadh.prep.data.content

import android.content.res.AssetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Loads the bundled JSON from assets/content. Assets are files packed inside the APK;
 * AssetManager reads them like a tiny read-only file system.
 */
class ContentRepository(private val assets: AssetManager) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var cached: Content? = null

    suspend fun content(): Content = mutex.withLock {
        cached ?: withContext(Dispatchers.IO) { load() }.also { cached = it }
    }

    private fun load(): Content {
        val meta = json.decodeFromString<TracksFile>(read("content/tracks.json"))
        val cardFiles = assets.list("content/cards").orEmpty().filter { it.endsWith(".json") }.sorted()
        val cards = cardFiles.flatMap { json.decodeFromString<CardsFile>(read("content/cards/$it")).cards }
        // Only keep cards that passed (or partially passed) the checker. Unchecked cards
        // are hidden so unverified content never reaches review.
        val usable = cards.filter { it.verified.status == "verified" || it.verified.status == "partial" }
        return Content(meta.tracks, meta.topics, usable)
    }

    private fun read(path: String) = assets.open(path).bufferedReader().use { it.readText() }
}
