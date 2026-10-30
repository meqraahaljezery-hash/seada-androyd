package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = MaqraahPrimaryLight,
    onPrimary = Color.White,
    secondary = MaqraahGold,
    background = Color(0xFF101814),
    surface = Color(0xFF18231D),
    onBackground = Color(0xFFE5EDE8),
    onSurface = Color(0xFFE5EDE8)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = MaqraahPrimary,
    onPrimary = Color.White,
    secondary = MaqraahGold,
    background = MaqraahBackground,
    surface = MaqraahSurface,
    onBackground = MaqraahText,
    onSurface = MaqraahText,
    outline = MaqraahLine
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
