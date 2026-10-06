package com.bangwokanzhe.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bangwokanzhe.app.model.*
import com.bangwokanzhe.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MonitorDashboardScreen(
    state: MonitorState,
    recentObservations: List<Observation>,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEndTask: () -> Unit,
    onAcknowledgeAlert: () -> Unit,
    onOpenSimulator: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val task = state.task ?: return
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. 核心状态 Hero 卡片
        ModernStateHeroCard(
            state = state,
            onAcknowledge = onAcknowledgeAlert,
            onEndTask = onEndTask
        )

        // 2. 快捷控制操作栏
        ModernControlActionBar(
            lifecycle = state.lifecycle,
            onPause = onPause,
            onResume = onResume,
            onEndTask = onEndTask,
            onOpenSimulator = onOpenSimulator,
            onOpenSettings = onOpenSettings
        )

        // 3. 任务目标信息与运行统计条
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(BrandTeal, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${task.targetSpec.clinicId}" + (task.targetSpec.departmentName?.let { " · $it" } ?: ""),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Ink
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "监看目标：${task.targetSpec.targetNumber} 号" +
                                (task.targetSpec.patientName?.let { " ($it)" } ?: ""),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = BrandIndigo
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "处理帧数", fontSize = 10.5.sp, color = InkTertiary)
                        Text(
                            text = "${state.totalFramesProcessed}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = InkSecondary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "有效证据", fontSize = 10.5.sp, color = InkTertiary)
                        Text(
                            text = "${state.validObservationCount}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusGreen
                        )
                    }
                }
            }
        }

        // 4. 观察记录与证据列表标题
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "实时观察证据流水",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
            Text(
                text = "最近 ${recentObservations.size} 条",
                fontSize = 11.5.sp,
                color = InkTertiary
            )
        }

        if (recentObservations.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = InkTertiary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "摄像头巡视待命中...",
                            color = InkSecondary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "镜头对准叫号大屏时将自动抓取并核对",
                            color = InkTertiary,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(recentObservations) { obs ->
                    ModernObservationItemCard(
                        observation = obs,
                        targetClinic = task.targetSpec.clinicId,
                        timeFormat = timeFormat
                    )
                }
            }
        }
    }
}

