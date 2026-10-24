package com.vedant.admin.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vedant.admin.ui.theme.NumericEmphasis
import com.vedant.admin.ui.theme.VedantTheme

/**
 * A titled panel — the detail screen's building block.
 *
 * Each section of an order (items, totals, delivery, timeline) is one of these,
 * so the screen reads like a receipt: a clear label, then its rows. The card uses
 * the theme `surface` colour so dark mode reads as layered rather than outlined.
 *
 * A subtle `tonalElevation` (1 dp) gives the card the faintest lift over the
 * background — enough to distinguish it from the paper surface without
 * introducing a visible shadow that would look heavy on a dense receipt.
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = VedantTheme.spacing
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = VedantTheme.sizing.cardTonalElevation,
        shape = RoundedCornerShape(VedantTheme.radius.lg),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(spacing.lg)) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(spacing.md))
            content()
        }
    }
}

/** A label/value line inside a [SectionCard]. */
@Composable
fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasise: Boolean = false,
) {
    val spacing = VedantTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = if (emphasise) NumericEmphasis else MaterialTheme.typography.bodyMedium,
        )
    }
}
