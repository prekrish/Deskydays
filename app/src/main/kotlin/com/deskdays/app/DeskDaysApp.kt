package com.deskdays.app

import android.app.Application
import com.deskdays.app.data.AppContainer

class DeskDaysApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.startTrackingSync()
    }
}
