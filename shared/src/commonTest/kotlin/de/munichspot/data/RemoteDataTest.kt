package de.munichspot.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RemoteDataTest {
    private val dir = Path(SystemTemporaryDirectory, "munichspot-test-${kotlin.random.Random.nextLong().toULong()}")
    private val cache = DataCache("test-latest.txt") { dir.toString() }

    @BeforeTest
    fun setUp() = SystemFileSystem.createDirectories(dir)

    @AfterTest
    fun tearDown() {
        SystemFileSystem.list(dir).forEach { SystemFileSystem.delete(it) }
        SystemFileSystem.delete(dir)
    }

    private fun client(timeoutMs: Long = 10_000, handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData) =
        HttpClient(MockEngine(handler)) { install(HttpTimeout) { requestTimeoutMillis = timeoutMs } }

    private val parse: (String) -> String? = { if (it.startsWith("ok")) it else null }

    private suspend fun fetch(client: HttpClient, conditional: Boolean = true, isNewer: (String) -> Boolean = { true }, parser: (String) -> String? = parse) =
        fetchConditional("https://example.test/data", cache, conditional, isNewer, client, parser)

    @Test
    fun okResponseIsUpdatedAndCachedWithEtag() = runTest {
        val result = fetch(client { respond("ok-1", HttpStatusCode.OK, headersOf(HttpHeaders.ETag, "\"v1\"")) })
        assertEquals(FetchResult.Updated("ok-1"), result)
        assertEquals("ok-1", cache.read())
        assertEquals("\"v1\"", cache.readEtag())
    }

    @Test
    fun cachedEtagIsSentAnd304IsUnchanged() = runTest {
        cache.save("ok-old", "\"v1\"")
        var sent: String? = null
        val result = fetch(client { sent = it.headers[HttpHeaders.IfNoneMatch]; respond("", HttpStatusCode.NotModified) })
        assertEquals("\"v1\"", sent)
        assertEquals(FetchResult.Unchanged, result)
        assertEquals("ok-old", cache.read())
        assertEquals("\"v1\"", cache.readEtag())
    }

    @Test
    fun nonConditionalFetchSendsNoEtag() = runTest {
        cache.save("ok-old", "\"v1\"")
        var sent: String? = "unset"
        fetch(client { sent = it.headers[HttpHeaders.IfNoneMatch]; respond("ok-new", HttpStatusCode.OK) }, conditional = false)
        assertNull(sent)
        assertEquals("ok-new", cache.read())
        assertNull(cache.readEtag())
    }

    @Test
    fun serverErrorFailsAndKeepsCache() = runTest {
        cache.save("ok-old", "\"v1\"")
        val result = fetch(client { respondError(HttpStatusCode.InternalServerError) })
        assertEquals(FetchResult.Failed, result)
        assertEquals("ok-old", cache.read())
        assertEquals("\"v1\"", cache.readEtag())
    }

    @Test
    fun parseThrowingFailsAndKeepsCache() = runTest {
        cache.save("ok-old", "\"v1\"")
        val result = fetch(client { respond("garbage", HttpStatusCode.OK, headersOf(HttpHeaders.ETag, "\"v2\"")) }, parser = { error("bad") })
        assertEquals(FetchResult.Failed, result)
        assertEquals("ok-old", cache.read())
        assertEquals("\"v1\"", cache.readEtag())
    }

    @Test
    fun implausibleDownloadFailsAndKeepsCache() = runTest {
        cache.save("ok-old", "\"v1\"")
        val result = fetch(client { respond("garbage", HttpStatusCode.OK, headersOf(HttpHeaders.ETag, "\"v2\"")) })
        assertEquals(FetchResult.Failed, result)
        assertEquals("ok-old", cache.read())
        assertEquals("\"v1\"", cache.readEtag())
    }

    @Test
    fun notNewerIsUnchangedButCachedWithNewEtag() = runTest {
        cache.save("ok-old", "\"v1\"")
        val result = fetch(client { respond("ok-same", HttpStatusCode.OK, headersOf(HttpHeaders.ETag, "\"v2\"")) }, isNewer = { false })
        assertEquals(FetchResult.Unchanged, result)
        assertEquals("ok-same", cache.read())
        assertEquals("\"v2\"", cache.readEtag())
    }

    @Test
    fun timeoutFailsAndKeepsCache() = runTest {
        cache.save("ok-old", "\"v1\"")
        val result = fetch(client(timeoutMs = 50) { delay(2_000); respond("ok-late", HttpStatusCode.OK) })
        assertEquals(FetchResult.Failed, result)
        assertEquals("ok-old", cache.read())
    }

    @Test
    fun etagWithoutCacheFileIsIgnored() = runTest {
        cache.save("ok-old", "\"v1\"")
        SystemFileSystem.delete(Path(dir, "test-latest.txt"))
        assertNull(cache.readEtag())
    }

    @Test
    fun loadCachedReturnsValidCache() = runTest {
        cache.save("ok-cached", null)
        assertEquals("ok-cached", loadCached(cache, parse))
        assertEquals("ok-cached", cache.read())
    }

    @Test
    fun loadCachedWithoutCacheIsNull() = runTest {
        assertNull(loadCached(cache, parse))
    }

    @Test
    fun implausibleCacheIsDeleted() = runTest {
        cache.save("junk", "\"v1\"")
        assertNull(loadCached(cache, parse))
        assertNull(cache.read())
        assertNull(cache.readEtag())
    }

    @Test
    fun unparsableCacheIsDeleted() = runTest {
        cache.save("junk", "\"v1\"")
        assertNull(loadCached<String>(cache) { error("bad") })
        assertNull(cache.read())
    }
}
