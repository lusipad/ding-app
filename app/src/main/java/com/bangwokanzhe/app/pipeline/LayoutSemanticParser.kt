package com.bangwokanzhe.app.pipeline

import com.bangwokanzhe.app.model.ClinicCell
import com.bangwokanzhe.app.model.RecognizedFields
import com.bangwokanzhe.app.model.RegionSemantics
import java.util.regex.Pattern

/**
 * 屏幕布局与多科室空间语义解析器
 * 支持：
 * 1. 单诊室大屏解析
 * 2. 多科室/多诊室矩阵分屏解析（行级空间隔离，彻底防止跨科室误认）
 * 3. 候诊排队顺位计算（用于提前叫号预警）
 * 4. 显式过号栏与隐式翻号越界检测
 */
object LayoutSemanticParser {

    private val CLINIC_PATTERN = Pattern.compile("([\\u4e00-\\u9fa5A-Za-z0-9]{0,10}(?:号诊室|诊室[0-9A-Za-z#]+|[0-9A-Za-z#]+诊室|诊区[0-9A-Za-z]+|号窗口|窗口[0-9A-Za-z#]+|[0-9A-Za-z#]+窗口|采血室|超声[0-9A-Za-z#]*室|检查室|化验室))")
    private val DEPARTMENT_PATTERN = Pattern.compile("([\\u4e00-\\u9fa5]{2,8}(?:科门诊|内科|外科|儿科|妇科|眼科|耳鼻喉科|口腔科|骨科|皮科|超声科|放射科|检验科|药房|输液室|门诊))")

    private val CALLING_KEYWORDS = listOf("当前呼叫", "呼叫", "正在就诊", "就诊", "正在叫号", "正在检查", "检查", "请", "到诊室", "到窗口", "请前往")
    private val WAITING_KEYWORDS = listOf("候诊", "等候", "排队", "等待", "准备", "下一位")
    private val HISTORY_KEYWORDS = listOf("过号", "弃号", "历史", "已就诊", "已呼叫", "已过")

    private val NUMBER_TOKEN_PATTERN = Pattern.compile("([A-Za-z]{1,3}[-]?[0-9]{1,5}|[0-9]{2,5})(?:号)?")

    fun parse(rawLines: List<String>, targetClinic: String? = null, targetDepartment: String? = null): RecognizedFields {
        var globalDepartment: String? = null
        var globalClinic: String? = null
        var globalCallingNumber: String? = null
        val globalWaiting = mutableListOf<String>()
        val globalHistory = mutableListOf<String>()

        val cells = mutableListOf<ClinicCell>()

        var currentCellClinic: String? = null
        var currentCellDept: String? = null
        var currentCellCalling: String? = null
        val currentCellWaiting = mutableListOf<String>()
        val currentCellHistory = mutableListOf<String>()

        fun flushCell() {
            if (currentCellClinic != null) {
                cells.add(
                    ClinicCell(
                        department = currentCellDept ?: globalDepartment,
                        clinic = currentCellClinic!!,
                        currentCallingNumber = currentCellCalling,
                        waitingNumbers = currentCellWaiting.distinct(),
                        historyNumbers = currentCellHistory.distinct()
                    )
                )
                currentCellClinic = null
                currentCellDept = null
                currentCellCalling = null
                currentCellWaiting.clear()
                currentCellHistory.clear()
            }
        }

        for (line in rawLines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            var lineDept: String? = null
            val deptMatcher = DEPARTMENT_PATTERN.matcher(trimmed)
            if (deptMatcher.find()) {
                lineDept = deptMatcher.group(1)
                if (globalDepartment == null) globalDepartment = lineDept
            }

            val clinicMatcher = CLINIC_PATTERN.matcher(trimmed)
            if (clinicMatcher.find()) {
                val detectedClinic = clinicMatcher.group(1)
                if (currentCellClinic != null && currentCellClinic != detectedClinic) {
                    flushCell()
                }
                currentCellClinic = detectedClinic
                currentCellDept = lineDept ?: currentCellDept ?: globalDepartment
                if (globalClinic == null) globalClinic = detectedClinic
            }

            // 对单行按语义关键词进行区间段精准切分
            val (callNums, waitNums, histNums) = parseLineSegments(trimmed)

            if (currentCellClinic != null) {
                if (currentCellCalling == null && callNums.isNotEmpty()) {
                    currentCellCalling = callNums.first()
                }
                currentCellWaiting.addAll(waitNums)
                currentCellHistory.addAll(histNums)
            } else {
                if (globalCallingNumber == null && callNums.isNotEmpty()) {
                    globalCallingNumber = callNums.first()
                }
                globalWaiting.addAll(waitNums)
                globalHistory.addAll(histNums)
            }
        }

        flushCell()

        // 整合全局列表（包含所有诊室的号码集合）
        val allWaiting = (globalWaiting + cells.flatMap { it.waitingNumbers }).distinct()
        val allHistory = (globalHistory + cells.flatMap { it.historyNumbers }).distinct()

        return RecognizedFields(
            clinic = globalClinic,
            department = globalDepartment,
            currentCallingNumber = globalCallingNumber,
            waitingNumbers = allWaiting,
            historyNumbers = allHistory,
            cells = cells,
            rawTextLines = rawLines
        )
    }

