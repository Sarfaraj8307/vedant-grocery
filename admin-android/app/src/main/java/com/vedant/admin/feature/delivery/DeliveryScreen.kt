package com.vedant.admin.feature.delivery

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import com.vedant.admin.domain.model.DeliveryOrder
import com.vedant.admin.ui.components.ContentPhase
import com.vedant.admin.ui.components.PrimaryButton
import com.vedant.admin.ui.components.SecondaryButton
import com.vedant.admin.ui.components.SectionCard
import com.vedant.admin.ui.components.StateScaffold
import com.vedant.admin.ui.components.VedantTopBar
import com.vedant.admin.ui.theme.VedantTheme

/**
 * The courier's queue (§21).
 *
 * Shows only what a courier needs to complete a run — who, where, how much, and
 * what to do next — and only the actions that are valid for the order's current
 * state. Authorisation is not decided here: `delivery_orders()` already narrowed
 * the list, and `admin_update_order_status()` re-validates every transition.
 *
 * CALL and NAVIGATE are **explicit** intents (`ACTION_DIAL` / `ACTION_VIEW`):
 * neither places a call nor starts navigation on its own, and both are hidden
 * when the data they need is missing rather than shown as dead buttons.
 */
@Composable
fun DeliveryScreen(
    factory: ViewModelProvider.Factory,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: DeliveryViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = VedantTheme.spacing
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.start() }

    Column(modifier = modifier.fillMaxSize()) {
        VedantTopBar(
            title = "My deliveries",
            subtitle = if (state.orders.isEmpty()) null else "${state.orders.size} active",
            onBack = onBack,
        )

        StateScaffold(
            phase = state.phase,
            emptyTitle = "Nothing to deliver",
            emptyMessage = "Orders assigned to you appear here.",
            errorMessage = state.error?.let { com.vedant.admin.core.error.ErrorCopy.of(it) },
            onRetry = viewModel::refresh,
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = spacing.screen,
                    vertical = spacing.sm,
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                state.actionMessage?.let { message ->
                    item(key = "msg") {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                items(state.orders, key = { it.orderId }) { order ->
                    DeliveryCard(
                        order = order,
                        busy = state.busyOrderId == order.orderId,
                        onCall = {
                            order.customerPhone?.let { phone ->
                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.fromParts("tel", phone, null)
                                }
                                runCatching { context.startActivity(intent) }
                            }
                        },
                        onNavigate = {
                            val intent = navigationIntent(order)
                            runCatching { context.startActivity(intent) }
                        },
                        onStart = { viewModel.advance(order.orderId, "OUT_FOR_DELIVERY") },
                        onDelivered = { viewModel.advance(order.orderId, "DELIVERED") },
                    )
                }
            }
        }
    }
}

/**
 * A `geo:` intent so the courier's preferred map app opens with the destination.
 *
 * Coordinates are preferred (§17). When the address has none — an address saved
 * before coordinates were captured, or one whose `address_id` was nulled — we
 * fall back to a **text query** rather than inventing a coordinate.
 */
private fun navigationIntent(order: DeliveryOrder): Intent {
    val uri = if (order.hasCoordinates) {
        val lat = order.latitude!!
        val lng = order.longitude!!
        val label = Uri.encode(order.customerName ?: order.orderNumber ?: "Delivery")
        Uri.parse("geo:$lat,$lng?q=$lat,$lng($label)")
    } else {
        Uri.parse("geo:0,0?q=${Uri.encode(order.addressLine)}")
    }
    return Intent(Intent.ACTION_VIEW, uri)
}

@Composable
private fun DeliveryCard(
    order: DeliveryOrder,
    busy: Boolean,
    onCall: () -> Unit,
    onNavigate: () -> Unit,
    onStart: () -> Unit,
    onDelivered: () -> Unit,
) {
    val spacing = VedantTheme.spacing

    SectionCard(title = order.orderNumber ?: "Order") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = order.statusLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = order.amount.format(),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Text(
            text = order.customerName ?: "Customer",
            style = MaterialTheme.typography.bodyLarge,
        )
        order.customerPhone?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = order.addressLine.ifBlank { "No address on file" },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (order.itemCount == 1L) "1 item" else "${order.itemCount} items",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.size(spacing.xs))

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            if (order.customerPhone != null) {
                SecondaryButton(
                    text = "Call",
                    onClick = onCall,
                    leadingIcon = Icons.Filled.Phone,
                    modifier = Modifier.weight(1f),
                )
            }
            if (order.addressLine.isNotBlank()) {
                SecondaryButton(
                    text = "Navigate",
                    onClick = onNavigate,
                    leadingIcon = Icons.Filled.Place,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Only the action that is valid for this state is offered.
        if (order.canStartDelivery) {
            Spacer(Modifier.size(spacing.xs))
            PrimaryButton(
                text = "Start delivery",
                onClick = onStart,
                isInFlight = busy,
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (order.canMarkDelivered) {
            Spacer(Modifier.size(spacing.xs))
            PrimaryButton(
                text = "Mark delivered",
                onClick = onDelivered,
                isInFlight = busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
