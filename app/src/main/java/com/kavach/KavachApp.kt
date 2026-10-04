package com.kavach

import android.app.Application
import com.kavach.data.BlocklistRepository
import com.kavach.data.KavachDatabase
import com.kavach.data.SettingsStore
import com.kavach.work.BlocklistUpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Application container. Manual wiring keeps the app small and dependency-free. */
class KavachApp : Application() {

    val appScope = CoroutineScope(SupervisorJob())
    lateinit var settings: SettingsStore
        private set
    lateinit var repository: BlocklistRepository
        private set
    lateinit var database: KavachDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        database = KavachDatabase.get(this)
        settings = SettingsStore(this)
        repository = BlocklistRepository(this)
        appScope.launch { repository.reload() }
        BlocklistUpdateWorker.schedule(this)
    }

    companion object {
        fun from(context: android.content.Context): KavachApp =
            context.applicationContext as KavachApp
    }
}
