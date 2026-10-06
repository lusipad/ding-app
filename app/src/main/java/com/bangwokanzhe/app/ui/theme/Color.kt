package com.bangwokanzhe.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ───────────── 品牌色：静谧靛蓝 → 青绿（医疗场景：冷静、可信、不焦虑） ─────────────
val BrandIndigo = Color(0xFF4F46E5)
val BrandIndigoDeep = Color(0xFF312E81)
val BrandTeal = Color(0xFF14B8A6)
val BrandSky = Color(0xFF38BDF8)

val BrandGradient = Brush.linearGradient(listOf(BrandIndigo, Color(0xFF2563EB), BrandTeal))
val HeroGradient = Brush.linearGradient(listOf(Color(0xFF1E1B4B), Color(0xFF312E81), Color(0xFF0F766E)))

// ───────────── 中性色：墨色文字 + 微冷灰底 ─────────────
val Ink = Color(0xFF0F172A)
val InkSecondary = Color(0xFF475569)
val InkTertiary = Color(0xFF94A3B8)
val Canvas = Color(0xFFF4F6FB)
val CardWhite = Color(0xFFFFFFFF)
val Hairline = Color(0xFFE5E9F2)
val Mist = Color(0xFFEEF2FF)

// ───────────── 兼容旧命名（其他页面仍在引用） ─────────────
val PrimaryBlue = BrandIndigo
val OnPrimaryBlue = Color.White
val PrimaryContainer = Mist
val OnPrimaryContainer = BrandIndigoDeep
val SecondaryTeal = BrandTeal
val SurfaceLight = Canvas
val OnSurfaceLight = Ink

// ───────────── 状态色（语义化，柔和底 + 饱和前景） ─────────────
val StatusGreen = Color(0xFF059669)
val StatusGreenBg = Color(0xFFECFDF5)

val StatusOrange = Color(0xFFD97706)
val StatusOrangeBg = Color(0xFFFFFBEB)

val StatusRed = Color(0xFFDC2626)
val StatusRedBg = Color(0xFFFEF2F2)

val StatusGray = Color(0xFF64748B)
val StatusGrayBg = Color(0xFFF1F5F9)

// 状态英雄卡渐变
val GradientCalm = Brush.linearGradient(listOf(Color(0xFF0F766E), Color(0xFF14B8A6)))
val GradientWarn = Brush.linearGradient(listOf(Color(0xFFB45309), Color(0xFFF59E0B)))
val GradientAlert = Brush.linearGradient(listOf(Color(0xFF991B1B), Color(0xFFEF4444)))
val GradientIdle = Brush.linearGradient(listOf(Color(0xFF334155), Color(0xFF64748B)))
val GradientWatch = Brush.linearGradient(listOf(BrandIndigoDeep, BrandIndigo, Color(0xFF2563EB)))
