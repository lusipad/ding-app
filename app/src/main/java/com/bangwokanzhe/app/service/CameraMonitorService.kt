package com.bangwokanzhe.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.bangwokanzhe.app.BangWoKanzheApp
import com.bangwokanzhe.app.model.MatchResult
import com.bangwokanzhe.app.model.TaskLifecycle
import com.bangwokanzhe.app.notification.AlertNotificationManager
import kotlinx.coroutines.*
import java.util.concurrent.Executors

class CameraMonitorService : Service(), LifecycleOwner {

    companion object {
        private const val TAG = "CameraMonitorService"

        const val ACTION_START = "com.bangwokanzhe.app.action.START"
        const val ACTION_STOP = "com.bangwokanzhe.app.action.STOP"
        const val ACTION_PAUSE = "com.bangwokanzhe.app.action.PAUSE"
        const val ACTION_RESUME = "com.bangwokanzhe.app.action.RESUME"

        fun startService(context: Context) {
            val intent = Intent(context, CameraMonitorService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, CameraMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val overlayManager get() = BangWoKanzheApp.instance.floatingOverlayManager
    private val lensHelper by lazy { CameraLensHelper(this) }

    private var cameraProvider: ProcessCameraProvider? = null
    private var lastAnalyzedTimestamp = 0L
    private var isHighRateBurstMode = false
    private var burstModeUntilTimestamp = 0L

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = BangWoKanzheApp.instance
        val notificationManager = app.notificationManager

        when (intent?.action) {
            ACTION_START -> {
                val currentTask = app.taskStateManager.state.value.task
                val notifText = if (currentTask != null) {
                    "正在看护 ${currentTask.targetSpec.clinicId} · ${currentTask.targetSpec.targetNumber} 号"
                } else {
                    "正在间歇检查屏幕..."
                }

                val notification = notificationManager.buildServiceNotification(notifText)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        AlertNotificationManager.SERVICE_NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                    )
                } else {
                    startForeground(AlertNotificationManager.SERVICE_NOTIFICATION_ID, notification)
                }

                lifecycleRegistry.currentState = Lifecycle.State.STARTED
                lifecycleRegistry.currentState = Lifecycle.State.RESUMED

                startCameraAnalysis()

                // 根据用户配置决定是否显示悬浮窗（默认关闭）
                val settings = app.settingsManager.settings.value
                if (settings.enableFloatingWindow && overlayManager.canDrawOverlays()) {
                    overlayManager.show()
                } else {
                    overlayManager.hide()
                }

                // 监听配置变更，动态启停悬浮窗
                observeSettingsChanges()
            }

            ACTION_STOP -> {
                overlayManager.hide()
                stopCameraAnalysis()
                app.taskStateManager.endTask()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            ACTION_PAUSE -> {
                app.taskStateManager.pauseTask()
            }

            ACTION_RESUME -> {
                app.taskStateManager.resumeTask()
            }
        }

        return START_NOT_STICKY
    }

    private fun startCameraAnalysis() {
        val app = BangWoKanzheApp.instance
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindImageAnalysis(cameraProvider!!)
            } catch (e: Exception) {
                Log.e(TAG, "初始化 CameraX 失败", e)
                app.taskStateManager.setBlocked("相机初始化失败: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindImageAnalysis(provider: ProcessCameraProvider) {
        val app = BangWoKanzheApp.instance

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
            processFrame(imageProxy)
        }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            provider.unbindAll()
            provider.bindToLifecycle(this, cameraSelector, imageAnalysis)
        } catch (e: Exception) {
            Log.e(TAG, "绑定相机生命周期失败", e)
            app.taskStateManager.setBlocked("相机被抢占或不可用: ${e.message}")
        }
    }

    private fun processFrame(imageProxy: ImageProxy) {
        val app = BangWoKanzheApp.instance
        val currentState = app.taskStateManager.state.value
        val task = currentState.task

        if (task == null || currentState.lifecycle != TaskLifecycle.RUNNING) {
            imageProxy.close()
            return
        }

        val now = System.currentTimeMillis()

        // 采样控制：根据设计文档，搜索模式 ~1fps (1000ms)，突发读取模式 ~3fps (300ms) 持续约 2 秒
        val minInterval = if (isHighRateBurstMode && now < burstModeUntilTimestamp) 300L else 1000L
        if (now - lastAnalyzedTimestamp < minInterval) {
            imageProxy.close()
            return
        }
        lastAnalyzedTimestamp = now

        serviceScope.launch(Dispatchers.Default) {
            try {
                val observation = app.observationPipeline.processImageProxy(imageProxy, task)
                val ruleResult = app.taskStateManager.onObservation(observation)

                // 若发现疑似屏幕或号码候选，触发 2 秒短时高频读取以锁定清晰画面
                if (observation.isValid && !isHighRateBurstMode) {
                    isHighRateBurstMode = true
                    burstModeUntilTimestamp = System.currentTimeMillis() + 2000L
                } else if (now >= burstModeUntilTimestamp) {
                    isHighRateBurstMode = false
                }

                // 触发下拉通知栏实时状态刷新
                app.notificationManager.updateServiceNotification(app.taskStateManager.state.value)

                // 触发通知提醒
                ruleResult?.alertEvent?.let { event ->
                    app.notificationManager.showAlert(event)
                }

                withContext(Dispatchers.Main) {
                    overlayManager.updateState(app.taskStateManager.state.value)
                }
            } catch (e: Exception) {
                Log.e(TAG, "处理视频帧异常", e)
            } finally {
                imageProxy.close()
            }
        }
    }

    private var settingsJob: Job? = null
    private fun observeSettingsChanges() {
        settingsJob?.cancel()
        val app = BangWoKanzheApp.instance
        settingsJob = serviceScope.launch {
            app.settingsManager.settings.collect { settings ->
                withContext(Dispatchers.Main) {
                    if (settings.enableFloatingWindow && overlayManager.canDrawOverlays()) {
                        overlayManager.show()
                        overlayManager.updateState(app.taskStateManager.state.value)
                    } else {
                        overlayManager.hide()
                    }
                }
            }
        }
    }

    private fun stopCameraAnalysis() {
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.e(TAG, "解绑相机失败", e)
        }
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        overlayManager.hide()
        stopCameraAnalysis()
        analysisExecutor.shutdown()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
