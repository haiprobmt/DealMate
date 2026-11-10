package com.dealmate.app.testutil

import com.dealmate.app.di.RepositoryModule
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
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/** Replaces the production repositories with in-memory fakes in every @HiltAndroidTest. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [RepositoryModule::class])
object FakeRepositoryModule {
    @Provides @Singleton fun fakeSearch() = FakeSearchRepository()
    @Provides @Singleton fun fakeProduct() = FakeProductRepository()
    @Provides @Singleton fun fakeWishlist() = FakeWishlistRepository()
    @Provides @Singleton fun fakeHistory() = FakeHistoryRepository()
    @Provides @Singleton fun fakeMeta() = FakeMetaRepository()
    @Provides @Singleton fun fakeAssistant() = FakeAssistantRepository()
    @Provides @Singleton fun fakeNotifications() = FakeNotificationRepository()
    @Provides @Singleton fun fakeSettings() = FakeSettingsRepository()
    @Provides @Singleton fun fakeImages() = FakeImagePreparer()
    @Provides @Singleton fun fakePresenter() = FakeAlertPresenter()

    @Provides fun search(fake: FakeSearchRepository): SearchRepository = fake
    @Provides fun product(fake: FakeProductRepository): ProductRepository = fake
    @Provides fun wishlist(fake: FakeWishlistRepository): WishlistRepository = fake
    @Provides fun history(fake: FakeHistoryRepository): HistoryRepository = fake
    @Provides fun meta(fake: FakeMetaRepository): MetaRepository = fake
    @Provides fun assistant(fake: FakeAssistantRepository): AssistantRepository = fake
    @Provides fun notifications(fake: FakeNotificationRepository): NotificationRepository = fake
    @Provides fun settings(fake: FakeSettingsRepository): SettingsRepository = fake
    @Provides fun images(fake: FakeImagePreparer): ImagePreparer = fake
    @Provides fun presenter(fake: FakeAlertPresenter): AlertPresenter = fake
}
