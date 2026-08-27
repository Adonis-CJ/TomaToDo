package com.tomatodo

import android.app.Application
import com.tomatodo.data.AppContainer
import com.tomatodo.data.defaultSubjects
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TomaTodoApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        seedDefaultSubjects()
    }

    private fun seedDefaultSubjects() {
        appScope.launch {
            val dao = container.database.subjectDao()
            if (dao.observeAll().first().isEmpty()) {
                dao.insertAll(defaultSubjects())
            }
        }
    }
}
