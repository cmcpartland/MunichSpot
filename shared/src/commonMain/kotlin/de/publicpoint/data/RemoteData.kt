package de.publicpoint.data

import de.publicpoint.Log
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

sealed interface FetchResult<out T> {
    data class Updated<T>(val data: T) : FetchResult<T>
    data object Unchanged : FetchResult<Nothing>
    data object Failed : FetchResult<Nothing>
}

private val defaultClient by lazy {
    HttpClient {
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }
    }
}

/** The cached download if [parse] accepts it (it returns null or throws for invalid data), otherwise null; a bad cache is deleted. */
internal suspend fun <T : Any> loadCached(cache: DataCache, parse: (String) -> T?): T? = withContext(Dispatchers.Default) {
    val text = withContext(Dispatchers.IO) { cache.read() } ?: return@withContext null
    val parsed = try {
        parse(text).also { if (it == null) Log.w(cache.name, "cache implausible, deleted") }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(cache.name, "cache parse failed, deleted", e)
        null
    }
    parsed ?: run { withContext(Dispatchers.IO) { cache.delete() }; null }
}

/**
 * With [conditional] and a cached download, asks the server only for newer data (304 -> Unchanged).
 * [parse] returns null or throws for invalid data (-> Failed); a download that is not [isNewer] is not used but still cached with its new ETag (-> Unchanged),
 * so the next request can hit.
 */
internal suspend fun <T : Any> fetchConditional(
    url: String,
    cache: DataCache,
    conditional: Boolean,
    isNewer: (T) -> Boolean = { true },
    client: HttpClient = defaultClient,
    parse: (String) -> T?,
): FetchResult<T> = withContext(Dispatchers.Default) {
    try {
        val etag = if (conditional) withContext(Dispatchers.IO) { cache.readEtag() } else null
        val tag = cache.name
        val response = client.get(url) { if (etag != null) header(HttpHeaders.IfNoneMatch, etag) }
        when {
            etag != null && response.status == HttpStatusCode.NotModified -> {
                Log.d(tag, "unchanged (etag hit)")
                FetchResult.Unchanged
            }
            !response.status.isSuccess() -> {
                Log.w(tag, "download failed: HTTP ${response.status.value}")
                FetchResult.Failed
            }
            else -> {
                val text = response.bodyAsText()
                val newEtag = response.headers[HttpHeaders.ETag]
                Log.d(tag, "downloaded (etag sent: ${etag != null}, new etag: ${newEtag != null})")
                val parsed = try {
                    parse(text)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(tag, "parse failed", e)
                    return@withContext FetchResult.Failed
                }
                when {
                    parsed == null -> {
                        Log.w(tag, "download implausible")
                        FetchResult.Failed
                    }
                    !isNewer(parsed) -> {
                        Log.d(tag, "download not newer")
                        withContext(Dispatchers.IO) { cache.save(text, newEtag) }
                        FetchResult.Unchanged
                    }
                    else -> {
                        withContext(Dispatchers.IO) { cache.save(text, newEtag) }
                        FetchResult.Updated(parsed)
                    }
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(cache.name, "download failed", e)
        FetchResult.Failed
    }
}
