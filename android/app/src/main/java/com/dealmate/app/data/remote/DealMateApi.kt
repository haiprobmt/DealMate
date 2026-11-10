package com.dealmate.app.data.remote

import com.dealmate.app.data.remote.dto.AssistantRequestDto
import com.dealmate.app.data.remote.dto.AssistantResponseDto
import com.dealmate.app.data.remote.dto.DeviceRegisterDto
import com.dealmate.app.data.remote.dto.HealthDto
import com.dealmate.app.data.remote.dto.HistoryResponseDto
import com.dealmate.app.data.remote.dto.MeResponseDto
import com.dealmate.app.data.remote.dto.NotificationListDto
import com.dealmate.app.data.remote.dto.PriceAlertCreateDto
import com.dealmate.app.data.remote.dto.PriceAlertDto
import com.dealmate.app.data.remote.dto.PriceAlertListDto
import com.dealmate.app.data.remote.dto.ProductDetailDto
import com.dealmate.app.data.remote.dto.ProductOffersResponseDto
import com.dealmate.app.data.remote.dto.SearchRequestDto
import com.dealmate.app.data.remote.dto.SearchResponseDto
import com.dealmate.app.data.remote.dto.SourcesStatusResponseDto
import com.dealmate.app.data.remote.dto.TrendingResponseDto
import com.dealmate.app.data.remote.dto.UrlSearchRequestDto
import com.dealmate.app.data.remote.dto.WishlistCreateDto
import com.dealmate.app.data.remote.dto.WishlistItemDto
import com.dealmate.app.data.remote.dto.WishlistListDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** REST API v1 of the DealMate backend. Paths are relative to the server URL set in Settings. */
interface DealMateApi {
    @GET("health")
    suspend fun health(): HealthDto

    @GET("api/v1/me")
    suspend fun me(): MeResponseDto

    @POST("api/v1/search")
    suspend fun search(@Body body: SearchRequestDto): SearchResponseDto

    @Multipart
    @POST("api/v1/search/image")
    suspend fun searchImage(@Part image: MultipartBody.Part, @Part("hint") hint: RequestBody?): SearchResponseDto

    @POST("api/v1/search/url")
    suspend fun searchUrl(@Body body: UrlSearchRequestDto): SearchResponseDto

    @GET("api/v1/products/{productId}")
    suspend fun product(@Path("productId") productId: Long): ProductDetailDto

    @GET("api/v1/products/{productId}/offers")
    suspend fun productOffers(
        @Path("productId") productId: Long,
        @Query("variant_id") variantId: Long?,
    ): ProductOffersResponseDto

    @POST("api/v1/wishlist")
    suspend fun addWishlist(@Body body: WishlistCreateDto): WishlistItemDto

    @GET("api/v1/wishlist")
    suspend fun wishlist(): WishlistListDto

    @DELETE("api/v1/wishlist/{id}")
    suspend fun deleteWishlist(@Path("id") id: Long): Response<Unit>

    @POST("api/v1/price-alerts")
    suspend fun createAlert(@Body body: PriceAlertCreateDto): PriceAlertDto

    @GET("api/v1/price-alerts")
    suspend fun alerts(): PriceAlertListDto

    @DELETE("api/v1/price-alerts/{id}")
    suspend fun deleteAlert(@Path("id") id: Long): Response<Unit>

    @GET("api/v1/history")
    suspend fun history(): HistoryResponseDto

    @DELETE("api/v1/history")
    suspend fun clearHistory(): Response<Unit>

    @GET("api/v1/sources/status")
    suspend fun sourcesStatus(): SourcesStatusResponseDto

    @GET("api/v1/explore")
    suspend fun explore(
        @Query("category") category: String?,
        @Query("marketplace") marketplace: String?,
    ): TrendingResponseDto

    @GET("api/v1/notifications")
    suspend fun notifications(@Query("unread_only") unreadOnly: Boolean): NotificationListDto

    @POST("api/v1/notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: Long): Response<Unit>

    @POST("api/v1/devices")
    suspend fun registerDevice(@Body body: DeviceRegisterDto): Response<Unit>

    @POST("api/v1/assistant/ask")
    suspend fun ask(@Body body: AssistantRequestDto): AssistantResponseDto
}
