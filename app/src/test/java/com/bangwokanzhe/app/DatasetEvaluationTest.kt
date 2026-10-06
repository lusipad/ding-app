package com.bangwokanzhe.app

import com.bangwokanzhe.app.engine.RuleEngine
import com.bangwokanzhe.app.model.*
import com.bangwokanzhe.app.pipeline.LayoutSemanticParser
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * 基于标准 Benchmark 测试集的端到端业务与语义评测套件
 * 对应设计文档 6 节验收标准：
 * - 覆盖单诊室、多科室矩阵、提前叫号、过号拦截、模糊拒绝与输液液位
 */
class DatasetEvaluationTest {

    private lateinit var ruleEngine: RuleEngine

    @Before
    fun setUp() {
        ruleEngine = RuleEngine()
    }

    @Test
    fun testBenchmarkCase1_SingleClinicConfirmedHit() {
        // 场景：单诊室清晰大屏正在呼叫 128号
        val task = Task(
            targetSpec = TargetSpec(clinicId = "3号诊室", targetNumber = "128")
        )
        val textLines = listOf(
            "综合门诊部 智能排队叫号系统",
            "【3号诊室】正在就诊",
            "请 128 号 到 3号诊室就诊",
            "候诊等待: 129号, 130号, 131号",
            "历史呼叫: 126号, 127号"
        )

        val fields = LayoutSemanticParser.parse(textLines, "3号诊室")
        val eval = LayoutSemanticParser.evaluateTargetSemantics(
            fields,
            targetClinic = "3号诊室",
            targetDepartment = null,
            targetNumber = "128",
            advanceWarningCount = 2
        )

        val obs = Observation(
            taskId = task.taskId,
            frameId = 101,
            qualityMetrics = QualityMetrics(95f, 130f, true),
            recognizedFields = fields,
            regionSemantics = eval.semantics
        )

        val result = ruleEngine.evaluate(task, obs)
        assertEquals(MatchResult.CONFIRMED_MATCH, result.matchResult)
        assertEquals(AlertKind.CONFIRMED_MATCH, result.alertEvent?.kind)
        assertTrue(result.alertEvent?.message?.contains("3号诊室") == true)
    }

    @Test
    fun testBenchmarkCase2_MultiClinicCrossIsolation() {
        // 场景：儿科1诊室正在叫 128号！用户等候的是外科3诊室（当前叫124号）
        // 核心验收：绝对不能因其他科室叫相同号码而产生跨诊室误报！
        val task = Task(
            targetSpec = TargetSpec(
                departmentName = "外科门诊",
                clinicId = "3号诊室",
                targetNumber = "128",
                advanceWarningCount = 2
            )
        )
        val textLines = listOf(
            "中心医院 门诊部多科室综合叫号大屏",
            "儿科门诊 · 1号诊室 当前呼叫: 128号 候诊: 129号, 130号",
            "消化内科 · 2号诊室 当前呼叫: 045号 候诊: 046号, 047号",
            "外科门诊 · 3号诊室 当前呼叫: 124号 候诊: 125号, 126号, 127号, 128号"
        )

        val fields = LayoutSemanticParser.parse(textLines, "3号诊室", "外科门诊")
        val eval = LayoutSemanticParser.evaluateTargetSemantics(
            fields,
            targetClinic = "3号诊室",
            targetDepartment = "外科门诊",
            targetNumber = "128",
            advanceWarningCount = 2
        )

        val obs = Observation(
            taskId = task.taskId,
            frameId = 102,
            qualityMetrics = QualityMetrics(90f, 130f, true),
            recognizedFields = fields,
            regionSemantics = eval.semantics
        )

        val result = ruleEngine.evaluate(task, obs)
        // 校验：绝不能命中儿科1诊室的 128号！
        assertNotEquals("严重错误：发生了跨科室误认！", MatchResult.CONFIRMED_MATCH, result.matchResult)
        assertEquals(RegionSemantics.WAITING_LIST, eval.semantics)
    }

