package com.example.vidiio.ui.screens

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
    val showTop10: Boolean
) {
    val cardHeight: Dp
        get() = if (card == CardKind.PORTRAIT) cardWidth * 3f / 2f else cardWidth * 9f / 16f
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
        showTop10 = true
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
        showTop10 = true
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
        showTop10 = false
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
        showTop10 = false
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
        showTop10 = true
    )
}
