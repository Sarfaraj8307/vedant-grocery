package com.vedant.admin.feature.slots

import com.vedant.admin.core.error.AppError
import com.vedant.admin.domain.model.DeliverySlot
import com.vedant.admin.domain.model.SlotLoad
import com.vedant.admin.ui.components.ContentPhase
import kotlinx.datetime.LocalDate

/** The add/edit form for one slot. Times are IST wall-clock `HH:mm`. */
data class SlotEditState(
    val id: String? = null,
    val name: String = "",
    val date: LocalDate? = null,
    val startTime: String = "08:00",
    val endTime: String = "10:00",
    val capacity: String = "",
    val sortOrder: String = "0",
    val isActive: Boolean = true,
    val isSaving: Boolean = false,
    val fieldError: String? = null,
    val error: AppError? = null,
) {

    val isCreate: Boolean get() = id == null

    companion object {
        fun from(slot: DeliverySlot): SlotEditState = SlotEditState(
            id = slot.id,
            name = slot.name.orEmpty(),
            date = slot.day,
            startTime = slot.startTimeIst,
            endTime = slot.endTimeIst,
            capacity = slot.capacity.toString(),
            sortOrder = slot.sortOrder.toString(),
            isActive = slot.isActive,
        )
    }
}

/**
 * Immutable state for the slots screen.
 *
 * [loads] is keyed by slot id; a missing entry means the `admin_slot_load` RPC
 * did not report that slot (its shape is BLOCKED), and the row simply omits the
 * utilisation bar rather than inventing a number.
 */
data class SlotsUiState(
    val phase: ContentPhase = ContentPhase.Loading,
    val days: Int = 7,
    val slots: List<DeliverySlot> = emptyList(),
    val loads: Map<String, SlotLoad> = emptyMap(),
    val loadError: AppError? = null,
    val editor: SlotEditState? = null,
    val deletingId: String? = null,
    val isEnsuring: Boolean = false,
    val actionError: AppError? = null,
) {

    fun loadFor(slotId: String): SlotLoad? = loads[slotId]

    val isBusy: Boolean get() = editor?.isSaving == true || deletingId != null || isEnsuring
}
