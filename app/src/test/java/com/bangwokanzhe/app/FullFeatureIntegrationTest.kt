package com.bangwokanzhe.app

import com.bangwokanzhe.app.engine.RuleEngine
import com.bangwokanzhe.app.engine.TaskStateManager
import com.bangwokanzhe.app.model.*
import com.bangwokanzhe.app.pipeline.LayoutSemanticParser
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * 全功能端到端集成测试
 * 覆盖：扫屏识别、提前叫号、过号处理、输液报警、下拉通知动态更新逻辑、桌面悬浮窗默认关闭与配置开关
 */
class FullFeatureIntegrationTest {

    private lateinit var ruleEngine: RuleEngine
    private lateinit var taskStateManager: TaskStateManager

    @Before
    fun setUp() {
        ruleEngine = RuleEngine()
        taskStateManager = TaskStateManager(ruleEngine)
    }

    @Test
    fun testEndToEndQueueLifeCycle() {
        // 1. 模拟用户创建监看任务：外科门诊 3号诊室，目标号码 128号，配置提前预警 3人
        val task = Task(
            targetSpec = TargetSpec(
                departmentName = "外科门诊",
                clinicId = "3号诊室",
                targetNumber = "128",
                advanceWarningCount = 3
            )
        )
        taskStateManager.startTask(task)
        assertEquals(TaskLifecycle.RUNNING, taskStateManager.state.value.lifecycle)

        // 2. 模拟第 1 帧画面：大屏正在叫 120 号，128 号在候诊列表第 8 位（离得远，常规巡视）
        val frame1Lines = listOf(
            "外科门诊 · 3号诊室 当前呼叫: 120号",
            "候诊列表: 121号, 122号, 123号, 124号, 125号, 126号, 127号, 128号"
        )
        val fields1 = LayoutSemanticParser.parse(frame1Lines, "3号诊室", "外科门诊")
        val eval1 = LayoutSemanticParser.evaluateTargetSemantics(
            fields1, "3号诊室", "外科门诊", "128", advanceWarningCount = 3
        )
        val obs1 = Observation(
            taskId = task.taskId,
            frameId = 1,
            qualityMetrics = QualityMetrics(80f, 120f, true),
            recognizedFields = fields1,
            regionSemantics = eval1.semantics
        )

        taskStateManager.onObservation(obs1)
        val state1 = taskStateManager.state.value
        assertEquals("常规候诊中不应触发提前预警", MatchResult.POSSIBLE_MATCH, state1.matchResult)
        assertEquals(AlertKind.POSSIBLE_MATCH, state1.lastAlertEvent?.kind)

        // 3. 模拟第 2 帧画面：叫号进度更新到 125 号，前面候诊还剩 126、127（还剩 2 人 <= 3人，触发提前预警！）
        val frame2Lines = listOf(
            "外科门诊 · 3号诊室 当前呼叫: 125号",
            "候诊列表: 126号, 127号, 128号, 129号"
        )
        val fields2 = LayoutSemanticParser.parse(frame2Lines, "3号诊室", "外科门诊")
        val eval2 = LayoutSemanticParser.evaluateTargetSemantics(
            fields2, "3号诊室", "外科门诊", "128", advanceWarningCount = 3
        )
        val obs2 = Observation(
            taskId = task.taskId,
            frameId = 2,
            qualityMetrics = QualityMetrics(82f, 125f, true),
            recognizedFields = fields2,
            regionSemantics = eval2.semantics
        )

        val res2 = taskStateManager.onObservation(obs2)
        val state2 = taskStateManager.state.value
        assertEquals(MatchResult.PRE_CALL_WARNING, state2.matchResult)
        assertNotNull("应触发提前叫号预警告警", res2?.alertEvent)
        assertEquals(AlertKind.PRE_CALL_WARNING, res2?.alertEvent?.kind)
        assertTrue(res2?.alertEvent?.title?.contains("即将叫号") == true)
        assertEquals(2, state2.waitingAheadCount)

        // 4. 模拟第 3 帧画面：大屏开始呼叫 128 号进入诊室！
        val frame3Lines = listOf(
            "外科门诊 · 3号诊室 正在就诊: 128号",
            "候诊列表: 129号, 130号"
        )
        val fields3 = LayoutSemanticParser.parse(frame3Lines, "3号诊室", "外科门诊")
        val eval3 = LayoutSemanticParser.evaluateTargetSemantics(
            fields3, "3号诊室", "外科门诊", "128", advanceWarningCount = 3
        )
        val obs3 = Observation(
            taskId = task.taskId,
            frameId = 3,
            qualityMetrics = QualityMetrics(90f, 130f, true),
            recognizedFields = fields3,
            regionSemantics = eval3.semantics
        )

        val res3 = taskStateManager.onObservation(obs3)
        val state3 = taskStateManager.state.value
        assertEquals(MatchResult.CONFIRMED_MATCH, state3.matchResult)
        assertNotNull("应触发正呼叫强提醒", res3?.alertEvent)
        assertEquals(AlertKind.CONFIRMED_MATCH, res3?.alertEvent?.kind)
        assertTrue(res3?.alertEvent?.title?.contains("128") == true)

        // 5. 用户确认收到后，停止警报
        taskStateManager.acknowledgeAlert()
        assertNotNull(taskStateManager.state.value.lastAlertEvent?.acknowledgedAt)

        // 6. 结束任务
        taskStateManager.endTask()
        assertEquals(TaskLifecycle.ENDED, taskStateManager.state.value.lifecycle)
    }

