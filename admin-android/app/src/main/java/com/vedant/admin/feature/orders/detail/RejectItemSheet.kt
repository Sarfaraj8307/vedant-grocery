package com.vedant.admin.feature.orders.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vedant.admin.core.error.ErrorCopy
import com.vedant.admin.ui.components.DestructiveButton
import com.vedant.admin.ui.components.SecondaryButton
import com.vedant.admin.ui.theme.VedantTheme

/**
 * T17 — the "reject item" bottom sheet.
 *
 * A rejection changes what the customer is charged, so the sheet:
 *  - makes the **reason mandatory** — the confirm button refuses a blank input and
 *    the field shows the validation message; the server enforces this again;
 *  - states plainly what will happen (a refund for that line and a server-side
 *    recalculation), so the operator is never surprised by the new total;
 *  - disables both actions while the request is in flight, so a double-tap cannot
 *    send a second rejection.
 *
 * The sheet renders only server truth: the totals it results in come from the
 * re-fetched order (see `OrderDetailViewModel.submitReject`), never from here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RejectItemSheet(
    state: RejectSheetState,
    onReasonChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VedantTheme.spacing
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        // While submitting, a stray tap must not dismiss the sheet and lose the
        // in-flight request's context.
        onDismissRequest = { if (!state.isSubmitting) onDismiss() },
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.md),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.screen)
                .padding(bottom = spacing.xxl),
        ) {
            Text(text = "Reject item", style = MaterialTheme.typography.titleLarge)

            Text(
                text = state.itemName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = "The customer is refunded for this line and the server recalculates " +
                    "the order total. You can restore the item afterwards.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = state.reason,
                onValueChange = onReasonChange,
                label = { Text("Reason (required)") },
                singleLine = false,
                minLines = 2,
                enabled = !state.isSubmitting,
                isError = state.reasonError != null,
                supportingText = state.reasonError?.let { message -> { Text(message) } },
                modifier = Modifier.fillMaxWidth(),
            )

            // A server rejection is surfaced here, inside the sheet, so the
            // operator can fix the reason and retry without losing their place.
            state.error?.let { error ->
                Text(
                    text = ErrorCopy.of(error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SecondaryButton(
                    text = "Keep item",
                    onClick = onDismiss,
                    enabled = !state.isSubmitting,
                    modifier = Modifier.weight(1f),
                )
                DestructiveButton(
                    text = "Reject item",
                    onClick = onSubmit,
                    isInFlight = state.isSubmitting,
                    leadingIcon = Icons.Filled.Delete,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
