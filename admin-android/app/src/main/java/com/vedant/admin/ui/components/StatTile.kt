package com.vedant.admin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.vedant.admin.ui.theme.VedantTheme

/**
 * A single figure with a label and an icon — the stat card pattern from
 * HyperUI's `application/stats` set, adapted to the Vedant tokens.
 *
 * Layout follows HyperUI's horizontal variant: the icon sits in a tinted
 * circle on the left, the label and value stack beside it, and an optional
 * supporting line carries the comparison ("from 24 yesterday"). That
 * arrangement survives a 2-column phone grid, where the vertical variant
 * gets cramped.
 *
 * The value is passed in **already formatted** — this component never
 * computes or rounds a number. Money in particular must arrive as the
 * server reported it (`Money.format()`), never re-derived here.
 *
 * @param value the formatted figure. Pass an em dash ("—") when the number is
 *   unknown; never pass "0", which would assert a fact the app does not have.
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    val spacing = VedantTheme.spacing
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(VedantTheme.radius.lg),
        shadowElevation = VedantTheme.sizing.cardTonalElevation,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            modifier = Modifier.padding(spacing.md),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(VedantTheme.sizing.avatar)
                    .background(accent.copy(alpha = 0.12f), CircleShape),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(VedantTheme.sizing.iconMd),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * A compact label/number pair for a pipeline row — "Pending 4", "Out 2".
 *
 * Deliberately not a [StatTile]: four of these sit side by side inside one
 * card, so an icon and a card surface each would be noise. The number carries
 * the weight, the label stays quiet, and the whole row reads in one glance.
 *
 * [value] is again pre-formatted, and "—" means unknown.
 */
@Composable
fun PipelineStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasis: Boolean = false,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VedantTheme.spacing.xxs),
        modifier = modifier,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = if (emphasis) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
