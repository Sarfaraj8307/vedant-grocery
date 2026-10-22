package com.vedant.admin.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vedant.admin.di.AppContainer
import com.vedant.admin.di.AppViewModelFactory
import com.vedant.admin.domain.model.OrderStatus
import com.vedant.admin.domain.model.SessionState
import com.vedant.admin.feature.auth.NotAuthorizedScreen
import com.vedant.admin.feature.auth.SignInScreen
import com.vedant.admin.feature.auth.SplashScreen
import com.vedant.admin.feature.catalogue.categories.CategoriesScreen
import com.vedant.admin.feature.catalogue.editor.ProductEditorScreen
import com.vedant.admin.feature.catalogue.hub.CatalogueHubScreen
import com.vedant.admin.feature.delivery.DeliveryScreen
import com.vedant.admin.feature.catalogue.offers.OffersScreen
import com.vedant.admin.feature.catalogue.weights.WeightsScreen
import com.vedant.admin.feature.dashboard.DashboardScreen
import com.vedant.admin.feature.orders.detail.OrderDetailScreen
import com.vedant.admin.feature.orders.list.OrdersListScreen
import com.vedant.admin.feature.slots.SlotsScreen
import com.vedant.admin.feature.settings.appearance.AppearanceScreen
import com.vedant.admin.feature.settings.profile.ProfileScreen
import com.vedant.admin.feature.settings.store.StoreSettingsScreen
import com.vedant.admin.feature.support.SupportScreen
import com.vedant.admin.feature.support.thread.ComplaintThreadScreen

/**
 * The navigation graph and the auth/admin guards (§7).
 *
 * Routing is **declarative**: the host observes [SessionState] and redirects,
 * rather than each screen deciding whether it is allowed to be shown. Because
 * the gate is derived from the server-confirmed session, there is no code path
 * that shows an admin screen to a non-admin — and on sign-out the whole back
 * stack is cleared so `Back` can never reveal an admin screen.
 *
 * The order routes (list, detail, support) hang off the dashboard and are only
 * reachable once the session gate has admitted the operator.
 */
