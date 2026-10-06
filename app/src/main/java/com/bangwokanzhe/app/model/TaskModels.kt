package com.bangwokanzhe.app.model

import java.util.UUID

/**
 * 任务场景定义
 */
enum class TaskScenario {
    HOSPITAL_CALL,          // 医院叫号
    INFUSION_EXPERIMENT     // 输液实验模块
}

/**
 * 目标规则规范
 */
data class TargetSpec(
    val departmentName: String? = null, // 所属科室（如 "内科"、"外科"，用于大屏多科室隔离）
    val clinicId: String,               // 诊室标识，如 "3号诊室"、"专家2诊室"
    val targetNumber: String,           // 目标叫号，如 "128"、"A128"
    val patientName: String? = null,    // 姓名（可选辅助匹配，不作唯一依据）
    val advanceWarningCount: Int = 2,   // 提前叫号预警人数（默认提前 2 人提醒前往诊室）
    val customScreenArea: RectRelative? = null // 用户可选框选的屏幕区域
)

/**
 * 输液监控规格参数
 */
data class InfusionSpec(
    val warningThresholdPercent: Float = 15f,  // 警戒余量百分比（默认低于 15% 报警）
    val bottleType: String = "STANDARD_BOTTLE", // 瓶型（排除软袋）
    val consecutiveConfirmFrames: Int = 3       // 连续确认触发帧数（防抖动）
)

data class RectRelative(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

/**
 * 任务生命周期状态（根据设计文档 4 状态拆分）
 */
enum class TaskLifecycle {
    READY,      // 已就绪，未开始
    RUNNING,    // 正在后台/前台监看中
    PAUSED,     // 用户主动暂停
    BLOCKED,    // 阻塞异常（相机被占用、权限关闭、温控限制等）
    ENDED       // 已完成或已结束
}

/**
 * 观察新鲜度状态
 */
enum class ObservationFreshness {
    NEVER_SEEN, // 从未看到有效屏幕
    FRESH,      // 观察新鲜（在新鲜度窗口内，如60秒内）
    STALE       // 观察过期（超时未看到，如超过60秒）
}

/**
 * 匹配判定结果
 */
enum class MatchResult {
    UNKNOWN,            // 未知/未找到目标
    NO_MATCH,           // 已看清屏幕，当前叫号非目标号码
    PRE_CALL_WARNING,   // 提前叫号预警（目标号码排在前面且距离 <= 设定人数，请准备前往诊室）
    POSSIBLE_MATCH,     // 疑似命中（弱证据，如在候诊长队列中出现）
    CONFIRMED_MATCH,    // 确认命中（强证据，在正在叫号栏完全吻合）
    MISSED_CALL         // 过号提醒（在过号列表中发现，或当前呼叫号已大幅超越目标号）
}

/**
 * 最小任务模型（设计文档 4 节）
 */
data class Task(
    val taskId: String = UUID.randomUUID().toString(),
    val scenario: TaskScenario = TaskScenario.HOSPITAL_CALL,
    val targetSpec: TargetSpec,
    val infusionSpec: InfusionSpec? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 2 * 60 * 60 * 1000L, // 默认有效期 2 小时
    val configVersion: Int = 1
)

/**
 * 整体聚合监看状态
 */
data class MonitorState(
    val task: Task? = null,
    val lifecycle: TaskLifecycle = TaskLifecycle.READY,
    val freshness: ObservationFreshness = ObservationFreshness.NEVER_SEEN,
    val matchResult: MatchResult = MatchResult.UNKNOWN,
    val lastObservation: Observation? = null,
    val lastAlertEvent: AlertEvent? = null,
    val waitingAheadCount: Int? = null, // 前面还有多少位排队
    val isMissedCall: Boolean = false,  // 是否已过号
    val infusionLevelPercent: Float? = null, // 输液剩余百分比
    val errorMessage: String? = null,
    val searchFps: Float = 1.0f,
    val totalFramesProcessed: Long = 0,
    val validObservationCount: Long = 0
)
