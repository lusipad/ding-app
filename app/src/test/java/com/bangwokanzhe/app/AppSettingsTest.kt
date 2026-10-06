package com.bangwokanzhe.app

import com.bangwokanzhe.app.model.AppSettings
import org.junit.Assert.*
import org.junit.Test

class AppSettingsTest {

    @Test
    fun testDefaultSettingsSpecifications() {
        val settings = AppSettings()

        // 验证用户关键诉求：下拉通知默认开启，桌面悬浮窗默认关闭
        assertTrue("下拉通知应默认开启", settings.enablePullDownNotification)
        assertFalse("桌面悬浮窗应默认关闭", settings.enableFloatingWindow)

        // 验证提醒与规则默认值
        assertTrue("物理振动提醒应默认开启", settings.enableVibration)
        assertTrue("声音报警应默认开启", settings.enableSound)
        assertEquals("默认提前叫号人数应为 3 人", 3, settings.defaultAdvanceWarningCount)
        assertEquals(15.0f, settings.defaultInfusionThresholdPercent, 0.001f)
        assertTrue("智能暗屏节电降温应默认开启", settings.enableDimScreenOnMonitor)
    }

    @Test
    fun testSettingsCustomization() {
        var settings = AppSettings()

        // 用户主动开启悬浮窗、调整预警人数
        settings = settings.copy(
            enableFloatingWindow = true,
            defaultAdvanceWarningCount = 1,
            defaultInfusionThresholdPercent = 20.0f
        )

        assertTrue(settings.enableFloatingWindow)
        assertEquals(1, settings.defaultAdvanceWarningCount)
        assertEquals(20.0f, settings.defaultInfusionThresholdPercent, 0.001f)
    }
}
