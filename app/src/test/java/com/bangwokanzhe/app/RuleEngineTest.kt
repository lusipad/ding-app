package com.bangwokanzhe.app

import com.bangwokanzhe.app.engine.RuleEngine
import com.bangwokanzhe.app.model.*
import com.bangwokanzhe.app.pipeline.LayoutSemanticParser
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RuleEngineTest {

    private lateinit var ruleEngine: RuleEngine
    private lateinit var sampleTask: Task

    @Before
    fun setUp() {
        ruleEngine = RuleEngine()
        sampleTask = Task(
            targetSpec = TargetSpec(
                departmentName = "外科门诊",
                clinicId = "3号诊室",
                targetNumber = "128",
                advanceWarningCount = 2
            )
        )
    }

    @Test
    fun testStrictNumberMatching() {
        // 目标是 128
        assertTrue(LayoutSemanticParser.isNumberMatch("128", "128"))
        assertTrue(LayoutSemanticParser.isNumberMatch("0128", "128"))
        assertTrue(LayoutSemanticParser.isNumberMatch("128", "0128"))

        // 禁止子串与超集匹配
        assertFalse(LayoutSemanticParser.isNumberMatch("1128", "128"))
        assertFalse(LayoutSemanticParser.isNumberMatch("28", "128"))
        assertFalse(LayoutSemanticParser.isNumberMatch("1281", "128"))
    }

    @Test
    fun testMultiClinicIsolationPreventsCrossClinicFalseAlarm() {
        // 大屏上：1号诊室正在叫 128号！但用户目标在 3号诊室（当前叫124号，128在等候排队中）
        val lines = listOf(
            "门诊部 多科室矩阵大屏",
            "儿科门诊 · 1号诊室 当前呼叫: 128号 候诊: 129号",
            "内科门诊 · 2号诊室 当前呼叫: 045号 候诊: 046号",
            "外科门诊 · 3号诊室 当前呼叫: 124号 候诊: 125号, 126号, 127号, 128号"
        )
        val fields = LayoutSemanticParser.parse(lines, "3号诊室", "外科门诊")
        val eval = LayoutSemanticParser.evaluateTargetSemantics(
            fields,
            targetClinic = "3号诊室",
            targetDepartment = "外科门诊",
            targetNumber = "128",
            advanceWarningCount = 2
        )

        val obs = Observation(
            taskId = sampleTask.taskId,
            frameId = 1,
            qualityMetrics = QualityMetrics(85f, 120f, true),
            recognizedFields = fields,
            regionSemantics = eval.semantics
        )

        val result = ruleEngine.evaluate(sampleTask, obs)
        // 必须严格杜绝 1号诊室的 128 触发 3号诊室的确认命中！
        assertNotEquals(MatchResult.CONFIRMED_MATCH, result.matchResult)
        assertEquals(RegionSemantics.WAITING_LIST, eval.semantics)
    }

    @Test
    fun testAdvancePreCallWarning() {
        // 3号诊室当前叫126号，候诊名单中 127号排第1位，目标128号排第2位（前面仅剩1人 <= advanceWarningCount=2）
        val lines = listOf(
            "外科门诊 3号诊室",
            "当前呼叫: 126号",
            "候诊等待: 127号, 128号, 129号"
        )
        val fields = LayoutSemanticParser.parse(lines, "3号诊室", "外科门诊")
        val eval = LayoutSemanticParser.evaluateTargetSemantics(
            fields,
            targetClinic = "3号诊室",
            targetDepartment = "外科门诊",
            targetNumber = "128",
            advanceWarningCount = 2
        )

        val obs = Observation(
            taskId = sampleTask.taskId,
            frameId = 2,
            qualityMetrics = QualityMetrics(85f, 120f, true),
            recognizedFields = fields,
            regionSemantics = eval.semantics
        )

        val result = ruleEngine.evaluate(sampleTask, obs)
        assertEquals(MatchResult.PRE_CALL_WARNING, result.matchResult)
        assertNotNull(result.alertEvent)
        assertEquals(AlertKind.PRE_CALL_WARNING, result.alertEvent?.kind)
        assertTrue(result.alertEvent?.title?.contains("即将叫号") == true)
    }

    @Test
    fun testCallingNowTriggersConfirmedMatch() {
        val lines = listOf(
            "外科门诊 3号诊室",
            "正在就诊: 请 128 号 到 3号诊室就诊",
            "候诊等待: 129号, 130号"
        )
        val fields = LayoutSemanticParser.parse(lines, "3号诊室", "外科门诊")
        val eval = LayoutSemanticParser.evaluateTargetSemantics(
            fields,
            targetClinic = "3号诊室",
            targetDepartment = "外科门诊",
            targetNumber = "128",
            advanceWarningCount = 2
        )

        val obs = Observation(
            taskId = sampleTask.taskId,
            frameId = 3,
            qualityMetrics = QualityMetrics(85f, 120f, true),
            recognizedFields = fields,
            regionSemantics = eval.semantics
        )

        val result = ruleEngine.evaluate(sampleTask, obs)
        assertEquals(MatchResult.CONFIRMED_MATCH, result.matchResult)
        assertNotNull(result.alertEvent)
        assertEquals(AlertKind.CONFIRMED_MATCH, result.alertEvent?.kind)
        assertTrue(result.alertEvent?.title?.contains("128") == true)
    }

    @Test
    fun testExplicitAndImplicitOvercallDetection() {
        // A. 显式过号栏
        val explicitLines = listOf(
            "3号诊室 当前呼叫: 130号",
            "过号列表: 128号, 129号"
        )
        val fieldsExplicit = LayoutSemanticParser.parse(explicitLines, "3号诊室")
        val evalExplicit = LayoutSemanticParser.evaluateTargetSemantics(
            fieldsExplicit,
            targetClinic = "3号诊室",
            targetDepartment = null,
            targetNumber = "128",
            advanceWarningCount = 2
        )
        val obsExplicit = Observation(
            taskId = sampleTask.taskId,
            frameId = 4,
            qualityMetrics = QualityMetrics(85f, 120f, true),
            recognizedFields = fieldsExplicit,
            regionSemantics = evalExplicit.semantics
        )
        val resExplicit = ruleEngine.evaluate(sampleTask, obsExplicit)
        assertEquals(MatchResult.MISSED_CALL, resExplicit.matchResult)
        assertEquals(AlertKind.MISSED_CALL, resExplicit.alertEvent?.kind)

        // B. 隐式翻号越界（诊室已叫到 132号，用户是 128号，候诊已无该号）
        ruleEngine.clear()
        val implicitLines = listOf(
            "3号诊室 正在就诊: 请 132 号就诊",
            "候诊: 133号, 134号"
        )
        val fieldsImplicit = LayoutSemanticParser.parse(implicitLines, "3号诊室")
        val evalImplicit = LayoutSemanticParser.evaluateTargetSemantics(
            fieldsImplicit,
            targetClinic = "3号诊室",
            targetDepartment = null,
            targetNumber = "128",
            advanceWarningCount = 2
        )
        val obsImplicit = Observation(
            taskId = sampleTask.taskId,
            frameId = 5,
            qualityMetrics = QualityMetrics(85f, 120f, true),
            recognizedFields = fieldsImplicit,
            regionSemantics = evalImplicit.semantics
        )
        val resImplicit = ruleEngine.evaluate(sampleTask, obsImplicit)
        assertEquals(MatchResult.MISSED_CALL, resImplicit.matchResult)
        assertEquals(AlertKind.MISSED_CALL, resImplicit.alertEvent?.kind)
    }

    @Test
    fun testInfusionLowLevelAlarm() {
        val infusionTask = Task(
            scenario = TaskScenario.INFUSION_EXPERIMENT,
            targetSpec = TargetSpec(clinicId = "输液室", targetNumber = "0"),
            infusionSpec = InfusionSpec(warningThresholdPercent = 15f)
        )

        val obsLow = Observation(
            taskId = infusionTask.taskId,
            frameId = 6,
            qualityMetrics = QualityMetrics(90f, 120f, true),
            recognizedFields = RecognizedFields(),
            regionSemantics = RegionSemantics.UNKNOWN,
            infusionMetrics = InfusionMetrics(
                estimatedLevelPercent = 10f, // 10% <= 15%
                confidence = 0.9f,
                isMeniscusDetected = true,
                isBelowWarningThreshold = true
            )
        )

        val result = ruleEngine.evaluate(infusionTask, obsLow)
        assertEquals(MatchResult.CONFIRMED_MATCH, result.matchResult)
        assertNotNull(result.alertEvent)
        assertEquals(AlertKind.INFUSION_LOW_LEVEL, result.alertEvent?.kind)
        assertTrue(result.alertEvent?.message?.contains("换瓶") == true)
    }
}
