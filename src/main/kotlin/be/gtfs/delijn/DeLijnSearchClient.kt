package be.gtfs.delijn

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.http.HttpClient
import java.time.Duration

/**
 * Client for the De Lijn **Open Data V1 Search API** (`https://api.delijn.be/DLZoekOpenData/v1`).
 *
 * The "Open Data Free" subscription key works for both this API and the Core API.
 * All calls are blocking and thread-safe. Non-2xx responses throw [DeLijnApiException].
 *
 * **Coordinate fix:** the Search API returns stop coordinates with `latitude` and `longitude` swapped. This client
 * swaps them back whenever `latitude < longitude`. That check is safe because everywhere De Lijn operates the
 * latitude (~51) is larger than the longitude (~3-6), so the fix keeps working once De Lijn corrects the API.
 *
 * @param apiKey subscription key (`Ocp-Apim-Subscription-Key`).
 * @param baseUrl override for testing.
 * @param httpClient the JDK client to use.
 * @param timeout per-request timeout.
 */
public class DeLijnSearchClient @JvmOverloads constructor(
    apiKey: String,
    baseUrl: String = DEFAULT_BASE_URL,
    httpClient: HttpClient = HttpClient.newHttpClient(),
    timeout: Duration = Duration.ofSeconds(30),
) {
    private val http = Transport(apiKey, baseUrl, httpClient, timeout)

    /**
     * Searches stops by name. `GET /zoek/haltes/{zoekArgument}`
     *
     * Returned stops only carry a subset of the [Stop] fields; use [DeLijnCoreClient.stop] with [Stop.key] for details.
     *
     * @param query search text.
     * @param position when given, results near this position rank higher.
     * @param startIndex index of the first hit to return, for paging.
     * @param maxHits maximum number of hits to return.
     */
    @JvmOverloads
    public fun searchStops(query: String, position: GeoCoordinate? = null, startIndex: Int? = null, maxHits: Int? = null): SearchResult<Stop> {
        val response = search("haltes", query, position, startIndex, maxHits)
        return SearchResult(response.totalHits(), response.field<Stop>("haltes").map(::fixCoordinate))
    }

    /**
     * Searches line directions by line number or name. `GET /zoek/lijnrichtingen/{zoekArgument}`
     *
     * @param query search text, e.g. a public line number.
     * @param position when given, results near this position rank higher.
     * @param startIndex index of the first hit to return, for paging.
     * @param maxHits maximum number of hits to return.
     */
    @JvmOverloads
    public fun searchLineDirections(
        query: String,
        position: GeoCoordinate? = null,
        startIndex: Int? = null,
        maxHits: Int? = null,
    ): SearchResult<LineDirection> {
        val response = search("lijnrichtingen", query, position, startIndex, maxHits)
        return SearchResult(response.totalHits(), response.field("lijnrichtingen"))
    }

    private fun search(kind: String, query: String, position: GeoCoordinate?, startIndex: Int?, maxHits: Int?): JsonElement {
        require(query.isNotBlank()) { "query must not be blank" }
        return http.get(
            "/zoek/$kind/${seg(query)}",
            mapOf(
                "huidigePositie" to position?.let { "${it.latitude},${it.longitude}" },
                "startIndex" to startIndex,
                "maxAantalHits" to maxHits,
            ),
        )
    }

    private fun JsonElement.totalHits(): Int = jsonObject["aantalHits"]?.jsonPrimitive?.int ?: 0

    public companion object {
        public const val DEFAULT_BASE_URL: String = "https://api.delijn.be/DLZoekOpenData/v1"
    }
}

internal fun fixCoordinate(stop: Stop): Stop {
    val c = stop.geoCoordinate ?: return stop
    return if (c.latitude < c.longitude) stop.copy(geoCoordinate = GeoCoordinate(c.longitude, c.latitude)) else stop
}
