package com.vedant.admin.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.vedant.admin.ui.theme.VedantTheme

/**
 * Buttons that make "never fake success" structural.
 *
 * A button that triggers a mutation is driven by an `ActionState`:
 * `Idle → InFlight → Idle | Failed`. While [isInFlight] is true the button is
 * **disabled** (so a double tap cannot send a second request) and shows a
 * spinner, so the operator can see that the change is not yet confirmed.
 *
 * Every button meets the 48 dp touch-target floor.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isInFlight: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isInFlight,
        shape = RoundedCornerShape(VedantTheme.radius.md),
        modifier = modifier.heightIn(min = VedantTheme.sizing.touchTarget),
    ) {
        ButtonContent(text = text, isInFlight = isInFlight, leadingIcon = leadingIcon)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isInFlight: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !isInFlight,
        shape = RoundedCornerShape(VedantTheme.radius.md),
        modifier = modifier.heightIn(min = VedantTheme.sizing.touchTarget),
    ) {
        ButtonContent(text = text, isInFlight = isInFlight, leadingIcon = leadingIcon)
    }
}

@Composable
fun DestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isInFlight: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isInFlight,
        shape = RoundedCornerShape(VedantTheme.radius.md),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
        modifier = modifier.heightIn(min = VedantTheme.sizing.touchTarget),
    ) {
        ButtonContent(text = text, isInFlight = isInFlight, leadingIcon = leadingIcon)
    }
}

/**
 * Shared button interior. The spinner is announced to accessibility services as
 * "In progress" so a non-visual user also knows the action has not completed.
 */
@Composable
private fun ButtonContent(
    text: String,
    isInFlight: Boolean,
    leadingIcon: ImageVector?,
) {
    val spacing = VedantTheme.spacing
    val sizing = VedantTheme.sizing

    if (isInFlight) {
        CircularProgressIndicator(
            color = LocalContentColor.current,
            strokeWidth = sizing.progressStroke,
            modifier = Modifier
                .size(sizing.iconMd)
                .semantics { contentDescription = "In progress" },
        )
        Spacer(Modifier.size(spacing.sm))
    } else if (leadingIcon != null) {
        Icon(
            imageVector = leadingIcon,
            contentDescription = null,
            modifier = Modifier.size(sizing.iconMd),
        )
        Spacer(Modifier.size(spacing.sm))
    }

    Text(text = text, style = MaterialTheme.typography.labelLarge)
}
