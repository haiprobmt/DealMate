package com.dealmate.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.dealmate.app.data.local.CachedSearchEntity
import com.dealmate.app.data.local.RecentSearchEntity
import com.dealmate.app.data.local.RecentlyViewedEntity
import com.dealmate.app.data.local.SearchDao
import com.dealmate.app.data.local.ViewedDao
import com.dealmate.app.data.local.WishlistDao
import com.dealmate.app.data.local.WishlistEntity
import com.dealmate.app.data.repository.HistoryRepositoryImpl
import com.dealmate.app.data.repository.ProductRepositoryImpl
import com.dealmate.app.data.repository.SearchRepositoryImpl
import com.dealmate.app.data.repository.WishlistRepositoryImpl
import com.dealmate.app.data.settings.SecretCipher
import com.dealmate.app.data.settings.SettingsRepositoryImpl
import com.dealmate.app.domain.model.AppError
import com.dealmate.app.domain.model.AppSettings
import com.dealmate.app.domain.model.Marketplace
import com.dealmate.app.domain.model.Outcome
import com.dealmate.app.domain.model.SearchRequest
import com.dealmate.app.domain.model.SearchType
import com.dealmate.app.domain.model.SortOption
import com.dealmate.app.domain.model.errorOrNull
import com.dealmate.app.testutil.FakeSettingsRepository
import com.dealmate.app.testutil.Fixtures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class InMemorySearchDao : SearchDao {
    val recent = MutableStateFlow<List<RecentSearchEntity>>(emptyList())
    val cache = mutableMapOf<String, CachedSearchEntity>()

    override suspend fun upsertRecent(entity: RecentSearchEntity) =
        recent.update { list -> list.filterNot { it.type == entity.type && it.query == entity.query } + entity }

    override fun recent(limit: Int): Flow<List<RecentSearchEntity>> =
        recent.map { list -> list.sortedByDescending { it.searchedAt }.take(limit) }

    override suspend fun trimRecent() = Unit
    override suspend fun clearRecent() { recent.value = emptyList() }
    override suspend fun putCached(entity: CachedSearchEntity) { cache[entity.cacheKey] = entity }
    override suspend fun cached(key: String): CachedSearchEntity? = cache[key]
    override suspend fun trimCached() = Unit
    override suspend fun clearCached() = cache.clear()
}

class InMemoryWishlistDao : WishlistDao {
    val rows = MutableStateFlow<List<WishlistEntity>>(emptyList())
    override fun observe(): Flow<List<WishlistEntity>> = rows.map { list -> list.sortedBy { it.position } }
    override suspend fun insertAll(items: List<WishlistEntity>) =
        rows.update { list -> list.filterNot { old -> items.any { it.id == old.id } } + items }
    override suspend fun clear() { rows.value = emptyList() }
    override suspend fun delete(id: Long) = rows.update { list -> list.filterNot { it.id == id } }
    override suspend fun productIdForVariant(variantId: Long): Long? = rows.value.firstOrNull { it.variantId == variantId }?.productId
    override suspend fun minPosition(): Int = rows.value.minOfOrNull { it.position } ?: 0
}

class InMemoryViewedDao : ViewedDao {
    val rows = MutableStateFlow<List<RecentlyViewedEntity>>(emptyList())
    override suspend fun upsert(entity: RecentlyViewedEntity) =
        rows.update { list -> list.filterNot { it.variantId == entity.variantId } + entity }
    override fun recent(limit: Int): Flow<List<RecentlyViewedEntity>> =
        rows.map { list -> list.sortedByDescending { it.viewedAt }.take(limit) }
    override suspend fun trim() = Unit
    override suspend fun clear() { rows.value = emptyList() }
}

