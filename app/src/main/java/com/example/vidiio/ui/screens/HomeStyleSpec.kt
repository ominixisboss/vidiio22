package com.example.vidiio.ui.screens

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vidiio.data.repository.HomeStyle

enum class HeroKind {
    /** Auto-rotating pager over several titles. */
    CAROUSEL,
    /** Single full-bleed backdrop with a strong bottom fade. */
    BILLBOARD,
    /** Shorter full-width backdrop, text in the lower third. */
    BANNER,
    /** Inset rounded card. */
    SPOTLIGHT,
    /** Short image strip with a single Play button. */
    COMPACT
}

enum class CardKind { PORTRAIT, LANDSCAPE }

data class HomeStyleSpec(
    val label: String,
    val accent: Color,
    /** null → use the app theme background. */
    val background: Color?,
    val heroKind: HeroKind,
    val heroHeight: Dp,
    val heroCenterText: Boolean,
    val card: CardKind,
    val cardWidth: Dp,
    val cardCorner: Dp,
    val cardShowTitle: Boolean,
    val ratingBadge: Boolean,
    val rowGap: Dp,
    val rowHeaderSize: TextUnit,
    val rowHeaderWeight: FontWeight,
    val uppercaseHeaders: Boolean,
    val showTop10: Boolean,
    // --- motion ---
    /** Slow zoom/pan on the hero image (Netflix / Disney+ feel). */
    val kenBurns: Boolean = false,
    /** Scale a card shrinks to while pressed. 1f = no reaction. */
    val cardPressScale: Float = 0.95f,
    /** Overshooting spring on card press + row entrance (Disney+ feel). */
    val bouncyCards: Boolean = false,
    /** Fade/slide row cards in as they first appear. */
    val staggerIn: Boolean = false
) {
    val cardHeight: Dp
        get() = if (card == CardKind.PORTRAIT) cardWidth * 3f / 2f else cardWidth * 9f / 16f
}

/** Navigation transition for switching between the top-level tabs. */
fun HomeStyle.topLevelTransition(): ContentTransform = when (this) {
    HomeStyle.VIDIIO ->
        fadeIn(tween(400)) + scaleIn(initialScale = 0.92f, animationSpec = tween(400)) togetherWith
            fadeOut(tween(400)) + scaleOut(targetScale = 0.92f, animationSpec = tween(400))
    HomeStyle.NETFLIX ->
        fadeIn(tween(350)) togetherWith fadeOut(tween(350))
    HomeStyle.HULU ->
        fadeIn(tween(180)) togetherWith fadeOut(tween(180))
    HomeStyle.PRIME ->
        fadeIn(tween(300)) + slideInHorizontally(tween(300)) { it / 12 } togetherWith
            fadeOut(tween(300)) + slideOutHorizontally(tween(300)) { -it / 12 }
    HomeStyle.DISNEY ->
        fadeIn(tween(400)) + scaleIn(
            initialScale = 0.85f,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)
        ) togetherWith fadeOut(tween(300)) + scaleOut(targetScale = 1.08f, animationSpec = tween(300))
}

/** Navigation transition for opening a Details screen. */
fun HomeStyle.detailTransition(): ContentTransform = when (this) {
    HomeStyle.VIDIIO ->
        slideInHorizontally(tween(400)) { it } + fadeIn(tween(400)) togetherWith
            slideOutHorizontally(tween(400)) { -it } + fadeOut(tween(400))
    HomeStyle.NETFLIX ->
        fadeIn(tween(320)) + scaleIn(initialScale = 1.08f, animationSpec = tween(320)) togetherWith
            fadeOut(tween(320)) + scaleOut(targetScale = 1.08f, animationSpec = tween(320))
    HomeStyle.HULU ->
        fadeIn(tween(180)) togetherWith fadeOut(tween(180))
    HomeStyle.PRIME ->
        slideInVertically(tween(320)) { it / 6 } + fadeIn(tween(320)) togetherWith fadeOut(tween(240))
    HomeStyle.DISNEY ->
        scaleIn(
            initialScale = 0.8f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)
        ) + fadeIn(tween(300)) togetherWith fadeOut(tween(260)) + scaleOut(targetScale = 0.92f)
}

