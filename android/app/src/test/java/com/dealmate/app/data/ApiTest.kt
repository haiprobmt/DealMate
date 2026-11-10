package com.dealmate.app.data

import com.dealmate.app.data.remote.DealMateApi
import com.dealmate.app.data.remote.ServerConfig
import com.dealmate.app.data.remote.ServerInterceptor
import com.dealmate.app.data.remote.apiCall
import com.dealmate.app.data.remote.dto.DealMateJson
import com.dealmate.app.data.repository.AssistantRepositoryImpl
import com.dealmate.app.data.repository.MetaRepositoryImpl
import com.dealmate.app.data.repository.NotificationRepositoryImpl
import com.dealmate.app.domain.model.AppError
import com.dealmate.app.domain.model.Marketplace
import com.dealmate.app.domain.model.Outcome
import com.dealmate.app.domain.model.errorOrNull
import com.dealmate.app.testutil.Fixtures
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

fun testApi(baseUrl: () -> String, token: () -> String = { "secret-token" }): DealMateApi = Retrofit.Builder()
    .baseUrl(ServerInterceptor.PLACEHOLDER_BASE_URL)
    .client(
        OkHttpClient.Builder()
            .readTimeout(2, TimeUnit.SECONDS)
            .addInterceptor(ServerInterceptor { ServerConfig(baseUrl(), token()) })
            .build(),
    )
    .addConverterFactory(DealMateJson.asConverterFactory("application/json".toMediaType()))
    .build()
    .create(DealMateApi::class.java)

