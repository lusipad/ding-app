package com.bangwokanzhe.app.model

import java.util.UUID

/**
 * 提醒事件类型
 */
enum class AlertKind {
    CONFIRMED_MATCH,        // 确认命中（正在呼叫目标号码）
    PRE_CALL_WARNING,       // 提前叫号预警（排在前面，还剩 N 人即将就诊）
    POSSIBLE_MATCH,         // 疑似命中（在较长候诊队列中出现）
    HISTORY_RECORD_SEEN,    // 在近期呼叫历史中看到
    MISSED_CALL,            // 已过号强提醒（在过号栏看到或当前呼叫号已大幅超越）
    INFUSION_LOW_LEVEL,     // 输液低液位报警（余量低于警戒阈值）
    LONG_TIME_NO_SEE        // 长时间未见屏幕提醒
}

/**
 * 提醒事件模型（设计文档 4 节）
 */
data class AlertEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val taskId: String,
    val kind: AlertKind,
    val evidenceObservationIds: List<String>,
    val createdAt: Long = System.currentTimeMillis(),
    var acknowledgedAt: Long? = null,
    val deduplicationKey: String,
    val title: String,
    val message: String
)
