package be.gtfs.delijn

/**
 * Enumerations used by the De Lijn APIs.
 *
 * Every enum has an [UNKNOWN] constant. Models never store these enums directly: they keep the raw
 * string the API sent (e.g. [LineDirection.rawDirection]) and expose the enum as a computed property.
 * A value De Lijn adds later therefore maps to `UNKNOWN` without breaking deserialization, and the raw
 * value stays available.
 */
public interface ApiValue {
    /** The literal value used by the De Lijn API, or `null` for `UNKNOWN`. */
    public val apiValue: String?
}

internal inline fun <reified E> parseApiValue(raw: String?): E where E : Enum<E>, E : ApiValue =
    enumValues<E>().firstOrNull { it.apiValue != null && it.apiValue == raw }
        ?: enumValues<E>().first { it.apiValue == null }

/** Direction of a line (`richting`). */
public enum class Direction(override val apiValue: String?) : ApiValue {
    /** `HEEN` */
    OUTBOUND("HEEN"),

    /** `TERUG` */
    INBOUND("TERUG"),
    UNKNOWN(null),
}

/** Mode of transport (`vervoertype`, `bedieningsTypes`). */
public enum class TransportType(override val apiValue: String?) : ApiValue {
    BUS("BUS"),
    TRAM("TRAM"),
    METRO("METRO"),

    /** `TREIN` */
    TRAIN("TREIN"),

    /** `ALLE` */
    ALL("ALLE"),
    UNKNOWN(null),
}

/** Kind of service a line provides (`bedieningtype`). */
public enum class ServiceType(override val apiValue: String?) : ApiValue {
    /** `NORMAAL` */
    REGULAR("NORMAAL"),

    /** `SNELDIENST` */
    EXPRESS("SNELDIENST"),

    /** `SCHOOLBUS` */
    SCHOOL_BUS("SCHOOLBUS"),

    /** `FABRIEKSLIJN` */
    FACTORY_LINE("FABRIEKSLIJN"),

    /** `TECHNISCHE_LIJN` */
    TECHNICAL_LINE("TECHNISCHE_LIJN"),

    /** `BELBUS` */
    DIAL_A_BUS("BELBUS"),
    FLEX("FLEX"),
    UNKNOWN(null),
}

/** Language of a stop's name (`taal`). */
public enum class StopLanguage(override val apiValue: String?) : ApiValue {
    /** `NEDERLANDS` */
    DUTCH("NEDERLANDS"),

    /** `FRANS` */
    FRENCH("FRANS"),

    /** `TWEETALIG` */
    BILINGUAL("TWEETALIG"),
    UNKNOWN(null),
}

/** Classification of a stop (`classificatie`). */
public enum class StopClassification(override val apiValue: String?) : ApiValue {
    /** `REGULIER` */
    REGULAR("REGULIER"),

    /** `TIJDELIJK` */
    TEMPORARY("TIJDELIJK"),
    FLEX("FLEX"),

    /** `COMBI` */
    COMBINED("COMBI"),
    UNKNOWN(null),
}

/** Accessibility of a stop (`halteToegankelijkheden`). */
public enum class Accessibility(override val apiValue: String?) : ApiValue {
    /** `MOTORISCHE_BEPERKING`: accessible for people with reduced mobility. */
    REDUCED_MOBILITY("MOTORISCHE_BEPERKING"),

    /** `MOTORISCH_MET_ASSIST`: accessible for people with reduced mobility, with assistance. */
    REDUCED_MOBILITY_WITH_ASSISTANCE("MOTORISCH_MET_ASSIST"),

    /** `VISUELE_BEPERKING`: accessible for visually impaired people. */
    VISUALLY_IMPAIRED("VISUELE_BEPERKING"),
    UNKNOWN(null),
}

/** Status of a (real-time) passage prediction (`predictionStatussen`). */
public enum class PredictionStatus(override val apiValue: String?) : ApiValue {
    /** `GESCHRAPT` */
    CANCELLED("GESCHRAPT"),

    /** `VERSTREKEN` */
    PASSED("VERSTREKEN"),
    REALTIME("REALTIME"),

    /** `GEENREALTIME` */
    NO_REALTIME("GEENREALTIME"),
    UNKNOWN(null),
}