/**
 * @param defaultAccent the current theme accent, used by the VIDIIO preset which
 *   respects the user's separate Color Theme choice.
 */
fun HomeStyle.spec(defaultAccent: Color): HomeStyleSpec = when (this) {
    HomeStyle.VIDIIO -> HomeStyleSpec(
        label = "Vidiio",
        accent = defaultAccent,
        background = null,
        heroKind = HeroKind.CAROUSEL,
        heroHeight = 500.dp,
        heroCenterText = true,
        card = CardKind.PORTRAIT,
        cardWidth = 140.dp,
        cardCorner = 20.dp,
        cardShowTitle = false,
        ratingBadge = true,
        rowGap = 16.dp,
        rowHeaderSize = 22.sp,
        rowHeaderWeight = FontWeight.Bold,
        uppercaseHeaders = false,
        showTop10 = true,
        cardPressScale = 0.95f
    )
    HomeStyle.NETFLIX -> HomeStyleSpec(
        label = "Netflix",
        accent = Color(0xFFE50914),
        background = Color(0xFF141414),
        heroKind = HeroKind.BILLBOARD,
        heroHeight = 560.dp,
        heroCenterText = true,
        card = CardKind.PORTRAIT,
        cardWidth = 128.dp,
        cardCorner = 4.dp,
        cardShowTitle = false,
        ratingBadge = false,
        rowGap = 10.dp,
        rowHeaderSize = 19.sp,
        rowHeaderWeight = FontWeight.Bold,
        uppercaseHeaders = false,
        showTop10 = true,
        kenBurns = true,
        cardPressScale = 0.93f
    )
    HomeStyle.HULU -> HomeStyleSpec(
        label = "Hulu",
        accent = Color(0xFF1CE783),
        background = Color(0xFF0B0C0F),
        heroKind = HeroKind.COMPACT,
        heroHeight = 360.dp,
        heroCenterText = false,
        card = CardKind.LANDSCAPE,
        cardWidth = 250.dp,
        cardCorner = 4.dp,
        cardShowTitle = true,
        ratingBadge = false,
        rowGap = 28.dp,
        rowHeaderSize = 16.sp,
        rowHeaderWeight = FontWeight.SemiBold,
        uppercaseHeaders = true,
        showTop10 = false,
        cardPressScale = 1f
    )
    HomeStyle.PRIME -> HomeStyleSpec(
        label = "Prime",
        accent = Color(0xFF00A8E1),
        background = Color(0xFF0F171E),
        heroKind = HeroKind.BANNER,
        heroHeight = 430.dp,
        heroCenterText = false,
        card = CardKind.LANDSCAPE,
        cardWidth = 300.dp,
        cardCorner = 8.dp,
        cardShowTitle = true,
        ratingBadge = true,
        rowGap = 20.dp,
        rowHeaderSize = 20.sp,
        rowHeaderWeight = FontWeight.Bold,
        uppercaseHeaders = false,
        showTop10 = false,
        cardPressScale = 0.96f
    )
    HomeStyle.DISNEY -> HomeStyleSpec(
        label = "Disney+",
        accent = Color(0xFF0063E5),
        background = Color(0xFF1A1D29),
        heroKind = HeroKind.SPOTLIGHT,
        heroHeight = 440.dp,
        heroCenterText = true,
        card = CardKind.LANDSCAPE,
        cardWidth = 270.dp,
        cardCorner = 14.dp,
        cardShowTitle = true,
        ratingBadge = false,
        rowGap = 22.dp,
        rowHeaderSize = 22.sp,
        rowHeaderWeight = FontWeight.ExtraBold,
        uppercaseHeaders = false,
        showTop10 = true,
        kenBurns = true,
        cardPressScale = 0.9f,
        bouncyCards = true,
        staggerIn = true
    )
}
