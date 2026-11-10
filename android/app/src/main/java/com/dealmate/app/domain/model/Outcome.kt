package com.dealmate.app.domain.model

import java.time.Instant

/** Result of a repository call. Errors are values, never thrown across the domain boundary. */
sealed interface Outcome<out T> {
    data class Success<T>(
        val data: T,
        /** true when the network failed and this is the last locally cached copy. */
        val fromCache: Boolean = false,
        val cachedAt: Instant? = null,
    ) : Outcome<T>

    data class Failure(val error: AppError) : Outcome<Nothing>
}

data class AppError(
    val kind: Kind,
    /** Human readable (Vietnamese) message from the server's error envelope, when there is one. */
    val message: String? = null,
    val code: String? = null,
) {
    enum class Kind {
        NOT_CONFIGURED, UNAUTHORIZED, OFFLINE, TIMEOUT, RATE_LIMITED, UNAVAILABLE,
        NOT_FOUND, VALIDATION, SERVER, UNKNOWN,
    }

    /** Failures where a locally cached copy is an acceptable substitute. */
    val isConnectivity: Boolean get() = kind == Kind.OFFLINE || kind == Kind.TIMEOUT
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(data), fromCache, cachedAt)
    is Outcome.Failure -> this
}

fun <T> Outcome<T>.dataOrNull(): T? = (this as? Outcome.Success)?.data
fun Outcome<*>.errorOrNull(): AppError? = (this as? Outcome.Failure)?.error
