package com.dealmate.app.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.dealmate.app.MainActivity
import com.dealmate.app.domain.model.AppError
import com.dealmate.app.domain.model.AppSettings
import com.dealmate.app.domain.model.Outcome
import com.dealmate.app.domain.model.SearchType
import com.dealmate.app.testutil.FakeAssistantRepository
import com.dealmate.app.testutil.FakeHistoryRepository
import com.dealmate.app.testutil.FakeMetaRepository
import com.dealmate.app.testutil.FakeProductRepository
import com.dealmate.app.testutil.FakeSearchRepository
import com.dealmate.app.testutil.FakeSettingsRepository
import com.dealmate.app.testutil.FakeWishlistRepository
import com.dealmate.app.testutil.Fixtures
import com.dealmate.app.testutil.failure
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import javax.inject.Inject

/**
 * End-to-end UI tests of the real activity + navigation graph + ViewModels under Robolectric, with the
 * repositories replaced by fakes fed from the JSON fixtures. The screen is made very tall so that
 * whole lazy lists are composed.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "vi-w411dp-h2800dp-xhdpi")
class AppFlowTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var search: FakeSearchRepository
    @Inject lateinit var products: FakeProductRepository
    @Inject lateinit var wishlist: FakeWishlistRepository
    @Inject lateinit var history: FakeHistoryRepository
    @Inject lateinit var meta: FakeMetaRepository
    @Inject lateinit var assistant: FakeAssistantRepository
    @Inject lateinit var settings: FakeSettingsRepository

    @Before
    fun setUp() = hilt.inject()

    private fun tag(tag: String, unmerged: Boolean = false) = compose.onNodeWithTag(tag, useUnmergedTree = unmerged)

    private fun searchFor(text: String) {
        tag("search_field").performTextInput(text)
        tag("search_button").performClick()
        compose.waitForIdle()
    }

    private fun openProductFromResults() {
        searchFor("kem chống nắng Anessa 60ml")
        tag("product_card_11").performClick()
        compose.waitForIdle()
        tag("product_screen").assertIsDisplayed()
    }

    @Test
    fun bottomNavigation_switchesBetweenTheFiveTabs() {
        tag("home_screen").assertIsDisplayed()
        tag("tab_explore").performClick()
        tag("explore_screen").assertIsDisplayed()
        // Explore invents nothing: the backend's own reason is the empty state
        compose.onNodeWithText(Fixtures.explore().reason!!).assertIsDisplayed()

        tag("tab_wishlist").performClick()
        tag("wishlist_screen").assertIsDisplayed()
        tag("tab_history").performClick()
        tag("history_screen").assertIsDisplayed()
        tag("history_search_501").assertIsDisplayed()
        tag("tab_settings").performClick()
        tag("settings_screen").assertIsDisplayed()
        tag("sources_section").assertExists()
        tag("tab_home").performClick()
        tag("home_screen").assertIsDisplayed()
    }

    @Test
    fun searchFlow_resultsShowCheapestMatchingOffer_thenProductDetail() {
        searchFor("kem chống nắng Anessa 60ml")
        assertEquals(SearchType.TEXT, search.requests.single().type)
        assertEquals("kem chống nắng Anessa 60ml", search.requests.single().value)

        tag("results_screen").assertIsDisplayed()
        // detail screens have no bottom bar
        tag("bottom_bar").assertDoesNotExist()
        // rule 1: the cheapest offer of the best-matching product is immediately visible
        tag("best_banner").assertIsDisplayed()
        tag("best_price", unmerged = true).assertTextEquals("315.000đ")
        tag("best_caveat", unmerged = true).assertExists()
        // interpreted query + per-marketplace status, with the failed marketplace named
        compose.onAllNodesWithTag("query_chip").assertCountEquals(5)
        tag("partial_notice").assertIsDisplayed()
        compose.onNode(hasText("TikTok Shop", substring = true) and hasAnyAncestor(hasTestTag("partial_notice"))).assertExists()
        // exact + likely groups, and the similar group in its own titled section below
        tag("product_card_11").assertIsDisplayed()
        tag("product_card_21").assertIsDisplayed()
        tag("similar_section").assertIsDisplayed()
        tag("product_card_12").assertIsDisplayed()
        val similarTop = tag("similar_section").fetchSemanticsNode().boundsInRoot.top
        assertTrue(tag("product_card_21").fetchSemanticsNode().boundsInRoot.bottom <= similarTop)
        assertTrue(tag("product_card_12").fetchSemanticsNode().boundsInRoot.top >= similarTop)

        tag("product_card_11").performClick()
        compose.waitForIdle()
        tag("product_screen").assertIsDisplayed()
        assertEquals(1L to 11L, products.offerRequests.single())
        tag("product_title").assertIsDisplayed()
        tag("offer_card_101").assertIsDisplayed()
        tag("price_history").assertIsDisplayed()
    }

    @Test
    fun filter_selectingAMarketplaceNarrowsResultsAndHidesBestBanner() {
        searchFor("kem chống nắng Anessa 60ml")
        tag("filter_button").performClick()
        compose.waitForIdle()
        tag("filter_sheet").assertIsDisplayed()
        tag("filter_marketplace_lazada").performClick()
        tag("filter_done").performClick()
        compose.waitForIdle()

        // the backend's best offer (Shopee) is filtered out -> no banner, an explicit notice instead
        tag("best_banner").assertDoesNotExist()
        tag("notice_card").assertIsDisplayed()
        compose.onNode(hasTestTag("group_price") and hasAnyAncestor(hasTestTag("product_card_11")), useUnmergedTree = true)
            .assertTextEquals("322.000đ")
        // the similar product is Shopee-only, so its section disappears too
        tag("product_card_12").assertDoesNotExist()
    }

    // Typing into a text field hosted in a Dialog only settles at Robolectric's small default window
    // size (see ComponentTest), so this test uses it and scrolls to what it needs.
    @Test
    @Config(qualifiers = "vi-w320dp-h470dp-xhdpi")
    fun wishlist_addWithTargetPrice_thenRemove_fromProductDetail() {
        searchFor("kem chống nắng Anessa 60ml")
        tag("results_list").performScrollToNode(hasTestTag("product_card_11"))
        tag("product_card_11").performClick()
        compose.waitForIdle()
        tag("product_screen").assertIsDisplayed()
        tag("wishlist_button").performClick()
        tag("target_price_field").performTextInput("300000")
        tag("target_confirm").performClick()
        compose.waitForIdle()
        assertEquals(11L to 300_000L, wishlist.added.single())
        compose.onNode(hasText("Đã lưu") and hasAnyAncestor(hasTestTag("wishlist_button")), useUnmergedTree = true).assertExists()
        tag("edit_target").assertIsDisplayed()

        tag("wishlist_button").performClick()
        compose.waitForIdle()
        assertEquals(1, wishlist.removed.size)
        compose.onNode(hasText("Lưu yêu thích") and hasAnyAncestor(hasTestTag("wishlist_button")), useUnmergedTree = true).assertExists()
    }

    @Test
    fun wishlistTab_removeWithUndo_thenRemoveForReal() {
        wishlist.serverItems = Fixtures.wishlist()
        tag("tab_wishlist").performClick()
        compose.waitForIdle()
        tag("wishlist_item_31").assertIsDisplayed()
        // previous price with a "down" indicator for the first item
        compose.onNode(hasTestTag("wishlist_change") and hasAnyAncestor(hasTestTag("wishlist_item_31")), useUnmergedTree = true)
            .assertTextEquals("Giảm 13.000đ so với 328.000đ")

        tag("wishlist_remove_31").performClick()
        compose.waitForIdle()
        tag("wishlist_item_31").assertDoesNotExist()
        compose.onNodeWithText("Hoàn tác").performClick()
        compose.waitForIdle()
        tag("wishlist_item_31").assertIsDisplayed()
        assertTrue(wishlist.removed.isEmpty())

        tag("wishlist_remove_31").performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(12_000)
        compose.waitUntil(5_000) { wishlist.removed == listOf(31L) }
        tag("wishlist_item_31").assertDoesNotExist()
        tag("wishlist_item_32").assertIsDisplayed()
    }

    @Test
    fun wishlistTab_offlineShowsCachedItemsWithHint() {
        wishlist.items.value = Fixtures.wishlist()
        wishlist.refreshOutcome = failure(AppError.Kind.OFFLINE)
        tag("tab_wishlist").performClick()
        compose.waitForIdle()
        tag("offline_hint").assertIsDisplayed()
        tag("wishlist_item_31").assertIsDisplayed()
        tag("error_state").assertDoesNotExist()
    }

    @Test
    fun errorState_showsServerMessage_andRetryRecovers() {
        search.outcome = failure(AppError.Kind.SERVER, "Máy chủ đang bảo trì, vui lòng thử lại sau.")
        searchFor("máy sấy tóc Dyson")
        tag("error_state").assertIsDisplayed()
        compose.onNodeWithText("Máy chủ đang bảo trì, vui lòng thử lại sau.").assertIsDisplayed()
        tag("best_banner").assertDoesNotExist()

        search.outcome = Outcome.Success(Fixtures.search())
        tag("retry_button").performClick()
        compose.waitForIdle()
        tag("error_state").assertDoesNotExist()
        tag("best_banner").assertIsDisplayed()
        assertEquals(2, search.requests.size)
    }

    @Test
    fun offline_cachedResultsAreShownWithAnOfflineHint() {
        search.outcome = Outcome.Success(Fixtures.search(), fromCache = true, cachedAt = Fixtures.NOW)
        searchFor("kem chống nắng Anessa 60ml")
        tag("offline_hint").assertIsDisplayed()
        tag("best_banner").assertIsDisplayed()
        tag("product_card_11").assertIsDisplayed()
    }

    @Test
    fun demoData_bannerAndDemoTagsAreShown_andSampleLinksNeverOpenABrowser() {
        searchFor("kem chống nắng Anessa 60ml")
        tag("demo_banner").assertIsDisplayed()
        // best banner + three product cards carry the tag
        assertTrue(compose.onAllNodesWithTag("demo_tag", useUnmergedTree = true).fetchSemanticsNodes().size >= 4)

        tag("product_card_11").performClick()
        compose.waitForIdle()
        tag("demo_banner").assertIsDisplayed()
        listOf(101, 102, 103).forEach { id ->
            compose.onNode(hasTestTag("demo_tag") and hasAnyAncestor(hasTestTag("offer_card_$id"))).assertExists()
        }

        tag("open_listing_101").performClick()
        compose.waitForIdle()
        tag("sample_dialog").assertIsDisplayed()
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test
    fun incompletePrice_isNeverLabelledCheapest() {
        openProductFromResults()
        // exactly one "cheapest" label, on the verified 315.000đ offer
        compose.onAllNodesWithTag("best_offer_label").assertCountEquals(1)
        compose.onNode(hasTestTag("best_offer_label") and hasAnyAncestor(hasTestTag("offer_card_101"))).assertExists()
        // the incomplete offer shows a LOWER number (299.000đ) but only as a known subtotal
        compose.onNode(hasTestTag("known_subtotal") and hasAnyAncestor(hasTestTag("offer_card_103"))).assertTextEquals("299.000đ")
        compose.onNode(hasTestTag("price_badge_incomplete") and hasAnyAncestor(hasTestTag("offer_card_103"))).assertExists()
        compose.onNode(hasText("Chưa gồm phí vận chuyển") and hasAnyAncestor(hasTestTag("offer_card_103"))).assertExists()
        compose.onNode(hasTestTag("final_price") and hasAnyAncestor(hasTestTag("offer_card_103"))).assertDoesNotExist()
        compose.onNode(hasTestTag("best_offer_label") and hasAnyAncestor(hasTestTag("offer_card_103"))).assertDoesNotExist()
    }

    @Test
    fun incompletePrice_evenWhenBackendMarksItBest_andWhenAllOffersAreIncomplete() {
        // defensive: a (buggy) best_offer_id pointing at an incomplete offer must not be highlighted
        products.offersOutcome = Outcome.Success(Fixtures.offers().copy(bestOfferId = 103))
        search.outcome = Outcome.Success(Fixtures.search("search_unknown_enums.json"))
        searchFor("bỉm Merries size L")
        tag("best_banner").assertDoesNotExist()
        tag("notice_card").assertIsDisplayed()
        compose.onNode(hasTestTag("group_price") and hasAnyAncestor(hasTestTag("product_card_71")), useUnmergedTree = true)
            .assertDoesNotExist()

        tag("product_card_71").performClick()
        compose.waitForIdle()
        tag("product_screen").assertIsDisplayed()
        compose.onAllNodesWithTag("best_offer_label").assertCountEquals(0)
    }

    @Test
    fun notConfigured_homeLinksToSettings_andSearchExplainsWhy() {
        settings.state.value = AppSettings(baseUrl = "http://10.0.2.2:8000/", token = "")
        compose.waitForIdle()
        tag("not_configured_card").assertIsDisplayed()

        search.outcome = failure(AppError.Kind.NOT_CONFIGURED)
        searchFor("nước giặt Ariel 3.6kg")
        tag("error_state").assertIsDisplayed()
        compose.onNodeWithText("Chưa kết nối máy chủ").assertIsDisplayed()
        tag("retry_button").assertDoesNotExist()
        compose.onNodeWithText("Mở Cài đặt").performClick()
        compose.waitForIdle()
        tag("settings_screen").assertIsDisplayed()
    }

    @Test
    fun categoryShortcut_runsATextSearch() {
        tag("category_Tã bỉm").performClick()
        compose.waitForIdle()
        assertEquals("Tã bỉm", search.requests.single().value)
        tag("results_screen").assertIsDisplayed()
    }

    @Test
    fun clarification_showsMessageAndSuggestionChipsThatSearch() {
        search.outcome = Outcome.Success(Fixtures.search("search_clarification.json"))
        searchFor("kem chống nắng")
        tag("clarification_card").assertIsDisplayed()
        compose.onNodeWithText("Chưa nhận ra chính xác sản phẩm trong ảnh. Bạn muốn tìm sản phẩm nào?").assertIsDisplayed()
        compose.onAllNodesWithTag("suggestion_chip").assertCountEquals(2)

        search.outcome = Outcome.Success(Fixtures.search())
        compose.onAllNodesWithTag("suggestion_chip").onFirst().performClick()
        compose.waitForIdle()
        assertEquals("Anessa Perfect UV Milk 60ml", search.requests.last().value)
        tag("best_banner").assertIsDisplayed()
    }

    @Test
    fun assistant_answersWithFactsDisclaimerAndWhoAnswered() {
        searchFor("kem chống nắng Anessa 60ml")
        tag("assistant_button").performClick()
        compose.waitForIdle()
        tag("assistant_sheet").assertIsDisplayed()
        compose.onAllNodesWithTag("assistant_chip").assertCountEquals(4)
        compose.onAllNodesWithTag("assistant_chip").onFirst().performClick()
        compose.waitForIdle()

        assertEquals("Shop nào rẻ nhất?" to listOf(11L, 21L), assistant.questions.single())
        tag("assistant_answer").assertIsDisplayed()
        tag("assistant_generated_by").assertTextEquals("Trả lời theo quy tắc (không dùng AI)")
        compose.onNodeWithText(Fixtures.assistant().disclaimer).assertIsDisplayed()
    }

    @Test
    fun settings_checkConnectionReportsSuccessAndDemoMode() {
        tag("tab_settings").performClick()
        compose.waitForIdle()
        tag("check_connection").performClick()
        compose.waitForIdle()
        tag("connection_ok").assertIsDisplayed()
        tag("push_status").assertIsDisplayed()

        meta.meOutcome = failure(AppError.Kind.UNAUTHORIZED, "Mã truy cập không đúng.")
        tag("token_field").performTextInput("x")
        tag("check_connection").performClick()
        compose.waitForIdle()
        tag("connection_failed").assertIsDisplayed()
        compose.onNodeWithText("Mã truy cập không đúng.").assertIsDisplayed()
    }

    @Test
    fun history_tapRepeatsTheSearch_andClearAsksForConfirmation() {
        tag("tab_history").performClick()
        compose.waitForIdle()
        tag("history_search_490").performClick()
        compose.waitForIdle()
        assertEquals("Tìm nước giặt Ariel 3.6kg", search.requests.single().value)
        tag("results_screen").assertIsDisplayed()

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        tag("history_clear").performClick()
        assertEquals(0, history.cleared)
        tag("history_clear_confirm").performClick()
        compose.waitForIdle()
        assertEquals(1, history.cleared)
        tag("empty_state").assertIsDisplayed()
    }
}
