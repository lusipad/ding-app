package com.bangwokanzhe.app.ui.screens

import android.annotation.SuppressLint
import android.graphics.Rect
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.bangwokanzhe.app.model.TargetSpec
import com.bangwokanzhe.app.model.Task
import com.bangwokanzhe.app.pipeline.ScreenScannerHelper
import com.bangwokanzhe.app.ui.theme.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanSetupScreen(
    onBack: () -> Unit,
    onConfirmTask: (Task) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var detectedTargets by remember { mutableStateOf<List<ScreenScannerHelper.DetectedClinicTarget>>(emptyList()) }
    var selectedTarget by remember { mutableStateOf<ScreenScannerHelper.DetectedClinicTarget?>(null) }
    var selectedNumber by remember { mutableStateOf("") }
    var customNumberInput by remember { mutableStateOf("") }
    val app = com.bangwokanzhe.app.BangWoKanzheApp.instance
    var advanceWarningCount by remember {
        mutableIntStateOf(app.settingsManager.settings.value.defaultAdvanceWarningCount)
    }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val textRecognizer = remember { TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()) }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            textRecognizer.close()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // 1. CameraX 实时取景预览
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = androidx.camera.core.Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        processScannerFrame(imageProxy, textRecognizer) { targets ->
                            detectedTargets = targets
                            if (selectedTarget == null && targets.isNotEmpty()) {
                                selectedTarget = targets.first()
                            }
                        }
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. 顶部透明标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.65f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = "对准叫号大屏，点击识别到的诊室",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // 3. 画面中动态浮现的诊室标签（若画面里检测到了诊室）
        if (detectedTargets.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "💡 点击下方画面中识别出的诊室锁定：",
                    color = Color.Yellow,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                detectedTargets.forEach { target ->
                    val isSelected = selectedTarget?.clinic == target.clinic
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .clickable {
                                selectedTarget = target
                                selectedNumber = target.candidateWaitingNumbers.firstOrNull() ?: ""
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) PrimaryBlue else Color.Black.copy(alpha = 0.75f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, Color.White) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = target.clinic + (target.department?.let { " ($it)" } ?: ""),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "当前就诊: " + (target.currentCalling?.let { "${it}号" } ?: "巡视中"),
                                    color = if (isSelected) Color.White.copy(alpha = 0.9f) else Color.LightGray,
                                    fontSize = 13.sp
                                )
                            }
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                            }
                        }
                    }
                }
            }
        } else {
            // 扫描框十字指引
            Box(
                modifier = Modifier
                    .size(260.dp, 180.dp)
                    .align(Alignment.Center)
                    .border(2.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "正在寻找叫号屏幕...\n保持手机朝向大屏即可",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }

        // 4. 底部快捷锁定卡片
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val currentTarget = selectedTarget

                if (currentTarget != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "已锁定：${currentTarget.clinic}",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlue
                            )
                            currentTarget.department?.let {
                                Text(text = "科室: $it", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                        SuggestionChip(
                            onClick = { /* 切换 */ },
                            label = { Text("重选诊室", fontSize = 12.sp) }
                        )
                    }

                    // 候选排队号码点选（如果从大屏候诊名单中读出了号码）
                    if (currentTarget.candidateWaitingNumbers.isNotEmpty()) {
                        Text(
                            text = "大屏候诊列表中发现号码，点击选择您的号：",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(currentTarget.candidateWaitingNumbers) { num ->
                                val isNumSelected = selectedNumber == num
                                FilterChip(
                                    selected = isNumSelected,
                                    onClick = {
                                        selectedNumber = num
                                        customNumberInput = num
                                    },
                                    label = { Text("${num}号", fontWeight = FontWeight.Bold) }
                                )
                            }
                        }
                    }

                    // 手动轻量输入号码（若屏幕上还没轮到显示自己的号）
                    OutlinedTextField(
                        value = if (selectedNumber.isNotEmpty()) selectedNumber else customNumberInput,
                        onValueChange = {
                            customNumberInput = it.trim()
                            selectedNumber = it.trim()
                        },
                        label = { Text("您的排队号码") },
                        placeholder = { Text("例如：128") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 启动按钮
                    val effectiveNumber = if (selectedNumber.isNotEmpty()) selectedNumber else customNumberInput
                    Button(
                        onClick = {
                            if (effectiveNumber.isNotBlank()) {
                                val task = Task(
                                    targetSpec = TargetSpec(
                                        departmentName = currentTarget.department,
                                        clinicId = currentTarget.clinic,
                                        targetNumber = effectiveNumber,
                                        advanceWarningCount = advanceWarningCount
                                    )
                                )
                                onConfirmTask(task)
                            }
                        },
                        enabled = effectiveNumber.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("一键开始帮我看着", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    // 尚未检测到目标时的提示
                    Text(
                        text = "正在实时识别镜头中的大屏文字...",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "对准医院大屏稍等 1~2 秒，检测到诊室后即可直接点选锁定。",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
private fun processScannerFrame(
    imageProxy: ImageProxy,
    recognizer: com.google.mlkit.vision.text.TextRecognizer,
    onResult: (List<ScreenScannerHelper.DetectedClinicTarget>) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }

    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    recognizer.process(inputImage)
        .addOnSuccessListener { visionText ->
            val targets = ScreenScannerHelper.extractTargetsFromVisionText(visionText)
            onResult(targets)
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}
