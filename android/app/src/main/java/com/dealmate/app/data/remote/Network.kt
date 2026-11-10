package com.dealmate.app.data.remote

import com.dealmate.app.data.remote.dto.DealMateJson
import com.dealmate.app.data.remote.dto.ErrorEnvelopeDto
import com.dealmate.app.domain.model.AppError
import com.dealmate.app.domain.model.Outcome
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

/** Server address + token as currently configured in Settings. */
data class ServerConfig(val baseUrl: String, val token: String)

fun interface ServerConfigProvider {
    /** Called on OkHttp's background threads. */
    fun get(): ServerConfig
}

/** Thrown inside the OkHttp chain when the server URL / token has not been set yet. */
class NotConfiguredException : IOException("Server is not configured")

/**
 * Retrofit is built against a placeholder host; this interceptor points every request at the server
 * configured in Settings (so changing it needs no app restart) and adds the bearer token.
 */
class ServerInterceptor(private val config: ServerConfigProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val cfg = config.get()
        val base = normalizeBaseUrl(cfg.baseUrl) ?: throw NotConfiguredException()
        val original = chain.request()
        val relative = original.url.encodedPath.trimStart('/')
        val needsAuth = relative.startsWith("api/")
        if (needsAuth && cfg.token.isBlank()) throw NotConfiguredException()

        val target = (base.resolve(relative) ?: throw NotConfiguredException())
            .newBuilder().encodedQuery(original.url.encodedQuery).build()
        val request = original.newBuilder().url(target).apply {
            header("Accept", "application/json")
            if (needsAuth) header("Authorization", "Bearer ${cfg.token.trim()}")
        }.build()
        return chain.proceed(request)
    }

    companion object {
        const val PLACEHOLDER_BASE_URL = "http://dealmate.placeholder/"

        fun normalizeBaseUrl(raw: String): HttpUrl? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            return (if (trimmed.endsWith("/")) trimmed else "$trimmed/").toHttpUrlOrNull()
        }
    }
}

object ApiErrors {
    fun fromHttp(status: Int, body: String?): AppError {
        val envelope = body?.takeIf { it.isNotBlank() }?.let {
            runCatching { DealMateJson.decodeFromString(ErrorEnvelopeDto.serializer(), it) }.getOrNull()
        }?.error
        val kind = when (status) {
            401, 403 -> AppError.Kind.UNAUTHORIZED
            404 -> AppError.Kind.NOT_FOUND
            400, 413, 415, 422 -> AppError.Kind.VALIDATION
            429 -> AppError.Kind.RATE_LIMITED
            503 -> AppError.Kind.UNAVAILABLE
            504 -> AppError.Kind.TIMEOUT
            else -> AppError.Kind.SERVER
        }
        return AppError(kind, envelope?.message?.takeIf { it.isNotBlank() }, envelope?.code)
    }

    fun fromThrowable(t: Throwable): AppError = when (t) {
        is HttpException -> fromHttp(t.code(), runCatching { t.response()?.errorBody()?.string() }.getOrNull())
        is NotConfiguredException -> AppError(AppError.Kind.NOT_CONFIGURED)
        is SocketTimeoutException -> AppError(AppError.Kind.TIMEOUT)
        is IOException -> AppError(AppError.Kind.OFFLINE)
        is SerializationException -> AppError(AppError.Kind.UNKNOWN, code = "bad_response")
        else -> AppError(AppError.Kind.UNKNOWN)
    }
}

/** Runs a Retrofit call and converts every failure into an [Outcome.Failure]. */
suspend fun <T> apiCall(block: suspend () -> T): Outcome<T> = try {
    Outcome.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Outcome.Failure(ApiErrors.fromThrowable(e))
}

/** For endpoints answering 204: fails on non-2xx. */
suspend fun apiCallUnit(block: suspend () -> retrofit2.Response<Unit>): Outcome<Unit> = apiCall {
    val response = block()
    if (!response.isSuccessful) throw HttpException(response)
}