class SearchRepositoryImplTest {
    private lateinit var server: MockWebServer
    private val dao = InMemorySearchDao()
    private val settings = FakeSettingsRepository()
    private lateinit var repo: SearchRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        repo = SearchRepositoryImpl(testApi(baseUrl = { server.url("/").toString() }), dao, settings, Fixtures.CLOCK)
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun ok(name: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(Fixtures.text(name))

    @Test
    fun `text search posts the query, remembers it and caches the response`() = runTest {
        server.enqueue(ok("search_text.json"))
        val outcome = repo.search(SearchRequest(SearchType.TEXT, "  kem chống nắng Anessa 60ml "))
        assertTrue(outcome is Outcome.Success && !outcome.fromCache)

        val request = server.takeRequest()
        assertEquals("/api/v1/search", request.path)
        // all marketplaces preferred -> the field is omitted (backend default == all)
        assertEquals("""{"query":"kem chống nắng Anessa 60ml","quantity":1,"include_similar":true}""", request.body.readUtf8())
        assertEquals(listOf("kem chống nắng Anessa 60ml"), repo.recentSearches().first().map { it.query })
        assertEquals(1, dao.cache.size)
    }

    @Test
    fun `preferred marketplaces are sent when they are a subset`() = runTest {
        settings.setPreferredMarketplaces(setOf(Marketplace.SHOPEE, Marketplace.LAZADA))
        server.enqueue(ok("search_text.json"))
        repo.search(SearchRequest(SearchType.TEXT, "anessa"))
        assertTrue(server.takeRequest().body.readUtf8().contains(""""marketplaces":["shopee","lazada"]"""))
    }

    @Test
    fun `offline falls back to the last cached result of the same query`() = runTest {
        server.enqueue(ok("search_text.json"))
        repo.search(SearchRequest(SearchType.TEXT, "Kem chống nắng  Anessa"))
        server.shutdown()

        // same query modulo case / whitespace
        val cached = repo.search(SearchRequest(SearchType.TEXT, "kem chống nắng anessa"))
        assertTrue(cached is Outcome.Success)
        cached as Outcome.Success
        assertTrue(cached.fromCache)
        assertEquals(Fixtures.NOW, cached.cachedAt)
        assertEquals(315_000L, cached.data.best?.finalPrice)

        // a different query has no cache -> the offline error surfaces
        assertEquals(AppError.Kind.OFFLINE, repo.search(SearchRequest(SearchType.TEXT, "dyson")).errorOrNull()?.kind)
    }

    @Test
    fun `server errors are never masked by the cache`() = runTest {
        server.enqueue(ok("search_text.json"))
        repo.search(SearchRequest(SearchType.TEXT, "anessa"))
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"code":"unauthorized","message":"Sai mã."}}"""))
        assertEquals(AppError.Kind.UNAUTHORIZED, repo.search(SearchRequest(SearchType.TEXT, "anessa")).errorOrNull()?.kind)
    }

    @Test
    fun `clarification-only answers are not cached for offline use`() = runTest {
        server.enqueue(ok("search_clarification.json"))
        repo.search(SearchRequest(SearchType.TEXT, "kem"))
        assertTrue(dao.cache.isEmpty())
    }

    @Test
    fun `url search uses the url endpoint`() = runTest {
        server.enqueue(ok("search_text.json"))
        repo.search(SearchRequest(SearchType.URL, "https://shopee.vn/product/1"))
        val request = server.takeRequest()
        assertEquals("/api/v1/search/url", request.path)
        assertEquals("""{"url":"https://shopee.vn/product/1"}""", request.body.readUtf8())
    }

    @Test
    fun `image search uploads multipart with image part and hint, and is not remembered`() = runTest {
        val file = File.createTempFile("upload", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)); deleteOnExit() }
        server.enqueue(ok("search_clarification.json"))
        val outcome = repo.search(SearchRequest(SearchType.IMAGE, file.absolutePath, hint = "kem chống nắng"))
        assertTrue((outcome as Outcome.Success).data.clarification.needed)

        val request = server.takeRequest()
        assertEquals("/api/v1/search/image", request.path)
        assertTrue(request.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("""name="image"; filename="photo.jpg""""))
        assertTrue(body.contains("Content-Type: image/jpeg"))
        assertTrue(body.contains("""name="hint""""))
        assertTrue(body.contains("kem chống nắng"))
        assertTrue(repo.recentSearches().first().isEmpty())
    }
}

class OtherRepositoriesTest {
    private lateinit var server: MockWebServer
    private val wishlistDao = InMemoryWishlistDao()
    private val viewedDao = InMemoryViewedDao()
    private val searchDao = InMemorySearchDao()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun api() = testApi(baseUrl = { server.url("/").toString() })
    private fun ok(name: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(Fixtures.text(name))

    @Test
    fun `wishlist refresh fills the offline cache in server order`() = runTest {
        val repo = WishlistRepositoryImpl(api(), wishlistDao)
        server.enqueue(ok("wishlist_list.json"))
        assertTrue(repo.refresh() is Outcome.Success)
        assertEquals(listOf(31L, 32L, 33L), repo.observe().first().map { it.id })
        assertEquals(5L, repo.productIdForVariant(51))

        // offline: the refresh fails but the cached list stays readable
        server.shutdown()
        assertEquals(AppError.Kind.OFFLINE, repo.refresh().errorOrNull()?.kind)
        assertEquals(3, repo.observe().first().size)
    }

    @Test
    fun `wishlist add, remove and target price call the right endpoints`() = runTest {
        val repo = WishlistRepositoryImpl(api(), wishlistDao)
        val item = """{"id":31,"product_id":1,"variant_id":11,"title":"Anessa","target_price":300000,"created_at":"2026-01-10T02:00:00Z"}"""
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(item))
        val added = repo.add(11, 300_000)
        assertEquals(31L, (added as Outcome.Success).data.id)
        server.takeRequest().let {
            assertEquals("POST", it.method)
            assertEquals("/api/v1/wishlist", it.path)
            assertEquals("""{"variant_id":11,"target_price":300000}""", it.body.readUtf8())
        }
        assertEquals(listOf(31L), repo.observe().first().map { it.id })

        // set target: POST /price-alerts then reload the list
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"id":41,"product_id":1,"variant_id":11,"title":"Anessa","target_price":280000,"is_active":true,"created_at":"2026-01-10T02:00:00Z"}"""))
        server.enqueue(ok("wishlist_list.json"))
        assertTrue(repo.setTargetPrice(11, alertId = null, targetPrice = 280_000) is Outcome.Success)
        server.takeRequest().let {
            assertEquals("/api/v1/price-alerts", it.path)
            assertEquals("""{"variant_id":11,"target_price":280000}""", it.body.readUtf8())
        }
        assertEquals("/api/v1/wishlist", server.takeRequest().path)

        // clear target: DELETE /price-alerts/{id} then reload
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(ok("wishlist_list.json"))
        assertTrue(repo.setTargetPrice(11, alertId = 41, targetPrice = null) is Outcome.Success)
        server.takeRequest().let {
            assertEquals("DELETE", it.method)
            assertEquals("/api/v1/price-alerts/41", it.path)
        }
        server.takeRequest()

        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(repo.remove(31) is Outcome.Success)
        assertEquals("/api/v1/wishlist/31", server.takeRequest().path)
        assertFalse(repo.observe().first().any { it.id == 31L })
    }

    @Test
    fun `a failed removal keeps the cached item`() = runTest {
        val repo = WishlistRepositoryImpl(api(), wishlistDao)
        server.enqueue(ok("wishlist_list.json"))
        repo.refresh()
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(AppError.Kind.SERVER, repo.remove(31).errorOrNull()?.kind)
        assertTrue(repo.observe().first().any { it.id == 31L })
    }

    @Test
    fun `product offers are requested per variant and remembered as recently viewed`() = runTest {
        val repo = ProductRepositoryImpl(api(), viewedDao, Fixtures.CLOCK)
        server.enqueue(ok("offers_anessa.json"))
        val offers = (repo.offers(1, 11) as Outcome.Success).data
        assertEquals(101L, offers.bestOfferId)
        assertEquals("/api/v1/products/1/offers?variant_id=11", server.takeRequest().path)
        val viewed = repo.recentlyViewed().first().single()
        assertEquals(11L, viewed.variantId)
        assertEquals(315_000L, viewed.lowestFinalPrice)

        server.enqueue(ok("product_anessa.json"))
        assertEquals(2, (repo.detail(1) as Outcome.Success).data.variants.size)
        assertEquals("/api/v1/products/1", server.takeRequest().path)

        // without a variant the query parameter is omitted
        server.enqueue(ok("offers_anessa.json"))
        repo.offers(1, null)
        assertEquals("/api/v1/products/1/offers", server.takeRequest().path)
    }

    @Test
    fun `clearing history also clears the local caches`() = runTest {
        searchDao.upsertRecent(RecentSearchEntity(type = "text", query = "anessa", searchedAt = 1))
        searchDao.putCached(CachedSearchEntity("text:anessa", "{}", 1))
        viewedDao.upsert(RecentlyViewedEntity(11, 1, "Anessa", null, 315_000, 1))
        val repo = HistoryRepositoryImpl(api(), searchDao, viewedDao)

        server.enqueue(ok("history_full.json"))
        assertEquals(4, (repo.history() as Outcome.Success).data.searches.size)
        server.takeRequest()

        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(repo.clear() is Outcome.Success)
        server.takeRequest().let {
            assertEquals("DELETE", it.method)
            assertEquals("/api/v1/history", it.path)
        }
        assertTrue(searchDao.recent.value.isEmpty())
        assertTrue(searchDao.cache.isEmpty())
        assertTrue(viewedDao.rows.value.isEmpty())
    }
}

