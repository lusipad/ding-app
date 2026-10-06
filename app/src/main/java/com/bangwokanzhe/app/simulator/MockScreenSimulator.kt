package com.bangwokanzhe.app.simulator

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.bangwokanzhe.app.BangWoKanzheApp
import com.bangwokanzhe.app.model.InfusionMetrics
import com.bangwokanzhe.app.model.Observation
import com.bangwokanzhe.app.model.QualityMetrics
import com.bangwokanzhe.app.model.RegionSemantics
import com.bangwokanzhe.app.model.TaskScenario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 模拟大屏生成器与回放工具
 * 支持：
 * 1. 单诊室翻号推进（候诊 -> 提前预警 -> 确认命中）
 * 2. 多科室多诊室表格矩阵（验证科室/诊室隔离，防止跨科室误认）
 * 3. 显式/隐式过号检测演练
 * 4. 输液低液位报警演练
 */
object MockScreenSimulator {

    data class MockScenarioStep(
        val department: String? = null,
        val clinic: String,
        val callingNumber: String,
        val waitingNumbers: List<String>,
        val historyNumbers: List<String>,
        val description: String,
        val isMultiClinicScreen: Boolean = false,
        val isInfusionStep: Boolean = false,
        val simulatedInfusionPercent: Float? = null,
        val autoSetTargetForScenario: com.bangwokanzhe.app.model.TargetSpec? = null
    )

    val defaultScenarios = listOf(
        // 场景 1：多科室多诊室分屏（其他科室也在叫128，但目标在3号诊室）
        MockScenarioStep(
            department = "儿科门诊",
            clinic = "1号诊室",
            callingNumber = "128", // 故意在1号诊室叫128，测试跨科室隔离！
            waitingNumbers = listOf("129", "130"),
            historyNumbers = emptyList(),
            description = "【多科室隔离测试】1号诊室叫128号（用户等3号诊室，应严格忽略不误报）",
            isMultiClinicScreen = true
        ),
        // 场景 2：3号诊室翻到126号，目标128号排在第2位（触发提前叫号预警！）
        MockScenarioStep(
            department = "综合门诊",
            clinic = "3号诊室",
            callingNumber = "126",
            waitingNumbers = listOf("127", "128", "129"), // 128 排在 index=1，前面仅1人
            historyNumbers = listOf("124", "125"),
            description = "【提前叫号预警】当前126号，目标128排在第2位（触发提前预警振动）"
        ),
        // 场景 3：3号诊室正式呼叫目标128号！
        MockScenarioStep(
            department = "综合门诊",
            clinic = "3号诊室",
            callingNumber = "128",
            waitingNumbers = listOf("129", "130", "131"),
            historyNumbers = listOf("126", "127"),
            description = "🎯【目标叫号命中】3号诊室 请 128 号就诊！（触发强振动与高优先级报警）"
        ),
        // 场景 4：显式过号栏检测（目标128号落入过号名单）
        MockScenarioStep(
            department = "综合门诊",
            clinic = "3号诊室",
            callingNumber = "132",
            waitingNumbers = listOf("133", "134"),
            historyNumbers = listOf("128", "129"), // 128 已进入过号列表
            description = "⚠️【显式过号强提醒】诊室叫到132号，目标128出现在过号栏（提示去分诊台）"
        ),
        // 场景 5：隐式跳号过号测试（大屏直接翻到138号，无显式过号栏但已大幅跳过目标）
        MockScenarioStep(
            department = "综合门诊",
            clinic = "3号诊室",
            callingNumber = "138",
            waitingNumbers = listOf("139", "140"),
            historyNumbers = emptyList(),
            description = "⚠️【隐式翻号越界】诊室已叫到138号，已大幅越过您的128号（自动触发隐式过号）"
        ),
        // 场景 6：西药房取药窗口大屏
        MockScenarioStep(
            department = "门诊药房",
            clinic = "3号取药窗口",
            callingNumber = "2058",
            waitingNumbers = listOf("2059", "2060"),
            historyNumbers = listOf("2055", "2056"),
            description = "💊【药房窗口叫号】3号取药窗口 请 2058 号取药（窗口类排队屏幕）",
            autoSetTargetForScenario = com.bangwokanzhe.app.model.TargetSpec(clinicId = "3号取药窗口", targetNumber = "2058", departmentName = "门诊药房")
        ),
        // 场景 7：采血室化验排队大屏
        MockScenarioStep(
            department = "检验科",
            clinic = "采血室2号窗口",
            callingNumber = "B018",
            waitingNumbers = listOf("B019", "B020"),
            historyNumbers = listOf("B016"),
            description = "🩸【采血化验叫号】采血室2号窗口 请 B018 号（字母流水号自适应识别）",
            autoSetTargetForScenario = com.bangwokanzhe.app.model.TargetSpec(clinicId = "采血室2号窗口", targetNumber = "B018", departmentName = "检验科")
        ),
        // 场景 8：诊室门牌电子液晶屏
        MockScenarioStep(
            department = "心血管内科",
            clinic = "专家1诊室",
            callingNumber = "A018",
            waitingNumbers = listOf("A019", "A020"),
            historyNumbers = listOf("A016"),
            description = "🩺【诊室门牌小屏】心血管内科 专家1诊室 主治医师:张主任 请 A018号",
            autoSetTargetForScenario = com.bangwokanzhe.app.model.TargetSpec(clinicId = "专家1诊室", targetNumber = "A018", departmentName = "心血管内科")
        ),
        // 场景 9：输液实验模块低液位报警（药液余量降至 10%）
        MockScenarioStep(
            clinic = "输液室A区",
            callingNumber = "0",
            waitingNumbers = emptyList(),
            historyNumbers = emptyList(),
            description = "🧪【输液低液位报警】液位降至 10%（低于 15% 阈值，触发换瓶警报）",
            isInfusionStep = true,
            simulatedInfusionPercent = 10f
        ),
        // 场景 10：输液平稳安全余量巡视（药液余量 55%）
        MockScenarioStep(
            clinic = "输液室A区",
            callingNumber = "0",
            waitingNumbers = emptyList(),
            historyNumbers = emptyList(),
            description = "🧪【输液正常余量】液位 55%（药液充裕，系统持续平稳静默看护）",
            isInfusionStep = true,
            simulatedInfusionPercent = 55f
        )
    )

