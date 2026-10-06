package com.bangwokanzhe.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bangwokanzhe.app.model.Task
import com.bangwokanzhe.app.model.TaskLifecycle
import com.bangwokanzhe.app.service.CameraMonitorService
import com.bangwokanzhe.app.ui.screens.HomeScreen
import com.bangwokanzhe.app.ui.screens.MonitorDashboardScreen
import com.bangwokanzhe.app.ui.screens.ScanSetupScreen
import com.bangwokanzhe.app.ui.screens.SimulatorDialog
import com.bangwokanzhe.app.ui.theme.BangWoKanzheTheme

class MainActivity : ComponentActivity() {

    private var pendingTaskToStart: Task? = null
    private var onCameraPermissionGrantedAction: (() -> Unit)? = null
    private var showSimulatorDialog = mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        if (cameraGranted) {
            onCameraPermissionGrantedAction?.invoke()
            onCameraPermissionGrantedAction = null

            pendingTaskToStart?.let { launchMonitorTask(it) }
            pendingTaskToStart = null
        } else {
            Toast.makeText(this, "需要相机权限以进行间歇屏幕识别", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 13+ 提前引导通知权限，确保下拉状态栏通知即时可用
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }

        setContent {
            BangWoKanzheTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val app = BangWoKanzheApp.instance
                    val monitorState by app.taskStateManager.state.collectAsStateWithLifecycle()
                    val recentObservations by app.taskStateManager.recentObservations.collectAsStateWithLifecycle()
                    var isScanningSetupMode by remember { mutableStateOf(false) }
                    val showSettingsDialog = remember { mutableStateOf(false) }

                    val isMonitoringActive = monitorState.task != null &&
                            monitorState.lifecycle != TaskLifecycle.READY &&
                            monitorState.lifecycle != TaskLifecycle.ENDED

                    when {
                        isMonitoringActive -> {
                            MonitorDashboardScreen(
                                state = monitorState,
                                recentObservations = recentObservations,
                                onPause = {
                                    app.taskStateManager.pauseTask()
                                },
                                onResume = {
                                    app.taskStateManager.resumeTask()
                                },
                                onEndTask = {
                                    CameraMonitorService.stopService(this@MainActivity)
                                    app.taskStateManager.endTask()
                                    app.floatingOverlayManager.hide()
                                },
                                onAcknowledgeAlert = {
                                    app.taskStateManager.acknowledgeAlert()
                                    app.notificationManager.cancelAlertNotification()
                                },
                                onOpenSimulator = {
                                    showSimulatorDialog.value = true
                                },
                                onOpenSettings = {
                                    showSettingsDialog.value = true
                                }
                            )
                        }

                        isScanningSetupMode -> {
                            ScanSetupScreen(
                                onBack = { isScanningSetupMode = false },
                                onConfirmTask = { task ->
                                    isScanningSetupMode = false
                                    checkPermissionsAndStart(task)
                                }
                            )
                        }

                        else -> {
                            HomeScreen(
                                onScanScreen = {
                                    checkCameraPermissionAndRun {
                                        isScanningSetupMode = true
                                    }
                                },
                                onStartTask = { task ->
                                    checkPermissionsAndStart(task)
                                },
                                onTestVibrate = {
                                    app.notificationManager.testVibrate()
                                },
                                onTestNotification = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS)
                                        != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                                    } else {
                                        app.notificationManager.testShowSampleNotification()
                                    }
                                },
                                onToggleFloatingOverlay = {
                                    if (!app.floatingOverlayManager.canDrawOverlays()) {
                                        com.bangwokanzhe.app.ui.overlay.FloatingOverlayManager.openOverlayPermissionSetting(this@MainActivity)
                                        Toast.makeText(this@MainActivity, "请在设置中允许「帮我看着」显示在其他应用上层", Toast.LENGTH_LONG).show()
                                    } else {
                                        val newState = app.floatingOverlayManager.toggle()
                                        app.settingsManager.setEnableFloatingWindow(newState)
                                    }
                                },
                                onOpenSimulator = {
                                    if (monitorState.task == null) {
                                        app.taskStateManager.startTask(
                                            Task(
                                                targetSpec = com.bangwokanzhe.app.model.TargetSpec(
                                                    clinicId = "3号诊室",
                                                    targetNumber = "128"
                                                )
                                            )
                                        )
                                    }
                                    showSimulatorDialog.value = true
                                },
                                onOpenSettings = {
                                    showSettingsDialog.value = true
                                }
                            )
                        }
                    }

                    if (showSimulatorDialog.value) {
                        SimulatorDialog(
                            onDismiss = { showSimulatorDialog.value = false }
                        )
                    }

                    if (showSettingsDialog.value) {
                        com.bangwokanzhe.app.ui.screens.SettingsDialog(
                            onDismiss = { showSettingsDialog.value = false },
                            onTestVibrate = { app.notificationManager.testVibrate() }
                        )
                    }
                }
            }
        }
    }

    private fun checkCameraPermissionAndRun(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            action()
        } else {
            onCameraPermissionGrantedAction = action
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        }
    }

    private fun checkPermissionsAndStart(task: Task) {
        val permissionsNeeded = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsNeeded.add(Manifest.permission.CAMERA)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsNeeded.isEmpty()) {
            launchMonitorTask(task)
        } else {
            pendingTaskToStart = task
            permissionLauncher.launch(permissionsNeeded.toTypedArray())
        }
    }

    private fun launchMonitorTask(task: Task) {
        val app = BangWoKanzheApp.instance
        app.taskStateManager.startTask(task)
        CameraMonitorService.startService(this)
        Toast.makeText(this, "「帮我看着」已启动，切到其他 App 也可正常监看", Toast.LENGTH_SHORT).show()
    }
}
