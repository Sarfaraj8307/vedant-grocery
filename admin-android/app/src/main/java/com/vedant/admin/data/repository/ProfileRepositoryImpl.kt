package com.vedant.admin.data.repository

import com.vedant.admin.core.error.AppError
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.core.result.outcomeOf
import com.vedant.admin.data.remote.SettingsRemoteSource
import com.vedant.admin.data.remote.mapper.toDomain
import com.vedant.admin.domain.model.OperatorProfile
import com.vedant.admin.domain.model.SessionState
import com.vedant.admin.domain.repository.AuthRepository
import com.vedant.admin.domain.repository.ProfileRepository

/**
 * The real [ProfileRepository].
 *
 * It composes the `profiles` row with the **server's** admin decision:
 * [AuthRepository.isAdmin] is the only source of the role, so the screen can
 * never show a locally cached one. Two edge cases are handled explicitly:
 *  - **No session** → `Failure(AppError.SessionExpired)`, so the UI can tell the
 *    operator to sign in again instead of rendering a stale identity.
 *  - **`is_admin()` fails** → that failure is propagated, rather than defaulting
 *    to "not an administrator", which would be a wrong answer presented as truth.
 *
 * A missing `profiles` row is not an error: the identity falls back to the
 * session's email and user id, which are themselves server-confirmed.
 */
class ProfileRepositoryImpl(
    private val remote: SettingsRemoteSource,
    private val auth: AuthRepository,
) : ProfileRepository {

    override suspend fun getProfile(): Outcome<OperatorProfile> {
        val userId = auth.currentUserId()?.takeIf { it.isNotBlank() }
            ?: return Outcome.Failure(AppError.SessionExpired)

        val isAdmin = when (val admin = auth.isAdmin()) {
            is Outcome.Success -> admin.value
            is Outcome.Failure -> return Outcome.Failure(admin.error)
        }

        return outcomeOf {
            val sessionEmail = (auth.sessionState.value as? SessionState.SignedIn)?.email
            val row = remote.readProfile(userId)?.toDomain()
            OperatorProfile(
                userId = userId,
                email = row?.email?.takeIf { it.isNotBlank() } ?: sessionEmail,
                displayName = row?.displayName?.takeIf { it.isNotBlank() } ?: sessionEmail ?: userId,
                username = row?.username,
                phone = row?.phone,
                storedRole = row?.role,
                isAdmin = isAdmin,
            )
        }
    }
}
