package be.gtfs.delijn

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Calls the real De Lijn API. Skipped unless the keys are set:
 *
 * ```
 * DELIJN_CORE_API_KEY=... DELIJN_SEARCH_API_KEY=... mvn test
 * ```
 *
 * Identifiers are looked up at runtime (starting from line 7 in Gent), so the test keeps working as the network changes.
 */
class LiveApiTest {
    private val today = LocalDate.now()
    private val now = LocalDateTime.now()

    @Test
    @EnabledIfEnvironmentVariable(named = "DELIJN_CORE_API_KEY", matches = ".+")
    fun `core API`() {
        val core = DeLijnCoreClient(System.getenv("DELIJN_CORE_API_KEY"))

        val entity = core.entities().first { it.entityNumber == "2" }
        assertEquals(entity, core.entity("2").copy(links = entity.links))
        assertTrue(core.entityMunicipalities("2").isNotEmpty())
        assertTrue(core.entityStops("2").isNotEmpty())
        assertTrue(core.entityLines("2", now).isNotEmpty())

        val municipality = core.municipalities().first { it.description == "GENT" && core.municipalityStops(it.municipalityNumber).isNotEmpty() }
        assertEquals(municipality.municipalityNumber, core.municipality(municipality.municipalityNumber).municipalityNumber)
        assertTrue(core.municipalityLines(municipality.municipalityNumber).isNotEmpty())
        assertTrue(core.municipalitiesStops(listOf(municipality.municipalityNumber)).single().stops.isNotEmpty())

        val lineKey = LineKey("2", "7")
        val line = core.line(lineKey)
        assertTrue(line.transportType != TransportType.UNKNOWN && line.serviceType != ServiceType.UNKNOWN)
        assertTrue(core.lineMunicipalities(lineKey).isNotEmpty())
        assertTrue(core.lineColors(lineKey).background != null)
        val direction = core.lineDirections(lineKey).first()
        assertEquals(direction.key, core.lineDirection(direction.key).key)
        val lineStops = core.lineDirectionStops(direction.key)
        assertTrue(lineStops.size > 2)
        val timetable = core.lineDirectionTimetable(direction.key, today)
        assertTrue(timetable.trips.isNotEmpty())
        core.lineDirectionRealTime(direction.key)
        val trip = timetable.trips.first()
        assertEquals(trip.tripNumber, core.lineDirectionTrip(direction.key, trip.tripNumber, today).trips.single().tripNumber)
        core.lineDirectionDiversions(direction.key)
        core.lineDirectionDisruptions(direction.key)
        assertTrue(core.lines().isNotEmpty())
        assertEquals(listOf(lineKey), core.lines(listOf(lineKey)).map { it.key })
        assertTrue(core.linesColors(listOf(lineKey)).isNotEmpty())
        assertTrue(core.linesLineDirections(listOf(lineKey)).single().lineDirections.isNotEmpty())
        core.lineDirectionsDiversions(listOf(direction.key))
        core.lineDirectionsDisruptions(listOf(direction.key))

        val stopKey = lineStops[1].key
        val stop = core.stop(stopKey)
        assertTrue(stop.geoCoordinate!!.latitude > 50)
        assertTrue(core.stopLineDirections(stopKey).isNotEmpty())
        assertTrue(core.stopTimetable(stopKey, today, true).stopPassages.isNotEmpty())
        core.stopRealTime(stopKey, 3)
        core.stopDiversions(stopKey)
        core.stopDisruptions(stopKey)
        val near = core.stopsNearby(stop.geoCoordinate!!.latitude, stop.geoCoordinate!!.longitude, 5, 500)
        assertTrue(near.any { it.id == stop.stopNumber })
        val stopKeys = lineStops.take(2).map { it.key }
        assertEquals(stopKeys.toSet(), core.stops(stopKeys).map { it.key }.toSet())
        assertTrue(core.stopsLineDirections(stopKeys).isNotEmpty())
        assertTrue(core.stopsTimetables(stopKeys, today).isNotEmpty())
        core.stopsRealTime(stopKeys, 2)
        core.stopsDiversions(stopKeys)
        core.stopsDisruptions(stopKeys)
        assertTrue(core.stops().size > 10_000)

        core.diversions(now, now)
        val region = core.transportRegions().first()
        assertEquals(region.code, core.transportRegion(region.code).code)
        val color = core.colors().first()
        assertEquals(color.hex, core.color(color.code).hex)

        assertFailsWith<DeLijnApiException> { core.transportRegion("XXXX") }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DELIJN_SEARCH_API_KEY", matches = ".+")
    fun `search API`() {
        val search = DeLijnSearchClient(System.getenv("DELIJN_SEARCH_API_KEY"))

        val stops = search.searchStops("Gent Sint-Pietersstation", maxHits = 5)
        assertTrue(stops.totalHits > 0)
        stops.items.forEach { assertTrue(it.geoCoordinate!!.latitude > it.geoCoordinate!!.longitude, "coordinates corrected") }

        val directions = search.searchLineDirections("7", GeoCoordinate(51.05, 3.72), startIndex = 0, maxHits = 5)
        assertTrue(directions.items.isNotEmpty())
        assertTrue(directions.items.none { it.direction == Direction.UNKNOWN })
    }
}
