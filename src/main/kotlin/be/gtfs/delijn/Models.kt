@file:UseSerializers(LocalDateSerializer::class, LocalDateTimeSerializer::class)

package be.gtfs.delijn

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.LocalDate
import java.time.LocalDateTime

// Models for the De Lijn Open Data APIs.
//
// Field names are English; @SerialName holds the original (Dutch) JSON name. Nullability follows what the live
// API returns, not the OpenAPI spec: a property is only non-null when every observed response contained it.
// Dates and times are local times in Europe/Brussels; the API does not send an offset.

/** A hypermedia link to a related resource. */
@Serializable
public data class Link(
    val rel: String,
    val url: String,
)

/** A WGS84 coordinate. */
@Serializable
public data class GeoCoordinate(
    val latitude: Double,
    val longitude: Double,
)

/** One of the five De Lijn regional entities (`entiteit`), e.g. `1` = Antwerpen. */
@Serializable
public data class Entity(
    @SerialName("entiteitnummer") val entityNumber: String,
    @SerialName("entiteitcode") val entityCode: String,
    @SerialName("omschrijving") val description: String,
    val links: List<Link> = emptyList(),
)

/** A municipality (`gemeente`). Note that De Lijn uses its own municipality numbers, not NIS codes. */
@Serializable
public data class Municipality(
    @SerialName("gemeentenummer") val municipalityNumber: String,
    @SerialName("omschrijving") val description: String,
    /** The main municipality this (sub)municipality belongs to, if any. */
    @SerialName("hoofdGemeente") val mainMunicipality: Municipality? = null,
    val links: List<Link> = emptyList(),
)

/** A municipality with its stops. */
@Serializable
public data class MunicipalityStops(
    @SerialName("gemeente") val municipality: Municipality,
    @SerialName("haltes") val stops: List<Stop> = emptyList(),
)

/** A named place, as used by [Stop.place]. */
@Serializable
public data class Place(
    @SerialName("plaatsNaam") val name: String? = null,
    @SerialName("verkortePlaatsNaam") val shortName: String? = null,
    @SerialName("geoCoordinaat") val geoCoordinate: GeoCoordinate? = null,
)

/**
 * A stop (`halte`).
 *
 * Only [entityNumber] and [stopNumber] are always present: stops embedded in other resources (diversions,
 * stop lists, search results) only carry a subset of the fields.
 */
@Serializable
public data class Stop(
    @SerialName("entiteitnummer") val entityNumber: String,
    @SerialName("haltenummer") val stopNumber: String,
    @SerialName("omschrijving") val description: String? = null,
    @SerialName("omschrijvingLang") val descriptionLong: String? = null,
    @SerialName("gemeentenummer") val municipalityNumber: String? = null,
    @SerialName("omschrijvingGemeente") val municipalityDescription: String? = null,
    @SerialName("geoCoordinaat") val geoCoordinate: GeoCoordinate? = null,
    @SerialName("halteToegankelijkheden") val rawAccessibilities: List<String> = emptyList(),
    @SerialName("hoofdHalte") val mainStop: Boolean? = null,
    @SerialName("taal") val rawLanguage: String? = null,
    @SerialName("classificatie") val rawClassification: String? = null,
    @SerialName("bedieningsTypes") val rawTransportTypes: List<String> = emptyList(),
    @SerialName("plaats") val place: Place? = null,
    val links: List<Link> = emptyList(),
) {
    /** The key to use when requesting this stop, e.g. in [DeLijnCoreClient.stops]. */
    val key: StopKey get() = StopKey(entityNumber, stopNumber)
    val accessibilities: List<Accessibility> get() = rawAccessibilities.map { parseApiValue(it) }
    val language: StopLanguage? get() = rawLanguage?.let { parseApiValue<StopLanguage>(it) }
    val classification: StopClassification? get() = rawClassification?.let { parseApiValue<StopClassification>(it) }
    val transportTypes: List<TransportType> get() = rawTransportTypes.map { parseApiValue(it) }
}