class ApiTest {
    private lateinit var server: MockWebServer
    private var token = "secret-token"
    private var pathPrefix = "/"
    private lateinit var api: DealMateApi

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        api = testApi(baseUrl = { server.url(pathPrefix).toString() }, token = { token })
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun json(name: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(Fixtures.text(name))

    private fun error(code: Int, errorCode: String, message: String) = MockResponse().setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody("""{"error":{"code":"$errorCode","message":"$message","details":null,"request_id":"r1"}}""")

    @Test
    fun `api calls carry the bearer token and hit the configured server`() = runTest {
        server.enqueue(json("me.json"))
        val me = MetaRepositoryImpl(api).me()
        assertEquals("Gia đình", (me as Outcome.Success).data.displayName)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/me", request.path)
        assertEquals("Bearer secret-token", request.getHeader("Authorization"))
    }

    @Test
    fun `health needs no token and sends no authorization header`() = runTest {
        token = ""
        server.enqueue(json("health.json"))
        val health = MetaRepositoryImpl(api).health()
        assertEquals("ok", (health as Outcome.Success).data.status)
        val request = server.takeRequest()
        assertEquals("/health", request.path)
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun `a server address with a path prefix is respected and query parameters are kept`() = runTest {
        pathPrefix = "/dealmate/"
        server.enqueue(json("explore_unavailable.json"))
        MetaRepositoryImpl(api).explore("Tã bỉm", Marketplace.TIKTOK_SHOP)
        val url = server.takeRequest().requestUrl!!
        assertEquals("/dealmate/api/v1/explore", url.encodedPath)
        assertEquals("Tã bỉm", url.queryParameter("category"))
        assertEquals("tiktok_shop", url.queryParameter("marketplace"))
    }

    @Test
    fun `missing token is reported as not configured without any network call`() = runTest {
        token = "  "
        val outcome = MetaRepositoryImpl(api).me()
        assertEquals(AppError.Kind.NOT_CONFIGURED, outcome.errorOrNull()?.kind)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `missing server address is reported as not configured`() = runTest {
        val unconfigured = testApi(baseUrl = { "" })
        assertEquals(AppError.Kind.NOT_CONFIGURED, MetaRepositoryImpl(unconfigured).health().errorOrNull()?.kind)
    }

    @Test
    fun `401 maps to unauthorized with the server message`() = runTest {
        server.enqueue(error(401, "unauthorized", "Mã truy cập không đúng."))
        val e = MetaRepositoryImpl(api).me().errorOrNull()!!
        assertEquals(AppError.Kind.UNAUTHORIZED, e.kind)
        assertEquals("Mã truy cập không đúng.", e.message)
        assertEquals("unauthorized", e.code)
    }

    @Test
    fun `429 maps to rate limited`() = runTest {
        server.enqueue(error(429, "rate_limited", "Bạn tìm kiếm quá nhanh, hãy thử lại sau 30 giây."))
        val e = MetaRepositoryImpl(api).sourcesStatus().errorOrNull()!!
        assertEquals(AppError.Kind.RATE_LIMITED, e.kind)
        assertEquals("Bạn tìm kiếm quá nhanh, hãy thử lại sau 30 giây.", e.message)
    }

    @Test
    fun `503 ai_unavailable keeps code and message`() = runTest {
        server.enqueue(json("error_ai_unavailable.json", code = 503))
        val e = AssistantRepositoryImpl(api).ask("Shop nào rẻ nhất?", listOf(11)).errorOrNull()!!
        assertEquals(AppError.Kind.UNAVAILABLE, e.kind)
        assertEquals("ai_unavailable", e.code)
        assertTrue(e.message!!.contains("khóa AI"))
        val body = server.takeRequest().body.readUtf8()
        assertEquals("""{"question":"Shop nào rẻ nhất?","variant_ids":[11]}""", body)
    }

    @Test
    fun `404, 422 and 504 map to their kinds`() = runTest {
        server.enqueue(error(404, "not_found", "Không tìm thấy."))
        server.enqueue(error(422, "validation_error", "Dữ liệu không hợp lệ."))
        server.enqueue(error(504, "timeout", "Hết thời gian."))
        val meta = MetaRepositoryImpl(api)
        assertEquals(AppError.Kind.NOT_FOUND, meta.me().errorOrNull()?.kind)
        assertEquals(AppError.Kind.VALIDATION, meta.me().errorOrNull()?.kind)
        assertEquals(AppError.Kind.TIMEOUT, meta.me().errorOrNull()?.kind)
    }

    @Test
    fun `a 500 with a non JSON body is a server error without message`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("<html>Internal Server Error</html>"))
        val e = MetaRepositoryImpl(api).me().errorOrNull()!!
        assertEquals(AppError.Kind.SERVER, e.kind)
        assertNull(e.message)
    }

    @Test
    fun `a 200 with a malformed body is an error, not a crash`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody("{not json"))
        val e = MetaRepositoryImpl(api).me().errorOrNull()!!
        assertEquals(AppError.Kind.UNKNOWN, e.kind)
        assertEquals("bad_response", e.code)
    }

    @Test
    fun `connection failure is offline and a stalled response is a timeout`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertEquals(AppError.Kind.TIMEOUT, MetaRepositoryImpl(api).me().errorOrNull()?.kind)
        server.shutdown()
        assertEquals(AppError.Kind.OFFLINE, MetaRepositoryImpl(api).me().errorOrNull()?.kind)
    }

    @Test
    fun `204 endpoints succeed and their failures are parsed`() = runTest {
        val repo = NotificationRepositoryImpl(api)
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(repo.markRead(61) is Outcome.Success)
        assertEquals("/api/v1/notifications/61/read", server.takeRequest().path)

        server.enqueue(error(404, "not_found", "Không có thông báo này."))
        assertEquals("Không có thông báo này.", repo.markRead(99).errorOrNull()?.message)
        server.takeRequest()

        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(repo.registerDevice("fcm-token-0123456789abcdef") is Outcome.Success)
        assertEquals("""{"token":"fcm-token-0123456789abcdef","platform":"android"}""", server.takeRequest().body.readUtf8())
    }

    @Test
    fun `unread notifications are requested with unread_only`() = runTest {
        server.enqueue(json("notifications_list.json"))
        val list = (NotificationRepositoryImpl(api).unread() as Outcome.Success).data
        assertEquals(2, list.size)
        assertEquals("/api/v1/notifications?unread_only=true", server.takeRequest().path)
    }

    @Test
    fun `apiCall rethrows cancellation instead of swallowing it`() = runTest {
        var thrown: Throwable? = null
        try {
            apiCall<Unit> { throw kotlinx.coroutines.CancellationException("cancelled") }
        } catch (e: kotlinx.coroutines.CancellationException) {
            thrown = e
        }
        assertEquals("cancelled", thrown?.message)
    }
}
