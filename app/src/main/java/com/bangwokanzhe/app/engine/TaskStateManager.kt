package com.bangwokanzhe.app.engine

import com.bangwokanzhe.app.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 任务与全局监看状态管理器
 * 统一管理生命周期 (5 状态)、新鲜度 (3 状态)、匹配判定 (4 状态)
 */
class TaskStateManager(
    private val ruleEngine: RuleEngine = RuleEngine()
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _state = MutableStateFlow(MonitorState())
    val state: StateFlow<MonitorState> = _state.asStateFlow()

    private val _recentObservations = MutableStateFlow<List<Observation>>(emptyList())
    val recentObservations: StateFlow<List<Observation>> = _recentObservations.asStateFlow()

    private var lastValidMonotonicTime: Long? = null
    private val freshnessWindowMs = 60_000L // 60 秒新鲜度阈值

    private var freshnessCheckJob: Job? = null

    init {
        startFreshnessCheckLoop()
    }

    fun startTask(task: Task) {
        ruleEngine.clear()
        lastValidMonotonicTime = null
        _recentObservations.value = emptyList()
        _state.update {
            MonitorState(
                task = task,
                lifecycle = TaskLifecycle.RUNNING,
                freshness = ObservationFreshness.NEVER_SEEN,
                matchResult = MatchResult.UNKNOWN,
                lastObservation = null,
                lastAlertEvent = null,
                errorMessage = null,
                totalFramesProcessed = 0,
                validObservationCount = 0
            )
        }
    }

    fun onObservation(observation: Observation): RuleEngine.RuleResult? {
        val currentTask = _state.value.task ?: return null
        if (_state.value.lifecycle != TaskLifecycle.RUNNING) return null

        val ruleResult = ruleEngine.evaluate(currentTask, observation)

        if (observation.isValid) {
            lastValidMonotonicTime = observation.monotonicTimeMs
        }

        // 保存最近证据列表（最多20条）
        _recentObservations.update { list ->
            (listOf(observation) + list).take(20)
        }

        val nowMonotonic = System.nanoTime() / 1_000_000L
        val freshness = calculateFreshness(nowMonotonic)

        _state.update { prev ->
            prev.copy(
                freshness = freshness,
                matchResult = if (observation.isValid) ruleResult.matchResult else prev.matchResult,
                lastObservation = if (observation.isValid) observation else prev.lastObservation,
                lastAlertEvent = ruleResult.alertEvent ?: prev.lastAlertEvent,
                waitingAheadCount = ruleResult.aheadCount ?: prev.waitingAheadCount,
                isMissedCall = ruleResult.matchResult == MatchResult.MISSED_CALL,
                infusionLevelPercent = observation.infusionMetrics?.estimatedLevelPercent ?: prev.infusionLevelPercent,
                totalFramesProcessed = prev.totalFramesProcessed + 1,
                validObservationCount = if (observation.isValid) prev.validObservationCount + 1 else prev.validObservationCount
            )
        }

        return ruleResult
    }

    fun pauseTask() {
        _state.update { it.copy(lifecycle = TaskLifecycle.PAUSED) }
    }

    fun resumeTask() {
        if (_state.value.task != null) {
            _state.update { it.copy(lifecycle = TaskLifecycle.RUNNING, errorMessage = null) }
        }
    }

    fun setBlocked(reason: String) {
        _state.update {
            it.copy(
                lifecycle = TaskLifecycle.BLOCKED,
                errorMessage = reason
            )
        }
    }

    fun endTask() {
        _state.update {
            it.copy(
                lifecycle = TaskLifecycle.ENDED
            )
        }
    }

    fun acknowledgeAlert() {
        _state.update { prev ->
            prev.copy(
                lastAlertEvent = prev.lastAlertEvent?.copy(acknowledgedAt = System.currentTimeMillis())
            )
        }
    }

    private fun calculateFreshness(nowMonotonic: Long): ObservationFreshness {
        val lastTime = lastValidMonotonicTime ?: return ObservationFreshness.NEVER_SEEN
        return if (nowMonotonic - lastTime <= freshnessWindowMs) {
            ObservationFreshness.FRESH
        } else {
            ObservationFreshness.STALE
        }
    }

    private fun startFreshnessCheckLoop() {
        freshnessCheckJob?.cancel()
        freshnessCheckJob = scope.launch {
            while (isActive) {
                delay(3000)
                if (_state.value.lifecycle == TaskLifecycle.RUNNING) {
                    val freshness = calculateFreshness(System.nanoTime() / 1_000_000L)
                    if (freshness != _state.value.freshness) {
                        _state.update { it.copy(freshness = freshness) }
                    }
                }
            }
        }
    }

    fun destroy() {
        freshnessCheckJob?.cancel()
        scope.cancel()
    }
}
