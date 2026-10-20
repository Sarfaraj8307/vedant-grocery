package com.vedant.admin.feature.catalogue

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.vedant.admin.ui.theme.VedantTheme

/**
 * A product image with a graceful fallback.
 *
 * When the server row has an `image_url` it is loaded with **Coil 3** from the
 * public `product-images` bucket; otherwise the product's glyph (an emoji, or
 * the name's initial) is drawn on the muted surface. Either way the box is the
 * same size, so a list never reflows when an image arrives.
 */
@Composable
fun ProductThumb(
    imageUrl: String?,
    glyph: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(VedantTheme.radius.md)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = shape,
        modifier = modifier.size(size),
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .clip(shape)
                    .size(size),
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(text = glyph, style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}
