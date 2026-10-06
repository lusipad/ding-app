package com.bangwokanzhe.app.pipeline

import android.graphics.Rect
import com.google.mlkit.vision.text.Text
import java.util.regex.Pattern

/**
 * 屏幕取景即时自动识别助手
 * 从 Camera 画面中实时自动框选诊室、当前叫号与候选排队号码，让用户零输入、一键点选创建任务
 */
object ScreenScannerHelper {

    data class DetectedClinicTarget(
        val id: String,
        val department: String? = null,
        val clinic: String,
        val currentCalling: String? = null,
        val candidateWaitingNumbers: List<String> = emptyList(),
        val boundingBox: Rect? = null
    )

    private val CLINIC_PATTERN = Pattern.compile("([\\u4e00-\\u9fa5A-Za-z0-9]{1,10}(?:号诊室|诊室[0-9A-Za-z#]+|[0-9A-Za-z#]+诊室|诊区[0-9A-Za-z]+))")
    private val DEPARTMENT_PATTERN = Pattern.compile("([\\u4e00-\\u9fa5]{2,6}(?:科门诊|内科|外科|儿科|妇科|眼科|耳鼻喉科|口腔科|骨科|皮科|门诊))")
    private val NUMBER_TOKEN_PATTERN = Pattern.compile("([A-Za-z]?[0-9]{2,5})(?:号)?")

    fun extractTargetsFromVisionText(visionText: Text): List<DetectedClinicTarget> {
        val targets = mutableListOf<DetectedClinicTarget>()

        var lastDept: String? = null

        for (block in visionText.textBlocks) {
            val blockText = block.text

            // 检查是否有科室
            val deptMatcher = DEPARTMENT_PATTERN.matcher(blockText)
            if (deptMatcher.find()) {
                lastDept = deptMatcher.group(1)
            }

            for (line in block.lines) {
                val lineText = line.text
                val clinicMatcher = CLINIC_PATTERN.matcher(lineText)

                if (clinicMatcher.find()) {
                    val clinicName = clinicMatcher.group(1) ?: continue
                    val allNumbers = extractNumbers(lineText)
                    val callingNum = allNumbers.firstOrNull()
                    val waitingNums = if (allNumbers.size > 1) allNumbers.drop(1) else emptyList()

                    val targetId = "$clinicName-${callingNum ?: "none"}"
                    if (targets.none { it.clinic == clinicName }) {
                        targets.add(
                            DetectedClinicTarget(
                                id = targetId,
                                department = lastDept,
                                clinic = clinicName,
                                currentCalling = callingNum,
                                candidateWaitingNumbers = waitingNums,
                                boundingBox = line.boundingBox ?: block.boundingBox
                            )
                        )
                    }
                }
            }
        }

        // 如果未按诊室关键词匹配到，但整屏有明显的叫号大字（如 "请 128 号到 3诊室"）
        if (targets.isEmpty()) {
            val fullText = visionText.text
            val clinicMatcher = CLINIC_PATTERN.matcher(fullText)
            if (clinicMatcher.find()) {
                val clinicName = clinicMatcher.group(1)!!
                val numbers = extractNumbers(fullText)
                targets.add(
                    DetectedClinicTarget(
                        id = "auto-single",
                        clinic = clinicName,
                        currentCalling = numbers.firstOrNull(),
                        candidateWaitingNumbers = numbers.drop(1),
                        boundingBox = visionText.textBlocks.firstOrNull()?.boundingBox
                    )
                )
            }
        }

        return targets
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
