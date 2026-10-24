package com.vedant.admin.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import com.vedant.admin.ui.theme.DURATION_QUICK
import com.vedant.admin.ui.theme.EasingDecelerate
import com.vedant.admin.ui.theme.LocalReduceMotion

/**
 * A subtle press-scale modifier for tappable cards and tiles.
 *
 * The caller must pass the **same** [interactionSource] it gives to the
 * surface's `clickable`/`Surface(onClick = …)`, so the press state that drives
 * the ripple also drives the scale. A 3% shrink over 150 ms reads as responsive
 * without the jumpiness a larger scale would produce.
 *
 * Honours `LocalReduceMotion`: when motion is reduced the transform is skipped
 * entirely, leaving the ripple as the only feedback. The tap itself is never
 * affected — this modifier only paints.
 */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f,
): Modifier = composed {
    if (LocalReduceMotion.current) return@composed this

    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }

    LaunchedEffect(isPressed) {
        scale.animateTo(
            targetValue = if (isPressed) pressedScale else 1f,
            animationSpec = tween(DURATION_QUICK, easing = EasingDecelerate),
        )
    }

    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}
