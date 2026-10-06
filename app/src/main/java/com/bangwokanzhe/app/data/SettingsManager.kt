package com.bangwokanzhe.app.data

import android.content.Context
import android.content.SharedPreferences
import com.bangwokanzhe.app.model.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 设置持久化管理类，提供响应式配置流与读写操作
 */
class SettingsManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "bangwokanzhe_prefs"
        private const val KEY_ENABLE_PULL_DOWN_NOTIF = "key_enable_pull_down_notif"
        private const val KEY_ENABLE_FLOATING_WINDOW = "key_enable_floating_window"
        private const val KEY_ENABLE_VIBRATION = "key_enable_vibration"
        private const val KEY_ENABLE_SOUND = "key_enable_sound"
        private const val KEY_ADVANCE_WARNING_COUNT = "key_advance_warning_count"
        private const val KEY_INFUSION_THRESHOLD = "key_infusion_threshold"
        private const val KEY_ENABLE_DIM_SCREEN = "key_enable_dim_screen"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        return AppSettings(
            enablePullDownNotification = prefs.getBoolean(KEY_ENABLE_PULL_DOWN_NOTIF, true),
            enableFloatingWindow = prefs.getBoolean(KEY_ENABLE_FLOATING_WINDOW, false),
            enableVibration = prefs.getBoolean(KEY_ENABLE_VIBRATION, true),
            enableSound = prefs.getBoolean(KEY_ENABLE_SOUND, true),
            defaultAdvanceWarningCount = prefs.getInt(KEY_ADVANCE_WARNING_COUNT, 3),
            defaultInfusionThresholdPercent = prefs.getFloat(KEY_INFUSION_THRESHOLD, 15.0f),
            enableDimScreenOnMonitor = prefs.getBoolean(KEY_ENABLE_DIM_SCREEN, true)
        )
    }

    fun setEnablePullDownNotification(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_PULL_DOWN_NOTIF, enabled).apply()
        _settings.value = _settings.value.copy(enablePullDownNotification = enabled)
    }

    fun setEnableFloatingWindow(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_FLOATING_WINDOW, enabled).apply()
        _settings.value = _settings.value.copy(enableFloatingWindow = enabled)
    }

    fun setEnableVibration(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_VIBRATION, enabled).apply()
        _settings.value = _settings.value.copy(enableVibration = enabled)
    }

    fun setEnableSound(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_SOUND, enabled).apply()
        _settings.value = _settings.value.copy(enableSound = enabled)
    }

    fun setDefaultAdvanceWarningCount(count: Int) {
        val safeCount = count.coerceIn(1, 5)
        prefs.edit().putInt(KEY_ADVANCE_WARNING_COUNT, safeCount).apply()
        _settings.value = _settings.value.copy(defaultAdvanceWarningCount = safeCount)
    }

    fun setDefaultInfusionThresholdPercent(percent: Float) {
        val safePercent = percent.coerceIn(5.0f, 35.0f)
        prefs.edit().putFloat(KEY_INFUSION_THRESHOLD, safePercent).apply()
        _settings.value = _settings.value.copy(defaultInfusionThresholdPercent = safePercent)
    }

    fun setEnableDimScreenOnMonitor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_DIM_SCREEN, enabled).apply()
        _settings.value = _settings.value.copy(enableDimScreenOnMonitor = enabled)
    }
}
