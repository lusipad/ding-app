package com.bangwokanzhe.app.pipeline

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.bangwokanzhe.app.model.InfusionMetrics
import com.bangwokanzhe.app.model.InfusionSpec

/**
 * 输液瓶低液位视觉分析器（根据设计文档 5 节规范）
 * 专注于固定类型硬质输液瓶在间歇拍摄下的液气界面（Meniscus Interface）检测与低液位判定。
 * 排除软袋；无法定位液气界面时返回未知，不靠液体颜色猜测。
 */
object InfusionAnalyzer {

    /**
     * 分析 Bitmap 图片中的输液瓶液位
     */
    fun analyze(bitmap: Bitmap, spec: InfusionSpec): InfusionMetrics {
        val width = bitmap.width
        val height = bitmap.height

        // 仅在中心纵向 60% 区域（瓶身ROI）进行采样，避免背景干扰
        val roiStartX = (width * 0.2f).toInt()
        val roiEndX = (width * 0.8f).toInt()
        val roiStartY = (height * 0.15f).toInt()
        val roiEndY = (height * 0.85f).toInt()

        val sampleStepX = 4
        val sampleStepY = 2

        val rowAverages = FloatArray((roiEndY - roiStartY) / sampleStepY)

        var idx = 0
        for (y in roiStartY until roiEndY step sampleStepY) {
            var sumLum = 0.0
            var count = 0
            for (x in roiStartX until roiEndX step sampleStepX) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xff
                val g = (pixel shr 8) and 0xff
                val b = pixel and 0xff
                val lum = 0.299 * r + 0.587 * g + 0.114 * b
                sumLum += lum
                count++
            }
            rowAverages[idx++] = if (count > 0) (sumLum / count).toFloat() else 0f
        }

        // 寻找纵向最大梯度突变处（液气界面在逆光/测光下通常具有显著水平暗纹或折射亮线）
        var maxGradient = 0f
        var meniscusRelativeIndex = -1

        for (i in 2 until rowAverages.size - 2) {
            val grad = kotlin.math.abs(rowAverages[i + 1] - rowAverages[i - 1])
            if (grad > maxGradient && grad > 12.0f) { // 梯度阈值，排除平滑过渡
                maxGradient = grad
                meniscusRelativeIndex = i
            }
        }

        if (meniscusRelativeIndex == -1) {
            // 未能可靠锁定液气界面，按设计规范返回不可用
            return InfusionMetrics(
                estimatedLevelPercent = 100f,
                confidence = 0f,
                isMeniscusDetected = false,
                isBelowWarningThreshold = false
            )
        }

        // 计算剩余百分比：越靠下方，余量越少
        // 瓶底位于 rowAverages.lastIndex，瓶顶位于 0
        val totalSpan = rowAverages.size.toFloat()
        val remainingHeight = totalSpan - meniscusRelativeIndex
        val levelPercent = ((remainingHeight / totalSpan) * 100f).coerceIn(0f, 100f)

        val isBelow = levelPercent <= spec.warningThresholdPercent
        val confidence = (maxGradient / 40.0f).coerceIn(0.5f, 0.95f)

        return InfusionMetrics(
            estimatedLevelPercent = levelPercent,
            confidence = confidence,
            isMeniscusDetected = true,
            isBelowWarningThreshold = isBelow
        )
    }
}
