package com.dealmate.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dealmate.app.ui.navigation.DealMateNavHost
import com.dealmate.app.ui.navigation.LaunchTarget
import com.dealmate.app.ui.theme.DealMateTheme
import dagger.hilt.android.AndroidEntryPoint

/** Single activity. AppCompatActivity is used so the per-app language (vi/en) works on all API levels. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private var launchTarget by mutableStateOf<LaunchTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) launchTarget = targetOf(intent)
        setContent {
            DealMateTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    DealMateNavHost(
                        launchTarget = launchTarget,
                        onLaunchTargetConsumed = { launchTarget = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        targetOf(intent)?.let { launchTarget = it }
    }

    private fun targetOf(intent: Intent?): LaunchTarget? {
        if (intent == null) return null
        val productId = intent.getLongExtra(EXTRA_PRODUCT_ID, -1L)
        return when {
            productId > 0 -> LaunchTarget.Product(productId, intent.getLongExtra(EXTRA_VARIANT_ID, 0L).takeIf { it > 0 })
            intent.getBooleanExtra(EXTRA_OPEN_WISHLIST, false) -> LaunchTarget.Wishlist
            else -> null
        }
    }

    companion object {
        const val EXTRA_PRODUCT_ID = "com.dealmate.app.extra.PRODUCT_ID"
        const val EXTRA_VARIANT_ID = "com.dealmate.app.extra.VARIANT_ID"
        const val EXTRA_OPEN_WISHLIST = "com.dealmate.app.extra.OPEN_WISHLIST"
    }
}
