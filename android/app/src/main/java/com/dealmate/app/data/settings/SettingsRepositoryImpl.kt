package com.dealmate.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.dealmate.app.domain.model.AppSettings
import com.dealmate.app.domain.model.Marketplace
import com.dealmate.app.domain.model.SortOption
import com.dealmate.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Settings in Preferences DataStore. The access token is stored only as Keystore-encrypted
 * ciphertext; the DataStore file is excluded from backups (see res/xml/backup_rules.xml).
 */
class SettingsRepositoryImpl(
    private val store: DataStore<Preferences>,
    private val cipher: SecretCipher,
    private val defaultBaseUrl: String,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map(::toSettings)
        .distinctUntilChanged()

    override suspend fun current(): AppSettings = settings.first()

    private fun toSettings(p: Preferences): AppSettings = AppSettings(
        baseUrl = p[BASE_URL] ?: defaultBaseUrl,
        token = p[TOKEN_ENC]?.let { cipher.decrypt(it) }.orEmpty(),
        preferredMarketplaces = p[MARKETPLACES]
            ?.map { Marketplace.fromWire(it) }?.filter { it in Marketplace.selectable }?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: Marketplace.selectable.toSet(),
        defaultSort = p[SORT]?.let { v -> SortOption.entries.firstOrNull { it.name == v } } ?: SortOption.PRICE,
        notificationsEnabled = p[NOTIFICATIONS] ?: false,
        deliveryLocation = p[LOCATION].orEmpty(),
        language = p[LANGUAGE]?.takeIf { it in SUPPORTED_LANGUAGES } ?: "vi",
    )

    override suspend fun setServer(baseUrl: String, token: String) {
        val encrypted = cipher.encrypt(token.trim())
        store.edit {
            it[BASE_URL] = baseUrl.trim()
            it[TOKEN_ENC] = encrypted
        }
    }

    override suspend fun setPreferredMarketplaces(marketplaces: Set<Marketplace>) {
        store.edit { p -> p[MARKETPLACES] = marketplaces.map { it.wire }.toSet() }
    }

    override suspend fun setDefaultSort(sort: SortOption) {
        store.edit { it[SORT] = sort.name }
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        store.edit { it[NOTIFICATIONS] = enabled }
    }

    override suspend fun setDeliveryLocation(location: String) {
        store.edit { it[LOCATION] = location.trim() }
    }

    override suspend fun setLanguage(language: String) {
        store.edit { it[LANGUAGE] = language }
    }

    companion object {
        val SUPPORTED_LANGUAGES = listOf("vi", "en")
        private val LANGUAGE = stringPreferencesKey("language")
        const val FILE_NAME = "dealmate_settings"
        private val BASE_URL = stringPreferencesKey("base_url")
        private val TOKEN_ENC = stringPreferencesKey("token_enc")
        private val MARKETPLACES = stringSetPreferencesKey("marketplaces")
        private val SORT = stringPreferencesKey("default_sort")
        private val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        private val LOCATION = stringPreferencesKey("delivery_location")
    }
}