    private data class SegmentResult(
        val callingNumbers: List<String>,
        val waitingNumbers: List<String>,
        val historyNumbers: List<String>
    )

    private fun parseLineSegments(text: String): SegmentResult {
        var callPos = -1
        for (kw in CALLING_KEYWORDS) {
            val idx = text.indexOf(kw)
            if (idx != -1 && (callPos == -1 || idx < callPos)) callPos = idx
        }

        var waitPos = -1
        for (kw in WAITING_KEYWORDS) {
            val idx = text.indexOf(kw)
            if (idx != -1 && (waitPos == -1 || idx < waitPos)) waitPos = idx
        }

        var histPos = -1
        for (kw in HISTORY_KEYWORDS) {
            val idx = text.indexOf(kw)
            if (idx != -1 && (histPos == -1 || idx < histPos)) histPos = idx
        }

        var callSegment = ""
        var waitSegment = ""
        var histSegment = ""

        if (callPos != -1) {
            val endCall = when {
                waitPos > callPos && histPos > callPos -> minOf(waitPos, histPos)
                waitPos > callPos -> waitPos
                histPos > callPos -> histPos
                else -> text.length
            }
            callSegment = text.substring(callPos, endCall)
        }

        if (waitPos != -1) {
            val endWait = if (histPos > waitPos) histPos else text.length
            waitSegment = text.substring(waitPos, endWait)
        }

        if (histPos != -1) {
            histSegment = text.substring(histPos)
        }

        if (callPos == -1 && waitPos == -1 && histPos == -1) {
            callSegment = text
        }

        return SegmentResult(
            callingNumbers = extractNumbers(callSegment),
            waitingNumbers = extractNumbers(waitSegment),
            historyNumbers = extractNumbers(histSegment)
        )
    }

    data class SemanticsEvaluation(
        val semantics: RegionSemantics,
        val matchedCell: ClinicCell? = null,
        val aheadCount: Int? = null,
        val isExplicitMissed: Boolean = false,
        val isImplicitMissed: Boolean = false
    )

    fun evaluateTargetSemantics(
        fields: RecognizedFields,
        targetClinic: String,
        targetDepartment: String?,
        targetNumber: String,
        advanceWarningCount: Int
    ): SemanticsEvaluation {
        val cleanTarget = normalizeNumber(targetNumber)

        // 1. 如果存在多诊室切分矩阵，优先匹配目标所属诊室单元格（彻底防止跨诊室误认）
        val targetCell = fields.cells.find { cell ->
            isClinicMatch(cell.clinic, targetClinic) &&
                    (targetDepartment == null || cell.department == null ||
                            cell.department.contains(targetDepartment) || targetDepartment.contains(cell.department))
        }

        if (targetCell != null) {
            return evaluateWithinCell(targetCell, cleanTarget, advanceWarningCount)
        }

        // 2. 若无单元格，核对全局字段
        val globalClinicMatch = fields.clinic == null || isClinicMatch(fields.clinic, targetClinic)
        if (!globalClinicMatch) {
            return SemanticsEvaluation(RegionSemantics.UNKNOWN)
        }

        // A. 正在叫号
        val callingNum = fields.currentCallingNumber?.let { normalizeNumber(it) }
        if (callingNum != null && isNumberMatch(callingNum, cleanTarget)) {
            return SemanticsEvaluation(RegionSemantics.CALLING_NOW)
        }

        // B. 显式过号栏
        if (fields.historyNumbers.any { isNumberMatch(normalizeNumber(it), cleanTarget) }) {
            return SemanticsEvaluation(RegionSemantics.OVER_CALLED, isExplicitMissed = true)
        }

        // C. 候诊列表
        val waitingIndex = fields.waitingNumbers.indexOfFirst { isNumberMatch(normalizeNumber(it), cleanTarget) }
        if (waitingIndex >= 0) {
            return SemanticsEvaluation(
                semantics = RegionSemantics.WAITING_LIST,
                aheadCount = waitingIndex
            )
        }

        // D. 隐式翻号越界过号
        if (callingNum != null && isNumericProgressExceeded(callingNum, cleanTarget)) {
            return SemanticsEvaluation(RegionSemantics.OVER_CALLED, isImplicitMissed = true)
        }

        return SemanticsEvaluation(RegionSemantics.UNKNOWN)
    }