class SettingsRepositoryImplTest {
    @get:Rule
    val tmp = TemporaryFolder()

    /** Reversible stand-in for the Keystore cipher (the Android Keystore does not exist on the JVM). */
    private class ReversingCipher : SecretCipher {
        override fun encrypt(plain: String): String = "enc:" + plain.reversed()
        override fun decrypt(encoded: String): String? = encoded.removePrefix("enc:").reversed().takeIf { encoded.startsWith("enc:") }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() = scope.cancel()

    private fun repo(file: File) = SettingsRepositoryImpl(
        PreferenceDataStoreFactory.create(scope = scope) { file },
        ReversingCipher(),
        defaultBaseUrl = "http://10.0.2.2:8000/",
    )

    @Test
    fun `defaults - build-time server URL, no token, all marketplaces, price sort, vietnamese`() = runTest {
        val s = repo(File(tmp.root, "a.preferences_pb")).current()
        assertEquals(
            AppSettings(baseUrl = "http://10.0.2.2:8000/", token = "", preferredMarketplaces = Marketplace.selectable.toSet()),
            s,
        )
        assertFalse(s.isConfigured)
        assertEquals("vi", s.language)
        assertEquals(SortOption.PRICE, s.defaultSort)
    }

    @Test
    fun `settings persist and the token is never written in plain text`() = runTest {
        val file = File(tmp.root, "b.preferences_pb")
        val repo = repo(file)
        repo.setServer(" https://dealmate.example.com/ ", " super-secret-token ")
        repo.setPreferredMarketplaces(setOf(Marketplace.LAZADA))
        repo.setDefaultSort(SortOption.RATING)
        repo.setNotificationsEnabled(true)
        repo.setDeliveryLocation("Quận 7, TP. Hồ Chí Minh")
        repo.setLanguage("en")

        val s = repo.current()
        assertEquals("https://dealmate.example.com/", s.baseUrl)
        assertEquals("super-secret-token", s.token)
        assertTrue(s.isConfigured)
        assertEquals(setOf(Marketplace.LAZADA), s.preferredMarketplaces)
        assertEquals(SortOption.RATING, s.defaultSort)
        assertTrue(s.notificationsEnabled)
        assertEquals("Quận 7, TP. Hồ Chí Minh", s.deliveryLocation)
        assertEquals("en", s.language)

        val onDisk = file.readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(onDisk.contains("super-secret-token"))
        assertTrue(onDisk.contains("enc:"))
    }

    @Test
    fun `an undecryptable token reads as empty instead of crashing`() = runTest {
        val file = File(tmp.root, "c.preferences_pb")
        val store = PreferenceDataStoreFactory.create(scope = scope) { file }
        val broken = object : SecretCipher {
            override fun encrypt(plain: String) = "x"
            override fun decrypt(encoded: String): String? = null
        }
        val repo = SettingsRepositoryImpl(store, broken, "http://10.0.2.2:8000/")
        repo.setServer("https://a.example/", "token")
        assertEquals("", repo.current().token)
    }
}
