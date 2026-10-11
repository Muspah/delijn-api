package be.gtfs.delijn

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Thrown when the API answers with a non-2xx status.
 *
 * @property statusCode the HTTP status code.
 * @property body the raw response body.
 */
public class DeLijnApiException(
    public val statusCode: Int,
    message: String,
    public val body: String,
) : RuntimeException(message)

/** Identifies a stop: entity number + stop number, e.g. `StopKey("2", "204911")`. */
public data class StopKey(val entityNumber: String, val stopNumber: String)

/** Identifies a line: entity number + internal line number, e.g. `LineKey("2", "7")`. */
public data class LineKey(val entityNumber: String, val lineNumber: String)

/** Identifies a direction of a line, e.g. `LineDirectionKey("2", "7", Direction.INBOUND)`. */
public data class LineDirectionKey(val entityNumber: String, val lineNumber: String, val direction: Direction)

internal val json = Json {
    ignoreUnknownKeys = true
    // The API sends some identifiers as numbers and others as strings; models use String throughout.
    isLenient = true
    // Turns explicit nulls into the property's default, e.g. "bedieningsTypes": null -> emptyList().
    coerceInputValues = true
}

/** Parses `yyyy-MM-dd` and `yyyy-MM-ddTHH:mm:ss`; the API is not consistent about which one it sends. */
internal object LocalDateTimeSerializer : KSerializer<LocalDateTime> {
    override val descriptor = PrimitiveSerialDescriptor("be.gtfs.delijn.LocalDateTime", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): LocalDateTime = decoder.decodeString().let {
        if (it.length == 10) LocalDate.parse(it).atStartOfDay() else LocalDateTime.parse(it)
    }
    override fun serialize(encoder: Encoder, value: LocalDateTime): Unit = encoder.encodeString(value.toString())
}

/** Parses `yyyy-MM-dd`, ignoring a time part if one is present. */
internal object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("be.gtfs.delijn.LocalDate", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString().take(10))
    override fun serialize(encoder: Encoder, value: LocalDate): Unit = encoder.encodeString(value.toString())
}

private val DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

internal fun LocalDateTime.toApi(): String = truncatedTo(ChronoUnit.SECONDS).format(DATE_TIME)

/** Shared HTTP plumbing for the API clients. */
internal class Transport(
    private val apiKey: String,
    baseUrl: String,
    private val httpClient: HttpClient,
    private val timeout: Duration,
) {
    private val baseUrl = baseUrl.trimEnd('/')

    init {
        require(apiKey.isNotBlank()) { "apiKey must not be blank" }
    }

    /** GETs [path] (segments must already be encoded) and returns the parsed JSON body. */
    fun get(path: String, query: Map<String, Any?> = emptyMap()): JsonElement {
        val queryString = query.entries
            .filter { it.value != null }
            .joinToString("&") { (k, v) ->
                val value = if (v is LocalDateTime) v.toApi() else v.toString()
                "$k=${URLEncoder.encode(value, StandardCharsets.UTF_8)}"
            }
        val uri = URI.create(baseUrl + path + if (queryString.isEmpty()) "" else "?$queryString")
        val request = HttpRequest.newBuilder(uri)
            .header("Ocp-Apim-Subscription-Key", apiKey)
            .header("Accept", "application/json")
            .timeout(timeout)
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val body = response.body()
        if (response.statusCode() !in 200..299) {
            throw DeLijnApiException(response.statusCode(), errorMessage(response.statusCode(), body), body)
        }
        return json.parseToJsonElement(body)
    }

    // Backend errors look like {"code":"400","boodschap":"..."}, gateway errors like {"statusCode":401,"message":"..."}.
    private fun errorMessage(status: Int, body: String): String {
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        val detail = obj?.let { (it["boodschap"] ?: it["message"])?.jsonPrimitive?.contentOrNull }
        return "De Lijn API returned HTTP $status" + (detail?.let { ": $it" } ?: "")
    }
}

/** Encodes one path segment. */
internal fun seg(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

internal inline fun <reified T> JsonElement.decode(): T = json.decodeFromJsonElement(serializer<T>(), this)

/** Decodes the list stored under [field] of a response envelope; a missing or null field yields an empty list. */
internal inline fun <reified T> JsonElement.field(field: String): List<T> {
    val value = (this as JsonObject)[field] ?: JsonNull
    return if (value is JsonNull) emptyList() else json.decodeFromJsonElement(serializer<List<T>>(), value)
}
