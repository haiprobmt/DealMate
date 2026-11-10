package com.dealmate.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.dealmate.app.domain.model.AppError
import com.dealmate.app.domain.model.AppSettings
import com.dealmate.app.domain.model.Health
import com.dealmate.app.domain.model.Me
import com.dealmate.app.domain.model.RecentSearch
import com.dealmate.app.domain.model.SearchRequest
import com.dealmate.app.domain.model.SearchType
import com.dealmate.app.domain.model.ViewedProduct
import com.dealmate.app.domain.usecase.ResultFilter
import com.dealmate.app.testutil.Fixtures
import com.dealmate.app.ui.assistant.AssistantContent
import com.dealmate.app.ui.assistant.AssistantUiState
import com.dealmate.app.ui.common.LocalClock
import com.dealmate.app.ui.components.SearchFilterContent
import com.dealmate.app.ui.explore.ExploreActions
import com.dealmate.app.ui.explore.ExploreScreen
import com.dealmate.app.ui.explore.ExploreUiState
import com.dealmate.app.ui.history.HistoryActions
import com.dealmate.app.ui.history.HistoryScreen
import com.dealmate.app.ui.history.HistoryUiState
import com.dealmate.app.ui.home.HomeActions
import com.dealmate.app.ui.home.HomeScreen
import com.dealmate.app.ui.home.HomeUiState
import com.dealmate.app.ui.navigation.DealMateBottomBar
import com.dealmate.app.ui.navigation.Routes
import com.dealmate.app.ui.product.ProductActions
import com.dealmate.app.ui.product.ProductScreen
import com.dealmate.app.ui.product.ProductUiState
import com.dealmate.app.ui.results.ResultsActions
import com.dealmate.app.ui.results.ResultsScreen
import com.dealmate.app.ui.results.ResultsUiState
import com.dealmate.app.ui.settings.ConnectionState
import com.dealmate.app.ui.settings.SettingsActions
import com.dealmate.app.ui.settings.SettingsScreen
import com.dealmate.app.ui.settings.SettingsUiState
import com.dealmate.app.ui.theme.DealMateTheme
import com.dealmate.app.ui.wishlist.WishlistActions
import com.dealmate.app.ui.wishlist.WishlistScreen
import com.dealmate.app.ui.wishlist.WishlistUiState
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

private const val PHONE = "vi-w411dp-h891dp-420dpi"
private const val PHONE_TALL = "vi-w411dp-h1700dp-420dpi"