    /**
     * 绘制模拟大屏图片
     */
    fun renderMockScreenBitmap(step: MockScenarioStep): Bitmap {
        val width = 800
        val height = 600
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (step.isInfusionStep) {
            // 绘制模拟输液瓶
            drawInfusionBottle(canvas, width, height, step.simulatedInfusionPercent ?: 50f)
            return bitmap
        }

        // 背景
        val bgPaint = Paint().apply { color = Color.rgb(18, 28, 48) }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 标题栏
        val titleBgPaint = Paint().apply { color = Color.rgb(30, 58, 102) }
        canvas.drawRect(0f, 0f, width.toFloat(), 85f, titleBgPaint)

        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 34f
            isAntiAlias = true
            isFakeBoldText = true
        }
        val headerTitle = if (step.isMultiClinicScreen) "门诊部 多科室矩阵叫号大屏" else "综合门诊部 智能排队叫号大屏"
        canvas.drawText(headerTitle, 40f, 56f, textPaint)

        if (step.isMultiClinicScreen) {
            // 绘制多诊室表格对比
            drawMultiClinicMatrix(canvas, width)
        } else {
            // 单诊室清晰卡片
            val cardPaint = Paint().apply { color = Color.rgb(25, 42, 77) }
            canvas.drawRect(30f, 105f, width - 30f, 310f, cardPaint)

            val clinicPaint = Paint().apply {
                color = Color.rgb(255, 204, 0)
                textSize = 32f
                isAntiAlias = true
                isFakeBoldText = true
            }
            canvas.drawText("【${step.clinic}】正在就诊", 50f, 155f, clinicPaint)

            val callNumberPaint = Paint().apply {
                color = Color.rgb(255, 77, 77)
                textSize = 66f
                isAntiAlias = true
                isFakeBoldText = true
            }
            canvas.drawText("请 ${step.callingNumber} 号 就诊", 50f, 245f, callNumberPaint)

            // 等候栏
            val waitBgPaint = Paint().apply { color = Color.rgb(25, 42, 77) }
            canvas.drawRect(30f, 330f, width - 30f, 430f, waitBgPaint)

            val waitPaint = Paint().apply {
                color = Color.rgb(140, 200, 255)
                textSize = 28f
                isAntiAlias = true
            }
            canvas.drawText("候诊等待: " + step.waitingNumbers.joinToString(", ") { "${it}号" }, 50f, 385f, waitPaint)

            // 历史记录栏
            val historyBgPaint = Paint().apply { color = Color.rgb(25, 42, 77) }
            canvas.drawRect(30f, 450f, width - 30f, 550f, historyBgPaint)

            val historyPaint = Paint().apply {
                color = Color.rgb(180, 180, 180)
                textSize = 26f
                isAntiAlias = true
            }
            canvas.drawText("过号/历史呼叫: " + step.historyNumbers.joinToString(", ") { "${it}号" }, 50f, 505f, historyPaint)
        }