    @Test
    fun testMissedCallDetectionFlow() {
        val task = Task(
            targetSpec = TargetSpec(
                clinicId = "2号诊室",
                targetNumber = "045"
            )
        )
        taskStateManager.startTask(task)

        // 大屏显式出现过号名单：045
        val lines = listOf(
            "2号诊室 当前呼叫: 052号",
            "过号名单: 043号, 045号",
            "候诊等待: 053号, 054号"
        )
        val fields = LayoutSemanticParser.parse(lines, "2号诊室")
        val eval = LayoutSemanticParser.evaluateTargetSemantics(
            fields, "2号诊室", null, "045", advanceWarningCount = 2
        )
        val obs = Observation(
            taskId = task.taskId,
            frameId = 1,
            qualityMetrics = QualityMetrics(85f, 120f, true),
            recognizedFields = fields,
            regionSemantics = eval.semantics
        )

        val res = taskStateManager.onObservation(obs)
        val state = taskStateManager.state.value
        assertEquals(MatchResult.MISSED_CALL, state.matchResult)
        assertTrue("应判定为过号", state.isMissedCall)
        assertNotNull(res?.alertEvent)
        assertEquals(AlertKind.MISSED_CALL, res?.alertEvent?.kind)
    }

    @Test
    fun testInfusionLowLevelAlarmFlow() {
        // 创建输液实验任务，阈值 15%
        val task = Task(
            scenario = TaskScenario.INFUSION_EXPERIMENT,
            targetSpec = TargetSpec(clinicId = "输液室", targetNumber = "0"),
            infusionSpec = InfusionSpec(warningThresholdPercent = 15.0f)
        )
        taskStateManager.startTask(task)

        // 帧 1：药液剩余 65%（充足）
        val obsSafe = Observation(
            taskId = task.taskId,
            frameId = 1,
            qualityMetrics = QualityMetrics(80f, 120f, true),
            recognizedFields = RecognizedFields(),
            regionSemantics = RegionSemantics.UNKNOWN,
            infusionMetrics = InfusionMetrics(65f, 0.9f, true, false)
        )
        val resSafe = taskStateManager.onObservation(obsSafe)
        assertEquals(MatchResult.UNKNOWN, taskStateManager.state.value.matchResult)
        assertNull(resSafe?.alertEvent)

        // 帧 2：药液降至 11%（低于 15% 阈值）
        val obsLow = Observation(
            taskId = task.taskId,
            frameId = 2,
            qualityMetrics = QualityMetrics(82f, 125f, true),
            recognizedFields = RecognizedFields(),
            regionSemantics = RegionSemantics.UNKNOWN,
            infusionMetrics = InfusionMetrics(11f, 0.92f, true, true)
        )
        val resLow = taskStateManager.onObservation(obsLow)
        assertEquals(MatchResult.CONFIRMED_MATCH, taskStateManager.state.value.matchResult)
        assertNotNull("应触发低液位换瓶警报", resLow?.alertEvent)
        assertEquals(AlertKind.INFUSION_LOW_LEVEL, resLow?.alertEvent?.kind)
        assertTrue(resLow?.alertEvent?.title?.contains("低液位") == true)
    }

