package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = SleekIndigo,
    secondary = SleekDarkSurfaceVariant,
    tertiary = SleekIndigoContainer,
    background = SleekDarkBg,
    surface = SleekDarkSurface,
    surfaceVariant = SleekDarkSurfaceVariant,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    onBackground = androidx.compose.ui.graphics.Color.White,
    onSurface = androidx.compose.ui.graphics.Color.White,
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF94A3B8) // Slate 400
  )

private val LightColorScheme =
  lightColorScheme(
    primary = SleekIndigo,
    secondary = SleekLightSurfaceVariant,
    tertiary = SleekIndigoContainer,
    background = SleekLightBg,
    surface = SleekLightSurface,
    surfaceVariant = SleekLightSurfaceVariant,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    onSecondary = SleekOnIndigoContainer,
    onBackground = Color(0xFF0F172A), // Slate 900
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF64748B) // Slate 500
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  amoledMode: Boolean = false,
  // Use our sleek theme colors by default to preserve Sleek Interface aesthetic
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  var colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  if (darkTheme && amoledMode) {
    colorScheme = colorScheme.copy(
      background = androidx.compose.ui.graphics.Color.Black,
      surface = androidx.compose.ui.graphics.Color.Black,
      surfaceVariant = androidx.compose.ui.graphics.Color(0xFF121212),
      scrim = androidx.compose.ui.graphics.Color.Black
    )
  }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
