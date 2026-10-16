package de.munichspot.data

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
import kotlinx.coroutines.withContext

sealed interface FetchResult<out T> {
    data class Updated<T>(val data: T) : FetchResult<T>
    data object Unchanged : FetchResult<Nothing>
    data object Failed : FetchResult<Nothing>
}

private val client by lazy { HttpClient { install(HttpTimeout) { requestTimeoutMillis = 10_000 } } }

/** The cached download if [parse] accepts it (it returns null or throws for invalid data), otherwise null; a bad cache is deleted. */
internal suspend fun <T : Any> loadCached(cache: DataCache, parse: (String) -> T?): T? = withContext(Dispatchers.Default) {
    val text = cache.read() ?: return@withContext null
    val parsed = try {
        parse(text)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
    parsed ?: run { cache.delete(); null }
}

/**
 * With [conditional] and a cached download, asks the server only for newer data (304 -> Unchanged).
 * [parse] returns null or throws for invalid data (-> Failed); a download that is not [isNewer] is neither used nor cached (-> Unchanged).
 */
internal suspend fun <T : Any> fetchConditional(
    url: String,
    cache: DataCache,
    conditional: Boolean,
    isNewer: (T) -> Boolean = { true },
    parse: (String) -> T?,
): FetchResult<T> = try {
    val etag = if (conditional) cache.readEtag() else null
    val response = client.get(url) { if (etag != null) header(HttpHeaders.IfNoneMatch, etag) }
    when {
        etag != null && response.status == HttpStatusCode.NotModified -> FetchResult.Unchanged
        !response.status.isSuccess() -> FetchResult.Failed
        else -> {
            val text = response.bodyAsText()
            val newEtag = response.headers[HttpHeaders.ETag]
            withContext(Dispatchers.Default) {
                val parsed = parse(text)
                when {
                    parsed == null -> FetchResult.Failed
                    !isNewer(parsed) -> FetchResult.Unchanged
                    else -> {
                        cache.save(text, newEtag)
                        FetchResult.Updated(parsed)
                    }
                }
            }
        }
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    FetchResult.Failed
}
