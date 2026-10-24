package com.vedant.admin.feature.settings.acceptance

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vedant.admin.feature.dashboard.DashboardViewModel
import com.vedant.admin.ui.components.LocalOnlineStatus
import com.vedant.admin.ui.components.PrimaryButton
import com.vedant.admin.ui.components.SectionCard
import com.vedant.admin.ui.components.VedantTopBar
import com.vedant.admin.ui.theme.Leaf500
import com.vedant.admin.ui.theme.Leaf600
import com.vedant.admin.ui.theme.LocalReduceMotion
import com.vedant.admin.ui.theme.VedantTheme

/**
 * S13 — Order acceptance.
 *
 * Stop or resume trade. This used to be a full-width card pinned above the
 * dashboard grid, which gave a once-a-month action the most valuable space on
 * the screen. It is now a normal destination reached from a dashboard tile.
 *
 * Closing **stops new orders immediately**; existing orders, delivery and
 * support keep working. The server requires a reason when closing, and this
 * screen validates that locally first so the operator gets an instant message
 * instead of a round trip that ends in an error.
 *
 * The state is read back from the server after every write — the screen never
 * assumes the flip worked.
 */
@Composable
fun OrderAcceptanceScreen(
    factory: ViewModelProvider.Factory,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VedantTheme.spacing
    val reduceMotion = LocalReduceMotion.current
    val online = LocalOnlineStatus.current

    val viewModel: DashboardViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var stopReason by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.start() }

    Column(modifier = modifier.fillMaxSize()) {
        VedantTopBar(
            title = "Order acceptance",
            subtitle = when (state.accepting) {
                true -> "Accepting orders"
                false -> "Closed"
                null -> "Status unknown"
            },
            onBack = onBack,
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(spacing.screen),
        ) {
            // --- Status -----------------------------------------------------
            SectionCard(title = "Current status") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulsingStatusDot(
                        color = when (state.accepting) {
                            true -> Leaf500
                            false -> MaterialTheme.colorScheme.error
                            null -> MaterialTheme.colorScheme.outline
                        },
                        pulse = state.accepting == true && !reduceMotion,
                    )
                    Spacer(Modifier.size(spacing.sm))
                    Text(
                        text = when (state.accepting) {
                            true -> "Accepting orders"
                            false -> "Not accepting orders"
                            null -> "Status unknown"
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Spacer(Modifier.size(spacing.sm))
                Text(
                    text = "Closing stops NEW orders immediately. Existing orders, " +
                        "delivery and support keep working.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // --- Control ----------------------------------------------------
            SectionCard(title = if (state.accepting == false) "Resume trade" else "Stop trade") {
                if (state.accepting == false) {
                    Text(
                        text = "The store is closed to new orders. Resuming makes the " +
                            "storefront orderable again straight away.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(spacing.md))
                    PrimaryButton(
                        text = "Start taking orders",
                        onClick = { viewModel.setAccepting(true, null) },
                        enabled = online,
                        isInFlight = state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        text = "A reason is required so the closure is explainable in " +
                            "the audit log.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(spacing.md))
                    OutlinedTextField(
                        value = stopReason,
                        onValueChange = { stopReason = it },
                        label = { Text("Reason (required)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.size(spacing.md))
                    PrimaryButton(
                        text = "Stop taking orders",
                        onClick = { viewModel.setAccepting(false, stopReason) },
                        enabled = online && stopReason.isNotBlank(),
                        isInFlight = state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            state.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.messageIsError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Leaf600
                    },
                )
            }
        }
    }
}

/**
 * A status dot that gently breathes when [pulse] is true.
 *
 * The pulse is a 1.6-second alpha cycle (0.4 -> 1.0 -> 0.4) driven by a single
 * `infiniteTransition`. When [pulse] is false or motion is reduced, the dot
 * renders at full alpha — it still communicates status by colour, just without
 * the motion.
 */
@Composable
private fun PulsingStatusDot(
    color: Color,
    pulse: Boolean,
) {
    if (pulse) {
        val transition = rememberInfiniteTransition(label = "status_pulse")
        val alpha by transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1600),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "status_alpha",
        )
        // Outer halo: a larger, fainter circle that breathes outward.
        val haloScale by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(1600),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "halo_scale",
        )
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(VedantTheme.spacing.sm)
                    .scale(haloScale)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
            )
            Box(
                modifier = Modifier
                    .size(VedantTheme.spacing.sm)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alpha)),
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(VedantTheme.spacing.sm)
                .clip(CircleShape)
                .background(color),
        )
    }
}
