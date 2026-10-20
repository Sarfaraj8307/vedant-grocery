package com.vedant.admin.testing

import com.vedant.admin.core.money.Money
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.data.remote.SettingsRemoteSource
import com.vedant.admin.data.remote.dto.ProfileDto
import com.vedant.admin.data.remote.dto.StoreSettingsDto
import com.vedant.admin.data.remote.dto.StoreSettingsUpdateDto
import com.vedant.admin.domain.model.AdminIdentity
import com.vedant.admin.domain.model.OperatorProfile
import com.vedant.admin.domain.model.SessionState
import com.vedant.admin.domain.model.StoreSettings
import com.vedant.admin.domain.model.StoreSettingsDraft
import com.vedant.admin.domain.repository.AuthRepository
import com.vedant.admin.domain.repository.ProfileRepository
import com.vedant.admin.domain.repository.StoreSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Instant

/**
 * Test data and fakes for store settings and the operator profile.
 *
 * Live settings/profile access is **[BLOCKED]** (no admin creds), so the role
 * resolution (server `is_admin()`, never a cached value) and the "save returns the
 * server's echoed row" contract are proven against these fakes.
 */

private val FIXED_INSTANT = Instant.parse("2026-10-11T10:00:00Z")

fun testStoreSettings(
    id: String = "s1",
    storeName: String = "Fresh Mart",
    phone: String? = null,
    whatsapp: String? = null,
    isOpen: Boolean = true,
    deliveryFee: Money = Money(2_000),
    freeDeliveryThreshold: Money = Money(49_900),
    minOrderAmount: Money = Money(10_000),
): StoreSettings = StoreSettings(
    id = id,
    storeName = storeName,
    phone = phone,
    whatsapp = whatsapp,
    isOpen = isOpen,
    deliveryFee = deliveryFee,
    freeDeliveryThreshold = freeDeliveryThreshold,
    minOrderAmount = minOrderAmount,
    updatedAt = FIXED_INSTANT,
)

fun testOperatorProfile(
    userId: String = "u1",
    email: String? = "owner@vedant.test",
    displayName: String = "Owner",
    isAdmin: Boolean = true,
): OperatorProfile = OperatorProfile(
    userId = userId,
    email = email,
    displayName = displayName,
    username = "owner",
    phone = null,
    storedRole = "admin",
    isAdmin = isAdmin,
)

/** A scriptable [StoreSettingsRepository]. */
class FakeStoreSettingsRepository : StoreSettingsRepository {

    var getResult: Outcome<StoreSettings> = Outcome.Success(testStoreSettings())
    var saveResult: Outcome<StoreSettings> = Outcome.Success(testStoreSettings(storeName = "Vedant Fruit & Vegetables"))

    var saveCalls = 0
    var lastDraft: StoreSettingsDraft? = null

    override suspend fun getSettings(): Outcome<StoreSettings> = getResult

    override suspend fun saveSettings(draft: StoreSettingsDraft): Outcome<StoreSettings> {
        saveCalls++
        lastDraft = draft
        return saveResult
    }
}

/** A scriptable [ProfileRepository]. */
class FakeProfileRepository : ProfileRepository {

    var getResult: Outcome<OperatorProfile> = Outcome.Success(testOperatorProfile())

    var getCalls = 0

    override suspend fun getProfile(): Outcome<OperatorProfile> {
        getCalls++
        return getResult
    }
}

/** A scriptable [AuthRepository] — the only source of the server-resolved role. */
class FakeAuthRepository(
    initialState: SessionState = SessionState.SignedIn(userId = "u1", email = "owner@vedant.test"),
) : AuthRepository {

    private val _sessionState = MutableStateFlow(initialState)
    override val sessionState: StateFlow<SessionState> = _sessionState

    var userId: String? = "u1"
    var adminResult: Outcome<Boolean> = Outcome.Success(true)
    var signOutResult: Outcome<Unit> = Outcome.Success(Unit)
    var restoreResult: Outcome<AdminIdentity> = Outcome.Success(AdminIdentity("u1", "owner@vedant.test", true))

    var signOutCalls = 0
    var isAdminCalls = 0

    override suspend fun signIn(email: String, password: String): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun signOut(): Outcome<Unit> {
        signOutCalls++
        if (signOutResult is Outcome.Success) _sessionState.value = SessionState.SignedOut
        return signOutResult
    }

    override suspend fun restoreSession(): Outcome<AdminIdentity> = restoreResult

    override suspend fun isAdmin(): Outcome<Boolean> {
        isAdminCalls++
        return adminResult
    }

    override suspend fun currentUserId(): String? = userId

    override suspend fun currentAccessToken(): String? = "test-token"
}

/** A fake PostgREST seam for the settings/profile repository tests. */
class FakeSettingsRemoteSource : SettingsRemoteSource {

    var settings: StoreSettingsDto? = StoreSettingsDto(id = "s1", storeName = "Fresh Mart", isOpen = true)
    var updateResult: StoreSettingsDto? = StoreSettingsDto(id = "s1", storeName = "Vedant Fruit & Vegetables")
    var profile: ProfileDto? = ProfileDto(id = "u1", username = "owner", email = "owner@vedant.test")

    val updates = mutableListOf<Pair<String, StoreSettingsUpdateDto>>()
    var lastProfileId: String? = null

    override suspend fun readStoreSettings(): StoreSettingsDto? = settings

    override suspend fun updateStoreSettings(id: String, update: StoreSettingsUpdateDto): StoreSettingsDto? {
        updates += id to update
        return updateResult
    }

    override suspend fun readProfile(userId: String): ProfileDto? {
        lastProfileId = userId
        return profile
    }
}
