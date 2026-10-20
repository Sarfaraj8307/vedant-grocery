package com.vedant.admin.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.vedant.admin.core.time.AppClock
import com.vedant.admin.ui.theme.VedantTheme
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * A date-only field backed by the Material 3 date picker.
 *
 * The picker reports **UTC midnight** for the chosen calendar day; this component
 * hides that quirk and works in the app's own terms: [instant] is an IST
 * start-of-day instant, and [onPick] returns one too. Callers therefore never
 * deal with the picker's time zone.
 *
 * Passing `null` to [onPick] (via the dialog's Clear action) means "no date".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    instant: Instant?,
    onPick: (Instant?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = instant
            ?.let { AppClock.toIst(it).date.atStartOfDayIn(TimeZone.UTC) }
            ?.toEpochMilliseconds(),
    )

    OutlinedButton(onClick = { showPicker = true }, modifier = modifier) {
        Icon(
            imageVector = Icons.Filled.DateRange,
            contentDescription = null,
            modifier = Modifier.size(VedantTheme.sizing.iconSm),
        )
        Spacer(Modifier.size(VedantTheme.spacing.xs))
        Text(
            text = instant?.let(AppClock::formatDate) ?: label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis -> onPick(istStartOfDay(millis)) }
                        showPicker = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { onPick(null); showPicker = false }) { Text("Clear") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** The picker's UTC midnight → the IST start-of-day instant the app stores. */
private fun istStartOfDay(utcMidnightMillis: Long): Instant {
    val date = Instant.fromEpochMilliseconds(utcMidnightMillis).toLocalDateTime(TimeZone.UTC).date
    return date.atStartOfDayIn(AppClock.IST)
}