/** A stop found by [DeLijnCoreClient.stopsNearby]. */
@Serializable
public data class NearbyStop(
    /** Operator of the stop, e.g. `DELIJN`. */
    val type: String? = null,
    /** The stop number ([Stop.stopNumber]). */
    val id: String,
    @SerialName("naam") val name: String? = null,
    /** Distance from the requested coordinate in meters. */
    @SerialName("afstand") val distance: Int? = null,
    @SerialName("geoCoordinaat") val geoCoordinate: GeoCoordinate? = null,
    val links: List<Link> = emptyList(),
)

/**
 * A line (`lijn`).
 *
 * Only [entityNumber] and [lineNumber] are always present: lines embedded in other resources only carry the key.
 */
@Serializable
public data class Line(
    @SerialName("entiteitnummer") val entityNumber: String,
    /** Internal line number, used in requests. */
    @SerialName("lijnnummer") val lineNumber: String,
    /** Line number shown to passengers, e.g. `"7"` or `"Flex"`. */
    @SerialName("lijnnummerPubliek") val publicLineNumber: String? = null,
    @SerialName("omschrijving") val description: String? = null,
    /** Code of the [TransportRegion]. */
    @SerialName("vervoerRegioCode") val transportRegionCode: String? = null,
    @SerialName("publiek") val public: Boolean? = null,
    @SerialName("vervoertype") val rawTransportType: String? = null,
    @SerialName("bedieningtype") val rawServiceType: String? = null,
    /** Network layer, free text, e.g. `Kernnet` or `Aanvullend net functioneel`. */
    @SerialName("vervoerslaag") val networkLayer: String? = null,
    @SerialName("lijnGeldigVan") val validFrom: LocalDate? = null,
    @SerialName("lijnGeldigTot") val validUntil: LocalDate? = null,
    val links: List<Link> = emptyList(),
) {
    /** The key to use when requesting this line, e.g. in [DeLijnCoreClient.lines]. */
    val key: LineKey get() = LineKey(entityNumber, lineNumber)
    val transportType: TransportType? get() = rawTransportType?.let { parseApiValue<TransportType>(it) }
    val serviceType: ServiceType? get() = rawServiceType?.let { parseApiValue<ServiceType>(it) }
}

/** One direction of a line (`lijnrichting`). Colour fields are only filled in by the Search API. */
@Serializable
public data class LineDirection(
    @SerialName("entiteitnummer") val entityNumber: String,
    @SerialName("lijnnummer") val lineNumber: String,
    @SerialName("richting") val rawDirection: String,
    @SerialName("omschrijving") val description: String? = null,
    @SerialName("lijnNummerPubliek") val publicLineNumber: String? = null,
    @SerialName("bestemming") val destination: String? = null,
    /** Hex colour, e.g. `#FFFFFF`. */
    @SerialName("kleurVoorGrond") val foregroundColor: String? = null,
    @SerialName("kleurAchterGrond") val backgroundColor: String? = null,
    @SerialName("kleurVoorGrondRand") val foregroundBorderColor: String? = null,
    @SerialName("kleurAchterGrondRand") val backgroundBorderColor: String? = null,
    val links: List<Link> = emptyList(),
) {
    /** The key to use when requesting this line direction, e.g. in [DeLijnCoreClient.lineDirectionsDiversions]. */
    val key: LineDirectionKey get() = LineDirectionKey(entityNumber, lineNumber, direction)
    val direction: Direction get() = parseApiValue(rawDirection)
}

/** A stop with the line directions serving it. */
@Serializable
public data class StopLineDirections(
    @SerialName("halte") val stop: Stop,
    @SerialName("lijnrichtingen") val lineDirections: List<LineDirection> = emptyList(),
)

/** A line with its directions. */
@Serializable
public data class LineWithDirections(
    @SerialName("lijn") val line: Line,
    @SerialName("lijnrichtingen") val lineDirections: List<LineDirection> = emptyList(),
)

