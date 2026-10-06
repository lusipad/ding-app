package com.bangwokanzhe.app.model

import java.util.UUID

/**
 * 图像质量指标
 */
data class QualityMetrics(
    val blurScore: Float,           // 清晰度得分 (0-100)
    val brightness: Float,          // 亮度 (0-255)
    val isAcceptable: Boolean       // 是否符合分析门槛
)

/**
 * 区域语义分类
 */
enum class RegionSemantics {
    CALLING_NOW,        // 正在叫号栏
    WAITING_LIST,       // 候诊列表栏
    HISTORY_RECORD,     // 历史叫号记录
    OVER_CALLED,        // 明确过号栏
    ADVERTISEMENT,      // 广告/非叫号区域
    UNKNOWN             // 未能明确区分
}

/**
 * 拒绝或不可读原因
 */
enum class RejectReason {
    BLURRY,                 // 画面模糊、抖动或反光严重
    SCREEN_NOT_FOUND,       // 未检测到屏幕或叫号板
    CLINIC_MISMATCH,        // 诊室号不匹配
    DEPARTMENT_MISMATCH,    // 科室不匹配（多科室分屏隔离）
    TARGET_NOT_FOUND,       // 诊室正确但号码未读取到
    LOW_CONFIDENCE,         // OCR置信度过低
    LAYOUT_UNSUPPORTED,     // 布局未能识别
    FRAME_DROPPED           // 帧堆积主动丢弃
}

/**
 * 单个诊室或分屏单元格（空间聚类后得到）
 * 解决大屏多个诊室/科室同屏并存时的归属隔离问题
 */
data class ClinicCell(
    val department: String? = null,                 // 所属科室（如 "儿科门诊"、"心血管内科"）
    val clinic: String,                             // 诊室名称（如 "3号诊室"、"诊室2"）
    val currentCallingNumber: String? = null,       // 该诊室当前呼叫号码
    val waitingNumbers: List<String> = emptyList(), // 该诊室专属候诊队列
    val historyNumbers: List<String> = emptyList(), // 该诊室专属过号/历史列表
    val bounds: RectRelative? = null                // 该单元格在屏幕中的归一化坐标范围
)

/**
 * 输液视觉分析指标
 */
data class InfusionMetrics(
    val estimatedLevelPercent: Float,       // 估算剩余液位百分比 (0-100%)
    val confidence: Float,                  // 置信度 (0-1)
    val isMeniscusDetected: Boolean,        // 是否成功锁定液气界面
    val isBelowWarningThreshold: Boolean    // 是否低于设定警戒线
)

/**
 * 结构化识别字段
 */
data class RecognizedFields(
    val clinic: String? = null,                     // 识别到的主诊室
    val department: String? = null,                 // 识别到的主科室
    val currentCallingNumber: String? = null,       // 正在叫号的号码
    val waitingNumbers: List<String> = emptyList(), // 候诊队列中的号码
    val historyNumbers: List<String> = emptyList(), // 历史呼叫的号码
    val cells: List<ClinicCell> = emptyList(),      // 多科室/多诊室矩阵单元列表
    val rawTextLines: List<String> = emptyList()    // 原始识别文字行
)

/**
 * 最小观察记录（设计文档 4 节）
 * 只有存在新帧且目标可读时才形成有效观察
 */
data class Observation(
    val observationId: String = UUID.randomUUID().toString(),
    val taskId: String,
    val frameId: Long,
    val capturedAt: Long = System.currentTimeMillis(),          // 墙钟时间（用于UI展示）
    val monotonicTimeMs: Long = System.nanoTime() / 1_000_000L, // 单调时钟（用于超时判定）
    val cameraId: String = "back_main",
    val targetRegion: RectRelative? = null,
    val qualityMetrics: QualityMetrics,
    val recognizedFields: RecognizedFields,
    val regionSemantics: RegionSemantics,
    val infusionMetrics: InfusionMetrics? = null,               // 输液指标（输液场景专属）
    val rejectReason: RejectReason? = null,
    val algorithmVersion: String = "v0.2-mlkit-spatial"
) {
    val isValid: Boolean get() = rejectReason == null
}
