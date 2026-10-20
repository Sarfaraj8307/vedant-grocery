package com.vedant.admin.ui.navigation

import com.vedant.admin.domain.model.SessionState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The gate mapping is small but it is the thing that decides whether an operator
 * reaches the app or is refused. It is asserted directly, including the case that
 * matters most: a signed-in user the server did not confirm as admin must be
 * refused, never admitted.
 */
class SessionViewModelTest {

    private val signedIn = SessionState.SignedIn(userId = "user-1", email = "owner@example.com")

    @Test
    fun `signed in and confirmed admin stays SignedIn`() {
        assertEquals(signedIn, SessionViewModel.resolve(signedIn, isAdmin = true))
    }

    @Test
    fun `signed in but not admin becomes NotAuthorized`() {
        assertEquals(SessionState.NotAuthorized, SessionViewModel.resolve(signedIn, isAdmin = false))
    }

    @Test
    fun `signed out is unchanged regardless of the admin flag`() {
        assertEquals(SessionState.SignedOut, SessionViewModel.resolve(SessionState.SignedOut, true))
        assertEquals(SessionState.SignedOut, SessionViewModel.resolve(SessionState.SignedOut, false))
    }

    @Test
    fun `unknown stays unknown so the splash keeps its spinner`() {
        assertEquals(SessionState.Unknown, SessionViewModel.resolve(SessionState.Unknown, true))
    }

    @Test
    fun `an already-refused state is not upgraded by a stale admin flag`() {
        assertEquals(
            SessionState.NotAuthorized,
            SessionViewModel.resolve(SessionState.NotAuthorized, isAdmin = true),
        )
    }
}
