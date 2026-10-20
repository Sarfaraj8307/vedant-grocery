package com.vedant.admin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.vedant.admin.domain.model.ComplaintStatus
import com.vedant.admin.domain.model.OrderStatus
import com.vedant.admin.ui.theme.Danger
import com.vedant.admin.ui.theme.DangerBg
import com.vedant.admin.ui.theme.Info
import com.vedant.admin.ui.theme.InfoBg
import com.vedant.admin.ui.theme.Success
import com.vedant.admin.ui.theme.SuccessBg
import com.vedant.admin.ui.theme.VedantTheme
import com.vedant.admin.ui.theme.Warning
import com.vedant.admin.ui.theme.WarningBg

/** The five semantic tones a badge can carry. */
enum class BadgeTone { Success, Warning, Danger, Info, Neutral }

/** A tone paired with a distinct icon shape. */
data class BadgeVisual(val tone: BadgeTone, val icon: ImageVector)

/**
 * A status pill that conveys its meaning by **word + icon shape + colour**
 * (WCAG 1.4.1). Colour is never the only signal: two states that share a tone
 * (e.g. `CONFIRMED` and `DELIVERED`, both green) are still distinguishable by
 * their icon and their label.
 */
@Composable
fun StatusBadge(
    label: String,
    tone: BadgeTone,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val spacing = VedantTheme.spacing
    val sizing = VedantTheme.sizing
    val (foreground, background) = tone.colors()

    Surface(
        color = background,
        contentColor = foreground,
        shape = RoundedCornerShape(VedantTheme.radius.pill),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(sizing.iconSm),
            )
            Text(text = label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Convenience for the order workflow. */
@Composable
fun OrderStatusBadge(status: OrderStatus, modifier: Modifier = Modifier) {
    val visual = status.badgeVisual()
    StatusBadge(label = status.label, tone = visual.tone, icon = visual.icon, modifier = modifier)
}

/**
 * Tolerant variant for a value the server may have added since this build.
 *
 * An unknown status renders the **raw** wire value with a neutral tone and an
 * info icon — the row is never dropped and never crashes, and the operator still
 * sees exactly what the database holds (§5.2).
 */
@Composable
fun OrderStatusBadge(status: OrderStatus?, rawStatus: String, modifier: Modifier = Modifier) {
    if (status != null) {
        OrderStatusBadge(status = status, modifier = modifier)
    } else {
        StatusBadge(
            label = rawStatus.ifBlank { "Unknown" },
            tone = BadgeTone.Neutral,
            icon = Icons.Filled.Info,
            modifier = modifier,
        )
    }
}

/**
 * The tone + icon for each order status. A pure function so it can be asserted
 * directly: the same tone never carries two states that are not also
 * distinguishable by shape.
 */
fun OrderStatus.badgeVisual(): BadgeVisual = when (this) {
    OrderStatus.PENDING -> BadgeVisual(BadgeTone.Info, Icons.AutoMirrored.Outlined.ReceiptLong)
    OrderStatus.CONFIRMED -> BadgeVisual(BadgeTone.Success, Icons.Filled.CheckCircle)
    OrderStatus.PROCESSING -> BadgeVisual(BadgeTone.Warning, Icons.Filled.Inventory2)
    OrderStatus.READY_FOR_DELIVERY -> BadgeVisual(BadgeTone.Warning, Icons.Filled.Inventory)
    OrderStatus.OUT_FOR_DELIVERY -> BadgeVisual(BadgeTone.Info, Icons.Filled.LocalShipping)
    OrderStatus.DELIVERED -> BadgeVisual(BadgeTone.Success, Icons.Filled.TaskAlt)
    OrderStatus.CANCELLED -> BadgeVisual(BadgeTone.Danger, Icons.Filled.Cancel)
}

/**
 * Convenience for the complaint workflow.
 *
 * Tolerant in the same way as [OrderStatusBadge]: an unknown wire status renders
 * its **raw** value with a neutral tone, so a server-side addition shows what the
 * database holds instead of crashing or dropping the row.
 */
@Composable
fun ComplaintStatusBadge(status: ComplaintStatus?, rawStatus: String, modifier: Modifier = Modifier) {
    val visual = complaintBadgeVisual(status)
    StatusBadge(
        label = status?.label ?: rawStatus.ifBlank { "Unknown" },
        tone = visual.tone,
        icon = visual.icon,
        modifier = modifier,
    )
}

/**
 * The tone + icon for each complaint status. Pure, so it is directly assertable:
 * each status carries a distinct icon shape, so two statuses that share a tone
 * are still distinguishable without colour (WCAG 1.4.1).
 */
fun complaintBadgeVisual(status: ComplaintStatus?): BadgeVisual = when (status) {
    ComplaintStatus.OPEN -> BadgeVisual(BadgeTone.Info, Icons.Filled.MarkChatUnread)
    ComplaintStatus.IN_PROGRESS -> BadgeVisual(BadgeTone.Warning, Icons.Filled.HourglassTop)
    ComplaintStatus.RESOLVED -> BadgeVisual(BadgeTone.Success, Icons.Filled.TaskAlt)
    ComplaintStatus.CLOSED -> BadgeVisual(BadgeTone.Neutral, Icons.Filled.Lock)
    null -> BadgeVisual(BadgeTone.Neutral, Icons.Filled.Info)
}

@Composable
private fun BadgeTone.colors(): Pair<Color, Color> = when (this) {
    BadgeTone.Success -> Success to SuccessBg
    BadgeTone.Warning -> Warning to WarningBg
    BadgeTone.Danger -> Danger to DangerBg
    BadgeTone.Info -> Info to InfoBg
    BadgeTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant to
        MaterialTheme.colorScheme.surfaceVariant
}