/**
 * Renders the real screen composables with fixture data (Robolectric native graphics + Roborazzi) and
 * writes PNGs to `android/screenshots/`, in light and dark. Used to review the design without a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE)
class ScreenshotTest {

    private val dir = File(System.getProperty("dealmate.screenshotDir") ?: "build/screenshots").apply { mkdirs() }

    private fun shoot(name: String, tab: String? = null, content: @Composable () -> Unit) {
        for (dark in listOf(false, true)) {
            val file = File(dir, "${name}_${if (dark) "dark" else "light"}.png")
            captureRoboImage(filePath = file.absolutePath) {
                DealMateTheme(darkTheme = dark) {
                    CompositionLocalProvider(LocalClock provides Fixtures.CLOCK) {
                        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                            Column {
                                Box(Modifier.weight(1f)) { content() }
                                if (tab != null) DealMateBottomBar(currentRoute = tab, onSelect = {})
                            }
                        }
                    }
                }
            }
            assertTrue("screenshot not written: $file", file.length() > 10_000)
        }
    }

    private val request = SearchRequest(SearchType.TEXT, "Tìm kem chống nắng Anessa 60ml giá rẻ nhất")

    @Test
    fun home() = shoot("home", tab = Routes.HOME) {
        HomeScreen(
            state = HomeUiState(
                recentSearches = listOf(
                    RecentSearch(SearchType.TEXT, "Tìm kem chống nắng Anessa 60ml giá rẻ nhất", Fixtures.NOW),
                    RecentSearch(SearchType.TEXT, "Tìm nước giặt Ariel 3.6kg", Fixtures.NOW),
                ),
                recentlyViewed = listOf(
                    ViewedProduct(1, 11, "Kem chống nắng Anessa Perfect UV Sunscreen Skincare Milk SPF50+ PA++++ 60ml", null, 315_000, Fixtures.NOW),
                    ViewedProduct(5, 51, "Máy sấy tóc Dyson Supersonic HD15", null, null, Fixtures.NOW),
                    ViewedProduct(7, 71, "Tã quần Merries size L 44 miếng", null, 345_000, Fixtures.NOW),
                ),
            ),
            actions = HomeActions(),
        )
    }

    @Test
    @Config(qualifiers = PHONE_TALL)
    fun homeFull() = shoot("home_full", tab = Routes.HOME) {
        HomeScreen(
            state = HomeUiState(
                configured = false,
                recentSearches = listOf(RecentSearch(SearchType.TEXT, "Tìm bỉm Merries size L", Fixtures.NOW)),
                recentlyViewed = listOf(ViewedProduct(7, 71, "Tã quần Merries size L 44 miếng", null, 345_000, Fixtures.NOW)),
            ),
            actions = HomeActions(),
        )
    }

    @Test
    fun results() = shoot("results") {
        ResultsScreen(ResultsUiState(request = request, loading = false, result = Fixtures.search()), ResultsActions())
    }

    @Test
    @Config(qualifiers = PHONE_TALL)
    fun resultsFull() = shoot("results_full") {
        ResultsScreen(ResultsUiState(request = request, loading = false, result = Fixtures.search()), ResultsActions())
    }

    @Test
    fun product() = shoot("product") {
        ProductScreen(
            ProductUiState(productId = 1, variantId = 11, loading = false, product = Fixtures.offers(), variants = Fixtures.product().variants),
            ProductActions(),
        )
    }

    @Test
    @Config(qualifiers = "vi-w411dp-h2300dp-420dpi")
    fun productFull() = shoot("product_full") {
        ProductScreen(
            ProductUiState(
                productId = 1, variantId = 11, loading = false,
                product = Fixtures.offers().copy(inWishlist = true, wishlistId = 31, targetPrice = 300_000),
                variants = Fixtures.product().variants,
            ),
            ProductActions(),
        )
    }

    @Test
    fun wishlist() = shoot("wishlist", tab = Routes.WISHLIST) {
        WishlistScreen(WishlistUiState(items = Fixtures.wishlist(), loading = false), WishlistActions())
    }

    @Test
    fun history() = shoot("history", tab = Routes.HISTORY) {
        HistoryScreen(HistoryUiState(loading = false, history = Fixtures.history()), HistoryActions())
    }

    @Test
    @Config(qualifiers = PHONE_TALL)
    fun settings() = shoot("settings", tab = Routes.SETTINGS) {
        val settings = AppSettings(baseUrl = "https://dealmate.example.com/", token = "a-family-token", deliveryLocation = "Quận 7, TP. Hồ Chí Minh")
        SettingsScreen(
            state = SettingsUiState(
                loaded = true, settings = settings, baseUrlInput = settings.baseUrl, tokenInput = settings.token,
                locationInput = settings.deliveryLocation,
                connection = ConnectionState.Connected(Health("ok", true, "0.1.0"), Me(1, "Gia đình", true)),
                sources = Fixtures.sources(),
            ),
            language = "vi",
            pushConfiguredInApp = false,
            versionName = "1.0.0",
            actions = SettingsActions(),
        )
    }

    @Test
    fun emptyState() = shoot("empty_state", tab = Routes.EXPLORE) {
        ExploreScreen(ExploreUiState(loading = false, explore = Fixtures.explore()), ExploreActions())
    }

    @Test
    fun errorState() = shoot("error_state") {
        ResultsScreen(ResultsUiState(request = request, loading = false, error = AppError(AppError.Kind.OFFLINE)), ResultsActions())
    }

    @Test
    fun filterSheet() = shoot("filter_sheet") {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            SearchFilterContent(
                filter = ResultFilter(marketplaces = setOf(com.dealmate.app.domain.model.Marketplace.SHOPEE)),
                marketplaces = com.dealmate.app.domain.model.Marketplace.selectable,
                onChange = {},
                onDone = {},
            )
        }
    }

    @Test
    fun assistant() = shoot("assistant") {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            AssistantContent(
                state = AssistantUiState(asked = "Shop nào rẻ nhất?", answer = Fixtures.assistant()),
                onQuestionChange = {},
                onAsk = {},
            )
        }
    }
}
