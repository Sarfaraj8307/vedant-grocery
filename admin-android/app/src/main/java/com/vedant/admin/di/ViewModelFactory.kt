package com.vedant.admin.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vedant.admin.feature.auth.NotAuthorizedViewModel
import com.vedant.admin.feature.auth.SignInViewModel
import com.vedant.admin.feature.auth.SplashViewModel
import com.vedant.admin.feature.catalogue.categories.CategoriesViewModel
import com.vedant.admin.feature.catalogue.editor.ProductEditorViewModel
import com.vedant.admin.feature.catalogue.hub.CatalogueHubViewModel
import com.vedant.admin.feature.catalogue.offers.OffersViewModel
import com.vedant.admin.feature.catalogue.weights.WeightsViewModel
import com.vedant.admin.feature.dashboard.DashboardViewModel
import com.vedant.admin.feature.delivery.DeliveryViewModel
import com.vedant.admin.feature.orders.detail.OrderDetailViewModel
import com.vedant.admin.feature.orders.list.OrdersListViewModel
import com.vedant.admin.feature.settings.profile.ProfileViewModel
import com.vedant.admin.feature.settings.store.StoreSettingsViewModel
import com.vedant.admin.feature.slots.SlotsViewModel
import com.vedant.admin.feature.support.list.ComplaintsListViewModel
import com.vedant.admin.feature.support.thread.ComplaintThreadViewModel
import com.vedant.admin.ui.navigation.SessionViewModel

/**
 * Resolves a [ViewModel] from the [AppContainer].
 *
 * Constructor injection only: every ViewModel takes repository **interfaces**,
 * never the SDK. That keeps `androidTest` able to swap in fakes and keeps the
 * eventual Hilt migration a one-file change (`di/`).
 */
class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val viewModel: ViewModel = when {
            modelClass.isAssignableFrom(SessionViewModel::class.java) ->
                SessionViewModel(container.authRepository)

            modelClass.isAssignableFrom(SplashViewModel::class.java) ->
                SplashViewModel(container.authRepository)

            modelClass.isAssignableFrom(SignInViewModel::class.java) ->
                SignInViewModel(container.authRepository)

            modelClass.isAssignableFrom(NotAuthorizedViewModel::class.java) ->
                NotAuthorizedViewModel(container.authRepository)

            modelClass.isAssignableFrom(OrdersListViewModel::class.java) ->
                OrdersListViewModel(container.orderRepository)

            modelClass.isAssignableFrom(OrderDetailViewModel::class.java) ->
                OrderDetailViewModel(container.orderRepository)

            modelClass.isAssignableFrom(DashboardViewModel::class.java) ->
                DashboardViewModel(container.adminRpc)

            modelClass.isAssignableFrom(DeliveryViewModel::class.java) ->
                DeliveryViewModel(container.adminRpc)

            // --- Catalogue (Phase 3) ----------------------------------------

            modelClass.isAssignableFrom(CatalogueHubViewModel::class.java) ->
                CatalogueHubViewModel(container.productRepository, container.categoryRepository)

            modelClass.isAssignableFrom(ProductEditorViewModel::class.java) ->
                ProductEditorViewModel(
                    container.productRepository,
                    container.categoryRepository,
                    container.weightRepository,
                )

            modelClass.isAssignableFrom(CategoriesViewModel::class.java) ->
                CategoriesViewModel(container.categoryRepository)

            modelClass.isAssignableFrom(WeightsViewModel::class.java) ->
                WeightsViewModel(container.weightRepository)

            modelClass.isAssignableFrom(OffersViewModel::class.java) ->
                OffersViewModel(container.offerRepository)

            // --- Delivery slots (Phase 3) -----------------------------------

            modelClass.isAssignableFrom(SlotsViewModel::class.java) ->
                SlotsViewModel(container.slotRepository)

            // --- Support / complaints (Phase 4) ------------------------------

            modelClass.isAssignableFrom(ComplaintsListViewModel::class.java) ->
                ComplaintsListViewModel(container.complaintRepository)

            modelClass.isAssignableFrom(ComplaintThreadViewModel::class.java) ->
                ComplaintThreadViewModel(container.complaintRepository)

            // --- Settings & profile (Phase 4) --------------------------------

            modelClass.isAssignableFrom(StoreSettingsViewModel::class.java) ->
                StoreSettingsViewModel(container.storeSettingsRepository)

            modelClass.isAssignableFrom(ProfileViewModel::class.java) ->
                ProfileViewModel(container.profileRepository, container.authRepository)

            else -> error("Unknown ViewModel: ${modelClass.name}")
        }
        return viewModel as T
    }
}
