package com.vedant.admin.core.result

import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.error.ErrorMapper
import kotlinx.coroutines.CancellationException

/**
 * The result of an operation that can fail in a *modelled* way.
 *
 * Repositories never throw across the boundary; they return `Outcome`. A
 * `Failure` always carries an [AppError] from the closed taxonomy, so the UI has
 * exactly one place to look for the reason and one place (`ErrorCopy`) to turn
 * it into a sentence.
 */
sealed interface Outcome<out T> {

    data class Success<T>(val value: T) : Outcome<T>

    data class Failure(val error: AppError) : Outcome<Nothing>
}

val Outcome<*>.isSuccess: Boolean get() = this is Outcome.Success

val Outcome<*>.isFailure: Boolean get() = this is Outcome.Failure

fun <T> Outcome<T>.getOrNull(): T? = (this as? Outcome.Success)?.value

fun Outcome<*>.errorOrNull(): AppError? = (this as? Outcome.Failure)?.error

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

inline fun <T, R> Outcome<T>.flatMap(transform: (T) -> Outcome<R>): Outcome<R> = when (this) {
    is Outcome.Success -> transform(value)
    is Outcome.Failure -> this
}

inline fun <T> Outcome<T>.onSuccess(action: (T) -> Unit): Outcome<T> {
    if (this is Outcome.Success) action(value)
    return this
}

inline fun <T> Outcome<T>.onFailure(action: (AppError) -> Unit): Outcome<T> {
    if (this is Outcome.Failure) action(error)
    return this
}

/**
 * Runs [block] and converts any thrown exception into `Outcome.Failure` via
 * [ErrorMapper].
 *
 * `CancellationException` is deliberately re-thrown: swallowing it would break
 * structured concurrency (a cancelled `viewModelScope` must actually cancel).
 */
suspend fun <T> outcomeOf(block: suspend () -> T): Outcome<T> =
    try {
        Outcome.Success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        Outcome.Failure(ErrorMapper.map(throwable))
    }
