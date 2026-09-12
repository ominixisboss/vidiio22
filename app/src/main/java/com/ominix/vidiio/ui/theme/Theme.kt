package com.ominix.vidiio.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.ominix.vidiio.data.repository.ColorTheme

private fun getColorScheme(colorTheme: ColorTheme): androidx.compose.material3.ColorScheme {
    val primaryColor = when (colorTheme) {
        ColorTheme.RED -> VidiioRed
        ColorTheme.BLUE -> VidiioBlue
        ColorTheme.GREEN -> VidiioGreen
        ColorTheme.PURPLE -> VidiioPurple
        ColorTheme.ORANGE -> VidiioOrange
        ColorTheme.TEAL -> VidiioTeal
        ColorTheme.PINK -> VidiioPink
        ColorTheme.INDIGO -> VidiioIndigo
        ColorTheme.GOLD -> VidiioGold
        ColorTheme.MONO -> VidiioMono
        ColorTheme.DYNAMIC -> VidiioRed
    }

    val onPrimaryColor = when (colorTheme) {
        ColorTheme.GOLD, ColorTheme.MONO, ColorTheme.ORANGE -> Color.Black
        else -> Color.White
    }

    return darkColorScheme(
        primary = primaryColor,
        secondary = primaryColor.copy(alpha = 0.7f),
        tertiary = NetflixGrey,
        background = DeepBlack,
        surface = SurfaceDark,
        surfaceVariant = Color(0xFF1E1E1E),
        onPrimary = onPrimaryColor,
        onSecondary = Color.White,
        onTertiary = Color.White,
        onBackground = Color.White,
        onSurface = Color.White,
        onSurfaceVariant = Color.LightGray
    )
}

@Composable
fun VidiioTheme(
    darkTheme: Boolean = true,
    colorTheme: ColorTheme = ColorTheme.RED,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        colorTheme == ColorTheme.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            dynamicDarkColorScheme(context)
        }
        else -> getColorScheme(colorTheme)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
