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
