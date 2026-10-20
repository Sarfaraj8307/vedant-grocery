package com.vedant.admin.feature.support

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.vedant.admin.feature.support.list.ComplaintsListScreen

/**
 * Support destination.
 *
 * In Phase 2 this was a placeholder so the order detail's "Raise a complaint"
 * link navigated somewhere real. It is now the real thing: the route entry to the
 * **complaints list** (`feature/support/list`). It stays a thin alias so the
 * existing `Routes.SUPPORT` destination and the order-detail link keep working
 * without churn — the screen itself lives beside its ViewModel and UiState.
 */
@Composable
fun SupportScreen(
    factory: ViewModelProvider.Factory,
    onOpenComplaint: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ComplaintsListScreen(
        factory = factory,
        onOpenComplaint = onOpenComplaint,
        onBack = onBack,
        modifier = modifier,
    )
}