    @Test
    fun testBenchmarkCase3_AdvancePreCallWarning() {
        // 场景：当前呼叫126号，目标128号排在候诊第2位（前面仅剩1人 <= advanceWarningCount=2）
        val task = Task(
            targetSpec = TargetSpec(
                clinicId = "3号诊室",
                targetNumber = "128",
                advanceWarningCount = 2
            )
        )
        val textLines = listOf(
            "眼科门诊 排队叫号系统",
            "【3号诊室】正在就诊",
            "请 126 号 到 3号诊室就诊",
            "候诊等待: 127号, 128号, 129号"
        )

        val fields = LayoutSemanticParser.parse(textLines, "3号诊室")
        val eval = LayoutSemanticParser.evaluateTargetSemantics(
            fields,
            targetClinic = "3号诊室",
            targetDepartment = null,
            targetNumber = "128",
            advanceWarningCount = 2
        )

        val obs = Observation(
            taskId = task.taskId,
            frameId = 103,
            qualityMetrics = QualityMetrics(90f, 130f, true),
            recognizedFields = fields,
            regionSemantics = eval.semantics
        )

        val result = ruleEngine.evaluate(task, obs)
        assertEquals(MatchResult.PRE_CALL_WARNING, result.matchResult)
        assertEquals(AlertKind.PRE_CALL_WARNING, result.alertEvent?.kind)
        assertEquals(1, result.aheadCount)
    }

    @Test
    fun testBenchmarkCase4_ExplicitOvercallDetection() {
        // 场景：大屏叫到135号，过号栏出现 128号
        val task = Task(
            targetSpec = TargetSpec(clinicId = "3号诊室", targetNumber = "128")
        )
        val textLines = listOf(
            "专家门诊 智能排队叫号系统",
            "【3号诊室】正在就诊 请 135 号 就诊",
            "【过号 / 弃号名单】请至分诊台刷卡激活",
            "过号人员: 122号, 128号, 131号"
        )

        val fields = LayoutSemanticParser.parse(textLines, "3号诊室")
        val eval = LayoutSemanticParser.evaluateTargetSemantics(
            fields,
            targetClinic = "3号诊室",
            targetDepartment = null,
            targetNumber = "128",
            advanceWarningCount = 2
        )

        val obs = Observation(
            taskId = task.taskId,
            frameId = 104,
            qualityMetrics = QualityMetrics(90f, 130f, true),
            recognizedFields = fields,
            regionSemantics = eval.semantics
        )

        val result = ruleEngine.evaluate(task, obs)
        assertEquals(MatchResult.MISSED_CALL, result.matchResult)
        assertEquals(AlertKind.MISSED_CALL, result.alertEvent?.kind)
        assertTrue(result.alertEvent?.title?.contains("已过号") == true)
    }

    @Test
    fun testBenchmarkCase5_QualityRejection() {
        val task = Task(
            targetSpec = TargetSpec(clinicId = "3号诊室", targetNumber = "128")
        )
        // 模拟严重模糊或反光未通过质量检查
        val blurryQuality = QualityMetrics(blurScore = 5.0f, brightness = 30f, isAcceptable = false)
        val obs = Observation(
            taskId = task.taskId,
            frameId = 105,
            qualityMetrics = blurryQuality,
            recognizedFields = RecognizedFields(),
            regionSemantics = RegionSemantics.UNKNOWN,
            rejectReason = RejectReason.BLURRY
        )

        val result = ruleEngine.evaluate(task, obs)
        assertEquals(MatchResult.UNKNOWN, result.matchResult)
        assertNull("模糊帧严禁产生任何误报警", result.alertEvent)
    }

    @Test
    fun testBenchmarkCase6_InfusionLowLevelAlarm() {
        val infusionTask = Task(
            scenario = TaskScenario.INFUSION_EXPERIMENT,
            targetSpec = TargetSpec(clinicId = "输液室", targetNumber = "0"),
            infusionSpec = InfusionSpec(warningThresholdPercent = 15f)
        )

        val obsLow = Observation(
            taskId = infusionTask.taskId,
            frameId = 106,
            qualityMetrics = QualityMetrics(95f, 140f, true),
            recognizedFields = RecognizedFields(),
            regionSemantics = RegionSemantics.UNKNOWN,
            infusionMetrics = InfusionMetrics(
                estimatedLevelPercent = 10f,
                confidence = 0.95f,
                isMeniscusDetected = true,
                isBelowWarningThreshold = true
            )
        )

        val result = ruleEngine.evaluate(infusionTask, obsLow)
        assertEquals(MatchResult.CONFIRMED_MATCH, result.matchResult)
        assertEquals(AlertKind.INFUSION_LOW_LEVEL, result.alertEvent?.kind)
        assertTrue(result.alertEvent?.message?.contains("换瓶") == true)
    }

