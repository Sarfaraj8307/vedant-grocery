package com.vedant.admin.feature.catalogue

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.vedant.admin.ui.theme.DURATION_STANDARD
import com.vedant.admin.ui.theme.EasingDecelerate
import com.vedant.admin.ui.theme.LocalReduceMotion
import com.vedant.admin.ui.theme.VedantTheme

/**
 * A product image with a graceful fallback.
 *
 * When the server row has an `image_url` it is loaded with **Coil 3** from the
 * public `product-images` bucket; otherwise the product's glyph (an emoji, or
 * the name's initial) is drawn on the muted surface. Either way the box is the
 * same size, so a list never reflows when an image arrives.
 *
 * The glyph stays painted *underneath* the photo and the photo fades in over it
 * once Coil reports a success. That removes the two things that read as
 * "glitchy" in a list: a blank tile while bytes are in flight, and a hard
 * snap the instant they land. Reduced motion skips the fade (duration 0) —
 * the photo simply appears.
 */
@Composable
fun ProductThumb(
    imageUrl: String?,
    glyph: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(VedantTheme.radius.md)
    val reduceMotion = LocalReduceMotion.current

    // Keyed on the URL so a recycled row does not inherit the previous
    // product's "already loaded" flag and flash the wrong image.
    var loaded by remember(imageUrl) { mutableStateOf(false) }
    val imageAlpha by animateFloatAsState(
        targetValue = if (loaded) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (reduceMotion) 0 else DURATION_STANDARD,
            easing = EasingDecelerate,
        ),
        label = "product_thumb_alpha",
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = shape,
        modifier = modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = glyph, style = MaterialTheme.typography.headlineSmall)

            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onState = { state ->
                        if (state is AsyncImagePainter.State.Success) loaded = true
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .graphicsLayer { alpha = imageAlpha },
                )
            }
        }
    }
}
