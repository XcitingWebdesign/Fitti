package com.fitti.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 9-step grayscale palette. Hierarchy comes from weight/size, not hue.
val Gray0 = Color(0xFF000000)
val Gray1 = Color(0xFF121212) // background
val Gray2 = Color(0xFF1E1E1E) // surface
val Gray3 = Color(0xFF2C2C2C) // surface variant (cards)
val Gray4 = Color(0xFF555555) // outline / dividers
val Gray5 = Color(0xFF888888) // disabled / subtle
val Gray6 = Color(0xFFB0B0B0) // secondary text
val Gray7 = Color(0xFFE0E0E0) // accent (was blue)
val Gray8 = Color(0xFFFFFFFF) // headings / pure white

private val FittiDarkColors = darkColorScheme(
    primary = Gray8,
    onPrimary = Gray1,
    secondary = Gray6,
    onSecondary = Gray1,
    tertiary = Gray5,
    error = Gray8,
    onError = Gray1,
    background = Gray1,
    onBackground = Gray8,
    surface = Gray2,
    onSurface = Gray8,
    surfaceVariant = Gray3,
    onSurfaceVariant = Gray6,
    outline = Gray4
)

private val FittiTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp
    )
)

@Composable
fun FittiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FittiDarkColors,
        typography = FittiTypography,
        content = content
    )
}