    private fun evaluateWithinCell(
        cell: ClinicCell,
        cleanTarget: String,
        advanceWarningCount: Int
    ): SemanticsEvaluation {
        // A. 正在就诊栏命中
        val callingNum = cell.currentCallingNumber?.let { normalizeNumber(it) }
        if (callingNum != null && isNumberMatch(callingNum, cleanTarget)) {
            return SemanticsEvaluation(RegionSemantics.CALLING_NOW, matchedCell = cell)
        }

        // B. 该诊室过号/历史栏命中
        if (cell.historyNumbers.any { isNumberMatch(normalizeNumber(it), cleanTarget) }) {
            return SemanticsEvaluation(RegionSemantics.OVER_CALLED, matchedCell = cell, isExplicitMissed = true)
        }

        // C. 候诊列表排位检测
        val waitingIndex = cell.waitingNumbers.indexOfFirst { isNumberMatch(normalizeNumber(it), cleanTarget) }
        if (waitingIndex >= 0) {
            return SemanticsEvaluation(
                semantics = RegionSemantics.WAITING_LIST,
                matchedCell = cell,
                aheadCount = waitingIndex
            )
        }

        // D. 隐式越界过号
        if (callingNum != null && isNumericProgressExceeded(callingNum, cleanTarget)) {
            return SemanticsEvaluation(RegionSemantics.OVER_CALLED, matchedCell = cell, isImplicitMissed = true)
        }

        return SemanticsEvaluation(RegionSemantics.UNKNOWN, matchedCell = cell)
    }

    fun isClinicMatch(detected: String, target: String): Boolean {
        val d = detected.replace("#", "").replace("号", "").trim()
        val t = target.replace("#", "").replace("号", "").trim()
        return d.contains(t) || t.contains(d)
    }

    fun normalizeNumber(raw: String): String {
        return raw.trim()
            .replace("号", "")
            .replace(" ", "")
            .uppercase()
    }

    fun isNumberMatch(candidate: String, target: String): Boolean {
        val c = normalizeNumber(candidate)
        val t = normalizeNumber(target)
        if (c.equals(t, ignoreCase = true)) return true

        // 容错连字符：如 "B-045" 与 "B045"
        val cClean = c.replace("-", "")
        val tClean = t.replace("-", "")
        if (cClean.equals(tClean, ignoreCase = true)) return true

        // 容错前导零：如 "045" 与 "45", "A018" 与 "A18"
        val cPrefix = cClean.takeWhile { it.isLetter() }
        val tPrefix = tClean.takeWhile { it.isLetter() }
        if (cPrefix.equals(tPrefix, ignoreCase = true)) {
            val cDigits = cClean.dropWhile { it.isLetter() }.trimStart('0')
            val tDigits = tClean.dropWhile { it.isLetter() }.trimStart('0')
            if (cDigits.isNotEmpty() && cDigits == tDigits) return true
        }

        return false
    }

    fun isNumericProgressExceeded(currentCalling: String, target: String): Boolean {
        val normCurrent = normalizeNumber(currentCalling).replace("-", "")
        val normTarget = normalizeNumber(target).replace("-", "")
        val prefixCurrent = normCurrent.takeWhile { it.isLetter() }
        val prefixTarget = normTarget.takeWhile { it.isLetter() }
        if (!prefixCurrent.equals(prefixTarget, ignoreCase = true)) return false

        val currentDigits = normCurrent.dropWhile { it.isLetter() }
        val targetDigits = normTarget.dropWhile { it.isLetter() }
        val currentInt = currentDigits.toIntOrNull() ?: return false
        val targetInt = targetDigits.toIntOrNull() ?: return false
        val diff = currentInt - targetInt
        return diff in 1..35
    }

    private fun extractNumbers(text: String): List<String> {
        val result = mutableListOf<String>()
        val matcher = NUMBER_TOKEN_PATTERN.matcher(text)
        while (matcher.find()) {
            val num = matcher.group(1)
            if (num != null && num.length in 2..6) {
                result.add(num)
            }
        }
        return result
    }
}
