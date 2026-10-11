# delijn-api

Kotlin client for the [De Lijn](https://www.delijn.be) Open Data APIs published on [data.delijn.be](https://data.delijn.be):

| API | Client | Endpoints |
|---|---|---|
| Open Data V1 Core API (`DLKernOpenData/api/v1`) | `DeLijnCoreClient` | 46 |
| Open Data V1 Search API (`DLZoekOpenData/v1`) | `DeLijnSearchClient` | 2 |

The GTFS Static, GTFS Realtime, NeTEx and BLTAC exports are out of scope. They are plain file and protobuf
downloads that existing GTFS and NeTEx tooling already handles.

- One runtime dependency (`kotlinx-serialization-json`); HTTP via the JDK's `java.net.http.HttpClient`.
- Java 17+. Usable from Java (`@JvmOverloads` on every optional parameter).
- Models are validated against live API responses, not just the OpenAPI spec (see [API quirks](#api-quirks)).

## Installation

```xml
<dependency>
    <groupId>be.gtfs</groupId>
    <artifactId>delijn-api</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Getting API keys

1. Sign up at [data.delijn.be](https://data.delijn.be).
2. Subscribe to the **Open Data Free** product. It includes both the Core and the Search API, so a single subscription
   key works for both clients.
3. Find the key under *Profile*.

## Usage

```kotlin
val core = DeLijnCoreClient(System.getenv("DELIJN_CORE_API_KEY"))
val search = DeLijnSearchClient(System.getenv("DELIJN_SEARCH_API_KEY"))

// Find a stop and show its next departures.
val stop = search.searchStops("Gent Sint-Pietersstation", maxHits = 1).items.first()
core.stopRealTime(stop.key, maxPassages = 5).stopPassages.flatMap { it.passages }.forEach {
    println("${it.lineNumber} → ${it.destination}: ${it.realTimeTime ?: it.scheduledTime} ${it.predictionStatuses}")
}

// Lines, directions, timetables.
val tram7 = LineKey("2", "7")
val direction = core.lineDirections(tram7).first()
val trips = core.lineDirectionTimetable(direction.key, LocalDate.now()).trips

// Stops near a coordinate.
core.stopsNearby(51.0362, 3.7093, maxStops = 5, radius = 300)

// Several resources in one call.
core.stopsRealTime(listOf(StopKey("2", "204911"), StopKey("2", "205914")))
```

From Java:

```java
var core = new DeLijnCoreClient(System.getenv("DELIJN_CORE_API_KEY"));
List<Line> lines = core.lines();
StopSchedule rt = core.stopRealTime(new StopKey("2", "204911"));
```

### Conventions

- **Naming:** API, class and property names are English. Every property's `@SerialName` holds the original Dutch
  JSON name, and every method's KDoc names the endpoint it calls (e.g. ``GET /haltes/{entiteitnummer}/{haltenummer}``).
- **Identifiers are `String`s**, both in models and in method parameters, so you can pass a model's value straight
  back into a call (`stop.key`, `line.key`, `lineDirection.key`).
- **Envelopes are unwrapped:** list endpoints return `List<T>`. Timetable and real-time endpoints return a schedule
  object, because it also carries notes and diversions.
- **Dates and times** are `java.time.LocalDate` / `LocalDateTime` in Europe/Brussels local time. The API sends no
  offset.
- **Enums never break deserialization.** Each enum-typed property has a raw `String` companion. If De Lijn sends a
  value this library doesn't know yet, the typed property returns `UNKNOWN` and the raw value is still there:

  ```kotlin
  stop.language      // StopLanguage.UNKNOWN
  stop.rawLanguage   // "SOMETHING_NEW"
  ```

- **Errors:** any non-2xx response throws `DeLijnApiException`, which has `statusCode`, the API's message and the raw
  `body`.
- **Thread-safety:** the clients are stateless and thread-safe. Calls are blocking; wrap them in
  `withContext(Dispatchers.IO)` or a virtual thread if you need async behaviour.

## API quirks

These were found by comparing the OpenAPI spec with live responses (October 2026).

| Quirk | How this library handles it |
|---|---|
| The Search API returns stop coordinates with `latitude` and `longitude` swapped. | `DeLijnSearchClient` swaps them back when `latitude < longitude`. In Flanders the latitude is always the larger value, so this stays correct if De Lijn fixes the bug. |
| The five `berichten` endpoints in the Core spec return 404 from the backend. | Not implemented. |
| Disruption (`storingen`) endpoints put their list under `storingen` or `omleidingen`, depending on the endpoint. | Both fields are read. |
| `/lijnen/lijst/{…}/lijnkleuren` returns the misspelled field `lijnLijnkleurCodesijst`. | That name is read. |
| `/gemeenten/lijst/{…}/haltes` separates numbers with `,`; every other list endpoint uses `_`. | Handled internally. |
| Identifiers are sometimes JSON numbers and sometimes strings (e.g. `lijnnummer` in passages). | Everything decodes to `String`. |
| Many fields marked "required" in the spec are missing in practice (e.g. stops embedded in diversions only have their key). | Properties are nullable unless every observed response contained them. |
| `/kleuren` returns `rgb` and `hex`, which the spec doesn't list. | Included in `Color`. |
| Error bodies come in two shapes: `{"code","boodschap"}` from the backend and `{"statusCode","message"}` from the gateway. | Both are parsed into `DeLijnApiException.message`. |

The OpenAPI specs this client was built against are kept in [`spec/`](spec).

## Building and testing

```bash
./mvnw verify
```

Unit tests run every endpoint against a local mock server that serves trimmed real responses from
`src/test/resources/fixtures`. The live integration test (`LiveApiTest`) calls the real API and only runs when keys
are provided:

```bash
DELIJN_CORE_API_KEY=... DELIJN_SEARCH_API_KEY=... ./mvnw test
```

### Releasing to Maven Central

Releases are built, signed and uploaded by GitHub Actions ([`release.yml`](.github/workflows/release.yml)) when a
version tag is pushed:

```bash
git tag v0.1.0
git push origin v0.1.0
```

The version comes from the tag; `pom.xml` stays on `-SNAPSHOT`. The workflow uploads to the Central Portal and waits
for validation, but does **not** publish. Review the deployment on
[central.sonatype.com](https://central.sonatype.com/publishing/deployments) and click *Publish*.

Required repository secrets:

| Secret | Value |
|---|---|
| `CENTRAL_TOKEN_USERNAME` / `CENTRAL_TOKEN_PASSWORD` | Central Portal user token |
| `GPG_PRIVATE_KEY` | ASCII-armored signing key (`gpg --export-secret-keys --armor <FPR>`) |
| `GPG_PASSPHRASE` | Passphrase of that key |
| `DELIJN_CORE_API_KEY` / `DELIJN_SEARCH_API_KEY` | Optional, enables the live tests |

To release from your own machine instead, put the token in a `central` server entry in `~/.m2/settings.xml` and run
`./mvnw -P release deploy`.

## License

[MIT](LICENSE). This project is not affiliated with De Lijn. Use of the API is subject to De Lijn's terms on
[data.delijn.be](https://data.delijn.be).
