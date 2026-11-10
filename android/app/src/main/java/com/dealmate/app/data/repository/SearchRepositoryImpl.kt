package com.dealmate.app.data.repository

import com.dealmate.app.data.local.CachedSearchEntity
import com.dealmate.app.data.local.RecentSearchEntity
import com.dealmate.app.data.local.SearchDao
import com.dealmate.app.data.remote.DealMateApi
import com.dealmate.app.data.remote.apiCall
import com.dealmate.app.data.remote.dto.DealMateJson
import com.dealmate.app.data.remote.dto.SearchRequestDto
import com.dealmate.app.data.remote.dto.SearchResponseDto
import com.dealmate.app.data.remote.dto.UrlSearchRequestDto
import com.dealmate.app.domain.model.Marketplace
import com.dealmate.app.domain.model.Outcome
import com.dealmate.app.domain.model.RecentSearch
import com.dealmate.app.domain.model.SearchRequest
import com.dealmate.app.domain.model.SearchResult
import com.dealmate.app.domain.model.SearchType
import com.dealmate.app.domain.repository.SearchRepository
import com.dealmate.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

class SearchRepositoryImpl @Inject constructor(
    private val api: DealMateApi,
    private val dao: SearchDao,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : SearchRepository {

    override suspend fun search(request: SearchRequest): Outcome<SearchResult> {
        val value = request.value.trim()
        val marketplaces = preferredMarketplaces()
        val outcome = apiCall {
            when (request.type) {
                SearchType.TEXT -> api.search(SearchRequestDto(query = value, marketplaces = marketplaces))
                SearchType.URL -> api.searchUrl(UrlSearchRequestDto(url = value, marketplaces = marketplaces))
                SearchType.IMAGE -> {
                    val file = File(value)
                    val part = MultipartBody.Part.createFormData(
                        "image", "photo.jpg", file.asRequestBody("image/jpeg".toMediaType()),
                    )
                    val hint = request.hint?.trim()?.takeIf { it.isNotEmpty() }
                        ?.toRequestBody("text/plain".toMediaType())
                    api.searchImage(part, hint)
                }
            }
        }
        // Image searches are one-off (the picture is a temp file): neither remembered nor cached.
        val cacheable = request.type != SearchType.IMAGE
        return when (outcome) {
            is Outcome.Success -> {
                if (cacheable) remember(request.type, value, outcome.data)
                Outcome.Success(outcome.data.toDomain())
            }
            is Outcome.Failure -> {
                val cached = if (cacheable && outcome.error.isConnectivity) loadCached(request.type, value) else null
                cached ?: outcome
            }
        }
    }

    private suspend fun preferredMarketplaces(): List<Marketplace>? {
        val preferred = settings.current().preferredMarketplaces.filter { it in Marketplace.selectable }
        // null == all marketplaces (the backend default).
        return preferred.takeIf { it.isNotEmpty() && it.size < Marketplace.selectable.size }
    }

    private suspend fun remember(type: SearchType, value: String, response: SearchResponseDto) {
        val now = clock.millis()
        runCatching {
            dao.upsertRecent(RecentSearchEntity(type = type.wire, query = value, searchedAt = now))
            dao.trimRecent()
            // A clarification-only answer is not a result worth showing offline.
            if (response.groups.isNotEmpty() || response.similarGroups.isNotEmpty()) {
                dao.putCached(
                    CachedSearchEntity(
                        cacheKey = cacheKey(type, value),
                        json = DealMateJson.encodeToString(SearchResponseDto.serializer(), response),
                        savedAt = now,
                    ),
                )
                dao.trimCached()
            }
        }
    }

    private suspend fun loadCached(type: SearchType, value: String): Outcome.Success<SearchResult>? {
        val row = runCatching { dao.cached(cacheKey(type, value)) }.getOrNull() ?: return null
        val dto = runCatching { DealMateJson.decodeFromString(SearchResponseDto.serializer(), row.json) }.getOrNull()
            ?: return null
        return Outcome.Success(dto.toDomain(), fromCache = true, cachedAt = Instant.ofEpochMilli(row.savedAt))
    }

    override fun recentSearches(limit: Int): Flow<List<RecentSearch>> = dao.recent(limit).map { rows ->
        rows.map { RecentSearch(SearchType.fromWire(it.type), it.query, Instant.ofEpochMilli(it.searchedAt)) }
    }

    companion object {
        fun cacheKey(type: SearchType, value: String): String =
            type.wire + ":" + value.trim().lowercase().split(Regex("\\s+")).joinToString(" ")
    }
}
