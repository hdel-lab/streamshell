package com.streamshell

import android.app.Application
import com.streamshell.core.privacy.FilterListRepository
import com.streamshell.core.privacy.FilterUpdateScheduler
import com.streamshell.core.settings.SettingsRepository
import com.streamshell.di.AppScope
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltAndroidApp
class StreamShellApp : Application() {
    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var filterLists: FilterListRepository

    @Inject @AppScope
    lateinit var scope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        com.streamshell.core.diag.CrashReport.install(this)
        // Offline-safe: builds the engine from the built-in list and CACHED lists only; no network here.
        scope.launch { filterLists.initialize() }
        scope.launch {
            settings.settings.map { it.updateIntervalHours }.distinctUntilChanged().collect {
                // A scheduling problem must never take the app down; lists simply stay as they are.
                runCatching { FilterUpdateScheduler.apply(this@StreamShellApp, it) }
            }
        }
    }
}
