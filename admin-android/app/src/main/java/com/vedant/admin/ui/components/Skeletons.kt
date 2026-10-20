package com.vedant.admin.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import com.vedant.admin.ui.theme.LocalReduceMotion
import com.vedant.admin.ui.theme.VedantTheme

/**
 * A single pulsing placeholder block.
 *
 * Used to build **skeleton** loading states instead of a bare spinner: the shape
 * of the content is visible immediately, so the screen does not jump when the
 * data lands. The pulse is a slow opacity breathe — never a busy shimmer that
 * would fight the operator for attention.
 */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(VedantTheme.radius.xs),
) {
    val base = MaterialTheme.colorScheme.surfaceVariant
    // Reduced motion: the pulse becomes a static tone rather than a breathe.
    val reduceMotion = LocalReduceMotion.current
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = SKELETON_ALPHA_LOW,
        targetValue = if (reduceMotion) SKELETON_ALPHA_LOW else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton-alpha",
    )
    Box(modifier = modifier.clip(shape).background(base.copy(alpha = alpha)))
}

private const val SKELETON_ALPHA_LOW = 0.45f

/** A placeholder shaped like one dense order row. */
@Composable
fun OrderRowSkeleton(modifier: Modifier = Modifier) {
    val spacing = VedantTheme.spacing
    val sizing = VedantTheme.sizing
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(VedantTheme.radius.lg),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(spacing.lg),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
                modifier = Modifier.weight(1f),
            ) {
                SkeletonBlock(modifier = Modifier.width(sizing.skeletonHero).height(sizing.skeletonLine))
                SkeletonBlock(modifier = Modifier.width(sizing.skeletonWide).height(sizing.skeletonTiny))
                SkeletonBlock(modifier = Modifier.width(sizing.previewImage).height(sizing.skeletonValue))
            }
            SkeletonBlock(modifier = Modifier.width(sizing.skeletonChip).height(sizing.skeletonRow))
        }
    }
}

/** A list of [count] order-row placeholders with the standard row rhythm. */
@Composable
fun OrderListSkeleton(modifier: Modifier = Modifier, count: Int = 6) {
    val spacing = VedantTheme.spacing
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.rowGap),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screen, vertical = spacing.md),
    ) {
        repeat(count) { OrderRowSkeleton() }
    }
}

// ---------------------------------------------------------------------------
// Shimmer variant
// ---------------------------------------------------------------------------

/**
 * The single animation driver for a **group** of [ShimmerBlock]s.
 *
 * One [rememberInfiniteTransition] per skeleton screen — not one per block —
 * so a six-row skeleton costs one animation, not six. That is what keeps a
 * loading state from turning into a recomposition storm: the blocks below are
 * dumb draws, and the progress value flows to them as an ordinary parameter.
 *
 * When [LocalReduceMotion] is on, the sweep is switched off and a static tone
 * (see [SHIMMER_STATIC]) is returned, so "remove animations" users see the
 * skeleton's shape without any movement at all.
 */
@Composable
fun rememberShimmerProgress(): Float {
    val reduceMotion = LocalReduceMotion.current
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SHIMMER_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-progress",
    )
    return if (reduceMotion) SHIMMER_STATIC else progress
}

/** What [rememberShimmerProgress] returns when motion is reduced: a flat block. */
const val SHIMMER_STATIC: Float = -1f

private const val SHIMMER_PERIOD_MS = 1400

/**
 * A placeholder block with a slow **shimmer** sweep — the gradient travels
 * left-to-right across the block, so the whole list reads as "loading" without
 * the pulse of a single block drawing the eye.
 *
 * Colours come from the colour scheme (`surfaceVariant` base,
 * `surfaceContainerHighest` highlight), so the skeleton re-tints itself
 * correctly in dark mode instead of glowing white.
 *
 * @param progress the sweep position in `[0, 1)`, from a shared
 *   [rememberShimmerProgress]; [SHIMMER_STATIC] renders a flat tone.
 */
@Composable
fun ShimmerBlock(
    progress: Float,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(VedantTheme.radius.xs),
) {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        modifier = modifier
            .clip(shape)
            .drawWithCache {
                if (progress < 0f) {
                    // Reduced motion: one flat tone, no sweep.
                    onDrawBehind { drawRect(base) }
                } else {
                    val sweepWidth = size.width * SHIMMER_SWEEP_FRACTION
                    val startX = progress * (size.width + sweepWidth) - sweepWidth
                    val brush = Brush.linearGradient(
                        colors = listOf(base, highlight, base),
                        start = Offset(startX, 0f),
                        end = Offset(startX + sweepWidth, size.height),
                    )
                    onDrawBehind { drawRect(brush) }
                }
            },
    )
}

/** The sweep window as a fraction of the block's width. */
private const val SHIMMER_SWEEP_FRACTION = 0.5f

/**
 * A placeholder shaped like one delivery card (the courier's queue row).
 *
 * The geometry mirrors [com.vedant.admin.feature.delivery]'s real card on
 * purpose — title row with the amount on the right, the customer and address
 * lines, the two button row, the primary action — so the loaded queue lands in
 * exactly the layout the skeleton promised and nothing jumps.
 */
@Composable
fun DeliveryCardSkeleton(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val spacing = VedantTheme.spacing
    val sizing = VedantTheme.sizing
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(VedantTheme.radius.lg),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
            modifier = Modifier.padding(spacing.lg),
        ) {
            // Title row: order reference on the left, amount on the right.
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShimmerBlock(
                    progress = progress,
                    modifier = Modifier
                        .width(sizing.skeletonWide)
                        .height(sizing.skeletonLine),
                )
                Box(modifier = Modifier.weight(1f))
                ShimmerBlock(
                    progress = progress,
                    modifier = Modifier
                        .width(sizing.skeletonChip)
                        .height(sizing.skeletonValue),
                )
            }

            // Status label, name, phone, address, item count.
            ShimmerBlock(
                progress = progress,
                modifier = Modifier
                    .width(sizing.skeletonChip)
                    .height(sizing.skeletonTiny),
            )
            ShimmerBlock(
                progress = progress,
                modifier = Modifier
                    .width(sizing.skeletonHero)
                    .height(sizing.skeletonLine),
            )
            ShimmerBlock(
                progress = progress,
                modifier = Modifier
                    .width(sizing.skeletonWide)
                    .height(sizing.skeletonTiny),
            )
            ShimmerBlock(
                progress = progress,
                modifier = Modifier
                    .width(sizing.skeletonWide)
                    .height(sizing.skeletonValue),
            )
            ShimmerBlock(
                progress = progress,
                modifier = Modifier
                    .width(sizing.skeletonPanel)
                    .height(sizing.skeletonTiny),
            )

            // The two secondary actions (Call / Navigate).
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                ShimmerBlock(
                    progress = progress,
                    modifier = Modifier
                        .weight(1f)
                        .height(sizing.skeletonRow),
                )
                ShimmerBlock(
                    progress = progress,
                    modifier = Modifier
                        .weight(1f)
                        .height(sizing.skeletonRow),
                )
            }

            // The primary action (Start delivery / Mark delivered).
            ShimmerBlock(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sizing.touchTarget),
            )
        }
    }
}

/** The delivery queue's loading skeleton: [count] cards with one shared sweep. */
@Composable
fun DeliveryListSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 4,
) {
    val spacing = VedantTheme.spacing
    val progress = rememberShimmerProgress()
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.rowGap),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screen, vertical = spacing.md),
    ) {
        repeat(count) { DeliveryCardSkeleton(progress = progress) }
    }
}
