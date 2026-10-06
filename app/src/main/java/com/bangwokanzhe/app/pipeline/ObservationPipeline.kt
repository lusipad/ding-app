package com.bangwokanzhe.app.pipeline

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.bangwokanzhe.app.model.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * 视觉观察处理管线
 * 流程：质量筛选 -> ML Kit 文本识别 -> 布局语义解析 -> 结构化 Observation 产出
 */
class ObservationPipeline {

    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    private var frameCounter = 0L

    /**
     * 处理来自 CameraX 的实时帧
     */
    suspend fun processImageProxy(
        imageProxy: ImageProxy,
        task: Task
    ): Observation = withContext(Dispatchers.Default) {
        val currentFrameId = ++frameCounter
        val monotonicNow = System.nanoTime() / 1_000_000L

        // 1. 质量前置评估
        val quality = QualityFilter.evaluate(imageProxy)
        if (!quality.isAcceptable) {
            return@withContext Observation(
                taskId = task.taskId,
                frameId = currentFrameId,
                monotonicTimeMs = monotonicNow,
                qualityMetrics = quality,
                recognizedFields = RecognizedFields(),
                regionSemantics = RegionSemantics.UNKNOWN,
                rejectReason = RejectReason.BLURRY
            )
        }

        // 2. ML Kit 文字识别
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            return@withContext Observation(
                taskId = task.taskId,
                frameId = currentFrameId,
                monotonicTimeMs = monotonicNow,
                qualityMetrics = quality,
                recognizedFields = RecognizedFields(),
                regionSemantics = RegionSemantics.UNKNOWN,
                rejectReason = RejectReason.SCREEN_NOT_FOUND
            )
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        return@withContext runTextAnalysis(inputImage, task, currentFrameId, quality, monotonicNow)
    }

    /**
     * 处理 Bitmap（用于模拟器、素材回放或截图测试）
     */
    suspend fun processBitmap(
        bitmap: Bitmap,
        task: Task
    ): Observation = withContext(Dispatchers.Default) {
        val currentFrameId = ++frameCounter
        val monotonicNow = System.nanoTime() / 1_000_000L

        val quality = QualityFilter.evaluate(bitmap)
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        return@withContext runTextAnalysis(inputImage, task, currentFrameId, quality, monotonicNow)
    }

    /**
     * 直接处理文本行（极速单元测试与离线回放）
     */
    fun processTextLines(
        lines: List<String>,
        task: Task,
        quality: QualityMetrics = QualityMetrics(85f, 120f, true)
    ): Observation {
        val currentFrameId = ++frameCounter
        val monotonicNow = System.nanoTime() / 1_000_000L

        val fields = LayoutSemanticParser.parse(lines, task.targetSpec.clinicId, task.targetSpec.departmentName)
        val eval = LayoutSemanticParser.evaluateTargetSemantics(
            fields,
            task.targetSpec.clinicId,
            task.targetSpec.departmentName,
            task.targetSpec.targetNumber,
            task.targetSpec.advanceWarningCount
        )

        val rejectReason = evaluateRejection(fields, task)

        return Observation(
            taskId = task.taskId,
            frameId = currentFrameId,
            monotonicTimeMs = monotonicNow,
            qualityMetrics = quality,
            recognizedFields = fields,
            regionSemantics = eval.semantics,
            rejectReason = rejectReason
        )
    }

    private suspend fun runTextAnalysis(
        inputImage: InputImage,
        task: Task,
        frameId: Long,
        quality: QualityMetrics,
        monotonicNow: Long
    ): Observation {
        return try {
            val visionText = recognizer.process(inputImage).await()
            val lines = visionText.textBlocks.flatMap { block -> block.lines.map { it.text } }

            val fields = LayoutSemanticParser.parse(lines, task.targetSpec.clinicId, task.targetSpec.departmentName)
            val eval = LayoutSemanticParser.evaluateTargetSemantics(
                fields,
                task.targetSpec.clinicId,
                task.targetSpec.departmentName,
                task.targetSpec.targetNumber,
                task.targetSpec.advanceWarningCount
            )
            val rejectReason = evaluateRejection(fields, task)

            Observation(
                taskId = task.taskId,
                frameId = frameId,
                monotonicTimeMs = monotonicNow,
                qualityMetrics = quality,
                recognizedFields = fields,
                regionSemantics = eval.semantics,
                rejectReason = rejectReason
            )
        } catch (e: Exception) {
            Observation(
                taskId = task.taskId,
                frameId = frameId,
                monotonicTimeMs = monotonicNow,
                qualityMetrics = quality,
                recognizedFields = RecognizedFields(),
                regionSemantics = RegionSemantics.UNKNOWN,
                rejectReason = RejectReason.LOW_CONFIDENCE
            )
        }
    }

    private fun evaluateRejection(fields: RecognizedFields, task: Task): RejectReason? {
        // 如果完全没有读到文字
        if (fields.rawTextLines.isEmpty()) {
            return RejectReason.SCREEN_NOT_FOUND
        }

        // 如果用户指定了诊室，检查诊室是否匹配（若能读到诊室名）
        val targetClinic = task.targetSpec.clinicId.trim()
        val detectedClinic = fields.clinic?.trim()

        if (targetClinic.isNotEmpty() && detectedClinic != null) {
            val clinicMatch = detectedClinic.contains(targetClinic) || targetClinic.contains(detectedClinic)
            if (!clinicMatch) {
                return RejectReason.CLINIC_MISMATCH
            }
        }

        return null
    }

    fun close() {
        recognizer.close()
    }
}
