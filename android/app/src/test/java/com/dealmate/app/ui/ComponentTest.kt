package com.dealmate.app.ui

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.dealmate.app.domain.model.Seller
import com.dealmate.app.ui.components.SellerInformation
import com.dealmate.app.ui.components.TargetPriceDialog
import com.dealmate.app.ui.components.rememberListingOpener
import com.dealmate.app.ui.theme.DealMateTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Component-level Robolectric tests. They run at Robolectric's default (small) window size: with
 * Compose 1.9 + Robolectric 4.16, typing into ANY text field hosted in a Dialog window never becomes
 * idle on larger simulated windows (reproduced with a bare `Dialog { BasicTextField() }`).
 */
@RunWith(RobolectricTestRunner::class)
class ComponentTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun targetPriceDialog_returnsTheTypedAmount() {
        var result: Long? = -1
        compose.setContent {
            DealMateTheme { TargetPriceDialog(initial = null, title = "Giá mong muốn", onConfirm = { result = it }, onDismiss = {}) }
        }
        compose.onNodeWithTag("target_price_field").performTextInput("300000")
        compose.onNodeWithText("300.000đ").assertIsDisplayed()
        compose.onNodeWithTag("target_confirm").performClick()
        assertEquals(300_000L, result)
    }

    @Test
    fun targetPriceDialog_emptyMeansNoTarget_andZeroIsRejected() {
        var result: Long? = -1
        compose.setContent {
            DealMateTheme { TargetPriceDialog(initial = null, title = "Giá mong muốn", onConfirm = { result = it }, onDismiss = {}) }
        }
        compose.onNodeWithTag("target_confirm").performClick()
        assertNull(result)

        compose.onNodeWithTag("target_price_field").performTextInput("0")
        compose.onNodeWithTag("target_confirm").assertIsNotEnabled()
    }

    @Test
    fun listingOpener_opensRealLinksWithActionView_butNeverSampleLinks() {
        compose.setContent {
            DealMateTheme {
                val open = rememberListingOpener()
                androidx.compose.foundation.layout.Column {
                Button(onClick = { open("https://shopee.vn/product/1") }, modifier = Modifier.testTag("real")) { Text("real") }
                Button(onClick = { open("https://shopee.demo.invalid/product/101") }, modifier = Modifier.testTag("sample")) { Text("sample") }
                Button(onClick = { open("javascript:alert(1)") }, modifier = Modifier.testTag("bad")) { Text("bad") }
                }
            }
        }
        compose.onNodeWithTag("sample").performClick()
        compose.onNodeWithTag("sample_dialog").assertIsDisplayed()
        assertNull(shadowOf(app).nextStartedActivity)
        compose.onNodeWithText("Đã hiểu").performClick()

        compose.onNodeWithTag("bad").performClick()
        compose.onNodeWithTag("cannot_open_dialog").assertIsDisplayed()
        assertNull(shadowOf(app).nextStartedActivity)
        compose.onNodeWithText("Đã hiểu").performClick()

        compose.onNodeWithTag("real").performClick()
        val intent = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://shopee.vn/product/1", intent.dataString)
    }

    @Test
    fun sellerInformation_showsOfficialLabelOnlyWhenTheMarketplaceSaysSo() {
        val seller = Seller("Anessa Official Store", isOfficialStore = true)
        compose.setContent { DealMateTheme { SellerInformation(seller, "Shopee") } }
        // worded as the marketplace's own label, not as an authenticity claim by the app
        compose.onNodeWithText("Gian hàng Mall/Official — nhãn do Shopee gắn").assertIsDisplayed()
    }

    @Test
    fun sellerInformation_noLabelWhenUnknownOrFalse() {
        compose.setContent {
            DealMateTheme {
                androidx.compose.foundation.layout.Column {
                    SellerInformation(Seller("Anessa Official Store", isOfficialStore = null), "Shopee")
                    SellerInformation(Seller("Shop chính hãng 100%", isOfficialStore = false), "Lazada")
                }
            }
        }
        compose.onNodeWithTag("seller_details").assertDoesNotExist()
    }
}
