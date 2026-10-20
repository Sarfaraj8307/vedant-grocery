package com.vedant.admin.domain.repository

import com.vedant.admin.core.result.Outcome
import com.vedant.admin.domain.model.OperatorProfile

/**
 * The signed-in operator's identity and **server-resolved** role.
 *
 * The role is always the `is_admin()` answer, composed with the `profiles` row —
 * never a locally cached role. When there is no session, [getProfile] returns
 * `Failure(AppError.SessionExpired)` so the UI can tell the operator to sign in
 * again rather than showing a stale identity.
 */
interface ProfileRepository {

    /** The operator's profile with the server-confirmed admin status. */
    suspend fun getProfile(): Outcome<OperatorProfile>
}
