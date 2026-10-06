package com.bangwokanzhe.app.ui.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.bangwokanzhe.app.MainActivity
import com.bangwokanzhe.app.R
import com.bangwokanzhe.app.model.MatchResult
import com.bangwokanzhe.app.model.MonitorState
import com.bangwokanzhe.app.model.TaskScenario

/**
 * 桌面悬浮窗管理器
 * 当用户切到微信、短视频或锁屏桌面时，在屏幕边缘显示轻量级紧凑排队胶囊
 * 支持手势拖拽、点击返回应用、点击小叉号关闭
 */
class FloatingOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    var isShowing: Boolean = false
        private set

    fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    companion object {
        fun openOverlayPermissionSetting(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:${context.packageName}")
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val fallbackIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(fallbackIntent)
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show(showToastFeedback: Boolean = true): Boolean {
        if (!canDrawOverlays()) {
            showToast("⚠️ 悬浮窗需要「显示在其他应用的上层」权限，请在设置中授权")
            return false
        }

        if (isShowing && overlayView != null) {
            return true
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 220
        }

        val (rootView, contentBody) = createOverlayContentView()
        setupDragTouchListener(contentBody, rootView, params)

        contentBody.setOnClickListener {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(intent)
        }

        try {
            windowManager.addView(rootView, params)
            overlayView = rootView
            isShowing = true
            if (showToastFeedback) {
                showToast("🪟 桌面悬浮窗已开启 (按住可任意拖动)")
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("创建悬浮窗失败: ${e.message}")
            return false
        }
    }

    fun updateState(state: MonitorState) {
        if (!isShowing || overlayView == null) return

        mainHandler.post {
            val tvStatus = overlayView?.findViewById<TextView>(R.id.tv_overlay_status) ?: return@post
            val root = overlayView as? LinearLayout

            val task = state.task
            if (task == null) {
                tvStatus.text = "帮我看着 · 待命"
                return@post
            }

            val targetNum = task.targetSpec.targetNumber

            when (state.matchResult) {
                MatchResult.CONFIRMED_MATCH -> {
                    tvStatus.text = "🎯 正在叫您的号 ${targetNum}！"
                    root?.background = createBackgroundDrawable(Color.argb(240, 220, 38, 38), Color.YELLOW)
                }
                MatchResult.PRE_CALL_WARNING -> {
                    tvStatus.text = "⚠️ 预警 · 还剩 ${state.waitingAheadCount ?: 1} 人到您"
                    root?.background = createBackgroundDrawable(Color.argb(240, 217, 119, 6), Color.WHITE)
                }
                MatchResult.MISSED_CALL -> {
                    tvStatus.text = "❌ 疑似已过号！速去分诊台"
                    root?.background = createBackgroundDrawable(Color.argb(240, 185, 28, 28), Color.WHITE)
                }
                else -> {
                    if (task.scenario == TaskScenario.INFUSION_EXPERIMENT) {
                        val level = state.infusionLevelPercent?.let { "${it.toInt()}%" } ?: "检测中"
                        tvStatus.text = "🧪 输液监看: 余量 $level"
                    } else {
                        val clinicName = task.targetSpec.clinicId
                        val calling = state.lastObservation?.let { obs ->
                            obs.recognizedFields.currentCallingNumber
                                ?: obs.recognizedFields.cells.firstOrNull { cell -> cell.clinic.contains(clinicName) || clinicName.contains(cell.clinic) }?.currentCallingNumber
                                ?: obs.recognizedFields.cells.firstOrNull()?.currentCallingNumber
                        } ?: "巡视中"
                        val ahead = state.waitingAheadCount?.let { " (剩${it}人)" } ?: ""
                        tvStatus.text = "等 $targetNum · 叫到 $calling$ahead"
                    }
                    root?.background = createBackgroundDrawable(Color.argb(235, 30, 27, 75), Color.argb(180, 79, 70, 229))
                }
            }
        }
    }

    fun hide(showToastFeedback: Boolean = true) {
        if (!isShowing || overlayView == null) return
        try {
            windowManager.removeView(overlayView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        overlayView = null
        isShowing = false
        if (showToastFeedback) {
            showToast("🪟 桌面悬浮窗已关闭")
        }
    }

    fun toggle(): Boolean {
        return if (isShowing) {
            hide(true)
            false
        } else {
            show(true)
        }
    }

    private fun createBackgroundDrawable(bgColor: Int, strokeColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = 36f
            setStroke(2, strokeColor)
        }
    }

    private fun createOverlayContentView(): Pair<View, View> {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(22, 12, 16, 12)
            gravity = Gravity.CENTER_VERTICAL
            background = createBackgroundDrawable(Color.argb(235, 30, 27, 75), Color.argb(180, 79, 70, 229))
            elevation = 16f
        }

        val contentBody = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
        }

        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_stat_eye)
            layoutParams = LinearLayout.LayoutParams(38, 38).apply {
                marginEnd = 12
            }
        }

        val statusTextView = TextView(context).apply {
            id = R.id.tv_overlay_status
            this.text = "帮我看着 · 监看中 (可拖动)"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 12
            }
        }

        contentBody.addView(icon)
        contentBody.addView(statusTextView)

        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(2, 28).apply {
                marginEnd = 6
            }
            setBackgroundColor(Color.argb(100, 255, 255, 255))
        }

        // 关闭小叉号按钮 (独立点击区域，避免拖动手势拦截)
        val closeBtn = TextView(context).apply {
            this.text = "✕"
            setTextColor(Color.argb(200, 255, 255, 255))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(12, 8, 12, 8)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                hide(true)
            }
        }

        root.addView(contentBody)
        root.addView(divider)
        root.addView(closeBtn)
        return Pair(root, contentBody)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragTouchListener(contentView: View, rootView: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var hasDragged = false

        contentView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    hasDragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    if (kotlin.math.abs(deltaX) > 8 || kotlin.math.abs(deltaY) > 8) {
                        hasDragged = true
                        params.x = initialX + deltaX
                        params.y = initialY + deltaY
                        try {
                            windowManager.updateViewLayout(rootView, params)
                        } catch (ignored: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!hasDragged) {
                        v.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
