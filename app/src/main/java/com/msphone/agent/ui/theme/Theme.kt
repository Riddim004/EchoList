package com.msphone.agent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.msphone.agent.domain.model.ThemeMode

/** 工作类任务主色（蓝色系） */
val WorkColor = Color(0xFF3056D3)

/** 生活类任务主色（绿色系） */
val LifeColor = Color(0xFF2E7D32)

/** 日期角标（今日/明日）主色（橙色系，与工作蓝/生活绿区分） */
val DayTagColor = Color(0xFFE65100)

private val LightColors = lightColorScheme(
    primary = Color(0xFF3056D3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE3FF),
    secondary = Color(0xFF2E7D32),
    secondaryContainer = Color(0xFFD7EED9),
    surface = Color(0xFFFDFDFD),
    surfaceVariant = Color(0xFFF0F1F5),
    background = Color(0xFFF7F8FA),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB6C4FF),
    onPrimary = Color(0xFF152B75),
    primaryContainer = Color(0xFF32448F),
    secondary = Color(0xFF9BD4A0),
    secondaryContainer = Color(0xFF1E4B23),
    surface = Color(0xFF17181C),
    surfaceVariant = Color(0xFF25272E),
    background = Color(0xFF121316),
    error = Color(0xFFF2B8B5),
)

@Composable
fun MsPhoneAgentTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        content = content,
    )
}