    @Test
    fun testRealWorldClinicDoorScreen() {
        // 场景：真实医院二级诊室门口屏（电子门牌）实拍
        // 诊室门口小屏：医生姓名、科室诊室、当前就诊号、候诊下一位
        val task = Task(
            targetSpec = TargetSpec(
                departmentName = "心血管内科",
                clinicId = "专家1诊室",
                targetNumber = "A018",
                advanceWarningCount = 2
            )
        )
        val realScreenLines = listOf(
            "市第一人民医院 心血管内科",
            "专家1诊室 主治医师: 张主任",
            "正在就诊: A018号",
            "下一位准备: A019号",
            "候诊列表: A020号, A021号"
        )

        val fields = LayoutSemanticParser.parse(realScreenLines, "专家1诊室", "心血管内科")
        val eval = LayoutSemanticParser.evaluateTargetSemantics(
            fields,
            targetClinic = "专家1诊室",
            targetDepartment = "心血管内科",
            targetNumber = "A018",
            advanceWarningCount = 2
        )

        val obs = Observation(
            taskId = task.taskId,
            frameId = 201,
            qualityMetrics = QualityMetrics(95f, 150f, true),
            recognizedFields = fields,
            regionSemantics = eval.semantics
        )

        val result = ruleEngine.evaluate(task, obs)
        assertEquals(MatchResult.CONFIRMED_MATCH, result.matchResult)
        assertEquals(AlertKind.CONFIRMED_MATCH, result.alertEvent?.kind)
        assertTrue(result.alertEvent?.title?.contains("A018") == true)
    }

    @Test
    fun testRealWorldTriageHallScreen() {
        // 场景：真实门诊大厅综合排队大屏实拍
        val task = Task(
            targetSpec = TargetSpec(
                departmentName = "骨科门诊",
                clinicId = "骨科2诊室",
                targetNumber = "B045",
                advanceWarningCount = 2
            )
        )
        val realHallLines = listOf(
            "门诊综合分诊叫号中心",
            "骨科门诊 · 骨科1诊室 当前呼叫: B032号 候诊: B033号, B034号",
            "骨科门诊 · 骨科2诊室 当前呼叫: B042号 候诊: B043号, B044号, B045号",
            "皮科门诊 · 诊室1 当前呼叫: C011号 候诊: C012号"
        )

        val fields = LayoutSemanticParser.parse(realHallLines, "骨科2诊室", "骨科门诊")
        val eval = LayoutSemanticParser.evaluateTargetSemantics(
            fields,
            targetClinic = "骨科2诊室",
            targetDepartment = "骨科门诊",
            targetNumber = "B045",
            advanceWarningCount = 2
        )

        val obs = Observation(
            taskId = task.taskId,
            frameId = 202,
            qualityMetrics = QualityMetrics(92f, 135f, true),
            recognizedFields = fields,
            regionSemantics = eval.semantics
        )

        val result = ruleEngine.evaluate(task, obs)
        // B045 在骨科2诊室候诊排第3位 (前面有B043, B044 两人，aheadCount=2, 触发提前预警或候诊)
        assertNotNull(result)
        assertNotEquals(MatchResult.CONFIRMED_MATCH, result.matchResult)
    }

