package be.gtfs.delijn

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

/** Minimal local HTTP server that records the last request and answers with a canned response. */
class MockServer : AutoCloseable {
    var status = 200
    var body = "{}"

    /** Raw path plus raw query of the last request, e.g. `/haltes/2/1?datum=2026-01-01`. */
    var lastRequest: String? = null
    var lastApiKey: String? = null

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            val uri = exchange.requestURI
            lastRequest = uri.rawPath + (uri.rawQuery?.let { "?$it" } ?: "")
            lastApiKey = exchange.requestHeaders.getFirst("Ocp-Apim-Subscription-Key")
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        start()
    }

    val url: String get() = "http://127.0.0.1:${server.address.port}"

    /** Respond with `src/test/resources/fixtures/<name>.json`. */
    fun respondWithFixture(name: String) {
        status = 200
        body = fixture(name)
    }

    override fun close() = server.stop(0)
}

fun fixture(name: String): String =
    requireNotNull(MockServer::class.java.getResource("/fixtures/$name.json")) { "missing fixture $name" }.readText()
