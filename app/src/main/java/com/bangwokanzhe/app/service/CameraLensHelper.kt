package com.bangwokanzhe.app.service

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log

/**
 * 镜头能力发现与智能调度助手（设计文档 2 节规范）
 * 探测设备超广角、主摄、长焦物理镜头，并在大范围搜索与小字读取时实现平滑切换与降级容错
 */
class CameraLensHelper(context: Context) {

    companion object {
        private const val TAG = "CameraLensHelper"
    }

    enum class LensType {
        ULTRA_WIDE, // 超广角（优先用于寻找目标）
        MAIN_WIDE,  // 标准主摄（通用稳定）
        TELEPHOTO   // 长焦（用于远距离或小字读取）
    }

    data class LensProfile(
        val cameraId: String,
        val lensType: LensType,
        val focalLength: Float,
        val hasMultiCameraCapability: Boolean
    )

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val availableLenses = mutableListOf<LensProfile>()

    private var currentLens: LensProfile? = null
    private var lastSwitchTimestamp = 0L
    private val switchCooldownMs = 8000L // 8 秒切换冷却时间，防止频繁震荡
    private val failureCounts = mutableMapOf<String, Int>()

    init {
        discoverCapabilities()
    }

    private fun discoverCapabilities() {
        try {
            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    val focal = focalLengths?.firstOrNull() ?: 4.0f

                    val type = when {
                        focal < 3.0f -> LensType.ULTRA_WIDE
                        focal > 6.0f -> LensType.TELEPHOTO
                        else -> LensType.MAIN_WIDE
                    }

                    availableLenses.add(
                        LensProfile(
                            cameraId = id,
                            lensType = type,
                            focalLength = focal,
                            hasMultiCameraCapability = true
                        )
                    )
                }
            }
            // 默认主摄
            currentLens = availableLenses.find { it.lensType == LensType.MAIN_WIDE } ?: availableLenses.firstOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "枚举镜头失败", e)
        }
    }

    /**
     * 根据场景推荐镜头
     */
    fun recommendLens(isSearchingScreen: Boolean, isTextTooSmall: Boolean): LensProfile? {
        val now = System.currentTimeMillis()
        if (now - lastSwitchTimestamp < switchCooldownMs) {
            return currentLens
        }

        val targetType = when {
            isSearchingScreen -> LensType.ULTRA_WIDE
            isTextTooSmall -> LensType.TELEPHOTO
            else -> LensType.MAIN_WIDE
        }

        val candidate = availableLenses.find {
            it.lensType == targetType && (failureCounts[it.cameraId] ?: 0) < 3
        } ?: availableLenses.find { it.lensType == LensType.MAIN_WIDE }

        if (candidate != null && candidate.cameraId != currentLens?.cameraId) {
            lastSwitchTimestamp = now
            currentLens = candidate
        }

        return currentLens
    }

    fun markSwitchFailed(cameraId: String) {
        failureCounts[cameraId] = (failureCounts[cameraId] ?: 0) + 1
        // 回退到默认主摄
        currentLens = availableLenses.find { it.lensType == LensType.MAIN_WIDE } ?: availableLenses.firstOrNull()
    }
}