    @Test
    fun testBenchmarkCase8_PharmacyDispensingScreen() {
        val task = Task(
            targetSpec = TargetSpec(
                departmentName = "门诊药房",
                clinicId = "3号取药窗口",
                targetNumber = "2058"
            )
        )
        val textLines = listOf(
            "门诊综合西药房 自动发药系统",
            "3号取药窗口 请 2058 号 李* 到窗口取药",
            "准备取药: 2059号, 2060号"
        )
        val fields = LayoutSemanticParser.parse(textLines, "3号取药窗口", "门诊药房")
        val eval = LayoutSemanticParser.evaluateTargetSemantics(fields, "3号取药窗口", "门诊药房", "2058", 2)
        val obs = Observation(
            taskId = task.taskId,
            frameId = 301,
            qualityMetrics = QualityMetrics(95f, 140f, true),
            recognizedFields = fields,
            regionSemantics = eval.semantics
        )
        val result = ruleEngine.evaluate(task, obs)
        assertEquals(MatchResult.CONFIRMED_MATCH, result.matchResult)
        assertEquals(AlertKind.CONFIRMED_MATCH, result.alertEvent?.kind)
    }

    @Test
    fun testBenchmarkCase9_BloodDrawQueueScreen() {
        val task = Task(
            targetSpec = TargetSpec(
                departmentName = "检验科",
                clinicId = "采血室2号窗口",
                targetNumber = "B018"
            )
        )
        val textLines = listOf(
            "中心实验室 门诊采血排队叫号",
            "采血室2号窗口 当前呼叫: B018号",
            "候诊等待: B019号, B020号"
        )
        val fields = LayoutSemanticParser.parse(textLines, "采血室2号窗口", "检验科")
        val eval = LayoutSemanticParser.evaluateTargetSemantics(fields, "采血室2号窗口", "检验科", "B018", 2)
        val obs = Observation(
            taskId = task.taskId,
            frameId = 302,
            qualityMetrics = QualityMetrics(90f, 130f, true),
            recognizedFields = fields,
            regionSemantics = eval.semantics
        )
        val result = ruleEngine.evaluate(task, obs)
        assertEquals(MatchResult.CONFIRMED_MATCH, result.matchResult)
    }

    @Test
    fun testBenchmarkCase10_ImplicitOvercallWithAlphaPrefix() {
        val task = Task(
            targetSpec = TargetSpec(
                clinicId = "3号诊室",
                targetNumber = "A128"
            )
        )
        val textLines = listOf(
            "综合门诊部 智能排队叫号系统",
            "【3号诊室】正在就诊 请 A135 号 就诊",
            "候诊等待: A136号, A137号"
        )
        val fields = LayoutSemanticParser.parse(textLines, "3号诊室")
        val eval = LayoutSemanticParser.evaluateTargetSemantics(fields, "3号诊室", null, "A128", 2)
        val obs = Observation(
            taskId = task.taskId,
            frameId = 303,
            qualityMetrics = QualityMetrics(92f, 130f, true),
            recognizedFields = fields,
            regionSemantics = eval.semantics
        )
        val result = ruleEngine.evaluate(task, obs)
        assertEquals(MatchResult.MISSED_CALL, result.matchResult)
        assertEquals(AlertKind.MISSED_CALL, result.alertEvent?.kind)
    }

    @Test
    fun testBenchmarkCase11_ZeroPaddedEquivalentMatching() {
        val task = Task(
            targetSpec = TargetSpec(clinicId = "2号诊室", targetNumber = "8")
        )
        val textLines = listOf(
            "眼科门诊 排队叫号系统",
            "2号诊室 正在就诊: 请 008 号 就诊"
        )
        val fields = LayoutSemanticParser.parse(textLines, "2号诊室")
        val eval = LayoutSemanticParser.evaluateTargetSemantics(fields, "2号诊室", null, "8", 2)
        val obs = Observation(
            taskId = task.taskId,
            frameId = 304,
            qualityMetrics = QualityMetrics(95f, 140f, true),
            recognizedFields = fields,
            regionSemantics = eval.semantics
        )
        val result = ruleEngine.evaluate(task, obs)
        assertEquals(MatchResult.CONFIRMED_MATCH, result.matchResult)
    }

