package com.bangwokanzhe.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bangwokanzhe.app.model.InfusionSpec
import com.bangwokanzhe.app.model.TargetSpec
import com.bangwokanzhe.app.model.Task
import com.bangwokanzhe.app.model.TaskScenario
import com.bangwokanzhe.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onScanScreen: () -> Unit,
    onStartTask: (Task) -> Unit,
    onTestVibrate: () -> Unit,
    onTestNotification: () -> Unit,
    onToggleFloatingOverlay: () -> Unit,
    onOpenSimulator: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val app = com.bangwokanzhe.app.BangWoKanzheApp.instance
    val settings by app.settingsManager.settings.collectAsState()

    var selectedScenario by remember { mutableStateOf(TaskScenario.HOSPITAL_CALL) }
    var showManualForm by remember { mutableStateOf(false) }

    // 手动填写参数
    var department by remember { mutableStateOf("") }
    var clinic by remember { mutableStateOf("3号诊室") }
    var targetNumber by remember { mutableStateOf("128") }
    var patientName by remember { mutableStateOf("") }
    var advanceWarningCount by remember { mutableIntStateOf(settings.defaultAdvanceWarningCount) }

    // 输液实验参数
    var warningThresholdPercent by remember { mutableFloatStateOf(settings.defaultInfusionThresholdPercent) }

    val presetClinics = listOf("3号诊室", "1号诊室", "专家2诊室", "儿科诊室")
    val presetDepts = listOf("外科门诊", "内科门诊", "儿科门诊", "心血管科")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. 顶栏：现代化 Hero 品牌卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HeroGradient)
                    .padding(22.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(Color.White.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "帮我看着",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "离线智能看护助手",
                                    fontSize = 11.sp,
                                    color = BrandTeal,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.White.copy(alpha = 0.12f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "偏好设置",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "安心候诊，镜头偶视大屏即自动跟踪叫号；多诊室空间隔离、提前智能预警、过号提醒与输液警戒全能守护。",
                        fontSize = 12.5.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // 2. 提醒偏好胶囊条（直观查看当前设置，一键直达）
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenSettings() },
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PreferenceStatusBadge(
                        label = "下拉通知",
                        enabled = settings.enablePullDownNotification
                    )
                    PreferenceStatusBadge(
                        label = "桌面悬浮",
                        enabled = settings.enableFloatingWindow
                    )
                    PreferenceStatusBadge(
                        label = "提前${settings.defaultAdvanceWarningCount}人",
                        enabled = true
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "配置",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandIndigo
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = BrandIndigo,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // 3. 现代化胶囊切页器（Segmented Control）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardWhite, RoundedCornerShape(16.dp))
                .padding(4.dp)
        ) {
            val isHospital = selectedScenario == TaskScenario.HOSPITAL_CALL
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isHospital) BrandIndigo else Color.Transparent)
                    .clickable { selectedScenario = TaskScenario.HOSPITAL_CALL }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "门诊大屏叫号",
                    fontSize = 13.5.sp,
                    fontWeight = if (isHospital) FontWeight.Bold else FontWeight.Medium,
                    color = if (isHospital) Color.White else InkSecondary
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (!isHospital) BrandIndigo else Color.Transparent)
                    .clickable { selectedScenario = TaskScenario.INFUSION_EXPERIMENT }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "输液余量看护",
                    fontSize = 13.5.sp,
                    fontWeight = if (!isHospital) FontWeight.Bold else FontWeight.Medium,
                    color = if (!isHospital) Color.White else InkSecondary
                )
            }
        }

        // 4. 主场景内容
        if (selectedScenario == TaskScenario.HOSPITAL_CALL) {
            // 核心主推荐：【扫大屏自动识别】超大快捷卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onScanScreen() },
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(22.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BrandGradient)
                        .padding(22.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "扫屏幕自动识别",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "AI 推荐",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "对准大屏自动定位诊室与叫号，点击即锁定，无需打字！",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                lineHeight = 17.sp
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ArrowForwardIos,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // 手动输入折叠区
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showManualForm = !showManualForm },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.EditNote,
                                contentDescription = null,
                                tint = BrandIndigo,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "手动配置诊室与号码",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Ink
                            )
                        }
                        Icon(
                            imageVector = if (showManualForm) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = InkTertiary
                        )
                    }

                    AnimatedVisibility(visible = showManualForm) {
                        Column(
                            modifier = Modifier.padding(top = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // 快捷诊室预选
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                presetClinics.forEach { c ->
                                    val isSelected = clinic == c
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) Mist else Canvas)
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) BrandIndigo else Hairline,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable { clinic = c }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = c,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) BrandIndigo else InkSecondary
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = clinic,
                                onValueChange = { clinic = it },
                                label = { Text("诊室名称") },
                                placeholder = { Text("例如：3号诊室") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = targetNumber,
                                onValueChange = { targetNumber = it.trim() },
                                label = { Text("您的排队号码") },
                                placeholder = { Text("例如：128") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = department,
                                onValueChange = { department = it },
                                label = { Text("所属科室 (可选)") },
                                placeholder = { Text("例如：外科门诊") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Button(
                                onClick = {
                                    if (targetNumber.isNotBlank()) {
                                        val task = Task(
                                            targetSpec = TargetSpec(
                                                departmentName = if (department.isNotBlank()) department else null,
                                                clinicId = clinic,
                                                targetNumber = targetNumber,
                                                patientName = if (patientName.isNotBlank()) patientName else null,
                                                advanceWarningCount = advanceWarningCount
                                            )
                                        )
                                        onStartTask(task)
                                    }
                                },
                                enabled = targetNumber.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo)
                            ) {
                                Text("启动手动监看", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
        } else {
            // 输液配置卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Mist, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.WaterDrop, contentDescription = null, tint = BrandIndigo)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "输液瓶低液位监看",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Ink
                            )
                            Text(
                                text = "对准输液瓶，当药液低于警戒阈值时发出强力警报",
                                fontSize = 11.5.sp,
                                color = InkTertiary
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(StatusRedBg, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "低液位警戒阈值",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = StatusRed
                            )
                            Text(
                                text = "${warningThresholdPercent.toInt()}%",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = StatusRed
                            )
                        }
                    }

                    Slider(
                        value = warningThresholdPercent,
                        onValueChange = { warningThresholdPercent = it },
                        valueRange = 5f..30f,
                        steps = 5,
                        colors = SliderDefaults.colors(
                            thumbColor = BrandIndigo,
                            activeTrackColor = BrandIndigo
                        )
                    )

                    Button(
                        onClick = {
                            val task = Task(
                                scenario = TaskScenario.INFUSION_EXPERIMENT,
                                targetSpec = TargetSpec(
                                    clinicId = "输液室",
                                    targetNumber = "0"
                                ),
                                infusionSpec = InfusionSpec(
                                    warningThresholdPercent = warningThresholdPercent
                                )
                            )
                            onStartTask(task)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo)
                    ) {
                        Text("开始输液守护", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }

        // 5. 提醒功能即时检验工具条（现代化自检微卡片）
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "硬件提醒快速检验",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink
                    )
                    Text(
                        text = "真机自检",
                        fontSize = 11.sp,
                        color = InkTertiary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickTestTile(
                        icon = Icons.Default.Vibration,
                        label = "测试振动",
                        onClick = onTestVibrate,
                        modifier = Modifier.weight(1f)
                    )
                    QuickTestTile(
                        icon = Icons.Default.NotificationsActive,
                        label = "测试通知",
                        onClick = onTestNotification,
                        modifier = Modifier.weight(1f)
                    )
                    QuickTestTile(
                        icon = Icons.Default.PictureInPictureAlt,
                        label = if (app.floatingOverlayManager.isShowing) "关闭悬浮" else "测试悬浮",
                        onClick = onToggleFloatingOverlay,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 6. 演练器入口
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenSimulator() },
            colors = CardDefaults.cardColors(containerColor = Mist),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(BrandIndigo.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = null,
                            tint = BrandIndigo,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "模拟大屏与演练器",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandIndigoDeep
                        )
                        Text(
                            text = "快速注入叫号、预警、过号与输液警报场景",
                            fontSize = 11.sp,
                            color = InkSecondary
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = BrandIndigoDeep,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun PreferenceStatusBadge(label: String, enabled: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(if (enabled) StatusGreenBg else StatusGrayBg, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(if (enabled) StatusGreen else StatusGray, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) StatusGreen else StatusGray
        )
    }
}

@Composable
private fun QuickTestTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Canvas)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BrandIndigo,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ink
            )
        }
    }
}
