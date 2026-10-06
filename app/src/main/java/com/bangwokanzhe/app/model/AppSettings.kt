package com.bangwokanzhe.app.model

/**
 * 用户看护与提醒偏好配置模型
 */
data class AppSettings(
    /**
     * 下拉通知栏动态显示与推送提醒（默认开启）
     */
    val enablePullDownNotification: Boolean = true,

    /**
     * 桌面悬浮小窗实时监控胶囊（默认关闭）
     */
    val enableFloatingWindow: Boolean = false,

    /**
     * 物理振动强提醒（默认开启）
     */
    val enableVibration: Boolean = true,

    /**
     * 声音告警提示（默认开启）
     */
    val enableSound: Boolean = true,

    /**
     * 提前叫号人数阈值（默认提前 3 人）
     */
    val defaultAdvanceWarningCount: Int = 3,

    /**
     * 输液低液位报警百分比阈值（默认 15%）
     */
    val defaultInfusionThresholdPercent: Float = 15.0f,

    /**
     * 智能暗屏节电降温模式（默认开启）
     */
    val enableDimScreenOnMonitor: Boolean = true
)