    @Test
    fun testBenchmarkCase12_HyphenatedTicketMatching() {
        val task = Task(
            targetSpec = TargetSpec(clinicId = "采血室1号窗口", targetNumber = "B045")
        )
        val textLines = listOf(
            "门诊化验室 智能采血排队系统",
            "采血室1号窗口 当前呼叫: B-045号"
        )
        val fields = LayoutSemanticParser.parse(textLines, "采血室1号窗口")
        val eval = LayoutSemanticParser.evaluateTargetSemantics(fields, "采血室1号窗口", null, "B045", 2)
        val obs = Observation(
            taskId = task.taskId,
            frameId = 305,
            qualityMetrics = QualityMetrics(95f, 140f, true),
            recognizedFields = fields,
            regionSemantics = eval.semantics
        )
        val result = ruleEngine.evaluate(task, obs)
        assertEquals(MatchResult.CONFIRMED_MATCH, result.matchResult)
    }

    @Test
    fun testBenchmarkCase13_HomographClinicDisambiguation() {
        // 内科一诊室 vs 内科二诊室：内科二诊室正在叫 102 号，但用户等的是内科一诊室（当前叫101，102排在候诊）
        val task = Task(
            targetSpec = TargetSpec(clinicId = "内科一诊室", targetNumber = "102", advanceWarningCount = 2)
        )
        val textLines = listOf(
            "门诊综合叫号屏",
            "内科门诊 · 内科一诊室 当前呼叫: 101号 候诊: 102号, 103号",
            "内科门诊 · 内科二诊室 当前呼叫: 102号 候诊: 104号"
        )
        val fields = LayoutSemanticParser.parse(textLines, "内科一诊室", "内科门诊")
        val eval = LayoutSemanticParser.evaluateTargetSemantics(fields, "内科一诊室", "内科门诊", "102", 2)
        val obs = Observation(
            taskId = task.taskId,
            frameId = 306,
            qualityMetrics = QualityMetrics(95f, 140f, true),
            recognizedFields = fields,
            regionSemantics = eval.semantics
        )
        val result = ruleEngine.evaluate(task, obs)
        // 绝不能因为内科二诊室叫102而提前报命中！必须是候诊预警
        assertNotEquals(MatchResult.CONFIRMED_MATCH, result.matchResult)
        assertEquals(MatchResult.PRE_CALL_WARNING, result.matchResult)
    }

    @Test
    fun testBenchmarkCase14_UltrasoundRadiologyQueueMatrix() {
        val task = Task(
            targetSpec = TargetSpec(clinicId = "超声1室", targetNumber = "U023")
        )
        val textLines = listOf(
            "超声医学科 检查叫号中心",
            "超声1室 正在检查: U023号 候诊: U024号",
            "超声2室 正在检查: U045号 候诊: U046号"
        )
        val fields = LayoutSemanticParser.parse(textLines, "超声1室", "超声医学科")
        val eval = LayoutSemanticParser.evaluateTargetSemantics(fields, "超声1室", "超声医学科", "U023", 2)
        val obs = Observation(
            taskId = task.taskId,
            frameId = 307,
            qualityMetrics = QualityMetrics(95f, 140f, true),
            recognizedFields = fields,
            regionSemantics = eval.semantics
        )
        val result = ruleEngine.evaluate(task, obs)
        assertEquals(MatchResult.CONFIRMED_MATCH, result.matchResult)
    }

    @Test
    fun testBenchmarkCase15_PediatricDoorScreenDesensitizedName() {
        val task = Task(
            targetSpec = TargetSpec(clinicId = "2号诊室", targetNumber = "062")
        )
        val textLines = listOf(
            "儿科门诊 · 2号诊室",
            "当前就诊: 062号 (王*涵)",
            "下一位准备: 063号 (张*诺)"
        )
        val fields = LayoutSemanticParser.parse(textLines, "2号诊室", "儿科门诊")
        val eval = LayoutSemanticParser.evaluateTargetSemantics(fields, "2号诊室", "儿科门诊", "062", 2)
        val obs = Observation(
            taskId = task.taskId,
            frameId = 308,
            qualityMetrics = QualityMetrics(95f, 140f, true),
            recognizedFields = fields,
            regionSemantics = eval.semantics
        )
        val result = ruleEngine.evaluate(task, obs)
        assertEquals(MatchResult.CONFIRMED_MATCH, result.matchResult)
    }

