package com.msphone.agent.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

@Composable
fun MsPhoneAgentTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}
