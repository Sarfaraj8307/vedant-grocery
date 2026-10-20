package com.vedant.admin.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vedant.admin.ui.components.DestructiveButton
import com.vedant.admin.ui.components.SectionCard
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vedant.admin.ui.components.PrimaryButton
import com.vedant.admin.ui.components.SecondaryButton
import com.vedant.admin.ui.components.VedantTopBar
import com.vedant.admin.ui.theme.VedantTheme

/**
 * S4 — Dashboard.
 *
 * The signed-in landing screen: the entry point to orders (Phase 2) and to the
 * catalogue and delivery slots (Phase 3). The session gate guarantees that only a
 * server-confirmed admin ever reaches it.
 */
@Composable
fun DashboardScreen(
    factory: ViewModelProvider.Factory,
    email: String?,
    onOpenOrders: () -> Unit,
    onOpenDelivery: () -> Unit,
    onOpenCatalogue: () -> Unit,
    onOpenSlots: () -> Unit,
    onOpenSupport: () -> Unit,
    onOpenStoreSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VedantTheme.spacing
    val radius = VedantTheme.radius

    val viewModel: DashboardViewModel = viewModel(factory = factory)
    val dash by viewModel.uiState.collectAsStateWithLifecycle()
    var stopReason by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { viewModel.start() }

    Column(modifier = modifier.fillMaxSize()) {
        VedantTopBar(
            title = "Dashboard",
            subtitle = email,
            actions = {
                IconButton(onClick = onSignOut) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Sign out",
                    )
                }
            },
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
            modifier = Modifier
                .fillMaxSize()
                .padding(spacing.screen),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(radius.lg),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                    modifier = Modifier.padding(spacing.lg),
                ) {
                    Text(
                        text = "Signed in",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = email ?: "Administrator",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            Text(
                text = "Manage today's orders, update their status and handle customer issues.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            PrimaryButton(
                text = "Manage orders",
                onClick = onOpenOrders,
                leadingIcon = Icons.AutoMirrored.Outlined.ReceiptLong,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionCard(title = "Order acceptance") {
                Text(
                    text = when (dash.accepting) {
                        true -> "Accepting orders"
                        false -> "Not accepting orders"
                        null -> "Status unknown — could not read the store"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = when (dash.accepting) {
                        true -> MaterialTheme.colorScheme.primary
                        false -> MaterialTheme.colorScheme.error
                        null -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    text = "Closing stops NEW orders immediately. Existing orders, " +
                        "delivery and support keep working.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.size(spacing.xs))

                if (dash.accepting == false) {
                    PrimaryButton(
                        text = "Start taking orders",
                        onClick = { viewModel.setAccepting(true, null) },
                        isInFlight = dash.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = stopReason,
                        onValueChange = { stopReason = it },
                        label = { Text("Reason (required)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.size(spacing.xxs))
                    DestructiveButton(
                        text = "Stop taking orders",
                        onClick = { viewModel.setAccepting(false, stopReason) },
                        isInFlight = dash.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                dash.message?.let { msg ->
                    Spacer(Modifier.size(spacing.xxs))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (dash.messageIsError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }

            SecondaryButton(
                text = "My deliveries",
                onClick = onOpenDelivery,
                leadingIcon = Icons.Outlined.LocalShipping,
                modifier = Modifier.fillMaxWidth(),
            )

            SecondaryButton(
                text = "Catalogue",
                onClick = onOpenCatalogue,
                leadingIcon = Icons.Filled.Inventory2,
                modifier = Modifier.fillMaxWidth(),
            )

            SecondaryButton(
                text = "Delivery slots",
                onClick = onOpenSlots,
                leadingIcon = Icons.Filled.DateRange,
                modifier = Modifier.fillMaxWidth(),
            )

            SecondaryButton(
                text = "Support",
                onClick = onOpenSupport,
                leadingIcon = Icons.Filled.SupportAgent,
                modifier = Modifier.fillMaxWidth(),
            )

            SecondaryButton(
                text = "Store settings",
                onClick = onOpenStoreSettings,
                leadingIcon = Icons.Filled.Storefront,
                modifier = Modifier.fillMaxWidth(),
            )

            SecondaryButton(
                text = "Profile",
                onClick = onOpenProfile,
                leadingIcon = Icons.Filled.AccountCircle,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.size(spacing.md))

            SecondaryButton(
                text = "Sign out",
                onClick = onSignOut,
                leadingIcon = Icons.AutoMirrored.Filled.Logout,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