@Composable
private fun ModernStateHeroCard(
    state: MonitorState,
    onAcknowledge: () -> Unit,
    onEndTask: () -> Unit
) {
    val task = state.task ?: return
    val targetNum = task.targetSpec.targetNumber

    // 解析当前叫号 (增强多诊室支持)
    val clinicName = task.targetSpec.clinicId
    val currentCalling = state.lastObservation?.let { obs ->
        obs.recognizedFields.currentCallingNumber
            ?: obs.recognizedFields.cells.firstOrNull { it.clinic.contains(clinicName) || clinicName.contains(it.clinic) }?.currentCallingNumber
            ?: obs.recognizedFields.cells.firstOrNull()?.currentCallingNumber
    } ?: "巡视中"

    val (brush, title, subtitle, liveBadge) = when {
        state.lifecycle == TaskLifecycle.BLOCKED -> {
            StateStyle(
                GradientWarn,
                "监看已中断",
                state.errorMessage ?: "相机被占用或权限关闭，请点击继续",
                "⚠️ 异常中断"
            )
        }
        state.lifecycle == TaskLifecycle.PAUSED -> {
            StateStyle(
                GradientIdle,
                "已暂停监看",
                "当前暂停识别屏幕，点击继续恢复",
                "⏸️ 暂停中"
            )
        }
        state.lifecycle == TaskLifecycle.ENDED -> {
            StateStyle(
                GradientIdle,
                "监看任务已结束",
                "已停止相机检测与桌面悬浮窗",
                "⏹️ 已结束"
            )
        }
        task.scenario == TaskScenario.INFUSION_EXPERIMENT -> {
            val level = state.infusionLevelPercent ?: 100f
            if (state.matchResult == MatchResult.CONFIRMED_MATCH || level <= (task.infusionSpec?.warningThresholdPercent ?: 15f)) {
                StateStyle(
                    GradientAlert,
                    "⚠️ 输液低液位报警！",
                    "剩余药液约为 ${level.toInt()}%，已低于警戒线，请立即呼叫护士换瓶！",
                    "🚨 紧急警报"
                )
            } else {
                StateStyle(
                    GradientCalm,
                    "🧪 输液巡视监看中",
                    "当前估算剩余约 ${level.toInt()}% · 状态正常",
                    "● 液位安全"
                )
            }
        }
        state.matchResult == MatchResult.MISSED_CALL || state.isMissedCall -> {
            StateStyle(
                GradientAlert,
                "⚠️ 您的 $targetNum 号疑似已过号！",
                "当前诊室已叫到后续号码或出现在过号名单中，请尽快前往分诊台！",
                "❌ 疑似过号"
            )
        }
        state.matchResult == MatchResult.CONFIRMED_MATCH -> {
            StateStyle(
                GradientAlert,
                "🎯 正在叫您的 $targetNum 号！",
                "已在「${state.lastObservation?.recognizedFields?.clinic ?: task.targetSpec.clinicId}」呼叫就诊，正在振动提醒！",
                "🎯 正叫号"
            )
        }
        state.matchResult == MatchResult.PRE_CALL_WARNING -> {
            StateStyle(
                GradientWarn,
                "🔔 即将叫号：前面还剩 ${state.waitingAheadCount ?: 1} 人！",
                "您的号码已排在候诊前列，请提前起身前往「${state.lastObservation?.recognizedFields?.clinic ?: task.targetSpec.clinicId}」门口候诊。",
                "⚠️ 预警临近"
            )
        }
        state.matchResult == MatchResult.POSSIBLE_MATCH -> {
            StateStyle(
                GradientWarn,
                "候诊中 · 发现 $targetNum 号",
                "在候诊列表中发现 $targetNum 号，当前叫到 $currentCalling 号",
                "● 候诊队列中"
            )
        }
        state.freshness == ObservationFreshness.FRESH && state.lastObservation != null -> {
            val secondsAgo = (System.currentTimeMillis() - state.lastObservation.capturedAt) / 1000
            StateStyle(
                GradientWatch,
                "等 $targetNum 号 · 叫到 $currentCalling 号",
                "${secondsAgo} 秒前更新 · 诊室状态正常 · 前方剩 ${state.waitingAheadCount ?: "..."} 人",
                "● 实时跟踪中"
            )
        }
        state.freshness == ObservationFreshness.STALE -> {
            StateStyle(
                GradientWarn,
                "暂未看到大屏",
                "距离上次有效观察超过 1 分钟，请将镜头对准大屏或稍后自动捕获",
                "⏱️ 画面待更新"
            )
        }
        else -> {
            StateStyle(
                GradientWatch,
                "正在等候 $targetNum 号",
                "摄像头间歇自动检测中，无需一直对准大屏",
                "● 智能巡视中"
            )
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush)
                .padding(22.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = liveBadge,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // 突出大号显示
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "目标",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.padding(bottom = 4.dp, end = 4.dp)
                        )
                        Text(
                            text = targetNum,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = title,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = subtitle,
                    fontSize = 12.5.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    lineHeight = 18.sp
                )

                // 核心报警确认按钮
                if ((state.matchResult == MatchResult.CONFIRMED_MATCH || state.matchResult == MatchResult.MISSED_CALL)
                    && state.lastAlertEvent?.acknowledgedAt == null
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onAcknowledge,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "确认收到 (停振动)",
                                color = StatusRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        OutlinedButton(
                            onClick = onEndTask,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(0.8f)
                        ) {
                            Text("结束任务", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModernControlActionBar(
    lifecycle: TaskLifecycle,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEndTask: () -> Unit,
    onOpenSimulator: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (lifecycle == TaskLifecycle.RUNNING) {
            FilledTonalButton(
                onClick = onPause,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Mist, contentColor = BrandIndigo)
            ) {
                Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("暂停", fontWeight = FontWeight.SemiBold)
            }
        } else {
            Button(
                onClick = onResume,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("继续", fontWeight = FontWeight.SemiBold)
            }
        }

        OutlinedButton(
            onClick = onOpenSimulator,
            modifier = Modifier.weight(1.2f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("演练器", fontWeight = FontWeight.SemiBold)
        }

        IconButton(
            onClick = onOpenSettings,
            colors = IconButtonDefaults.iconButtonColors(containerColor = CardWhite),
            modifier = Modifier.size(42.dp)
        ) {
            Icon(Icons.Default.Settings, contentDescription = "偏好设置", tint = InkSecondary, modifier = Modifier.size(20.dp))
        }

        Button(
            onClick = onEndTask,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
        ) {
            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("结束", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ModernObservationItemCard(
    observation: Observation,
    targetClinic: String,
    timeFormat: SimpleDateFormat
) {
    val timeStr = remember(observation.capturedAt) { timeFormat.format(Date(observation.capturedAt)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = timeStr,
                    fontSize = 11.5.sp,
                    color = InkTertiary,
                    fontWeight = FontWeight.Medium
                )

                val tagColor = when {
                    !observation.isValid -> StatusGray
                    observation.regionSemantics == RegionSemantics.CALLING_NOW -> StatusRed
                    observation.regionSemantics == RegionSemantics.WAITING_LIST -> BrandIndigo
                    observation.regionSemantics == RegionSemantics.HISTORY_RECORD -> StatusOrange
                    else -> BrandTeal
                }

                val tagBg = when {
                    !observation.isValid -> StatusGrayBg
                    observation.regionSemantics == RegionSemantics.CALLING_NOW -> StatusRedBg
                    observation.regionSemantics == RegionSemantics.WAITING_LIST -> Mist
                    observation.regionSemantics == RegionSemantics.HISTORY_RECORD -> StatusOrangeBg
                    else -> StatusGreenBg
                }

                Box(
                    modifier = Modifier
                        .background(tagBg, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = when {
                            !observation.isValid -> "无效: ${observation.rejectReason?.name ?: "模糊"}"
                            observation.regionSemantics == RegionSemantics.CALLING_NOW -> "正在呼叫"
                            observation.regionSemantics == RegionSemantics.WAITING_LIST -> "候诊队列"
                            observation.regionSemantics == RegionSemantics.HISTORY_RECORD -> "历史记录"
                            else -> "清晰可读"
                        },
                        fontSize = 10.5.sp,
                        color = tagColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val fields = observation.recognizedFields
            val matchedCell = fields.cells.firstOrNull { it.clinic.contains(targetClinic) || targetClinic.contains(it.clinic) }
                ?: fields.cells.firstOrNull()

            val callingNumber = fields.currentCallingNumber ?: matchedCell?.currentCallingNumber
            val clinic = fields.clinic ?: matchedCell?.clinic ?: "诊室"
            val waitingList = if (fields.waitingNumbers.isNotEmpty()) fields.waitingNumbers else (matchedCell?.waitingNumbers ?: emptyList())

            val detail = when {
                !observation.isValid -> "画面未通过质检或大屏反光模糊"
                callingNumber != null ->
                    "$clinic · 当前呼叫: ${callingNumber}号" +
                            (if (waitingList.isNotEmpty()) " · 等候: ${waitingList.joinToString(",")}" else "")
                else -> "已捕获画面文字，等待更新叫号数字"
            }

            Text(
                text = detail,
                fontSize = 13.sp,
                color = Ink,
                lineHeight = 18.sp
            )
        }
    }
}

private data class StateStyle(
    val brush: Brush,
    val title: String,
    val subtitle: String,
    val liveBadge: String
)
