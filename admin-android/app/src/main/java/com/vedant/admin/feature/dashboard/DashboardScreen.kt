package com.vedant.admin.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
