package com.vedant.admin.data.remote.mapper

import com.vedant.admin.data.remote.dto.ProfileDto
import com.vedant.admin.domain.model.Profile

/**
 * `ProfileDto` → [Profile].
 *
 * A pure function so it is trivially unit-testable and has no SDK dependency.
 * Nullability is preserved one-for-one: a nullable column maps to a nullable
 * domain field, never coalesced to a fake default that could be mistaken for
 * server truth (§5.3).
 */
fun ProfileDto.toDomain(): Profile = Profile(
    id = id,
    username = username,
    email = email,
    firstName = firstName,
    lastName = lastName,
    phone = phone,
    role = role,
)