    @Test
    fun testBenchmarkCase16_InfusionSafeLevelQuietMonitoring() {
        val task = Task(
            scenario = TaskScenario.INFUSION_EXPERIMENT,
            targetSpec = TargetSpec(clinicId = "输液室", targetNumber = "0"),
            infusionSpec = InfusionSpec(warningThresholdPercent = 15f)
        )
        val obsSafe = Observation(
            taskId = task.taskId,
            frameId = 309,
            qualityMetrics = QualityMetrics(95f, 140f, true),
            recognizedFields = RecognizedFields(),
            regionSemantics = RegionSemantics.UNKNOWN,
            infusionMetrics = InfusionMetrics(
                estimatedLevelPercent = 60f,
                confidence = 0.94f,
                isMeniscusDetected = true,
                isBelowWarningThreshold = false
            )
        )
        val result = ruleEngine.evaluate(task, obsSafe)
        assertEquals(MatchResult.UNKNOWN, result.matchResult)
        assertNull("余量充裕时绝不触发告警", result.alertEvent)
    }

    @Test
    fun testBenchmarkCase17_InfusionBoundaryThresholdAlert() {
        val task = Task(
            scenario = TaskScenario.INFUSION_EXPERIMENT,
            targetSpec = TargetSpec(clinicId = "输液室", targetNumber = "0"),
            infusionSpec = InfusionSpec(warningThresholdPercent = 15f)
        )
        val obsThreshold = Observation(
            taskId = task.taskId,
            frameId = 310,
            qualityMetrics = QualityMetrics(95f, 140f, true),
            recognizedFields = RecognizedFields(),
            regionSemantics = RegionSemantics.UNKNOWN,
            infusionMetrics = InfusionMetrics(
                estimatedLevelPercent = 14.5f,
                confidence = 0.96f,
                isMeniscusDetected = true,
                isBelowWarningThreshold = true
            )
        )
        val result = ruleEngine.evaluate(task, obsThreshold)
        assertEquals(MatchResult.CONFIRMED_MATCH, result.matchResult)
        assertEquals(AlertKind.INFUSION_LOW_LEVEL, result.alertEvent?.kind)
    }

    @Test
    fun testBenchmarkCase18_DarkEnvironmentLowBrightnessRejection() {
        val task = Task(
            targetSpec = TargetSpec(clinicId = "3号诊室", targetNumber = "128")
        )
        val obsDark = Observation(
            taskId = task.taskId,
            frameId = 311,
            qualityMetrics = QualityMetrics(blurScore = 80f, brightness = 15f, isAcceptable = false),
            recognizedFields = RecognizedFields(),
            regionSemantics = RegionSemantics.UNKNOWN,
            rejectReason = RejectReason.LOW_CONFIDENCE
        )
        val result = ruleEngine.evaluate(task, obsDark)
        assertEquals(MatchResult.UNKNOWN, result.matchResult)
        assertNull(result.alertEvent)
    }

    @Test
    fun testBenchmarkCase19_RapidFrameThroughputBenchmark() {
        val task = Task(
            targetSpec = TargetSpec(clinicId = "3号诊室", targetNumber = "128")
        )
        val textLines = listOf(
            "综合门诊部 智能排队叫号系统",
            "【3号诊室】正在就诊 请 128 号 就诊",
            "候诊等待: 129号, 130号"
        )
        val startTime = System.currentTimeMillis()
        val iterations = 500
        for (i in 0 until iterations) {
            val fields = LayoutSemanticParser.parse(textLines, "3号诊室")
            val eval = LayoutSemanticParser.evaluateTargetSemantics(fields, "3号诊室", null, "128", 2)
            val obs = Observation(
                taskId = task.taskId,
                frameId = i.toLong(),
                qualityMetrics = QualityMetrics(95f, 140f, true),
                recognizedFields = fields,
                regionSemantics = eval.semantics
            )
            ruleEngine.evaluate(task, obs)
        }
        val duration = System.currentTimeMillis() - startTime
        val avgLatencyMs = duration.toDouble() / iterations
        assertTrue("单次全流程离线语义与规则解析耗时必须小于 3ms (实际: ${avgLatencyMs}ms)", avgLatencyMs < 3.0)
    }
}
