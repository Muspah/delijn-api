package be.gtfs.delijn

import java.net.http.HttpClient
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Client for the De Lijn **Open Data V1 Core API** (`https://api.delijn.be/DLKernOpenData/api/v1`).
 *
 * Get a subscription key for the "Open Data Free" product (it also covers the Search API) on [data.delijn.be](https://data.delijn.be).
 * All calls are blocking and thread-safe. Non-2xx responses throw [DeLijnApiException].
 *
 * Common parameters:
 * - `validAt` (`geldigOp`): moment at which the resource must be valid; the API defaults to now.
 * - `date` (`datum`): operating day; the API defaults to today.
 *
 * The `berichten` endpoints listed in the OpenAPI spec are not implemented because the API answers them with 404.
 *
 * @param apiKey subscription key (`Ocp-Apim-Subscription-Key`).
 * @param baseUrl override for testing.
 * @param httpClient the JDK client to use; share one between clients if you like.
 * @param timeout per-request timeout. `stops()` returns ~18 MB, so keep it generous.
 */
public class DeLijnCoreClient @JvmOverloads constructor(
    apiKey: String,
    baseUrl: String = DEFAULT_BASE_URL,
    httpClient: HttpClient = HttpClient.newHttpClient(),
    timeout: Duration = Duration.ofSeconds(60),
) {
    private val http = Transport(apiKey, baseUrl, httpClient, timeout)

    // ---- Entities (entiteiten) ----

    /** All entities. `GET /entiteiten` */
    public fun entities(): List<Entity> = http.get("/entiteiten").field("entiteiten")

    /** One entity. `GET /entiteiten/{entiteitnummer}` */
    public fun entity(entityNumber: String): Entity = http.get("/entiteiten/${seg(entityNumber)}").decode()

    /** Municipalities of an entity. `GET /entiteiten/{entiteitnummer}/gemeenten` */
    public fun entityMunicipalities(entityNumber: String): List<Municipality> =
        http.get("/entiteiten/${seg(entityNumber)}/gemeenten").field("gemeenten")

    /** Stops of an entity. `GET /entiteiten/{entiteitnummer}/haltes` */
    @JvmOverloads
    public fun entityStops(entityNumber: String, validAt: LocalDateTime? = null): List<Stop> =
        http.get("/entiteiten/${seg(entityNumber)}/haltes", mapOf("geldigOp" to validAt)).field("haltes")

    /** Lines of an entity that are valid now or in the future. `GET /entiteiten/{entiteitnummer}/lijnen` */
    @JvmOverloads
    public fun entityLines(entityNumber: String, validAt: LocalDateTime? = null): List<Line> =
        http.get("/entiteiten/${seg(entityNumber)}/lijnen", mapOf("geldigOp" to validAt)).field("lijnen")

    // ---- Municipalities (gemeenten) ----

    /** All municipalities. `GET /gemeenten` */
    public fun municipalities(): List<Municipality> = http.get("/gemeenten").field("gemeenten")

    /** One municipality. `GET /gemeenten/{gemeenteNummer}` */
    public fun municipality(municipalityNumber: String): Municipality =
        http.get("/gemeenten/${seg(municipalityNumber)}").decode()

    /** Stops in a municipality. `GET /gemeenten/{gemeenteNummer}/haltes` */
    @JvmOverloads
    public fun municipalityStops(municipalityNumber: String, validAt: LocalDateTime? = null): List<Stop> =
        http.get("/gemeenten/${seg(municipalityNumber)}/haltes", mapOf("geldigOp" to validAt)).field("haltes")

    /** Lines serving a municipality. `GET /gemeenten/{gemeenteNummer}/lijnen` */
    @JvmOverloads
    public fun municipalityLines(municipalityNumber: String, validAt: LocalDateTime? = null): List<Line> =
        http.get("/gemeenten/${seg(municipalityNumber)}/lijnen", mapOf("geldigOp" to validAt)).field("lijnen")

    /** Stops of several municipalities. `GET /gemeenten/lijst/{gemeenteNummers}/haltes` */
    @JvmOverloads
    public fun municipalitiesStops(municipalityNumbers: List<String>, validAt: LocalDateTime? = null): List<MunicipalityStops> =
        // Unlike the other list endpoints, this one separates with commas.
        http.get("/gemeenten/lijst/${list(municipalityNumbers.map(::seg), ",")}/haltes", mapOf("geldigOp" to validAt))
            .field("gemeenteHaltes")

    // ---- Stops (haltes) ----

    /** All stops (large: ~18 MB). `GET /haltes` */
    @JvmOverloads
    public fun stops(validAt: LocalDateTime? = null): List<Stop> = http.get("/haltes", mapOf("geldigOp" to validAt)).field("haltes")

    /**
     * Stops near a coordinate, nearest first. `GET /haltes/indebuurt/{latlng}`
     *
     * @param maxStops maximum number of stops; the API defaults to and caps at 50.
     * @param radius search radius in meters; the API defaults to 750.
     */
    @JvmOverloads
    public fun stopsNearby(latitude: Double, longitude: Double, maxStops: Int? = null, radius: Int? = null): List<NearbyStop> =
        http.get("/haltes/indebuurt/$latitude,$longitude", mapOf("maxAantalHaltes" to maxStops, "radius" to radius))
            .field("haltes")

    /** One stop. `GET /haltes/{entiteitnummer}/{haltenummer}` */
    @JvmOverloads
    public fun stop(stop: StopKey, validAt: LocalDateTime? = null): Stop =
        http.get(path(stop), mapOf("geldigOp" to validAt)).decode()

    /** Line directions serving a stop. `GET /haltes/{entiteitnummer}/{haltenummer}/lijnrichtingen` */
    @JvmOverloads
    public fun stopLineDirections(stop: StopKey, validAt: LocalDateTime? = null): List<LineDirection> =
        http.get(path(stop) + "/lijnrichtingen", mapOf("geldigOp" to validAt)).field("lijnrichtingen")

    /**
     * Scheduled passages at a stop for one day. `GET /haltes/{entiteitnummer}/{haltenummer}/dienstregelingen`
     *
     * @param includeLastPassage whether to include the last passage (`inclusiefLaatsteDoorkomst`).
     */
    @JvmOverloads
    public fun stopTimetable(stop: StopKey, date: LocalDate? = null, includeLastPassage: Boolean? = null): StopSchedule =
        http.get(path(stop) + "/dienstregelingen", mapOf("datum" to date, "inclusiefLaatsteDoorkomst" to includeLastPassage)).decode()

    /**
     * Upcoming passages at a stop, with real-time predictions. `GET /haltes/{entiteitnummer}/{haltenummer}/real-time`
     *
     * @param maxPassages maximum number of passages; the API defaults to 10.
     */
    @JvmOverloads
    public fun stopRealTime(stop: StopKey, maxPassages: Int? = null): StopSchedule =
        http.get(path(stop) + "/real-time", mapOf("maxAantalDoorkomsten" to maxPassages)).decode()

    /** Diversions affecting a stop. `GET /haltes/{entiteitnummer}/{haltenummer}/omleidingen` */
    @JvmOverloads
    public fun stopDiversions(stop: StopKey, date: LocalDate? = null): List<Diversion> =
        diversions(http.get(path(stop) + "/omleidingen", mapOf("datum" to date)))

    /** Disruptions affecting a stop. `GET /haltes/{entiteitnummer}/{haltenummer}/storingen` */
    @JvmOverloads
    public fun stopDisruptions(stop: StopKey, date: LocalDate? = null): List<Diversion> =
        diversions(http.get(path(stop) + "/storingen", mapOf("datum" to date)))

    /** Several stops. `GET /haltes/lijst/{haltesleutels}` */
    @JvmOverloads
    public fun stops(stops: List<StopKey>, validAt: LocalDateTime? = null): List<Stop> =
        http.get(stopsPath(stops), mapOf("geldigOp" to validAt)).field("haltes")

    /** Line directions serving each of several stops. `GET /haltes/lijst/{haltesleutels}/lijnrichtingen` */
    @JvmOverloads
    public fun stopsLineDirections(stops: List<StopKey>, validAt: LocalDateTime? = null): List<StopLineDirections> =
        http.get(stopsPath(stops) + "/lijnrichtingen", mapOf("geldigOp" to validAt)).field("halteLijnrichtingen")

    /** Scheduled passages at several stops. `GET /haltes/lijst/{haltesleutels}/dienstregelingen` */
    @JvmOverloads
    public fun stopsTimetables(stops: List<StopKey>, date: LocalDate? = null): List<StopSchedule> =
        http.get(stopsPath(stops) + "/dienstregelingen", mapOf("datum" to date)).field("halteDoorkomstenLijst")

    /** Real-time passages at several stops. `GET /haltes/lijst/{haltesleutels}/real-time` */
    @JvmOverloads
    public fun stopsRealTime(stops: List<StopKey>, maxPassages: Int? = null): List<StopSchedule> =
        http.get(stopsPath(stops) + "/real-time", mapOf("maxAantalDoorkomsten" to maxPassages)).field("halteDoorkomstenLijst")

    /** Diversions per stop. Stops without diversions are omitted. `GET /haltes/lijst/{haltesleutels}/omleidingen` */
    @JvmOverloads
    public fun stopsDiversions(stops: List<StopKey>, date: LocalDate? = null): List<StopDiversions> =
        http.get(stopsPath(stops) + "/omleidingen", mapOf("datum" to date)).field("halteOmleidingen")

    /** Disruptions per stop. Stops without disruptions are omitted. `GET /haltes/lijst/{haltesleutels}/storingen` */
    @JvmOverloads
    public fun stopsDisruptions(stops: List<StopKey>, date: LocalDate? = null): List<StopDiversions> =
        http.get(stopsPath(stops) + "/storingen", mapOf("datum" to date)).field("halteOmleidingen")

    // ---- Lines (lijnen) ----

    /** All lines valid at or after [validAt]. `GET /lijnen` */
    @JvmOverloads
    public fun lines(validAt: LocalDateTime? = null): List<Line> = http.get("/lijnen", mapOf("geldigOp" to validAt)).field("lijnen")

    /** One line. `GET /lijnen/{entiteitnummer}/{lijnnummer}` */
    @JvmOverloads
    public fun line(line: LineKey, validAt: LocalDateTime? = null): Line = http.get(path(line), mapOf("geldigOp" to validAt)).decode()

    /** Municipalities served by a line. `GET /lijnen/{entiteitnummer}/{lijnnummer}/gemeenten` */
    @JvmOverloads
    public fun lineMunicipalities(line: LineKey, validAt: LocalDateTime? = null): List<Municipality> =
        http.get(path(line) + "/gemeenten", mapOf("geldigOp" to validAt)).field("gemeenten")

    /** Colours of a line. `GET /lijnen/{entiteitnummer}/{lijnnummer}/lijnkleuren` */
    @JvmOverloads
    public fun lineColors(line: LineKey, validAt: LocalDateTime? = null): LineColors =
        http.get(path(line) + "/lijnkleuren", mapOf("geldigOp" to validAt)).decode()

    /** Directions of a line. `GET /lijnen/{entiteitnummer}/{lijnnummer}/lijnrichtingen` */
    @JvmOverloads
    public fun lineDirections(line: LineKey, validAt: LocalDateTime? = null): List<LineDirection> =
        http.get(path(line) + "/lijnrichtingen", mapOf("geldigOp" to validAt)).field("lijnrichtingen")

    /** One direction of a line. `GET /lijnen/{entiteitnummer}/{lijnnummer}/lijnrichtingen/{richting}` */
    @JvmOverloads
    public fun lineDirection(lineDirection: LineDirectionKey, validAt: LocalDateTime? = null): LineDirection =
        http.get(path(lineDirection), mapOf("geldigOp" to validAt)).decode()

    /** Stops of a line direction, in order. `GET /lijnen/{entiteitnummer}/{lijnnummer}/lijnrichtingen/{richting}/haltes` */
    @JvmOverloads
    public fun lineDirectionStops(lineDirection: LineDirectionKey, validAt: LocalDateTime? = null): List<Stop> =
        http.get(path(lineDirection) + "/haltes", mapOf("geldigOp" to validAt)).field("haltes")

    /** Scheduled trips of a line direction for one day. `GET /lijnen/{entiteitnummer}/{lijnnummer}/lijnrichtingen/{richting}/dienstregelingen` */
    @JvmOverloads
    public fun lineDirectionTimetable(lineDirection: LineDirectionKey, date: LocalDate? = null): LineDirectionSchedule =
        http.get(path(lineDirection) + "/dienstregelingen", mapOf("datum" to date)).decode()

    /** Current trips of a line direction with real-time predictions. `GET /lijnen/{entiteitnummer}/{lijnnummer}/lijnrichtingen/{richting}/real-time` */
    public fun lineDirectionRealTime(lineDirection: LineDirectionKey): LineDirectionSchedule =
        http.get(path(lineDirection) + "/real-time").decode()

    /** One trip of a line direction. `GET /lijnen/{entiteitnummer}/{lijnnummer}/lijnrichtingen/{richting}/rit/{ritnummer}` */
    public fun lineDirectionTrip(lineDirection: LineDirectionKey, tripNumber: String, date: LocalDate): LineDirectionSchedule =
        http.get(path(lineDirection) + "/rit/${seg(tripNumber)}", mapOf("datum" to date)).decode()

    /** Diversions of a line direction. `GET /lijnen/{entiteitnummer}/{lijnnummer}/lijnrichtingen/{richting}/omleidingen` */
    @JvmOverloads
    public fun lineDirectionDiversions(lineDirection: LineDirectionKey, date: LocalDate? = null): List<Diversion> =
        diversions(http.get(path(lineDirection) + "/omleidingen", mapOf("datum" to date)))

    /** Disruptions of a line direction. `GET /lijnen/{entiteitnummer}/{lijnnummer}/lijnrichtingen/{richting}/storingen` */
    @JvmOverloads
    public fun lineDirectionDisruptions(lineDirection: LineDirectionKey, date: LocalDate? = null): List<Diversion> =
        diversions(http.get(path(lineDirection) + "/storingen", mapOf("datum" to date)))

    /** Several lines. `GET /lijnen/lijst/{lijnsleutels}` */
    @JvmOverloads
    public fun lines(lines: List<LineKey>, validAt: LocalDateTime? = null): List<Line> =
        http.get(linesPath(lines), mapOf("geldigOp" to validAt)).field("lijnen")

    /** Colours of several lines. `GET /lijnen/lijst/{lijnsleutels}/lijnkleuren` */
    @JvmOverloads
    public fun linesColors(lines: List<LineKey>, validAt: LocalDateTime? = null): List<LineWithColors> =
        // "lijnLijnkleurCodesijst" (sic) is the actual field name sent by the API.
        http.get(linesPath(lines) + "/lijnkleuren", mapOf("geldigOp" to validAt)).field("lijnLijnkleurCodesijst")

    /** Directions of several lines. `GET /lijnen/lijst/{lijnsleutels}/lijnrichtingen` */
    @JvmOverloads
    public fun linesLineDirections(lines: List<LineKey>, validAt: LocalDateTime? = null): List<LineWithDirections> =
        http.get(linesPath(lines) + "/lijnrichtingen", mapOf("geldigOp" to validAt)).field("lijnLijnrichtingen")

    /** Diversions of several line directions. `GET /lijnen/lijst/{lijnrichtingsleutels}/omleidingen` */
    @JvmOverloads
    public fun lineDirectionsDiversions(lineDirections: List<LineDirectionKey>, date: LocalDate? = null): List<Diversion> =
        diversions(http.get(lineDirectionsPath(lineDirections) + "/omleidingen", mapOf("datum" to date)))

    /** Disruptions of several line directions. `GET /lijnen/lijst/{lijnrichtingsleutels}/storingen` */
    @JvmOverloads
    public fun lineDirectionsDisruptions(lineDirections: List<LineDirectionKey>, date: LocalDate? = null): List<Diversion> =
        diversions(http.get(lineDirectionsPath(lineDirections) + "/storingen", mapOf("datum" to date)))

    // ---- Other ----

    /** All diversions active between [start] and [end] (both default to now). `GET /omleidingen` */
    @JvmOverloads
    public fun diversions(start: LocalDateTime? = null, end: LocalDateTime? = null): List<Diversion> =
        diversions(http.get("/omleidingen", mapOf("startDatum" to start, "eindDatum" to end)))

    /** All transport regions. `GET /vervoerregios` */
    public fun transportRegions(): List<TransportRegion> = http.get("/vervoerregios").field("vervoerRegios")

    /** One transport region, e.g. `GE` (Gent). `GET /vervoerregios/{code}` */
    public fun transportRegion(code: String): TransportRegion = http.get("/vervoerregios/${seg(code)}").decode()

    /** All line colours. `GET /kleuren` */
    public fun colors(): List<Color> = http.get("/kleuren").field("kleuren")

    /** One line colour, e.g. `GE` (yellow). `GET /kleuren/{code}` */
    public fun color(code: String): Color = http.get("/kleuren/${seg(code)}").decode()

    // Diversion and disruption endpoints put their list under "omleidingen" or "storingen" depending on the endpoint.
    private fun diversions(response: kotlinx.serialization.json.JsonElement): List<Diversion> =
        response.field<Diversion>("omleidingen") + response.field("storingen")

    private fun path(stop: StopKey) = "/haltes/${seg(stop.entityNumber)}/${seg(stop.stopNumber)}"
    private fun path(line: LineKey) = "/lijnen/${seg(line.entityNumber)}/${seg(line.lineNumber)}"
    private fun path(ld: LineDirectionKey) = path(LineKey(ld.entityNumber, ld.lineNumber)) + "/lijnrichtingen/${apiDirection(ld.direction)}"
    private fun stopsPath(stops: List<StopKey>) = "/haltes/lijst/" + list(stops.map { "${seg(it.entityNumber)}_${seg(it.stopNumber)}" })
    private fun linesPath(lines: List<LineKey>) = "/lijnen/lijst/" + list(lines.map { "${seg(it.entityNumber)}_${seg(it.lineNumber)}" })
    private fun lineDirectionsPath(lds: List<LineDirectionKey>) =
        "/lijnen/lijst/" + list(lds.map { "${seg(it.entityNumber)}_${seg(it.lineNumber)}_${apiDirection(it.direction)}" })

    private fun list(items: List<String>, separator: String = "_"): String {
        require(items.isNotEmpty()) { "at least one key is required" }
        return items.joinToString(separator)
    }

    private fun apiDirection(direction: Direction): String =
        requireNotNull(direction.apiValue) { "Direction.UNKNOWN cannot be used in a request" }

    public companion object {
        public const val DEFAULT_BASE_URL: String = "https://api.delijn.be/DLKernOpenData/api/v1"
    }
}
