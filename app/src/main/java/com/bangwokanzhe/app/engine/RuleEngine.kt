package com.bangwokanzhe.app.engine

import com.bangwokanzhe.app.model.*
import com.bangwokanzhe.app.pipeline.LayoutSemanticParser

/**
 * 规则引擎
 * 消费不可变观察记录，输出 MatchResult 与 AlertEvent，不直接操作相机或硬件
 */
class RuleEngine {

    // 已触发去重记录：deduplicationKey -> 上次触发时间
    private val alertHistory = mutableMapOf<String, Long>()

    // 同一事件去重冷却时间（默认 3 分钟内同一状态不重复强通知）
    private val deduplicationCooldownMs = 3 * 60 * 1000L

    data class RuleResult(
        val matchResult: MatchResult,
        val alertEvent: AlertEvent?,
        val aheadCount: Int? = null
    )

    fun evaluate(task: Task, observation: Observation): RuleResult {
        // 输液场景处理
        if (task.scenario == TaskScenario.INFUSION_EXPERIMENT) {
            val infusion = observation.infusionMetrics
            if (infusion != null && infusion.isBelowWarningThreshold) {
                val eventKey = "${task.taskId}_INFUSION_LOW"
                val event = if (shouldTrigger(eventKey)) {
                    AlertEvent(
                        taskId = task.taskId,
                        kind = AlertKind.INFUSION_LOW_LEVEL,
                        evidenceObservationIds = listOf(observation.observationId),
                        deduplicationKey = eventKey,
                        title = "⚠️ 输液低液位报警！",
                        message = "检测到剩余药液约为 ${infusion.estimatedLevelPercent.toInt()}%，已低于警戒线，请呼叫护士换瓶！"
                    ).also {
                        alertHistory[eventKey] = System.currentTimeMillis()
                    }
                } else null

                return RuleResult(MatchResult.CONFIRMED_MATCH, event)
            }
            return RuleResult(MatchResult.UNKNOWN, null)
        }

        // 医院叫号场景处理
        if (!observation.isValid) {
            return RuleResult(MatchResult.UNKNOWN, null)
        }

        val targetNum = task.targetSpec.targetNumber
        val fields = observation.recognizedFields
        val clinicTitle = fields.clinic ?: task.targetSpec.clinicId

        when (observation.regionSemantics) {
            RegionSemantics.CALLING_NOW -> {
                // 强证据：正在叫号栏出现目标号码！
                val callingNum = fields.currentCallingNumber ?: targetNum
                val eventKey = "${task.taskId}_CONFIRMED_${callingNum}"

                val event = if (shouldTrigger(eventKey)) {
                    AlertEvent(
                        taskId = task.taskId,
                        kind = AlertKind.CONFIRMED_MATCH,
                        evidenceObservationIds = listOf(observation.observationId),
                        deduplicationKey = eventKey,
                        title = "看到 ${callingNum} 号！",
                        message = "正在呼叫您的号码，请尽快前往 ${clinicTitle} 就诊！"
                    ).also {
                        alertHistory[eventKey] = System.currentTimeMillis()
                    }
                } else null

                return RuleResult(MatchResult.CONFIRMED_MATCH, event)
            }

            RegionSemantics.OVER_CALLED -> {
                // 过号强提醒：无论是在显式过号栏，还是当前翻号已越界
                val eventKey = "${task.taskId}_MISSED_${targetNum}"
                val currentCalling = fields.currentCallingNumber ?: "后续号码"

                val event = if (shouldTrigger(eventKey)) {
                    AlertEvent(
                        taskId = task.taskId,
                        kind = AlertKind.MISSED_CALL,
                        evidenceObservationIds = listOf(observation.observationId),
                        deduplicationKey = eventKey,
                        title = "⚠️ 您的 ${targetNum} 号可能已过号！",
                        message = "当前诊室已叫到 ${currentCalling}。请尽快前往分诊台或诊室前刷卡重新激活排队！"
                    ).also {
                        alertHistory[eventKey] = System.currentTimeMillis()
                    }
                } else null

                return RuleResult(MatchResult.MISSED_CALL, event)
            }

            RegionSemantics.WAITING_LIST -> {
                val matchedCell = fields.cells.find {
                    LayoutSemanticParser.isClinicMatch(it.clinic, task.targetSpec.clinicId)
                }
                val waitingList = matchedCell?.waitingNumbers ?: fields.waitingNumbers
                val targetClean = LayoutSemanticParser.normalizeNumber(targetNum)
                val waitingIndex = waitingList.indexOfFirst {
                    LayoutSemanticParser.isNumberMatch(LayoutSemanticParser.normalizeNumber(it), targetClean)
                }

                val aheadCount = if (waitingIndex >= 0) waitingIndex else null
                val advanceThreshold = task.targetSpec.advanceWarningCount

                if (aheadCount != null && aheadCount < advanceThreshold) {
                    // 提前预警：前面还剩 aheadCount 位！
                    val eventKey = "${task.taskId}_PRECALL_${targetNum}_${aheadCount}"
                    val event = if (shouldTrigger(eventKey)) {
                        AlertEvent(
                            taskId = task.taskId,
                            kind = AlertKind.PRE_CALL_WARNING,
                            evidenceObservationIds = listOf(observation.observationId),
                            deduplicationKey = eventKey,
                            title = "即将叫号：前面还剩 ${aheadCount} 人！",
                            message = "您的目标号排在候诊前列，请提前起身前往 ${clinicTitle} 门前准备就诊。"
                        ).also {
                            alertHistory[eventKey] = System.currentTimeMillis()
                        }
                    } else null

                    return RuleResult(MatchResult.PRE_CALL_WARNING, event, aheadCount = aheadCount)
                } else {
                    // 常规候诊中
                    val eventKey = "${task.taskId}_WAITING_${targetNum}"
                    val event = if (shouldTrigger(eventKey)) {
                        AlertEvent(
                            taskId = task.taskId,
                            kind = AlertKind.POSSIBLE_MATCH,
                            evidenceObservationIds = listOf(observation.observationId),
                            deduplicationKey = eventKey,
                            title = "候诊排队中：包含 ${targetNum} 号",
                            message = "当前叫号为 ${fields.currentCallingNumber ?: "未知"}，请留意屏幕变化。"
                        ).also {
                            alertHistory[eventKey] = System.currentTimeMillis()
                        }
                    } else null

                    return RuleResult(MatchResult.POSSIBLE_MATCH, event, aheadCount = aheadCount)
                }
            }

            RegionSemantics.HISTORY_RECORD -> {
                val eventKey = "${task.taskId}_HISTORY_${targetNum}"
                val event = if (shouldTrigger(eventKey)) {
                    AlertEvent(
                        taskId = task.taskId,
                        kind = AlertKind.HISTORY_RECORD_SEEN,
                        evidenceObservationIds = listOf(observation.observationId),
                        deduplicationKey = eventKey,
                        title = "在历史叫号中看到 ${targetNum} 号",
                        message = "可能已过号或此前呼叫过，请向分诊台核对。"
                    ).also {
                        alertHistory[eventKey] = System.currentTimeMillis()
                    }
                } else null

                return RuleResult(MatchResult.NO_MATCH, event)
            }

            else -> {
                return RuleResult(MatchResult.NO_MATCH, null)
            }
        }
    }

    private fun shouldTrigger(key: String): Boolean {
        val lastTrigger = alertHistory[key] ?: return true
        return (System.currentTimeMillis() - lastTrigger) > deduplicationCooldownMs
    }

    fun clear() {
        alertHistory.clear()
    }
}
