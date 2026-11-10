package com.dealmate.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.dealmate.app.BuildConfig
import com.dealmate.app.data.image.ImagePreparerImpl
import com.dealmate.app.data.local.DealMateDatabase
import com.dealmate.app.data.local.SearchDao
import com.dealmate.app.data.local.ViewedDao
import com.dealmate.app.data.local.WishlistDao
import com.dealmate.app.data.remote.DealMateApi
import com.dealmate.app.data.remote.ServerConfig
import com.dealmate.app.data.remote.ServerConfigProvider
import com.dealmate.app.data.remote.ServerInterceptor
import com.dealmate.app.data.remote.dto.DealMateJson
import com.dealmate.app.data.repository.AssistantRepositoryImpl
import com.dealmate.app.data.repository.HistoryRepositoryImpl
import com.dealmate.app.data.repository.MetaRepositoryImpl
import com.dealmate.app.data.repository.NotificationRepositoryImpl
import com.dealmate.app.data.repository.ProductRepositoryImpl
import com.dealmate.app.data.repository.SearchRepositoryImpl
import com.dealmate.app.data.repository.WishlistRepositoryImpl
import com.dealmate.app.data.settings.KeystoreSecretCipher
import com.dealmate.app.data.settings.SettingsRepositoryImpl
import com.dealmate.app.domain.repository.AssistantRepository
import com.dealmate.app.domain.repository.HistoryRepository
import com.dealmate.app.domain.repository.ImagePreparer
import com.dealmate.app.domain.repository.MetaRepository
import com.dealmate.app.domain.repository.NotificationRepository
import com.dealmate.app.domain.repository.ProductRepository
import com.dealmate.app.domain.repository.SearchRepository
import com.dealmate.app.domain.repository.SettingsRepository
import com.dealmate.app.domain.repository.WishlistRepository
import com.dealmate.app.notifications.AlertPresenter
import com.dealmate.app.notifications.PriceAlertNotifier
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    fun clock(): Clock = Clock.systemUTC()
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun serverConfigProvider(settings: SettingsRepository): ServerConfigProvider = ServerConfigProvider {
        // Runs on OkHttp dispatcher threads only, never on the main thread.
        runBlocking { settings.current() }.let { ServerConfig(it.baseUrl, it.token) }
    }

    @Provides
    @Singleton
    fun okHttp(config: ServerConfigProvider): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // Searches fan out to several marketplaces on the server and may take a while.
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(ServerInterceptor(config))
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                        redactHeader("Authorization")
                    },
                )
            }
        }
        .build()

    @Provides
    @Singleton
    fun api(client: OkHttpClient): DealMateApi = Retrofit.Builder()
        .baseUrl(ServerInterceptor.PLACEHOLDER_BASE_URL)
        .client(client)
        .addConverterFactory(DealMateJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(DealMateApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): DealMateDatabase =
        Room.databaseBuilder(context, DealMateDatabase::class.java, "dealmate.db")
            // Everything in this database is a re-fetchable cache.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides fun searchDao(db: DealMateDatabase): SearchDao = db.searchDao()
    @Provides fun wishlistDao(db: DealMateDatabase): WishlistDao = db.wishlistDao()
    @Provides fun viewedDao(db: DealMateDatabase): ViewedDao = db.viewedDao()
}

/** Everything the UI layer depends on. UI tests replace this whole module with in-memory fakes. */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun settingsStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(SettingsRepositoryImpl.FILE_NAME) }

    @Provides
    @Singleton
    fun settings(store: DataStore<Preferences>, cipher: KeystoreSecretCipher): SettingsRepository =
        SettingsRepositoryImpl(store, cipher, BuildConfig.DEFAULT_BASE_URL)

    @Provides @Singleton fun search(impl: SearchRepositoryImpl): SearchRepository = impl
    @Provides @Singleton fun product(impl: ProductRepositoryImpl): ProductRepository = impl
    @Provides @Singleton fun wishlist(impl: WishlistRepositoryImpl): WishlistRepository = impl
    @Provides @Singleton fun history(impl: HistoryRepositoryImpl): HistoryRepository = impl
    @Provides @Singleton fun meta(impl: MetaRepositoryImpl): MetaRepository = impl
    @Provides @Singleton fun assistant(impl: AssistantRepositoryImpl): AssistantRepository = impl
    @Provides @Singleton fun notifications(impl: NotificationRepositoryImpl): NotificationRepository = impl
    @Provides @Singleton fun imagePreparer(impl: ImagePreparerImpl): ImagePreparer = impl
    @Provides fun alertPresenter(impl: PriceAlertNotifier): AlertPresenter = impl
}
