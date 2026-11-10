package com.dealmate.app.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.dealmate.app.R
import com.dealmate.app.domain.model.AppError
import com.dealmate.app.domain.model.AppSettings
import com.dealmate.app.domain.model.Marketplace
import com.dealmate.app.domain.model.MatchType
import com.dealmate.app.domain.model.Outcome
import com.dealmate.app.domain.model.RecentSearch
import com.dealmate.app.domain.model.SearchRequest
import com.dealmate.app.domain.model.SearchType
import com.dealmate.app.domain.model.SortOption
import com.dealmate.app.domain.usecase.ResultFilter
import com.dealmate.app.testutil.FakeAssistantRepository
import com.dealmate.app.testutil.FakeHistoryRepository
import com.dealmate.app.testutil.FakeImagePreparer
import com.dealmate.app.testutil.FakeMetaRepository
import com.dealmate.app.testutil.FakeProductRepository
import com.dealmate.app.testutil.FakeSearchRepository
import com.dealmate.app.testutil.FakeSettingsRepository
import com.dealmate.app.testutil.FakeWishlistRepository
import com.dealmate.app.testutil.Fixtures
import com.dealmate.app.testutil.MainDispatcherRule
import com.dealmate.app.testutil.failure
import com.dealmate.app.ui.assistant.AssistantViewModel
import com.dealmate.app.ui.common.UiMessage
import com.dealmate.app.ui.explore.ExploreViewModel
import com.dealmate.app.ui.history.HistoryViewModel
import com.dealmate.app.ui.home.HomeViewModel
import com.dealmate.app.ui.navigation.Routes
import com.dealmate.app.ui.product.ProductViewModel
import com.dealmate.app.ui.results.ResultsViewModel
import com.dealmate.app.ui.settings.ConnectionState
import com.dealmate.app.ui.settings.SettingsViewModel
import com.dealmate.app.ui.wishlist.WishlistViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun resultsHandle(type: String = "text", value: String = "kem chống nắng Anessa 60ml") =
    SavedStateHandle(mapOf(Routes.ARG_TYPE to type, Routes.ARG_VALUE to value, Routes.ARG_HINT to ""))

class ResultsViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val search = FakeSearchRepository()
    private val settings = FakeSettingsRepository()

    private fun vm() = ResultsViewModel(resultsHandle(), search, settings)

    @Test
    fun `success shows groups, best offer and similar section data`() {
        val state = vm().state.value
        assertFalse(state.loading)
        assertNull(state.error)
        assertEquals(SearchRequest(SearchType.TEXT, "kem chống nắng Anessa 60ml"), search.requests.single())
        assertEquals(listOf(11L, 21L), state.groups.map { it.variantId })
        assertEquals(listOf(12L), state.similarGroups.map { it.variantId })
        assertTrue(state.bestVisible)
        assertEquals(11L, state.bestGroup?.variantId)
        assertFalse(state.fromCache)
        assertEquals(listOf(11L, 21L), state.assistantVariantIds)
    }

    @Test
    fun `partial result keeps the failed marketplace visible in state`() {
        val state = vm().state.value
        val result = requireNotNull(state.result)
        assertTrue(result.partial)
        assertEquals(listOf(Marketplace.TIKTOK_SHOP), result.sources.filter { !it.isOk }.map { it.marketplace })
        assertEquals(listOf(Marketplace.SHOPEE, Marketplace.LAZADA, Marketplace.TIKTOK_SHOP), state.availableMarketplaces)
    }

    @Test
    fun `error then retry recovers`() {
        search.outcome = failure(AppError.Kind.RATE_LIMITED, "Bạn tìm quá nhanh.")
        val vm = vm()
        assertEquals(AppError.Kind.RATE_LIMITED, vm.state.value.error?.kind)
        assertEquals("Bạn tìm quá nhanh.", vm.state.value.error?.message)
        assertNull(vm.state.value.result)

        search.outcome = Outcome.Success(Fixtures.search())
        vm.retry()
        assertNull(vm.state.value.error)
        assertNotNull(vm.state.value.result)
        assertEquals(2, search.requests.size)
    }

    @Test
    fun `offline cache fallback is flagged`() {
        search.outcome = Outcome.Success(Fixtures.search(), fromCache = true, cachedAt = Fixtures.NOW)
        val state = vm().state.value
        assertTrue(state.fromCache)
        assertEquals(Fixtures.NOW, state.cachedAt)
        assertNotNull(state.result)
    }

    @Test
    fun `filter that hides the backend's best offer hides the best banner`() {
        val vm = vm()
        vm.setFilter(ResultFilter(marketplaces = setOf(Marketplace.LAZADA)))
        val state = vm.state.value
        assertFalse(state.bestVisible)
        assertEquals(listOf(102L), state.groups.first { it.variantId == 11L }.offers.map { it.id })
        assertEquals(1, state.filter.activeCount)

        vm.setFilter(ResultFilter(matchTypes = setOf(MatchType.LIKELY)))
        assertEquals(listOf(21L), vm.state.value.groups.map { it.variantId })
        // the match filter never empties the separate "similar" section
        assertEquals(listOf(12L), vm.state.value.similarGroups.map { it.variantId })
    }

    @Test
    fun `default sort comes from settings`() {
        settings.state.value = settings.state.value.copy(defaultSort = SortOption.RATING)
        assertEquals(SortOption.RATING, vm().state.value.filter.sort)
    }

    @Test
    fun `all-incomplete result has no best offer`() {
        search.outcome = Outcome.Success(Fixtures.search("search_unknown_enums.json"))
        val state = vm().state.value
        assertFalse(state.bestVisible)
        assertNull(state.groups.single().bestOfferId)
        assertNull(state.groups.single().lowestFinalPrice)
    }

    @Test
    fun `clarification suggestion runs a new text search`() {
        search.outcome = Outcome.Success(Fixtures.search("search_clarification.json"))
        val vm = ResultsViewModel(resultsHandle(type = "image", value = "/tmp/x.jpg"), search, settings)
        assertTrue(vm.state.value.result!!.clarification.needed)
        search.outcome = Outcome.Success(Fixtures.search())
        vm.searchSuggestion("Anessa Perfect UV Milk 60ml")
        assertEquals(SearchRequest(SearchType.TEXT, "Anessa Perfect UV Milk 60ml"), search.requests.last())
        assertTrue(vm.state.value.groups.isNotEmpty())
    }
}

class ProductViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val products = FakeProductRepository()
    private val wishlist = FakeWishlistRepository()

    private fun vm() = ProductViewModel(
        SavedStateHandle(mapOf(Routes.ARG_PRODUCT_ID to 1L, Routes.ARG_VARIANT_ID to 11L)), products, wishlist,
    )

    @Test
    fun `loads offers and variants`() {
        val state = vm().state.value
        assertEquals(1L to 11L, products.offerRequests.single())
        assertEquals(3, state.product?.offers?.size)
        assertEquals(101L, state.bestOffer?.id)
        assertEquals(2, state.variants.size)
    }

    @Test
    fun `best offer is never an offer without a final price`() {
        val base = Fixtures.offers()
        products.offersOutcome = Outcome.Success(base.copy(bestOfferId = 103))
        assertNull(vm().state.value.bestOffer)
    }

    @Test
    fun `wishlist add then remove`() {
        val vm = vm()
        vm.addToWishlist(300_000)
        assertEquals(11L to 300_000L, wishlist.added.single())
        val saved = vm.state.value.product!!
        assertTrue(saved.inWishlist)
        assertEquals(300_000L, saved.targetPrice)
        assertEquals(R.string.wishlist_added, (vm.state.value.message as UiMessage.Text).res)

        vm.removeFromWishlist()
        assertEquals(listOf(saved.wishlistId), wishlist.removed)
        assertFalse(vm.state.value.product!!.inWishlist)
        assertNull(vm.state.value.product!!.targetPrice)
    }

    @Test
    fun `wishlist failure surfaces a message and leaves the state unchanged`() {
        wishlist.mutationError = AppError(AppError.Kind.OFFLINE)
        val vm = vm()
        vm.addToWishlist(null)
        assertFalse(vm.state.value.product!!.inWishlist)
        assertEquals(AppError.Kind.OFFLINE, (vm.state.value.message as UiMessage.Failure).error.kind)
        vm.messageShown()
        assertNull(vm.state.value.message)
    }

    @Test
    fun `error and retry`() {
        products.offersOutcome = failure(AppError.Kind.NOT_FOUND, "Không tìm thấy sản phẩm.")
        val vm = vm()
        assertEquals("Không tìm thấy sản phẩm.", vm.state.value.error?.message)
        products.offersOutcome = Outcome.Success(Fixtures.offers())
        vm.load()
        assertNull(vm.state.value.error)
        assertNotNull(vm.state.value.product)
    }

    @Test
    fun `selecting another variant reloads`() {
        val vm = vm()
        vm.selectVariant(12)
        assertEquals(1L to 12L, products.offerRequests.last())
    }
}

class WishlistViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val repo = FakeWishlistRepository().apply { serverItems = Fixtures.wishlist() }

    @Test
    fun `loads from server into the list`() = runTest {
        WishlistViewModel(repo).state.test {
            val state = expectMostRecentItem()
            assertEquals(listOf(31L, 32L, 33L), state.items.map { it.id })
            assertFalse(state.loading)
            assertFalse(state.offline)
        }
    }

    @Test
    fun `offline shows cached items with the offline flag and no error screen`() = runTest {
        repo.items.value = Fixtures.wishlist().take(2)
        repo.refreshOutcome = failure(AppError.Kind.OFFLINE)
        WishlistViewModel(repo).state.test {
            val state = expectMostRecentItem()
            assertEquals(2, state.items.size)
            assertTrue(state.offline)
            assertNull(state.error)
        }
    }

    @Test
    fun `failure with nothing cached is an error`() = runTest {
        repo.refreshOutcome = failure(AppError.Kind.UNAUTHORIZED)
        WishlistViewModel(repo).state.test {
            val state = expectMostRecentItem()
            assertTrue(state.items.isEmpty())
            assertEquals(AppError.Kind.UNAUTHORIZED, state.error?.kind)
            assertFalse(state.loading)
        }
    }

    @Test
    fun `remove hides immediately, undo restores, confirm deletes on the server`() = runTest {
        val vm = WishlistViewModel(repo)
        vm.state.test {
            val first = expectMostRecentItem().items.first()
            vm.requestRemoval(first)
            assertEquals(listOf(32L, 33L), expectMostRecentItem().items.map { it.id })
            assertTrue(repo.removed.isEmpty())

            vm.undoRemoval()
            assertEquals(listOf(31L, 32L, 33L), expectMostRecentItem().items.map { it.id })
            assertTrue(repo.removed.isEmpty())

            vm.requestRemoval(first)
            vm.confirmRemoval()
            assertEquals(listOf(31L), repo.removed)
            val after = expectMostRecentItem()
            assertEquals(listOf(32L, 33L), after.items.map { it.id })
            assertNull(after.pendingRemoval)
        }
    }

    @Test
    fun `edit target price`() = runTest {
        val vm = WishlistViewModel(repo)
        vm.state.test {
            val item = expectMostRecentItem().items.first()
            vm.setTarget(item, 290_000)
            assertEquals(Triple(11L, 41L, 290_000L), repo.targets.single())
            val state = expectMostRecentItem()
            assertEquals(290_000L, state.items.first().targetPrice)
            assertEquals(R.string.target_saved, (state.message as UiMessage.Text).res)

            // unchanged value -> no call
            vm.setTarget(state.items.first(), 290_000)
            assertEquals(1, repo.targets.size)
        }
    }
}

class SettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val settings = FakeSettingsRepository(AppSettings(baseUrl = "http://10.0.2.2:8000/"))
    private val meta = FakeMetaRepository()

    @Test
    fun `initial state mirrors stored settings and does not query sources when unconfigured`() {
        val state = SettingsViewModel(settings, meta).state.value
        assertTrue(state.loaded)
        assertEquals("http://10.0.2.2:8000/", state.baseUrlInput)
        assertEquals("", state.tokenInput)
        assertNull(state.sources)
        assertEquals(ConnectionState.Idle, state.connection)
    }

    @Test
    fun `check connection saves the inputs, then verifies health and token`() {
        val vm = SettingsViewModel(settings, meta)
        vm.setBaseUrlInput("https://dealmate.example.com/")
        vm.setTokenInput("abc")
        vm.saveAndCheckConnection()
        assertEquals("https://dealmate.example.com/", settings.state.value.baseUrl)
        assertEquals("abc", settings.state.value.token)
        val connection = vm.state.value.connection as ConnectionState.Connected
        assertEquals("Gia đình", connection.me.displayName)
        assertEquals("0.1.0", connection.health.version)
        // a working connection also loads the source status
        assertEquals(3, vm.state.value.sources?.sources?.size)
    }

    @Test
    fun `unreachable server versus rejected token are told apart`() {
        val vm = SettingsViewModel(settings, meta)
        meta.healthOutcome = failure(AppError.Kind.OFFLINE)
        vm.saveAndCheckConnection()
        assertEquals(false, (vm.state.value.connection as ConnectionState.Failed).serverReachable)

        meta.healthOutcome = Outcome.Success(com.dealmate.app.domain.model.Health("ok", false, "0.1.0"))
        meta.meOutcome = failure(AppError.Kind.UNAUTHORIZED, "Mã truy cập không đúng.")
        vm.saveAndCheckConnection()
        val failed = vm.state.value.connection as ConnectionState.Failed
        assertTrue(failed.serverReachable)
        assertEquals("Mã truy cập không đúng.", failed.error.message)

        // editing an input resets the verdict
        vm.setTokenInput("new")
        assertEquals(ConnectionState.Idle, vm.state.value.connection)
    }

    @Test
    fun `preferences are persisted and at least one marketplace stays selected`() {
        val vm = SettingsViewModel(settings, meta)
        vm.toggleMarketplace(Marketplace.SHOPEE)
        vm.toggleMarketplace(Marketplace.LAZADA)
        assertEquals(setOf(Marketplace.TIKTOK_SHOP), settings.state.value.preferredMarketplaces)
        vm.toggleMarketplace(Marketplace.TIKTOK_SHOP)
        assertEquals(setOf(Marketplace.TIKTOK_SHOP), settings.state.value.preferredMarketplaces)
        assertEquals(setOf(Marketplace.TIKTOK_SHOP), vm.state.value.settings.preferredMarketplaces)

        vm.setDefaultSort(SortOption.RATING)
        vm.setNotificationsEnabled(true)
        vm.setLocation("Hà Nội")
        vm.setLanguage("en")
        with(settings.state.value) {
            assertEquals(SortOption.RATING, defaultSort)
            assertTrue(notificationsEnabled)
            assertEquals("Hà Nội", deliveryLocation)
            assertEquals("en", language)
        }
    }

    @Test
    fun `source status error is kept separately from the settings`() {
        val configured = FakeSettingsRepository()
        meta.sourcesOutcome = failure(AppError.Kind.UNAVAILABLE)
        val vm = SettingsViewModel(configured, meta)
        assertEquals(AppError.Kind.UNAVAILABLE, vm.state.value.sourcesError?.kind)
        meta.sourcesOutcome = Outcome.Success(Fixtures.sources())
        vm.loadSources()
        assertNull(vm.state.value.sourcesError)
        assertTrue(vm.state.value.sources!!.demoMode)
    }
}

class HomeViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val search = FakeSearchRepository()
    private val products = FakeProductRepository()
    private val settings = FakeSettingsRepository()
    private val images = FakeImagePreparer()

    @Test
    fun `request depends on the selected method and its input`() = runTest {
        val vm = HomeViewModel(search, products, settings, images)
        vm.state.test {
            assertNull(expectMostRecentItem().request)
            vm.setQuery(" a ")
            assertNull(expectMostRecentItem().request)
            vm.setQuery(" bỉm Merries size L ")
            assertEquals(SearchRequest(SearchType.TEXT, "bỉm Merries size L"), expectMostRecentItem().request)

            vm.setMethod(SearchType.URL)
            assertNull(expectMostRecentItem().request)
            vm.setUrl("https://shopee.vn/product/1")
            assertEquals(SearchRequest(SearchType.URL, "https://shopee.vn/product/1"), expectMostRecentItem().request)

            vm.setMethod(SearchType.IMAGE)
            assertNull(expectMostRecentItem().request)
            vm.setImageHint("kem chống nắng")
            vm.onImagePicked("content://media/1")
            assertEquals(SearchRequest(SearchType.IMAGE, "/tmp/upload.jpg", "kem chống nắng"), expectMostRecentItem().request)
            vm.clearImage()
            assertNull(expectMostRecentItem().request)
        }
    }

    @Test
    fun `unreadable image reports an error and offers no request`() = runTest {
        images.outcome = failure(AppError.Kind.VALIDATION, code = "image_unreadable")
        val vm = HomeViewModel(search, products, settings, images)
        vm.state.test {
            vm.setMethod(SearchType.IMAGE)
            vm.onImagePicked("content://media/2")
            val state = expectMostRecentItem()
            assertEquals("image_unreadable", state.imageError?.code)
            assertNull(state.request)
        }
    }

    @Test
    fun `recent searches and configuration state flow into the UI state`() = runTest {
        settings.state.value = AppSettings(baseUrl = "http://10.0.2.2:8000/", token = "")
        search.recent.value = listOf(RecentSearch(SearchType.TEXT, "anessa", Fixtures.NOW))
        HomeViewModel(search, products, settings, images).state.test {
            val state = expectMostRecentItem()
            assertFalse(state.configured)
            assertEquals("anessa", state.recentSearches.single().query)
        }
    }
}

class OtherViewModelsTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun `history loads and clears`() {
        val repo = FakeHistoryRepository()
        val vm = HistoryViewModel(repo)
        assertEquals(4, vm.state.value.history?.searches?.size)
        assertFalse(vm.state.value.isEmpty)
        vm.clear()
        assertEquals(1, repo.cleared)
        assertTrue(vm.state.value.isEmpty)
        assertEquals(R.string.history_cleared, (vm.state.value.message as UiMessage.Text).res)
    }

    @Test
    fun `history clear failure keeps the list`() {
        val repo = FakeHistoryRepository().apply { clearOutcome = failure(AppError.Kind.OFFLINE) }
        val vm = HistoryViewModel(repo)
        vm.clear()
        assertFalse(vm.state.value.isEmpty)
        assertTrue(vm.state.value.message is UiMessage.Failure)
    }

    @Test
    fun `explore passes filters to the backend and keeps its reason when unavailable`() {
        val meta = FakeMetaRepository()
        val vm = ExploreViewModel(meta)
        assertFalse(vm.state.value.explore!!.available)
        assertNotNull(vm.state.value.explore!!.reason)
        vm.setCategory("Tã bỉm")
        vm.setMarketplace(Marketplace.LAZADA)
        assertEquals("Tã bỉm" to Marketplace.LAZADA, meta.exploreRequests.last())
    }

    @Test
    fun `assistant asks about the given variants and exposes who answered`() {
        val repo = FakeAssistantRepository()
        val vm = AssistantViewModel(repo)
        vm.ask("  Shop nào rẻ nhất? ", listOf(11, 21))
        assertEquals("Shop nào rẻ nhất?" to listOf(11L, 21L), repo.questions.single())
        assertEquals("rules", vm.state.value.answer?.generatedBy)
        assertEquals("Shop nào rẻ nhất?", vm.state.value.asked)

        repo.outcome = failure(AppError.Kind.UNAVAILABLE, "AI chưa sẵn sàng.", "ai_unavailable")
        vm.ask("Có phải giá tốt không?", listOf(11))
        assertNull(vm.state.value.answer)
        assertEquals("AI chưa sẵn sàng.", vm.state.value.error?.message)

        // too short / no variants -> nothing is sent
        vm.ask("a", listOf(11))
        vm.ask("Vì sao giá chênh lệch?", emptyList())
        assertEquals(2, repo.questions.size)
    }
}
