package com.example.vidiio.ui.components

import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Alignment as RiveAlignment
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.Loop

/**
 * A Composable that wraps RiveAnimationView to display Rive animations.
 */
@Composable
fun RiveAnimation(
    modifier: Modifier = Modifier,
    resId: Int? = null,
    url: String? = null,
    autoPlay: Boolean = true,
    fit: Fit = Fit.CONTAIN,
    alignment: RiveAlignment = RiveAlignment.CENTER,
    loop: Loop = Loop.LOOP,
    stateMachineName: String? = null,
    artboardName: String? = null,
    fallback: @Composable () -> Unit = { DefaultFallbackAnimation() }
) {
    // Online URLs removed to prevent 403 crashes. If url is specified or resId is null, use fallback.
    val isOnlineUrl = !url.isNullOrBlank()
    var hasError by remember(resId, url) { mutableStateOf(isOnlineUrl || resId == null) }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (hasError) {
            fallback()
        } else {
            AndroidView(
                factory = { context ->
                    try {
                        val builder = RiveAnimationView.Builder(context)
                        resId?.let { builder.setResource(it) }
                        // Online URL loading removed to prevent 403 crashes
                        builder.setAutoplay(autoPlay)
                            .setFit(fit)
                            .setAlignment(alignment)
                            .setLoop(loop)
                        
                        stateMachineName?.let { builder.setStateMachineName(it) }
                        artboardName?.let { builder.setArtboardName(it) }
                        
                        builder.build()
                    } catch (t: Throwable) {
                        Log.e("RiveAnimation", "Critical error in Rive factory", t)
                        hasError = true
                        android.view.View(context)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    try {
                        if (view is RiveAnimationView) {
                            view.autoplay = autoPlay
                            view.fit = fit
                            view.alignment = alignment
                        }
                    } catch (t: Throwable) {
                        Log.e("RiveAnimation", "Critical error in Rive update", t)
                        hasError = true
                    }
                }
            )
        }
    }
}

@Composable
fun DefaultFallbackAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .size(100.dp)
            .scale(scale)
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Icon(
            imageVector = androidx.compose.material.icons.Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
    }
}