        return bitmap
    }

    private fun drawMultiClinicMatrix(canvas: Canvas, width: Int) {
        val rowPaint = Paint().apply { color = Color.rgb(25, 42, 77) }
        val textHeader = Paint().apply { color = Color.rgb(255, 204, 0); textSize = 24f; isFakeBoldText = true }
        val textCalling = Paint().apply { color = Color.rgb(255, 100, 100); textSize = 26f; isFakeBoldText = true }
        val textWait = Paint().apply { color = Color.rgb(160, 210, 255); textSize = 22f }

        // 行1：1号诊室（正在叫 128号，但这是1号诊室！）
        canvas.drawRect(30f, 110f, width - 30f, 220f, rowPaint)
        canvas.drawText("儿科门诊 · 1号诊室", 50f, 150f, textHeader)
        canvas.drawText("当前呼叫: 128号", 300f, 150f, textCalling)
        canvas.drawText("候诊: 129号, 130号", 50f, 195f, textWait)

        // 行2：2号诊室
        canvas.drawRect(30f, 240f, width - 30f, 350f, rowPaint)
        canvas.drawText("内科门诊 · 2号诊室", 50f, 280f, textHeader)
        canvas.drawText("当前呼叫: 045号", 300f, 280f, textCalling)
        canvas.drawText("候诊: 046号, 047号", 50f, 325f, textWait)

        // 行3：3号诊室（用户正在等的诊室，当前呼叫 124号）
        canvas.drawRect(30f, 370f, width - 30f, 480f, rowPaint)
        canvas.drawText("外科门诊 · 3号诊室 (目标)", 50f, 410f, textHeader)
        canvas.drawText("当前呼叫: 124号", 300f, 410f, textCalling)
        canvas.drawText("候诊: 125号, 126号, 127号, 128号", 50f, 455f, textWait)
    }

    private fun drawInfusionBottle(canvas: Canvas, width: Int, height: Int, percent: Float) {
        val bgPaint = Paint().apply { color = Color.rgb(30, 35, 45) }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 瓶身外框
        val bottleStartX = width * 0.35f
        val bottleEndX = width * 0.65f
        val bottleTopY = height * 0.2f
        val bottleBottomY = height * 0.8f

        val glassPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawRoundRect(bottleStartX, bottleTopY, bottleEndX, bottleBottomY, 30f, 30f, glassPaint)

        // 液面高度
        val liquidHeight = (bottleBottomY - bottleTopY) * (percent / 100f)
        val meniscusY = bottleBottomY - liquidHeight

        val liquidPaint = Paint().apply {
            color = Color.rgb(60, 160, 240)
            style = Paint.Style.FILL
        }
        canvas.drawRect(bottleStartX + 4, meniscusY, bottleEndX - 4, bottleBottomY - 4, liquidPaint)

        // 警戒线标注
        val warningY = bottleBottomY - (bottleBottomY - bottleTopY) * 0.15f
        val linePaint = Paint().apply {
            color = Color.RED
            strokeWidth = 4f
        }
        canvas.drawLine(bottleStartX - 20, warningY, bottleEndX + 20, warningY, linePaint)

        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 28f
            isFakeBoldText = true
        }
        canvas.drawText("输液瓶液位仿真: ${percent.toInt()}%", 50f, 70f, textPaint)
        canvas.drawText("红色虚线为 15% 报警警戒线", 50f, 110f, Paint().apply { color = Color.RED; textSize = 22f })
    }

    suspend fun injectStep(step: MockScenarioStep) = withContext(Dispatchers.Default) {
        val app = BangWoKanzheApp.instance
        val task = if (step.autoSetTargetForScenario != null) {
            val targetedTask = com.bangwokanzhe.app.model.Task(targetSpec = step.autoSetTargetForScenario)
            app.taskStateManager.startTask(targetedTask)
            targetedTask
        } else {
            app.taskStateManager.state.value.task ?: run {
                val defaultTask = com.bangwokanzhe.app.model.Task(
                    targetSpec = com.bangwokanzhe.app.model.TargetSpec(
                        clinicId = "3号诊室",
                        targetNumber = "128"
                    )
                )
                app.taskStateManager.startTask(defaultTask)
                defaultTask
            }
        }

        if (step.isInfusionStep) {
            val level = step.simulatedInfusionPercent ?: 10f
            val isBelow = level <= (task.infusionSpec?.warningThresholdPercent ?: 15f)
            val obs = Observation(
                taskId = task.taskId,
                frameId = System.currentTimeMillis() % 10000,
                qualityMetrics = QualityMetrics(90f, 130f, true),
                recognizedFields = com.bangwokanzhe.app.model.RecognizedFields(),
                regionSemantics = RegionSemantics.UNKNOWN,
                infusionMetrics = InfusionMetrics(
                    estimatedLevelPercent = level,
                    confidence = 0.92f,
                    isMeniscusDetected = true,
                    isBelowWarningThreshold = isBelow
                )
            )
            val ruleResult = app.taskStateManager.onObservation(obs)

            // 同步刷新下拉通知卡片与悬浮胶囊
            app.notificationManager.updateServiceNotification(app.taskStateManager.state.value)
            app.floatingOverlayManager.updateState(app.taskStateManager.state.value)

            ruleResult?.alertEvent?.let { event ->
                app.notificationManager.showAlert(event)
            }
            return@withContext
        }

        val lines = if (step.isMultiClinicScreen) {
            listOf(
                "门诊部 多科室矩阵叫号系统",
                "儿科门诊 · 1号诊室 当前呼叫: 128号 候诊: 129号, 130号",
                "内科门诊 · 2号诊室 当前呼叫: 045号 候诊: 046号, 047号",
                "外科门诊 · 3号诊室 当前呼叫: 124号 候诊: 125号, 126号, 127号, 128号"
            )
        } else {
            val deptHeader = step.department?.let { "$it " } ?: "综合门诊部 "
            listOf(
                "${deptHeader}智能排队叫号系统",
                step.clinic,
                "正在就诊: 请 ${step.callingNumber} 号 就诊",
                "候诊等待: " + step.waitingNumbers.joinToString(", ") { "${it}号" },
                "过号/历史呼叫: " + step.historyNumbers.joinToString(", ") { "${it}号" }
            )
        }

        val observation = app.observationPipeline.processTextLines(lines, task)
        val ruleResult = app.taskStateManager.onObservation(observation)

        // 同步刷新下拉通知卡片与悬浮胶囊
        app.notificationManager.updateServiceNotification(app.taskStateManager.state.value)
        app.floatingOverlayManager.updateState(app.taskStateManager.state.value)

        ruleResult?.alertEvent?.let { event ->
            app.notificationManager.showAlert(event)
        }
    }
}
