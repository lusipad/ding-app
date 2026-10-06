package com.bangwokanzhe.app.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.bangwokanzhe.app.MainActivity
import com.bangwokanzhe.app.R
import com.bangwokanzhe.app.model.AlertEvent
import com.bangwokanzhe.app.model.AlertKind

class AlertNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_SERVICE_ID = "channel_kanzhe_service_v2"
        const val CHANNEL_ALERT_ID = "channel_kanzhe_alert_v2"
        const val SERVICE_NOTIFICATION_ID = 1001
        const val ALERT_NOTIFICATION_ID = 2001
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private val audioAttributes by lazy {
        android.media.AudioAttributes.Builder()
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
            .build()
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                // 删除旧版本的低优先级渠道以使新配置生效
                notificationManager.deleteNotificationChannel("channel_kanzhe_service")
                notificationManager.deleteNotificationChannel("channel_kanzhe_alert")
            } catch (ignored: Exception) {}

            // 常驻前台服务与叫号进度渠道（使用 DEFAULT 优先级，确保在下拉通知栏正常展示）
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_ID,
                "排队进度与监看状态",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "在通知栏实时展示当前呼叫号码与排队进度"
                setShowBadge(false)
            }

            // 高优先级叫号提醒渠道（横幅浮动通知+急促振动）
            val alertChannel = NotificationChannel(
                CHANNEL_ALERT_ID,
                "叫号命中与输液强提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "当识别到正在呼叫目标号码或输液低液位时发出强提醒"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 800)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(alertChannel)
        }
    }

    fun buildServiceNotification(content: String): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_SERVICE_ID)
            .setContentTitle("帮我看着 · 监看中")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_stat_eye)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    /**
     * 实时更新下拉通知栏常驻卡片，实时显示叫号与排队动态
     */
    fun updateServiceNotification(state: com.bangwokanzhe.app.model.MonitorState) {
        val app = com.bangwokanzhe.app.BangWoKanzheApp.instance
        val settings = app.settingsManager.settings.value
        val task = state.task ?: return

        val (title, body) = if (settings.enablePullDownNotification) {
            when (state.matchResult) {
                com.bangwokanzhe.app.model.MatchResult.CONFIRMED_MATCH ->
                    "🎯 正在叫您的号码！" to "请立即前往 ${task.targetSpec.clinicId} 就诊 (已叫到 ${task.targetSpec.targetNumber} 号)"
                com.bangwokanzhe.app.model.MatchResult.PRE_CALL_WARNING ->
                    "⚠️ 叫号预警 · 即将到达" to "当前前面还剩 ${state.waitingAheadCount ?: 1} 人，请准备进入诊室"
                com.bangwokanzhe.app.model.MatchResult.MISSED_CALL ->
                    "❌ 疑似已过号提醒" to "当前叫号可能已超过您的号码 ${task.targetSpec.targetNumber}，请联系分诊台"
                else -> {
                    if (task.scenario == com.bangwokanzhe.app.model.TaskScenario.INFUSION_EXPERIMENT) {
                        val level = state.infusionLevelPercent?.let { "${it.toInt()}%" } ?: "检测中"
                        "🧪 输液监看中" to "当前药液液位: $level · 阈值: ${task.infusionSpec?.warningThresholdPercent?.toInt() ?: 15}%"
                    } else {
                        val currentCalling = state.lastObservation?.recognizedFields?.currentCallingNumber ?: "巡视中"
                        val aheadText = state.waitingAheadCount?.let { " (前方还剩 $it 人)" } ?: ""
                        "帮我看着 · 监看中" to "${task.targetSpec.clinicId} · 等待 ${task.targetSpec.targetNumber} 号 · 叫到: $currentCalling$aheadText"
                    }
                }
            }
        } else {
            "帮我看着 · 运行中" to "后台持续监看中（下拉通知动态显示已静音）"
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val updatedNotification = NotificationCompat.Builder(context, CHANNEL_SERVICE_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_stat_eye)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationManager.notify(SERVICE_NOTIFICATION_ID, updatedNotification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun showAlert(event: AlertEvent) {
        val app = com.bangwokanzhe.app.BangWoKanzheApp.instance
        val settings = app.settingsManager.settings.value

        // 若用户开启了下拉通知，则弹出高优先级悬浮横幅通知
        if (settings.enablePullDownNotification) {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                event.eventId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ALERT_ID)
                .setContentTitle(event.title)
                .setContentText(event.message)
                .setSmallIcon(R.drawable.ic_stat_eye)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(pendingIntent, true)

            if (settings.enableSound) {
                val soundUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                builder.setSound(soundUri)
            }

            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    notificationManager.notify(ALERT_NOTIFICATION_ID, builder.build())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 检查振动设置
        if (settings.enableVibration) {
            triggerVibration(event.kind)
        }
    }

    fun triggerVibration(kind: AlertKind) {
        val pattern = when (kind) {
            AlertKind.CONFIRMED_MATCH, AlertKind.INFUSION_LOW_LEVEL ->
                // 强力重复节奏振动（叫号命中或输液低液位警报）
                longArrayOf(0, 500, 150, 500, 150, 800)
            AlertKind.MISSED_CALL ->
                // 警示型急促双长振（过号提示）
                longArrayOf(0, 600, 200, 600)
            AlertKind.PRE_CALL_WARNING ->
                // 提前预警振动（中等提示，提醒起身准备）
                longArrayOf(0, 350, 150, 350)
            AlertKind.POSSIBLE_MATCH ->
                // 温和提醒振动
                longArrayOf(0, 200, 100, 200)
            else ->
                // 单次明确提示
                longArrayOf(0, 300)
        }
        executeVibration(pattern)
    }

    /**
     * 执行底层物理振动：使用 USAGE_ALARM 属性穿透静音，且仅使用标准时序（免振幅兼容所有机型）
     */
    private fun executeVibration(pattern: LongArray) {
        try {
            if (!vibrator.hasVibrator()) {
                showToast("⚠️ 当前设备未检测到物理振动硬件（如在模拟器运行）")
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(pattern, -1)
                vibrator.vibrate(effect, audioAttributes)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // 极端异常时单次兜底振动
            try {
                @Suppress("DEPRECATION")
                vibrator.vibrate(400)
            } catch (ignored: Exception) {}
        }
    }

    /**
     * 试一试强力振动（用于创建任务时的功能测试）
     */
    fun testVibrate() {
        val pattern = longArrayOf(0, 400, 150, 400, 150, 700)
        executeVibration(pattern)
        showToast("📳 正在触发强力叫号振动（闹钟模式穿透静音）")
    }

    /**
     * 发送测试通知：在下拉通知栏显示一条标准排队状态卡片，让用户即时检验
     */
    fun testShowSampleNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                showToast("⚠️ 缺少通知权限，请在手机设置中开启「帮我看着」通知权限")
                return
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            999,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sampleNotification = NotificationCompat.Builder(context, CHANNEL_SERVICE_ID)
            .setContentTitle("帮我看着 · 叫号进度通知 (测试卡片)")
            .setContentText("综合门诊 3号诊室 · 等待 128 号 · 当前已叫到 126 号 (前方还剩 2 人)")
            .setSmallIcon(R.drawable.ic_stat_eye)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            notificationManager.notify(SERVICE_NOTIFICATION_ID, sampleNotification)
            showToast("📬 下拉通知已发送！请下滑手机屏幕查看状态栏通知")
        } catch (e: Exception) {
            showToast("发送通知失败: ${e.message}")
        }
    }

    private fun showToast(message: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun cancelAlertNotification() {
        notificationManager.cancel(ALERT_NOTIFICATION_ID)
    }
}
