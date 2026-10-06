package com.bangwokanzhe.app

import android.app.Application
import com.bangwokanzhe.app.engine.TaskStateManager
import com.bangwokanzhe.app.notification.AlertNotificationManager
import com.bangwokanzhe.app.pipeline.ObservationPipeline

class BangWoKanzheApp : Application() {

    companion object {
        lateinit var instance: BangWoKanzheApp
            private set
    }

    val taskStateManager: TaskStateManager by lazy { TaskStateManager() }
    val notificationManager: AlertNotificationManager by lazy { AlertNotificationManager(this) }
    val observationPipeline: ObservationPipeline by lazy { ObservationPipeline() }
    val settingsManager: com.bangwokanzhe.app.data.SettingsManager by lazy { com.bangwokanzhe.app.data.SettingsManager(this) }
    val floatingOverlayManager: com.bangwokanzhe.app.ui.overlay.FloatingOverlayManager by lazy {
        com.bangwokanzhe.app.ui.overlay.FloatingOverlayManager(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onTerminate() {
        super.onTerminate()
        floatingOverlayManager.hide(false)
        taskStateManager.destroy()
        observationPipeline.close()
    }
}
