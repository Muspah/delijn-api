package be.gtfs.delijn

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Runs every Core endpoint against a local mock server serving trimmed real responses
 * (`src/test/resources/fixtures`), checking the request URL and that the response decodes.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeLijnCoreClientTest {
    private val server = MockServer()
    private val client = DeLijnCoreClient("test-key", baseUrl = server.url)

    @AfterAll
    fun stop() = server.close()

    private val stop = StopKey("2", "204911")
    private val stop2 = StopKey("2", "205914")
    private val line = LineKey("2", "7")
    private val line2 = LineKey("1", "1")
    private val inbound = LineDirectionKey("2", "7", Direction.INBOUND)
    private val outbound = LineDirectionKey("1", "1", Direction.OUTBOUND)
    private val day = LocalDate.of(2026, 10, 11)
    private val moment = LocalDateTime.of(2026, 10, 11, 12, 30, 15)
    private val m = "2026-10-11T12%3A30%3A15"

    /** fixture name, expected request, call. A call returning a list must return a non-empty one. */
    private class Case(val fixture: String, val request: String, val call: DeLijnCoreClient.() -> Any)

    private val cases = listOf(
        Case("entiteiten", "/entiteiten") { entities() },
        Case("entiteit", "/entiteiten/1") { entity("1") },
        Case("entiteitGemeenten", "/entiteiten/2/gemeenten") { entityMunicipalities("2") },
        Case("entiteitHaltes", "/entiteiten/2/haltes?geldigOp=$m") { entityStops("2", moment) },
        Case("entiteitLijnen", "/entiteiten/2/lijnen") { entityLines("2") },
        Case("gemeenten", "/gemeenten") { municipalities() },
        Case("gemeente", "/gemeenten/1633") { municipality("1633") },
        Case("gemeenteHaltes", "/gemeenten/1378/haltes") { municipalityStops("1378") },
        Case("gemeenteLijnen", "/gemeenten/1378/lijnen?geldigOp=$m") { municipalityLines("1378", moment) },
        Case("gemeentenLijstHaltes", "/gemeenten/lijst/1633,1378/haltes") { municipalitiesStops(listOf("1633", "1378")) },
        Case("haltes", "/haltes") { stops() },
        Case("haltesInDeBuurt", "/haltes/indebuurt/51.036,3.709?maxAantalHaltes=5&radius=300") { stopsNearby(51.036, 3.709, 5, 300) },
        Case("halte", "/haltes/2/204911") { stop(stop) },
        Case("halteLijnrichtingen", "/haltes/2/204911/lijnrichtingen") { stopLineDirections(stop) },
        Case("halteDienstregelingen", "/haltes/2/204911/dienstregelingen?datum=2026-10-11&inclusiefLaatsteDoorkomst=true") {
            stopTimetable(stop, day, true)
        },
        Case("halteRealtime", "/haltes/2/204911/real-time?maxAantalDoorkomsten=5") { stopRealTime(stop, 5) },
        Case("halteOmleidingen", "/haltes/2/204911/omleidingen?datum=2026-10-11") { stopDiversions(stop, day).let { "may be empty" } },
        Case("halteStoringen", "/haltes/2/204911/storingen") { stopDisruptions(stop) },
        Case("haltesLijst", "/haltes/lijst/2_204911_2_205914") { stops(listOf(stop, stop2)) },
        Case("haltesLijstLijnrichtingen", "/haltes/lijst/2_204911_2_205914/lijnrichtingen") { stopsLineDirections(listOf(stop, stop2)) },
        Case("haltesLijstDienstregelingen", "/haltes/lijst/2_204911_2_205914/dienstregelingen?datum=2026-10-11") {
            stopsTimetables(listOf(stop, stop2), day)
        },
        Case("haltesLijstRealtime", "/haltes/lijst/2_204911_2_205914/real-time?maxAantalDoorkomsten=3") { stopsRealTime(listOf(stop, stop2), 3) },
        Case("haltesLijstOmleidingen", "/haltes/lijst/2_204911_2_205914/omleidingen") { stopsDiversions(listOf(stop, stop2)).let { "may be empty" } },
        Case("haltesLijstStoringen", "/haltes/lijst/2_204911_2_205914/storingen") { stopsDisruptions(listOf(stop, stop2)) },
        Case("lijnen", "/lijnen") { lines() },
        Case("lijn", "/lijnen/2/7") { line(line) },
        Case("lijnGemeenten", "/lijnen/2/7/gemeenten") { lineMunicipalities(line) },
        Case("lijnLijnkleuren", "/lijnen/2/7/lijnkleuren") { lineColors(line) },
        Case("lijnLijnrichtingen", "/lijnen/2/7/lijnrichtingen") { lineDirections(line) },
        Case("lijnrichting", "/lijnen/2/7/lijnrichtingen/TERUG?geldigOp=$m") { lineDirection(inbound, moment) },
        Case("lijnrichtingHaltes", "/lijnen/2/7/lijnrichtingen/TERUG/haltes") { lineDirectionStops(inbound) },
        Case("lijnrichtingDienstregelingen", "/lijnen/2/7/lijnrichtingen/TERUG/dienstregelingen?datum=2026-10-11") {
            lineDirectionTimetable(inbound, day)
        },
        Case("lijnrichtingRealtime", "/lijnen/2/7/lijnrichtingen/TERUG/real-time") { lineDirectionRealTime(inbound) },
        Case("rit", "/lijnen/2/7/lijnrichtingen/TERUG/rit/230?datum=2026-10-11") { lineDirectionTrip(inbound, "230", day) },
        Case("lijnrichtingOmleidingen", "/lijnen/2/7/lijnrichtingen/TERUG/omleidingen") { lineDirectionDiversions(inbound) },
        Case("lijnrichtingStoringen", "/lijnen/2/7/lijnrichtingen/TERUG/storingen?datum=2026-10-11") { lineDirectionDisruptions(inbound, day) },
        Case("lijnenLijst", "/lijnen/lijst/2_7_1_1") { lines(listOf(line, line2)) },
        Case("lijnenLijstLijnkleuren", "/lijnen/lijst/2_7_1_1/lijnkleuren") { linesColors(listOf(line, line2)) },
        Case("lijnenLijstLijnrichtingen", "/lijnen/lijst/2_7_1_1/lijnrichtingen") { linesLineDirections(listOf(line, line2)) },
        Case("lijnrichtingenLijstOmleidingen", "/lijnen/lijst/2_7_TERUG_1_1_HEEN/omleidingen") { lineDirectionsDiversions(listOf(inbound, outbound)) },
        Case("lijnrichtingenLijstStoringen", "/lijnen/lijst/2_7_TERUG_1_1_HEEN/storingen") { lineDirectionsDisruptions(listOf(inbound, outbound)) },
        Case("omleidingen", "/omleidingen?startDatum=$m&eindDatum=$m") { diversions(moment, moment) },
        Case("vervoerregios", "/vervoerregios") { transportRegions() },
        Case("vervoerregio", "/vervoerregios/GE") { transportRegion("GE") },
        Case("kleuren", "/kleuren") { colors() },
        Case("kleur", "/kleuren/TU") { color("TU") },
    )

    @TestFactory
    fun `every endpoint builds the right request and decodes a real response`() = cases.map { case ->
        DynamicTest.dynamicTest(case.request) {
            server.respondWithFixture(case.fixture)
            val result = case.call(client)
            assertEquals(case.request, server.lastRequest)
            assertEquals("test-key", server.lastApiKey)
            if (result is List<*>) assertTrue(result.isNotEmpty(), "expected a non-empty list")
        }
    }

    @Test
    fun `covers all 46 implemented endpoints`() = assertEquals(46, cases.size)

    @Test
    fun `stop maps enums and keeps raw values`() {
        server.body = """{"entiteitnummer":"2","haltenummer":"1","taal":"KLINGON","classificatie":"COMBI",
            |"halteToegankelijkheden":["VISUELE_BEPERKING","NIEUW"],"bedieningsTypes":null,"gemeentenummer":1378}""".trimMargin()
        val stop = client.stop(stop)
        assertEquals(StopLanguage.UNKNOWN, stop.language)
        assertEquals("KLINGON", stop.rawLanguage)
        assertEquals(StopClassification.COMBINED, stop.classification)
        assertEquals(listOf(Accessibility.VISUALLY_IMPAIRED, Accessibility.UNKNOWN), stop.accessibilities)
        assertEquals(listOf("VISUELE_BEPERKING", "NIEUW"), stop.rawAccessibilities)
        assertEquals(emptyList(), stop.transportTypes, "null list becomes empty")
        assertEquals("1378", stop.municipalityNumber, "JSON number decodes into String")
        assertEquals(StopKey("2", "1"), stop.key)
    }

    @Test
    fun `real-time passages decode times, line numbers and statuses`() {
        server.respondWithFixture("halteRealtime")
        val passage = client.stopRealTime(stop).stopPassages.single().passages.first()
        assertEquals(LocalDateTime.of(2026, 10, 11, 12, 45), passage.scheduledTime)
        assertEquals(LocalDateTime.of(2026, 10, 11, 12, 45), passage.realTimeTime)
        assertEquals("4", passage.lineNumber)
        assertEquals(Direction.INBOUND, passage.direction)
        assertEquals(listOf(PredictionStatus.REALTIME), passage.predictionStatuses)
    }

    @Test
    fun `line decodes dates and enums`() {
        server.respondWithFixture("lijn")
        val line = client.line(line)
        assertEquals(TransportType.TRAM, line.transportType)
        assertEquals(ServiceType.REGULAR, line.serviceType)
        assertTrue(line.validFrom!! <= line.validUntil!!)
    }

    @Test
    fun `disruptions are read from either the storingen or the omleidingen field`() {
        server.respondWithFixture("halteStoringen") // {"storingen": [...]}
        val fromStoringen = client.stopDisruptions(stop)
        server.respondWithFixture("lijnrichtingenLijstStoringen") // {"omleidingen": [...]}
        val fromOmleidingen = client.lineDirectionsDisruptions(listOf(inbound))
        assertTrue(fromStoringen.isNotEmpty() && fromOmleidingen.isNotEmpty())
        assertEquals("nederlands", fromStoringen.first().titleTranslations.keys.first())
    }

    @Test
    fun `period accepts date-only and missing end`() {
        server.body = """{"omleidingen":[{"periode":{"startDatum":"2026-01-05"}}]}"""
        val period = client.diversions().single().period!!
        assertEquals(LocalDateTime.of(2026, 1, 5, 0, 0), period.start)
        assertNull(period.end)
    }

    @Test
    fun `backend error body is turned into an exception`() {
        server.status = 404
        server.body = """{"code":"404","boodschap":"Resource met volgende identificatie niet gevonden : 1"}"""
        val e = assertFailsWith<DeLijnApiException> { client.municipality("1") }
        assertEquals(404, e.statusCode)
        assertEquals("De Lijn API returned HTTP 404: Resource met volgende identificatie niet gevonden : 1", e.message)
        server.status = 200
    }

    @Test
    fun `gateway error body is turned into an exception`() {
        server.status = 401
        server.body = """{ "statusCode": 401, "message": "Access denied due to missing subscription key." }"""
        val e = assertFailsWith<DeLijnApiException> { client.entities() }
        assertEquals("De Lijn API returned HTTP 401: Access denied due to missing subscription key.", e.message)
        server.status = 200
    }

    @Test
    fun `path segments are encoded`() {
        server.body = """{"code":"X","omschrijving":"x"}"""
        client.color("A/B C")
        assertEquals("/kleuren/A%2FB%20C", server.lastRequest)
    }

    @Test
    fun `invalid arguments are rejected before calling the API`() {
        assertFailsWith<IllegalArgumentException> { client.stops(emptyList()) }
        assertFailsWith<IllegalArgumentException> { client.lineDirection(LineDirectionKey("2", "7", Direction.UNKNOWN)) }
        assertFailsWith<IllegalArgumentException> { DeLijnCoreClient(" ") }
    }
}
