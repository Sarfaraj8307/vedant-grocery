package com.vedant.admin.feature.slots

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedant.admin.core.logging.AppLog
import com.vedant.admin.core.result.Outcome
import com.vedant.admin.core.time.AppClock
import com.vedant.admin.domain.model.DeliverySlot
import com.vedant.admin.domain.model.DeliverySlotDraft
import com.vedant.admin.domain.model.SlotLoad
import com.vedant.admin.domain.repository.SlotRepository
import com.vedant.admin.ui.components.ContentPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

private const val TAG = "Slots"

/**
 * Drives the delivery-slots screen.
 *
 * The screen reads the day's schedule and the utilisation in one pass: a failure
 * to read the load is **not** a failure of the screen — the slots still render,
 * just without their bars. A write returns the server-confirmed slot, after which
 * the whole window is re-read (so the day grouping and the load stay consistent).
 */
class SlotsViewModel(private val slotRepository: SlotRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SlotsUiState())
    val uiState: StateFlow<SlotsUiState> = _uiState.asStateFlow()

    private var started = false

    fun start() {
        if (started) return
        started = true
        bootstrap()
    }

    fun reload() = load()

    /**
     * Materialise the recurring window, then read it.
     *
     * This is what makes the dates roll forward **by themselves**. Every time the
     * screen opens, `ensure_delivery_slots` tops up today … today+[days] in IST,
     * so the operator never has to press "generate" to see tomorrow appear. The
     * server side is idempotent (it skips any `starts_at` that already exists) and
     * skips windows that have already closed, so calling it on every open is safe.
     *
     * A failure here is **not fatal**: the existing rows are still read and shown,
     * and the toolbar action retries. Slots that are already there must never be
     * hidden just because topping up failed.
     */
    private fun bootstrap() {
        _uiState.update { it.copy(phase = ContentPhase.Loading, loadError = null, isEnsuring = true) }
        viewModelScope.launch {
            val days = _uiState.value.days
            runCatching { slotRepository.ensureSlots(windowStart(), days) }
            _uiState.update { it.copy(isEnsuring = false) }
            load()
        }
    }

    /** `ensure_delivery_slots` — materialise the recurring slots, then re-read. */
    fun ensureSlots() {
        val state = _uiState.value
        if (state.isEnsuring) return
        _uiState.update { it.copy(isEnsuring = true, actionError = null) }
        viewModelScope.launch {
            when (val outcome = slotRepository.ensureSlots(windowStart(), state.days)) {
                is Outcome.Success -> {
                    _uiState.update { it.copy(isEnsuring = false) }
                    load()
                }
                is Outcome.Failure -> _uiState.update { it.copy(isEnsuring = false, actionError = outcome.error) }
            }
        }
    }

    // --- Editor -------------------------------------------------------------

    fun openCreate() {
        _uiState.update {
            it.copy(
                editor = SlotEditState(date = AppClock.nowIst().date),
                actionError = null,
            )
        }
    }

    fun openEdit(slot: DeliverySlot) {
        _uiState.update { it.copy(editor = SlotEditState.from(slot), actionError = null) }
    }

    fun onNameChange(value: String) {
        _uiState.update { state -> state.copy(editor = state.editor?.copy(name = value, fieldError = null, error = null)) }
    }

    fun onDateChange(date: LocalDate?) {
        _uiState.update { state -> state.copy(editor = state.editor?.copy(date = date, fieldError = null)) }
    }

    fun onStartTimeChange(value: String) {
        _uiState.update { state -> state.copy(editor = state.editor?.copy(startTime = value, fieldError = null)) }
    }

    fun onEndTimeChange(value: String) {
        _uiState.update { state -> state.copy(editor = state.editor?.copy(endTime = value, fieldError = null)) }
    }

    fun onCapacityChange(value: String) {
        _uiState.update { state -> state.copy(editor = state.editor?.copy(capacity = value, fieldError = null)) }
    }

    fun onSortOrderChange(value: String) {
        _uiState.update { state -> state.copy(editor = state.editor?.copy(sortOrder = value)) }
    }

    fun onActiveChange(active: Boolean) {
        _uiState.update { state -> state.copy(editor = state.editor?.copy(isActive = active)) }
    }

    fun cancelEditor() {
        if (_uiState.value.editor?.isSaving == true) return
        _uiState.update { it.copy(editor = null) }
    }

    fun save() {
        val edit = _uiState.value.editor ?: return
        if (edit.isSaving) return

        val date = edit.date
        if (date == null) {
            _uiState.update { it.copy(editor = edit.copy(fieldError = "Pick a day for the slot.")) }
            return
        }
        val start = AppClock.parseLocalTime(edit.startTime)
        if (start == null) {
            _uiState.update { it.copy(editor = edit.copy(fieldError = "Start time must look like 08:00.")) }
            return
        }
        val end = AppClock.parseLocalTime(edit.endTime)
        if (end == null) {
            _uiState.update { it.copy(editor = edit.copy(fieldError = "End time must look like 10:00.")) }
            return
        }
        val capacity = edit.capacity.trim().toIntOrNull()
        if (capacity == null || capacity <= 0) {
            _uiState.update { it.copy(editor = edit.copy(fieldError = "Capacity must be a whole number of at least 1.")) }
            return
        }

        val startsAt = AppClock.toInstant(
            LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, start.hour, start.minute),
        )
        val endsAt = AppClock.toInstant(
            LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, end.hour, end.minute),
        )
        if (endsAt <= startsAt) {
            _uiState.update { it.copy(editor = edit.copy(fieldError = "The slot must end after it starts.")) }
            return
        }

        val draft = DeliverySlotDraft(
            id = edit.id,
            name = edit.name.trim().takeIf { it.isNotEmpty() },
            startsAt = startsAt,
            endsAt = endsAt,
            capacity = capacity,
            sortOrder = edit.sortOrder.trim().toIntOrNull() ?: 0,
            isActive = edit.isActive,
        )

        _uiState.update { it.copy(editor = edit.copy(isSaving = true, fieldError = null, error = null)) }
        viewModelScope.launch {
            when (val outcome = slotRepository.saveSlot(draft)) {
                is Outcome.Success -> {
                    AppLog.i(TAG, "saved slot ${outcome.value.id}")
                    _uiState.update { it.copy(editor = null) }
                    load()
                }
                is Outcome.Failure -> _uiState.update { current ->
                    current.copy(editor = current.editor?.copy(isSaving = false, error = outcome.error))
                }
            }
        }
    }

    // --- Delete -------------------------------------------------------------

    fun delete(slotId: String) {
        if (_uiState.value.deletingId != null) return
        _uiState.update { it.copy(deletingId = slotId, actionError = null) }
        viewModelScope.launch {
            when (val outcome = slotRepository.deleteSlot(slotId)) {
                is Outcome.Success -> {
                    _uiState.update { current ->
                        current.copy(
                            slots = current.slots.filterNot { it.id == slotId },
                            loads = current.loads - slotId,
                            deletingId = null,
                        )
                    }
                }
                is Outcome.Failure -> _uiState.update { it.copy(deletingId = null, actionError = outcome.error) }
            }
        }
    }

    fun dismissActionError() {
        _uiState.update { it.copy(actionError = null) }
    }

    // --- Load ---------------------------------------------------------------

    private fun load() {
        val days = _uiState.value.days
        _uiState.update { it.copy(phase = ContentPhase.Loading, loadError = null) }
        viewModelScope.launch {
            val from = windowStart()
            when (val slots = slotRepository.listSlots(from, days)) {
                is Outcome.Failure -> _uiState.update {
                    it.copy(phase = ContentPhase.Error, loadError = slots.error)
                }
                is Outcome.Success -> {
                    // Utilisation is a bonus: a failure here must not blank the schedule.
                    val loads: Map<String, SlotLoad> = when (val load = slotRepository.slotLoad(from, days)) {
                        is Outcome.Success -> load.value.associateBy { it.slotId }
                        is Outcome.Failure -> emptyMap()
                    }
                    _uiState.update {
                        it.copy(
                            phase = if (slots.value.isEmpty()) ContentPhase.Empty else ContentPhase.Data,
                            slots = slots.value,
                            loads = loads,
                            loadError = null,
                        )
                    }
                }
            }
        }
    }

    /** Midnight today, IST, as a UTC instant — the window's lower bound. */
    private fun windowStart() = AppClock.startOfTodayIstUtc()
}