/** A reference to a [Color] by its code; resolve it with [DeLijnCoreClient.color]. */
@Serializable
public data class ColorRef(
    val code: String,
    val links: List<Link> = emptyList(),
)

/** The colours of a line. */
@Serializable
public data class LineColors(
    @SerialName("voorgrond") val foreground: ColorRef? = null,
    @SerialName("achtergrond") val background: ColorRef? = null,
    @SerialName("voorgrondRand") val foregroundBorder: ColorRef? = null,
    @SerialName("achtergrondRand") val backgroundBorder: ColorRef? = null,
    val links: List<Link> = emptyList(),
)

/** A line with its colours. */
@Serializable
public data class LineWithColors(
    @SerialName("lijn") val line: Line,
    @SerialName("lijnkleurCodes") val colors: LineColors,
)

/** An RGB colour value. */
@Serializable
public data class Rgb(
    @SerialName("rood") val red: Int,
    @SerialName("groen") val green: Int,
    @SerialName("blauw") val blue: Int,
)

/** A colour (`kleur`) used for line branding. */
@Serializable
public data class Color(
    val code: String,
    @SerialName("omschrijving") val description: String,
    val rgb: Rgb? = null,
    /** Hex value without `#`, e.g. `0099AA`. */
    val hex: String? = null,
    val links: List<Link> = emptyList(),
)

/** A transport region (`vervoerregio`). */
@Serializable
public data class TransportRegion(
    val code: String,
    @SerialName("naam") val name: String,
    @SerialName("nr") val number: String,
    val links: List<Link> = emptyList(),
)

/** A start and (optional) end moment. */
@Serializable
public data class Period(
    @SerialName("startDatum") val start: LocalDateTime? = null,
    /** `null` when the end is not known yet (e.g. "until the end of the works"). */
    @SerialName("eindDatum") val end: LocalDateTime? = null,
)

/**
 * A diversion (`omleiding`) or disruption (`storing`); both share the same structure.
 *
 * Translation maps are keyed by Dutch language names as sent by the API: `nederlands`, `frans`, `engels`, `duits`.
 */
@Serializable
public data class Diversion(
    @SerialName("titel") val title: String? = null,
    @SerialName("titelMeertalig") val titleTranslations: Map<String, String> = emptyMap(),
    @SerialName("omschrijving") val description: String? = null,
    @SerialName("omschrijvingMeertalig") val descriptionTranslations: Map<String, String> = emptyMap(),
    @SerialName("omschrijvingMeertaligKort") val shortDescriptionTranslations: Map<String, String> = emptyMap(),
    @SerialName("periode") val period: Period? = null,
    @SerialName("lijnrichtingen") val lineDirections: List<LineDirection> = emptyList(),
    @SerialName("haltes") val stops: List<Stop> = emptyList(),
    @SerialName("referentieOmleiding") val diversionReference: Int? = null,
    val type: String? = null,
    /** Days on which the diversion applies, as sent by the API (e.g. `ZONDAG`). */
    @SerialName("omleidingsDagen") val diversionDays: List<String> = emptyList(),
    val links: List<Link> = emptyList(),
)

/** A stop with its diversions or disruptions. */
@Serializable
public data class StopDiversions(
    @SerialName("halte") val stop: Stop,
    @SerialName("omleidingen") val diversions: List<Diversion> = emptyList(),
)

/**
 * A note (`nota`) attached to a timetable, trip or passage.
 *
 * Structure taken from the OpenAPI spec: no live response containing notes was observed while building this client.
 */
@Serializable
public data class Note(
    val id: String? = null,
    @SerialName("titel") val title: String? = null,
    @SerialName("omschrijving") val description: String? = null,
    @SerialName("entiteitnummer") val entityNumber: String? = null,
    @SerialName("lijnnummer") val lineNumber: String? = null,
    @SerialName("richting") val rawDirection: String? = null,
    @SerialName("ritnummer") val tripNumber: String? = null,
    @SerialName("haltenummer") val stopNumber: String? = null,
) {
    val direction: Direction? get() = rawDirection?.let { parseApiValue<Direction>(it) }
}

