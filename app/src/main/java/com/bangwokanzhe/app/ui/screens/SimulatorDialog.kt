package com.bangwokanzhe.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bangwokanzhe.app.simulator.MockScreenSimulator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SimulatorDialog(
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isRunningContinuous by remember { mutableStateOf(false) }
    var selectedStep by remember { mutableStateOf<MockScreenSimulator.MockScenarioStep?>(null) }

    val stepPreviewBitmap = remember(selectedStep) {
        selectedStep?.let { MockScreenSimulator.renderMockScreenBitmap(it) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 顶部标题
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "模拟大屏翻号演练器",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "无真实医院大屏时，一键验证振动、通知栏与桌面悬浮窗闭环",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                // 模拟大屏图片预览
                if (stepPreviewBitmap != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Image(
                            bitmap = stepPreviewBitmap.asImageBitmap(),
                            contentDescription = "模拟大屏预览",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // 一键自动演练按钮
                Button(
                    onClick = {
                        isRunningContinuous = true
                        coroutineScope.launch {
                            for (step in MockScreenSimulator.defaultScenarios) {
                                selectedStep = step
                                MockScreenSimulator.injectStep(step)
                                delay(1800)
                            }
                            isRunningContinuous = false
                        }
                    },
                    enabled = !isRunningContinuous,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isRunningContinuous) "正在全场景自动演练中..." else "一键顺序演练全场景 (多科室/预警/命中/过号/输液)")
                }

                Text(
                    text = "点击单步注入：",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                // 场景单步列表
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(MockScreenSimulator.defaultScenarios) { step ->
                        OutlinedCard(
                            onClick = {
                                selectedStep = step
                                coroutineScope.launch {
                                    MockScreenSimulator.injectStep(step)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "叫号: ${step.callingNumber}号 (${step.clinic})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = step.description,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                                Text(
                                    text = "注入",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
