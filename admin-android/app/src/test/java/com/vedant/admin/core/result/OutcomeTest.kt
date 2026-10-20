package com.vedant.admin.core.result

import com.vedant.admin.core.error.AppError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * `Outcome` is the boundary type every repository returns (architecture §9). Two
 * behaviours are load-bearing and asserted here:
 *
 *  - [outcomeOf] turns a thrown exception into `Failure` via the error taxonomy,
 *    so no exception escapes the data layer unmodelled.
 *  - [outcomeOf] **re-throws** `CancellationException`, because swallowing it
 *    would break structured concurrency — a cancelled `viewModelScope` must
 *    actually cancel.
 */
class OutcomeTest {

    @Test
    fun `a successful block becomes Success`() = runTest {
        val outcome = outcomeOf { 42 }
        assertTrue(outcome is Outcome.Success)
        assertEquals(42, (outcome as Outcome.Success).value)
    }

    @Test
    fun `a thrown unknown exception becomes Failure(Unknown)`() = runTest {
        val outcome = outcomeOf<Int> { error("boom") }
        assertTrue(outcome is Outcome.Failure)
        assertTrue((outcome as Outcome.Failure).error is AppError.Unknown)
    }

    @Test
    fun `a local require failure becomes Failure(Validation)`() = runTest {
        val outcome = outcomeOf<Int> {
            require(false) { "A reason is required." }
            0
        }
        val error = (outcome as Outcome.Failure).error
        assertTrue("expected Validation, was $error", error is AppError.Validation)
        assertEquals("A reason is required.", (error as AppError.Validation).message)
    }

    @Test
    fun `cancellation is re-thrown and never swallowed into a Failure`() = runTest {
        try {
            outcomeOf<Int> { throw CancellationException("cancelled") }
            fail("outcomeOf swallowed a CancellationException")
        } catch (expected: CancellationException) {
            assertEquals("cancelled", expected.message)
        }
    }

    @Test
    fun `isSuccess and isFailure agree with the variant`() {
        assertTrue(Outcome.Success(1).isSuccess)
        assertTrue(Outcome.Failure(AppError.Network).isFailure)
    }

    @Test
    fun `getOrNull returns the value or null`() {
        assertEquals(7, Outcome.Success(7).getOrNull())
        assertNull(Outcome.Failure(AppError.Network).getOrNull())
    }

    @Test
    fun `errorOrNull returns the error or null`() {
        assertEquals(AppError.Network, Outcome.Failure(AppError.Network).errorOrNull())
        assertNull(Outcome.Success(7).errorOrNull())
    }

    @Test
    fun `map transforms a Success and passes a Failure through unchanged`() {
        assertEquals(Outcome.Success("2"), Outcome.Success(2).map { it.toString() })
        val failure: Outcome<Int> = Outcome.Failure(AppError.Unauthorized)
        assertEquals(failure, failure.map { it * 2 })
    }

    @Test
    fun `flatMap chains a Success and short-circuits a Failure`() {
        assertEquals(
            Outcome.Success(4),
            Outcome.Success(2).flatMap { Outcome.Success(it * 2) },
        )
        val failure: Outcome<Int> = Outcome.Failure(AppError.Network)
        assertEquals(failure, failure.flatMap { Outcome.Success(it) })
    }

    @Test
    fun `onSuccess and onFailure run only for their own variant`() {
        var seen: Int? = null
        Outcome.Success(5).onSuccess { seen = it }.onFailure { seen = -1 }
        assertEquals(5, seen)

        var error: AppError? = null
        Outcome.Failure(AppError.SessionExpired).onSuccess { error = AppError.Network }
            .onFailure { error = it }
        assertEquals(AppError.SessionExpired, error)
    }
}
