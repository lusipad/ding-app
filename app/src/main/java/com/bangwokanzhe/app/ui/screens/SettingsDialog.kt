package com.bangwokanzhe.app.ui.screens

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bangwokanzhe.app.BangWoKanzheApp
import com.bangwokanzhe.app.ui.overlay.FloatingOverlayManager
import com.bangwokanzhe.app.ui.theme.PrimaryBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    onTestVibrate: () -> Unit
) {
    val context = LocalContext.current
    val app = BangWoKanzheApp.instance
    val settingsManager = app.settingsManager
    val settings by settingsManager.settings.collectAsState()

    var showOverlayPermissionPrompt by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // 顶部标题与关闭按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "看护与提醒偏好设置",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color.LightGray.copy(alpha = 0.4f))

                // 可滚动内容区域
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 分组一：显示与通知
                    Text(
                        text = "📱 显示与通知方式",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue
                    )

                    // 1. 下拉通知栏（默认开启）
                    SettingSwitchCard(
                        icon = Icons.Default.Notifications,
                        title = "下拉通知栏动态更新",
                        subtitle = "默认开启。在系统通知栏实时显示排队叫号进度及剩余人数，并在叫号时推送横幅",
                        checked = settings.enablePullDownNotification,
                        onCheckedChange = { isChecked ->
                            settingsManager.setEnablePullDownNotification(isChecked)
                        },
                        extraAction = {
                            TextButton(onClick = {
                                app.notificationManager.testShowSampleNotification()
                            }) {
                                Text("发送测试通知", fontSize = 12.sp)
                            }
                        }
                    )

                    // 2. 桌面悬浮窗（默认关闭）
                    SettingSwitchCard(
                        icon = Icons.Default.Layers,
                        title = "桌面悬浮窗实时显示",
                        subtitle = "默认关闭。切到微信或桌面时，在屏幕侧边显示轻量看护胶囊。开启需系统悬浮窗权限",
                        checked = settings.enableFloatingWindow,
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                if (!app.floatingOverlayManager.canDrawOverlays()) {
                                    showOverlayPermissionPrompt = true
                                } else {
                                    settingsManager.setEnableFloatingWindow(true)
                                    app.floatingOverlayManager.show()
                                }
                            } else {
                                settingsManager.setEnableFloatingWindow(false)
                                app.floatingOverlayManager.hide()
                            }
                        },
                        extraAction = {
                            TextButton(onClick = {
                                if (!app.floatingOverlayManager.canDrawOverlays()) {
                                    showOverlayPermissionPrompt = true
                                } else {
                                    val newState = app.floatingOverlayManager.toggle()
                                    settingsManager.setEnableFloatingWindow(newState)
                                }
                            }) {
                                Text(if (app.floatingOverlayManager.isShowing) "关闭悬浮窗" else "预览悬浮窗", fontSize = 12.sp)
                            }
                        }
                    )

                    Divider(color = Color.LightGray.copy(alpha = 0.3f))

                    // 分组二：提醒媒介
                    Text(
                        text = "🔔 声音与触觉提醒",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue
                    )

                    // 3. 物理振动提醒
                    SettingSwitchCard(
                        icon = Icons.Default.Vibration,
                        title = "强力振动提醒",
                        subtitle = "命中排队号码、低液位时触发专属节奏强振动（穿透静音模式）",
                        checked = settings.enableVibration,
                        onCheckedChange = { settingsManager.setEnableVibration(it) },
                        extraAction = {
                            TextButton(onClick = onTestVibrate) {
                                Text("试一试强力振动", fontSize = 12.sp)
                            }
                        }
                    )

                    // 4. 声音提示音
                    SettingSwitchCard(
                        icon = Icons.Default.VolumeUp,
                        title = "报警提示音",
                        subtitle = "叫号命中与输液预警时播放提示声音",
                        checked = settings.enableSound,
                        onCheckedChange = { settingsManager.setEnableSound(it) }
                    )

                    Divider(color = Color.LightGray.copy(alpha = 0.3f))

                    // 分组三：阈值规则参数
                    Text(
                        text = "🎯 叫号与输液阈值",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue
                    )

                    // 5. 提前叫号人数阈值
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "提前叫号预警人数",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "当前呼叫落后您几位时开始提醒（默认提前 3 人）",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(1, 2, 3, 5).forEach { count ->
                                    FilterChip(
                                        selected = settings.defaultAdvanceWarningCount == count,
                                        onClick = { settingsManager.setDefaultAdvanceWarningCount(count) },
                                        label = { Text("提前 $count 人") }
                                    )
                                }
                            }
                        }
                    }

                    // 6. 输液低液位报警线
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "输液低液位报警线",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "当药液降至该余量以下时触发呼叫护士告警（默认 15%）",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(10f, 15f, 20f, 25f).forEach { percent ->
                                    FilterChip(
                                        selected = settings.defaultInfusionThresholdPercent == percent,
                                        onClick = { settingsManager.setDefaultInfusionThresholdPercent(percent) },
                                        label = { Text("${percent.toInt()}%") }
                                    )
                                }
                            }
                        }
                    }

                    // 7. 智能暗屏节电
                    SettingSwitchCard(
                        icon = Icons.Default.BrightnessMedium,
                        title = "智能暗屏节电降温",
                        subtitle = "监看锁定后自动降低主界面亮度，大幅降低发热并保护电池续航",
                        checked = settings.enableDimScreenOnMonitor,
                        onCheckedChange = { settingsManager.setEnableDimScreenOnMonitor(it) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("完成设置")
                }
            }
        }
    }

    // 悬浮窗权限请求引导弹窗
    if (showOverlayPermissionPrompt) {
        AlertDialog(
            onDismissRequest = { showOverlayPermissionPrompt = false },
            icon = { Icon(Icons.Default.Layers, contentDescription = null, tint = PrimaryBlue) },
            title = { Text("开启桌面悬浮窗权限") },
            text = {
                Text("桌面悬浮窗功能需要在系统设置中授予「显示在其他应用的上层」权限。授权后，当您切出本应用时，即可在屏幕侧边查看实时排队胶囊。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showOverlayPermissionPrompt = false
                        FloatingOverlayManager.openOverlayPermissionSetting(context)
                        Toast.makeText(context, "请在设置中允许「帮我看着」显示在其他应用上层", Toast.LENGTH_LONG).show()
                    }
                ) {
                    Text("前往系统授权")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayPermissionPrompt = false }) {
                    Text("暂不开启")
                }
            }
        )
    }
}

@Composable
fun SettingSwitchCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    extraAction: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(PrimaryBlue.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = Color.Gray,
                        lineHeight = 15.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange
                )
            }
            if (extraAction != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    extraAction()
                }
            }
        }
    }
}
