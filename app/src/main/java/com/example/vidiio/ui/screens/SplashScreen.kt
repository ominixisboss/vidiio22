package com.example.vidiio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vidiio.ui.components.DefaultFallbackAnimation
import com.example.vidiio.ui.components.RiveAnimation
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(3000) // 3 seconds splash
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // High-end Rive Animation for Splash with Fallback
            RiveAnimation(
                url = "https://public.rive.app/community/runtime-files/2195-4346-loading-animation.riv",
                modifier = Modifier.size(250.dp),
                fit = app.rive.runtime.kotlin.core.Fit.CONTAIN,
                fallback = {
                    // Pulsing logo fallback if Rive fails
                    DefaultFallbackAnimation()
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "VIDIIO",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 10.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            )
            
            Text(
                text = "STREAM EVERYTHING",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    letterSpacing = 2.sp
                )
            )
        }
    }
}
