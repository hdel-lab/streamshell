package com.streamshell.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.streamshell.core.browser.AndroidExternalNavigator
import com.streamshell.core.browser.AndroidNetworkMonitor
import com.streamshell.core.browser.ExternalNavigator
import com.streamshell.core.browser.NetworkMonitor
import com.streamshell.core.library.DataStoreLibraryRepository
import com.streamshell.core.library.LibraryRepository
import com.streamshell.core.privacy.BlockedRequestRepository
import com.streamshell.core.privacy.DataStoreFilterMetaStore
import com.streamshell.core.privacy.DefaultFilterListRepository
import com.streamshell.core.privacy.FilterListRepository
import com.streamshell.core.privacy.HttpsFilterListDownloader
import com.streamshell.core.privacy.InMemoryBlockedRequestRepository
import com.streamshell.core.privacy.InternalFilterFileStore
import com.streamshell.core.privacy.PrivacyCoordinator
import com.streamshell.core.settings.DataStoreSettingsRepository
import com.streamshell.core.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SettingsStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FilterStore

/** Bookmarks, history and open tabs. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LibraryStore

/**
 * Singletons here are process-wide services (stores, filter state, network watcher). WebViews are
 * not among them: those are owned by the activity-scoped BrowserViewModel (BrowserEngine).
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton @AppScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides @Singleton @SettingsStore
    fun settingsStore(@ApplicationContext c: Context, @AppScope scope: CoroutineScope): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { c.preferencesDataStoreFile("settings") }

    @Provides @Singleton
    fun settingsRepository(@SettingsStore store: DataStore<Preferences>): SettingsRepository = DataStoreSettingsRepository(store)

    @Provides @Singleton @LibraryStore
    fun libraryStore(@ApplicationContext c: Context, @AppScope scope: CoroutineScope): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { c.preferencesDataStoreFile("library") }

    @Provides @Singleton
    fun libraryRepository(@LibraryStore store: DataStore<Preferences>): LibraryRepository = DataStoreLibraryRepository(store)

    @Provides @Singleton
    fun blockedRequests(): BlockedRequestRepository = InMemoryBlockedRequestRepository()

    @Provides @Singleton @FilterStore
    fun filterStore(@ApplicationContext c: Context, @AppScope scope: CoroutineScope): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { c.preferencesDataStoreFile("filters") }

    @Provides @Singleton
    fun filterLists(@ApplicationContext c: Context, @FilterStore store: DataStore<Preferences>): FilterListRepository = DefaultFilterListRepository(
        builtin = { c.assets.open("filters/builtin.txt").use { it.readBytes() } },
        meta = DataStoreFilterMetaStore(store),
        files = InternalFilterFileStore(c),
        downloader = HttpsFilterListDownloader()
    )

    @Provides @Singleton
    fun privacyCoordinator(
        @AppScope scope: CoroutineScope,
        settings: SettingsRepository,
        filterLists: FilterListRepository,
        blocked: BlockedRequestRepository
    ): PrivacyCoordinator = PrivacyCoordinator(scope, settings.settings, filterLists.engineState, blocked)

    @Provides @Singleton
    fun networkMonitor(@ApplicationContext c: Context): NetworkMonitor = AndroidNetworkMonitor(c)

    @Provides @Singleton
    fun externalNavigator(@ApplicationContext c: Context): ExternalNavigator = AndroidExternalNavigator(c)
}
