package com.bangwokanzhe.app.pipeline

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.bangwokanzhe.app.model.QualityMetrics
import java.nio.ByteBuffer

/**
 * 图像质量前置过滤器
 * 在 OCR 前快速剔除严重模糊、过暗或全黑全白帧，保护 CPU 与电池能耗
 */
object QualityFilter {

    private const val MIN_ACCEPTABLE_BRIGHTNESS = 25f
    private const val MAX_ACCEPTABLE_BRIGHTNESS = 245f
    private const val MIN_LAPLACIAN_VARIANCE = 15.0f

    /**
     * 针对 CameraX ImageProxy (YUV_420_888) 的 Y 分量快速采样计算亮度与简易梯度方差
     */
    fun evaluate(image: ImageProxy): QualityMetrics {
        val yPlane = image.planes[0]
        val buffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val width = image.width
        val height = image.height

        val stepX = 8
        val stepY = 8

        var totalBrightness = 0L
        var sampleCount = 0

        var sumGrad = 0.0
        var sumGradSq = 0.0
        var gradCount = 0

        val rowData = ByteArray(width)

        var y = 0
        while (y < height - stepY) {
            val offset = y * rowStride
            buffer.position(offset)
            val bytesToRead = minOf(width, buffer.remaining())
            buffer.get(rowData, 0, bytesToRead)

            var x = 0
            while (x < bytesToRead - stepX) {
                val lum = rowData[x].toInt() and 0xFF
                totalBrightness += lum
                sampleCount++

                // 简易水平梯度
                val lumNext = rowData[x + stepX].toInt() and 0xFF
                val diff = (lumNext - lum).toDouble()
                sumGrad += diff
                sumGradSq += diff * diff
                gradCount++

                x += stepX
            }
            y += stepY
        }

        buffer.rewind()

        if (sampleCount == 0 || gradCount == 0) {
            return QualityMetrics(blurScore = 0f, brightness = 0f, isAcceptable = false)
        }

        val avgBrightness = totalBrightness.toFloat() / sampleCount
        val meanGrad = sumGrad / gradCount
        val variance = (sumGradSq / gradCount) - (meanGrad * meanGrad)
        val blurScore = variance.toFloat().coerceIn(0f, 100f)

        val isAcceptable = avgBrightness in MIN_ACCEPTABLE_BRIGHTNESS..MAX_ACCEPTABLE_BRIGHTNESS &&
                blurScore >= MIN_LAPLACIAN_VARIANCE

        return QualityMetrics(
            blurScore = blurScore,
            brightness = avgBrightness,
            isAcceptable = isAcceptable
        )
    }

    /**
     * 针对 Bitmap 的质量快速评估（用于测试或模拟器回放）
     */
    fun evaluate(bitmap: Bitmap): QualityMetrics {
        val width = bitmap.width
        val height = bitmap.height
        val step = 16
        var totalBrightness = 0L
        var count = 0
        var sumDiffSq = 0.0

        for (y in 0 until height step step) {
            for (x in 0 until width - step step step) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xff
                val g = (pixel shr 8) and 0xff
                val b = pixel and 0xff
                val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                totalBrightness += lum
                count++

                val nextPixel = bitmap.getPixel(x + step, y)
                val nr = (nextPixel shr 16) and 0xff
                val ng = (nextPixel shr 8) and 0xff
                val nb = nextPixel and 0xff
                val nextLum = (0.299 * nr + 0.587 * ng + 0.114 * nb).toInt()

                val diff = (nextLum - lum).toDouble()
                sumDiffSq += diff * diff
            }
        }

        if (count == 0) return QualityMetrics(0f, 0f, false)

        val avgBrightness = totalBrightness.toFloat() / count
        val variance = sumDiffSq / count
        val blurScore = variance.toFloat().coerceIn(0f, 100f)
        val isAcceptable = avgBrightness in MIN_ACCEPTABLE_BRIGHTNESS..MAX_ACCEPTABLE_BRIGHTNESS &&
                blurScore >= MIN_LAPLACIAN_VARIANCE

        return QualityMetrics(
            blurScore = blurScore,
            brightness = avgBrightness,
            isAcceptable = isAcceptable
        )
    }
}
