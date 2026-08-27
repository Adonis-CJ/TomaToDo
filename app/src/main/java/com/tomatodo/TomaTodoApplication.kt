package com.tomatodo

import android.app.Application
import com.tomatodo.data.AppContainer

class TomaTodoApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
