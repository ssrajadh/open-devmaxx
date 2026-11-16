package io.github.ssrajadh.prep.data

import io.github.ssrajadh.prep.data.content.CardsFile
import io.github.ssrajadh.prep.data.content.TracksFile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Parses the bundled JSON with the app's own models, so a pipeline change that breaks
 * the schema fails here instead of crashing on the phone.
 */
class ContentAssetsTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val dir = File("src/main/assets/content")

    @Test fun allContentParses() {
        val meta = json.decodeFromString<TracksFile>(File(dir, "tracks.json").readText())
        val topicIds = meta.topics.map { it.id }.toSet()
        val files = File(dir, "cards").listFiles { f -> f.extension == "json" }.orEmpty()
        assertTrue("no card files", files.isNotEmpty())
        val cards = files.flatMap { json.decodeFromString<CardsFile>(it.readText()).cards }
        assertTrue(cards.isNotEmpty())
        for (c in cards) {
            assertTrue("${c.id} topic", c.topicId in topicIds)
            assertTrue("${c.id} has sources", c.sources.isNotEmpty())
            assertTrue("${c.id} has key points", c.keyPoints.isNotEmpty())
        }
    }
}
