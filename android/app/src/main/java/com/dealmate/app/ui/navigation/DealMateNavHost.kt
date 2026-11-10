package com.dealmate.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dealmate.app.R
import com.dealmate.app.domain.model.SearchRequest
import com.dealmate.app.ui.explore.ExploreRoute
import com.dealmate.app.ui.history.HistoryRoute
import com.dealmate.app.ui.home.HomeRoute
import com.dealmate.app.ui.product.ProductRoute
import com.dealmate.app.ui.results.ResultsRoute
import com.dealmate.app.ui.settings.SettingsRoute
import com.dealmate.app.ui.wishlist.WishlistRoute

/** Where a tapped notification should take the user. */
sealed interface LaunchTarget {
    data class Product(val productId: Long, val variantId: Long?) : LaunchTarget
    data object Wishlist : LaunchTarget
}

private data class Tab(
    val route: String,
    @StringRes val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

private val tabs = listOf(
    Tab(Routes.HOME, R.string.tab_home, Icons.Outlined.Home, Icons.Filled.Home),
    Tab(Routes.EXPLORE, R.string.tab_explore, Icons.Outlined.Explore, Icons.Filled.Explore),
    Tab(Routes.WISHLIST, R.string.tab_wishlist, Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
    Tab(Routes.HISTORY, R.string.tab_history, Icons.Outlined.History, Icons.Filled.History),
    Tab(Routes.SETTINGS, R.string.tab_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
)

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** The five top-level tabs. */
@Composable
fun DealMateBottomBar(currentRoute: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.testTag("bottom_bar"),
    ) {
        tabs.forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(tab.route) },
                icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                modifier = Modifier.testTag("tab_${tab.route}"),
            )
        }
    }
}

@Composable
fun DealMateNavHost(
    launchTarget: LaunchTarget? = null,
    onLaunchTargetConsumed: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == null || currentRoute in Routes.topLevel

    val openProduct: (Long, Long) -> Unit = { productId, variantId -> navController.navigate(Routes.product(productId, variantId)) }
    val openSearch: (SearchRequest) -> Unit = { request -> navController.navigate(Routes.results(request)) }
    val openSettings: () -> Unit = { navController.navigateTab(Routes.SETTINGS) }

    LaunchedEffect(launchTarget) {
        when (launchTarget) {
            is LaunchTarget.Product -> navController.navigate(Routes.product(launchTarget.productId, launchTarget.variantId))
            LaunchTarget.Wishlist -> navController.navigateTab(Routes.WISHLIST)
            null -> return@LaunchedEffect
        }
        onLaunchTargetConsumed()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                DealMateBottomBar(currentRoute = currentRoute, onSelect = { navController.navigateTab(it) })
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Routes.HOME) {
                HomeRoute(
                    onSearch = openSearch,
                    onOpenProduct = openProduct,
                    onOpenWishlist = { navController.navigateTab(Routes.WISHLIST) },
                    onOpenSettings = openSettings,
                )
            }
            composable(Routes.EXPLORE) { ExploreRoute(onOpenProduct = openProduct, onOpenSettings = openSettings) }
            composable(Routes.WISHLIST) {
                WishlistRoute(
                    onOpenProduct = openProduct,
                    onOpenSettings = openSettings,
                    onGoSearch = { navController.navigateTab(Routes.HOME) },
                )
            }
            composable(Routes.HISTORY) {
                HistoryRoute(onSearch = openSearch, onOpenProduct = openProduct, onOpenSettings = openSettings)
            }
            composable(Routes.SETTINGS) { SettingsRoute() }
            composable(
                route = Routes.RESULTS,
                arguments = listOf(
                    navArgument(Routes.ARG_TYPE) { type = NavType.StringType; defaultValue = "text" },
                    navArgument(Routes.ARG_VALUE) { type = NavType.StringType; defaultValue = "" },
                    navArgument(Routes.ARG_HINT) { type = NavType.StringType; defaultValue = "" },
                ),
            ) {
                ResultsRoute(
                    onBack = { navController.popBackStack() },
                    onOpenProduct = openProduct,
                    onOpenSettings = openSettings,
                )
            }
            composable(
                route = Routes.PRODUCT,
                arguments = listOf(
                    navArgument(Routes.ARG_PRODUCT_ID) { type = NavType.LongType },
                    navArgument(Routes.ARG_VARIANT_ID) { type = NavType.LongType; defaultValue = 0L },
                ),
            ) {
                ProductRoute(onBack = { navController.popBackStack() }, onOpenSettings = openSettings)
            }
        }
    }
}
