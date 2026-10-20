package com.vedant.admin.data.remote.mapper

import com.vedant.admin.core.logging.AppLog
import com.vedant.admin.core.money.Money
import com.vedant.admin.data.remote.dto.StoreSettingsDto
import com.vedant.admin.data.remote.dto.StoreSettingsUpdateDto
import com.vedant.admin.domain.model.StoreSettings
import com.vedant.admin.domain.model.StoreSettingsDraft

private const val TAG = "SettingsMapper"

/**
 * `store_settings` DTO ↔ domain.
 *
 * Reads are tolerant (§5.3): a nullable column maps to a nullable domain field,
 * and a missing money column is logged and rendered as zero rather than throwing.
 * A blank `phone`/`whatsapp` becomes `null` so the form shows an empty field
 * instead of a string of spaces.
 */

fun StoreSettingsDto.toDomain(): StoreSettings = StoreSettings(
    id = id.orEmpty(),
    storeName = storeName.orEmpty(),
    phone = phone?.takeIf { it.isNotBlank() },
    whatsapp = whatsapp?.takeIf { it.isNotBlank() },
    isOpen = isOpen ?: true,
    deliveryFee = deliveryFee.orZero("delivery_fee"),
    freeDeliveryThreshold = freeDeliveryThreshold.orZero("free_delivery_threshold"),
    minOrderAmount = minOrderAmount.orZero("min_order_amount"),
    updatedAt = parseInstantOrNull(updatedAt),
)

/** The write payload — only the editable columns are set. */
fun StoreSettingsDraft.toWriteFields(): StoreSettingsUpdateDto = StoreSettingsUpdateDto(
    storeName = storeName.trim(),
    phone = phone?.trim()?.takeIf { it.isNotEmpty() },
    whatsapp = whatsapp?.trim()?.takeIf { it.isNotEmpty() },
    isOpen = isOpen,
    deliveryFee = deliveryFee,
    freeDeliveryThreshold = freeDeliveryThreshold,
    minOrderAmount = minOrderAmount,
)

/** Back to an editable draft — used to seed the form from the server's row. */
fun StoreSettings.toDraft(): StoreSettingsDraft = StoreSettingsDraft(
    id = id,
    storeName = storeName,
    phone = phone,
    whatsapp = whatsapp,
    isOpen = isOpen,
    deliveryFee = deliveryFee,
    freeDeliveryThreshold = freeDeliveryThreshold,
    minOrderAmount = minOrderAmount,
)

private fun Money?.orZero(field: String): Money {
    if (this == null) {
        AppLog.w(TAG, "expected money column '$field' was absent; rendering 0")
        return Money.ZERO
    }
    return this
}
