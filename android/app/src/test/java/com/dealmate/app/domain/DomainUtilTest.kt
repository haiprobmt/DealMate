package com.dealmate.app.domain

import com.dealmate.app.data.image.ImagePreparerImpl
import com.dealmate.app.domain.util.Listings
import com.dealmate.app.domain.util.Money
import com.dealmate.app.domain.util.RelativeTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class MoneyTest {
    @Test
    fun `formats VND with dot grouping and d suffix`() {
        assertEquals("315.000đ", Money.format(315_000))
        assertEquals("0đ", Money.format(0))
        assertEquals("999đ", Money.format(999))
        assertEquals("1.000đ", Money.format(1_000))
        assertEquals("7.990.000đ", Money.format(7_990_000))
        assertEquals("1.234.567.890đ", Money.format(1_234_567_890))
        assertEquals("12.345đ", Money.format(12_345))
    }

    @Test
    fun `groups plain counts`() {
        assertEquals("35.200", Money.group(35_200))
        assertEquals("96", Money.group(96))
    }

    @Test
    fun `formats negative amounts`() {
        assertEquals("-50.000đ", Money.format(-50_000))
    }

    @Test
    fun `parses user input in common shapes`() {
        assertEquals(250_000L, Money.parse("250000"))
        assertEquals(250_000L, Money.parse("250.000"))
        assertEquals(250_000L, Money.parse("250,000đ"))
        assertEquals(8_000_000L, Money.parse(" 8 000 000 "))
    }

    @Test
    fun `rejects empty zero and absurd input`() {
        assertNull(Money.parse(""))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("0"))
        assertNull(Money.parse("999999999999999"))
    }
}

class RelativeTimeTest {
    private val now = Instant.parse("2026-01-15T10:00:00Z")
    private fun ago(seconds: Long) = RelativeTime.between(now.minusSeconds(seconds), now, ZoneOffset.UTC)

    @Test
    fun `buckets durations`() {
        assertEquals(RelativeTime.JustNow, ago(20))
        assertEquals(RelativeTime.Minutes(5), ago(5 * 60 + 10))
        assertEquals(RelativeTime.Hours(2), ago(2 * 3600 + 100))
        assertEquals(RelativeTime.Days(3), ago(3 * 86400 + 5))
        assertEquals(RelativeTime.OnDate("05/01/2026"), ago(10 * 86400))
    }

    @Test
    fun `future timestamps (clock skew) read as just now`() {
        assertEquals(RelativeTime.JustNow, ago(-600))
    }
}

class ListingsTest {
    @Test
    fun `sample links on the invalid TLD are recognised and never openable`() {
        listOf(
            "https://shopee.demo.invalid/product/101",
            "http://example.invalid",
            "https://A.B.INVALID/path?x=1",
            "https://lazada.demo.invalid./x",
        ).forEach {
            assertTrue(it, Listings.isSampleUrl(it))
            assertFalse(it, Listings.isOpenable(it))
        }
    }

    @Test
    fun `real marketplace links are openable`() {
        assertTrue(Listings.isOpenable("https://shopee.vn/product/1"))
        assertTrue(Listings.isOpenable(" https://www.lazada.vn/products/x.html "))
        assertFalse(Listings.isSampleUrl("https://shopee.vn/invalid"))
        assertFalse(Listings.isSampleUrl("https://notinvalid.vn/"))
    }

    @Test
    fun `non http schemes and garbage are refused`() {
        listOf("", "shopee.vn/product", "javascript:alert(1)", "intent://x#Intent;end", "file:///sdcard/a", "content://a/b").forEach {
            assertFalse(it, Listings.isOpenable(it))
        }
    }
}

class ImageSampleSizeTest {
    @Test
    fun `sample size keeps the decoded image at or above the target side`() {
        assertEquals(1, ImagePreparerImpl.sampleSize(1200, 800, 1600))
        assertEquals(1, ImagePreparerImpl.sampleSize(3000, 2000, 1600))
        assertEquals(2, ImagePreparerImpl.sampleSize(4000, 3000, 1600))
        assertEquals(4, ImagePreparerImpl.sampleSize(8000, 6000, 1600))
    }
}