@Composable
fun VedantNavHost(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val factory = remember(container) { AppViewModelFactory(container) }

    val sessionViewModel: SessionViewModel = viewModel(factory = factory)
    val sessionState by sessionViewModel.sessionState.collectAsStateWithLifecycle()

    LaunchedEffect(sessionState) {
        val destination = when (sessionState) {
            // Restore in flight: stay on the splash spinner.
            SessionState.Unknown -> null
            SessionState.SignedOut -> Routes.SIGN_IN
            SessionState.NotAuthorized -> Routes.NOT_AUTHORIZED
            is SessionState.SignedIn -> Routes.DASHBOARD
        }
        if (destination != null && navController.currentDestination?.route != destination) {
            navController.navigate(destination) {
                // Clear the whole back stack: after a gate change there is no
                // legitimate "back" into the previous state.
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        modifier = modifier,
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(factory = factory)
        }
        composable(Routes.SIGN_IN) {
            SignInScreen(factory = factory)
        }
        composable(Routes.NOT_AUTHORIZED) {
            // The refusal screen owns its own sign-out (so it can show an
            // in-flight state); it exposes no admin data.
            NotAuthorizedScreen(factory = factory)
        }
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                factory = factory,
                email = (sessionState as? SessionState.SignedIn)?.email,
                onOpenOrders = { navController.navigate(Routes.orders()) },
                onOpenDelivery = { navController.navigate(Routes.DELIVERY) },
                onOpenCatalogue = { navController.navigate(Routes.CATALOGUE) },
                onOpenSlots = { navController.navigate(Routes.SLOTS) },
                onOpenSupport = { navController.navigate(Routes.SUPPORT) },
                onOpenAnalytics = { navController.navigate(Routes.ANALYTICS) },
                onOpenStoreSettings = { navController.navigate(Routes.STORE_SETTINGS) },
                onOpenProfile = { navController.navigate(Routes.PROFILE) },
                onSignOut = sessionViewModel::signOut,
            )
        }

        // --- Delivery -------------------------------------------------------

        composable(Routes.DELIVERY) {
            DeliveryScreen(
                factory = factory,
                onBack = { navController.popBackStack() },
            )
        }

        // --- Orders ---------------------------------------------------------

        composable(
            route = Routes.ORDERS_ROUTE,
            arguments = listOf(
                navArgument(Routes.ARG_STATUS) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            val rawStatus = entry.arguments?.getString(Routes.ARG_STATUS)
            OrdersListScreen(
                factory = factory,
                initialStatus = OrderStatus.fromWire(rawStatus),
                onOpenOrder = { orderId -> navController.navigate(Routes.orderDetail(orderId)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.ORDER_DETAIL_ROUTE,
            arguments = listOf(
                navArgument(Routes.ARG_ORDER_ID) { type = NavType.StringType },
            ),
        ) { entry ->
            OrderDetailScreen(
                factory = factory,
                orderId = entry.arguments?.getString(Routes.ARG_ORDER_ID).orEmpty(),
                onBack = { navController.popBackStack() },
                onOpenComplaint = { navController.navigate(Routes.SUPPORT) },
            )
        }

        composable(Routes.SUPPORT) {
            SupportScreen(
                factory = factory,
                onOpenComplaint = { complaintId -> navController.navigate(Routes.complaintThread(complaintId)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.COMPLAINT_THREAD_ROUTE,
            arguments = listOf(
                navArgument(Routes.ARG_COMPLAINT_ID) { type = NavType.StringType },
            ),
        ) { entry ->
            ComplaintThreadScreen(
                factory = factory,
                complaintId = entry.arguments?.getString(Routes.ARG_COMPLAINT_ID).orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }

        // --- Catalogue ------------------------------------------------------

        composable(Routes.CATALOGUE) {
            CatalogueHubScreen(
                factory = factory,
                onOpenProduct = { productId -> navController.navigate(Routes.productEditor(productId)) },
                onOpenCategories = { navController.navigate(Routes.CATEGORIES) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.CATEGORIES) {
            CategoriesScreen(
                factory = factory,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.PRODUCT_EDITOR_ROUTE,
            arguments = listOf(
                navArgument(Routes.ARG_PRODUCT_ID) { type = NavType.StringType },
            ),
        ) { entry ->
            val rawId = entry.arguments?.getString(Routes.ARG_PRODUCT_ID)
            ProductEditorScreen(
                factory = factory,
                productId = rawId?.takeIf { it != Routes.NEW_PRODUCT },
                onBack = { navController.popBackStack() },
                onOpenWeights = { productId -> navController.navigate(Routes.weights(productId)) },
                onOpenOffers = { productId -> navController.navigate(Routes.offers(productId)) },
            )
        }

        composable(
            route = Routes.WEIGHTS_ROUTE,
            arguments = listOf(
                navArgument(Routes.ARG_PRODUCT_ID) { type = NavType.StringType },
            ),
        ) { entry ->
            WeightsScreen(
                factory = factory,
                productId = entry.arguments?.getString(Routes.ARG_PRODUCT_ID).orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.OFFERS_ROUTE,
            arguments = listOf(
                navArgument(Routes.ARG_PRODUCT_ID) { type = NavType.StringType },
            ),
        ) { entry ->
            OffersScreen(
                factory = factory,
                productId = entry.arguments?.getString(Routes.ARG_PRODUCT_ID),
                onBack = { navController.popBackStack() },
            )
        }

        // --- Delivery slots -------------------------------------------------

        composable(Routes.SLOTS) {
            SlotsScreen(
                factory = factory,
                onBack = { navController.popBackStack() },
            )
        }

        // --- Settings -------------------------------------------------------

        composable(Routes.STORE_SETTINGS) {
            StoreSettingsScreen(
                factory = factory,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.PROFILE) {
            ProfileScreen(
                factory = factory,
                onBack = { navController.popBackStack() },
                onOpenAppearance = { navController.navigate(Routes.APPEARANCE) },
            )
        }

        composable(Routes.APPEARANCE) {
            AppearanceScreen(
                factory = factory,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