/**
 * A vehicle passing a stop (`doorkomst`).
 *
 * In stop-based responses the line and trip fields are filled in; in line-direction-based responses they are on the
 * enclosing [Trip] and [stopNumber] is filled in instead.
 */
@Serializable
public data class Passage(
    /** Unique id across all line directions. */
    @SerialName("doorkomstId") val passageId: String,
    @SerialName("dienstregelingTijdstip") val scheduledTime: LocalDateTime,
    /** Predicted time; only present for real-time requests with a prediction. */
    @SerialName("real-timeTijdstip") val realTimeTime: LocalDateTime? = null,
    @SerialName("entiteitnummer") val entityNumber: String? = null,
    @SerialName("lijnnummer") val lineNumber: String? = null,
    @SerialName("richting") val rawDirection: String? = null,
    @SerialName("ritnummer") val tripNumber: String? = null,
    @SerialName("haltenummer") val stopNumber: String? = null,
    @SerialName("bestemming") val destination: String? = null,
    @SerialName("plaatsBestemming") val placeDestination: String? = null,
    val vias: List<String> = emptyList(),
    @SerialName("bestemmingKort") val destinationShort: String? = null,
    @SerialName("bestemmingKortFrans") val destinationShortFrench: String? = null,
    /** Vehicle number. */
    @SerialName("vrtnum") val vehicleNumber: String? = null,
    @SerialName("predictionStatussen") val rawPredictionStatuses: List<String> = emptyList(),
    val status: String? = null,
    val links: List<Link> = emptyList(),
) {
    val direction: Direction? get() = rawDirection?.let { parseApiValue<Direction>(it) }
    val predictionStatuses: List<PredictionStatus> get() = rawPredictionStatuses.map { parseApiValue(it) }
}

/** The passages at one stop. */
@Serializable
public data class StopPassages(
    @SerialName("haltenummer") val stopNumber: String,
    @SerialName("doorkomsten") val passages: List<Passage> = emptyList(),
)

/** Timetable or real-time passages for a stop (`dienstregelingen`, `real-time`). */
@Serializable
public data class StopSchedule(
    @SerialName("halteDoorkomsten") val stopPassages: List<StopPassages> = emptyList(),
    @SerialName("doorkomstNotas") val passageNotes: List<Note> = emptyList(),
    @SerialName("ritNotas") val tripNotes: List<Note> = emptyList(),
    @SerialName("dienstregelingNotas") val timetableNotes: List<Note> = emptyList(),
    @SerialName("omleidingen") val diversions: List<Diversion> = emptyList(),
    val links: List<Link> = emptyList(),
)

/** A trip (`rit`) with its passages. */
@Serializable
public data class Trip(
    @SerialName("ritnummer") val tripNumber: String,
    @SerialName("bestemming") val destination: String? = null,
    @SerialName("plaatsBestemming") val placeDestination: String? = null,
    val vias: List<String> = emptyList(),
    @SerialName("bestemmingKort") val destinationShort: String? = null,
    @SerialName("bestemmingKortFrans") val destinationShortFrench: String? = null,
    @SerialName("doorkomsten") val passages: List<Passage> = emptyList(),
)

/** Timetable, real-time or single-trip passages for a line direction. */
@Serializable
public data class LineDirectionSchedule(
    @SerialName("ritDoorkomsten") val trips: List<Trip> = emptyList(),
    @SerialName("doorkomstNotas") val passageNotes: List<Note> = emptyList(),
    @SerialName("ritNotas") val tripNotes: List<Note> = emptyList(),
    @SerialName("dienstregelingNotas") val timetableNotes: List<Note> = emptyList(),
    @SerialName("omleidingen") val diversions: List<Diversion> = emptyList(),
    val links: List<Link> = emptyList(),
)

/** A page of search results. */
public data class SearchResult<T>(
    /** Total number of hits, across all pages. */
    val totalHits: Int,
    val items: List<T>,
)
