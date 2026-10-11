package be.gtfs.delijn

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeLijnSearchClientTest {
    private val server = MockServer()
    private val client = DeLijnSearchClient("search-key", baseUrl = server.url)

    @AfterAll
    fun stop() = server.close()

    @Test
    fun `searchStops builds the request and decodes hits`() {
        server.respondWithFixture("zoekHaltes")
        val result = client.searchStops("gent sint-pieters", GeoCoordinate(51.05, 3.72), startIndex = 2, maxHits = 3)
        assertEquals("/zoek/haltes/gent%20sint-pieters?huidigePositie=51.05%2C3.72&startIndex=2&maxAantalHits=3", server.lastRequest)
        assertEquals("search-key", server.lastApiKey)
        assertEquals(20, result.totalHits)
        assertEquals(StopKey("2", "204911"), result.items.first().key)
    }

    @Test
    fun `swapped coordinates from the Search API are corrected`() {
        server.respondWithFixture("zoekHaltes") // API sends latitude 3.70878, longitude 51.03707
        val coordinate = client.searchStops("gent").items.first().geoCoordinate
        assertEquals(GeoCoordinate(51.03707, 3.70878), coordinate)
        assertEquals("/zoek/haltes/gent", server.lastRequest)
    }

    @Test
    fun `correct coordinates are left alone`() {
        server.body = """{"aantalHits":1,"haltes":[{"entiteitnummer":"2","haltenummer":"1","geoCoordinaat":{"latitude":51.0,"longitude":3.7}}]}"""
        assertEquals(GeoCoordinate(51.0, 3.7), client.searchStops("x").items.single().geoCoordinate)
    }

    @Test
    fun `searchLineDirections builds the request and decodes hits`() {
        server.respondWithFixture("zoekLijnrichtingen")
        val result = client.searchLineDirections("1", maxHits = 3)
        assertEquals("/zoek/lijnrichtingen/1?maxAantalHits=3", server.lastRequest)
        assertEquals(3, result.totalHits)
        val first = result.items.first()
        assertEquals(Direction.OUTBOUND, first.direction)
        assertEquals("#8C2B87", first.backgroundColor)
        assertEquals(LineDirectionKey("1", "1", Direction.OUTBOUND), first.key)
    }

    @Test
    fun `blank query is rejected`() {
        assertFailsWith<IllegalArgumentException> { client.searchStops(" ") }
    }
}
