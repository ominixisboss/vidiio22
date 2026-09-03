package com.example.vidiio.ui.theme

import android.app.Activity
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
import com.example.vidiio.data.repository.ColorTheme

private fun getColorScheme(
    darkTheme: Boolean,
    colorTheme: ColorTheme
): androidx.compose.material3.ColorScheme {
    val primaryColor = when (colorTheme) {
        ColorTheme.RED -> VidiioRed
        ColorTheme.BLUE -> VidiioBlue
        ColorTheme.GREEN -> VidiioGreen
        ColorTheme.PURPLE -> VidiioPurple
        ColorTheme.ORANGE -> VidiioOrange
        ColorTheme.TEAL -> VidiioTeal
    }

    return if (darkTheme) {
        darkColorScheme(
            primary = primaryColor,
            secondary = primaryColor.copy(alpha = 0.7f),
            tertiary = NetflixGrey,
            background = DeepBlack,
            surface = SurfaceDark,
            surfaceVariant = Color(0xFF1E1E1E),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onTertiary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White,
            onSurfaceVariant = Color.LightGray
        )
    } else {
        lightColorScheme(
            primary = primaryColor,
            secondary = primaryColor.copy(alpha = 0.7f),
            tertiary = NetflixGrey,
            background = Color(0xFFF8F8F8),
            surface = Color.White,
            surfaceVariant = Color(0xFFE0E0E0),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onTertiary = Color.White,
            onBackground = Color.Black,
            onSurface = Color.Black,
            onSurfaceVariant = Color.DarkGray
        )
    }
}

@Composable
fun VidiioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorTheme: ColorTheme = ColorTheme.RED,
    // Dynamic color is available on Android 12+
    // Setting default to false to maintain Netflix-style brand colors
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        else -> getColorScheme(darkTheme, colorTheme)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