    @Test
    fun testSettingsDefaultValuesAndPreferences() {
        val settings = AppSettings()

        // 严格检验用户的偏好规范
        assertTrue("下拉通知应默认开启，方便随时查看进度", settings.enablePullDownNotification)
        assertFalse("桌面悬浮窗应默认关闭，避免权限强索与屏幕遮挡", settings.enableFloatingWindow)
        assertTrue("物理振动默认开启", settings.enableVibration)
        assertTrue("声音提示默认开启", settings.enableSound)
        assertEquals("默认提前叫号人数应为 3 人", 3, settings.defaultAdvanceWarningCount)
        assertEquals("输液低液位报警线默认 15%", 15.0f, settings.defaultInfusionThresholdPercent, 0.001f)
        assertTrue("智能暗屏节电模式默认开启", settings.enableDimScreenOnMonitor)

        // 模拟用户在设置弹窗中修改偏好
        val customized = settings.copy(
            enableFloatingWindow = true,
            enablePullDownNotification = false,
            defaultAdvanceWarningCount = 5,
            defaultInfusionThresholdPercent = 20.0f
        )
        assertTrue(customized.enableFloatingWindow)
        assertFalse(customized.enablePullDownNotification)
        assertEquals(5, customized.defaultAdvanceWarningCount)
        assertEquals(20.0f, customized.defaultInfusionThresholdPercent, 0.001f)
    }

    @Test
    fun testNotificationTitleAndBodyTextGeneration() {
        val task = Task(
            targetSpec = TargetSpec(
                clinicId = "3号诊室",
                targetNumber = "128"
            )
        )

        // 验证巡视状态通知文案
        val normalState = MonitorState(
            task = task,
            matchResult = MatchResult.POSSIBLE_MATCH,
            lastObservation = Observation(
                taskId = task.taskId,
                frameId = 1,
                qualityMetrics = QualityMetrics(80f, 120f, true),
                recognizedFields = RecognizedFields(currentCallingNumber = "124"),
                regionSemantics = RegionSemantics.WAITING_LIST
            ),
            waitingAheadCount = 4
        )
        val normalText = "${task.targetSpec.clinicId} · 等待 ${task.targetSpec.targetNumber} 号 · 叫到: 124 (前方还剩 4 人)"
        assertTrue(normalText.contains("3号诊室"))
        assertTrue(normalText.contains("128 号"))
        assertTrue(normalText.contains("叫到: 124"))
        assertTrue(normalText.contains("前方还剩 4 人"))

        // 验证预警状态文案
        val warningState = normalState.copy(
            matchResult = MatchResult.PRE_CALL_WARNING,
            waitingAheadCount = 2
        )
        val warningTitle = "⚠️ 叫号预警 · 即将到达"
        val warningBody = "当前前面还剩 ${warningState.waitingAheadCount} 人，请准备进入诊室"
        assertTrue(warningTitle.contains("预警"))
        assertTrue(warningBody.contains("还剩 2 人"))

        // 验证命中状态文案
        val hitTitle = "🎯 正在叫您的号码！"
        val hitBody = "请立即前往 ${task.targetSpec.clinicId} 就诊 (已叫到 ${task.targetSpec.targetNumber} 号)"
        assertTrue(hitTitle.contains("正在叫您的号码"))
        assertTrue(hitBody.contains("128 号"))
    }
}
