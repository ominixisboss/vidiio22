package com.ominix.vidiio.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.ui.components.tvClickable
import com.ominix.vidiio.ui.components.tvFocusRing
import com.ominix.vidiio.ui.tv.LocalIsTv
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * The visual language shared by the Anime, Movies and TV pages: a full-bleed hero, shelves of
 * posters, filter chips. Each page brings its own [BrowsePalette], so they read as siblings
 * without looking identical.
 */
data class BrowsePalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val background: Color,
    val label: String,
)

/** Crunchyroll's orange, Funimation's purple, HIDIVE's blue. */
val AnimePalette = BrowsePalette(
    primary = Color(0xFFF47521), secondary = Color(0xFF6A3DE8), tertiary = Color(0xFF1AB4F5),
    background = Color(0xFF0B0A12), label = "ANIME"
)

/** Cinema: red and gold over near-black. */
val MoviesPalette = BrowsePalette(
    primary = Color(0xFFE5484D), secondary = Color(0xFFF5B83D), tertiary = Color(0xFF4F7CFF),
    background = Color(0xFF0D0A0B), label = "MOVIES"
)

/** Television: teal and violet. */
val TvPalette = BrowsePalette(
    primary = Color(0xFF2DD4BF), secondary = Color(0xFF8B5CF6), tertiary = Color(0xFFFB923C),
    background = Color(0xFF080D12), label = "TV SHOWS"
)

/**
 * Shortcuts the hero can show, provided by the app shell. [onLibrary] is only set where the
 * navigation bar has no room for My List / Downloads / Settings (phones).
 */
data class BrowseShortcuts(val onSearch: () -> Unit, val onLibrary: (() -> Unit)?)

val LocalBrowseShortcuts = staticCompositionLocalOf<BrowseShortcuts?> { null }

/** What a hero slide shows, whatever the data came from. */
data class HeroSpec(
    val key: Any,
    val title: String,
    val imageUrl: String?,
    val badge: String,
    val tag: String? = null,
    val meta: String = "",
    val blurb: String = "",
)

// ───────────────────────────── Backdrop & sections ─────────────────────────────

/** Soft brand glows behind the page, the same trick Home uses for its accent. */
@Composable
fun BrowseBackdrop(palette: BrowsePalette) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .size(420.dp)
                .align(Alignment.TopEnd)
                .background(Brush.radialGradient(listOf(palette.secondary.copy(alpha = 0.22f), Color.Transparent)))
        )
        Box(
            Modifier
                .size(360.dp)
                .align(Alignment.CenterStart)
                .background(Brush.radialGradient(listOf(palette.primary.copy(alpha = 0.14f), Color.Transparent)))
        )
    }
}

@Composable
fun BrowseSection(
    title: String,
    subtitle: String?,
    accent: Color,
    content: @Composable () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (subtitle != null) {
                    Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

// ───────────────────────────── Hero ─────────────────────────────

@Composable
fun BrowseHero(
    palette: BrowsePalette,
    items: List<HeroSpec>,
    loadingKey: Any?,
    onOpen: (Int) -> Unit,
    onFocused: () -> Unit,
) {
    val isTv = LocalIsTv.current
    val shortcuts = LocalBrowseShortcuts.current
    val pagerState = rememberPagerState(pageCount = { items.size })

    // Touch devices rotate the hero; under a remote it would move out from under the cursor.
    LaunchedEffect(pagerState.isScrollInProgress, items.size, isTv) {
        if (!isTv && !pagerState.isScrollInProgress && items.size > 1) {
            while (true) {
                delay(7000)
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % items.size)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isTv) 440.dp else 460.dp)
            .onFocusChanged { if (it.hasFocus) onFocused() }
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            HeroPage(palette, items[page], rank = page + 1, loading = loadingKey == items[page].key, onOpen = { onOpen(page) })
        }

        if (items.size > 1) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(items.size) { i ->
                    Box(
                        Modifier
                            .size(if (i == pagerState.currentPage) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (i == pagerState.currentPage) palette.primary else Color.White.copy(alpha = 0.35f))
                    )
                }
            }
        }

        // The page's own mark, top left, clear of the status bar.
        Row(
            modifier = Modifier.statusBarsPadding().padding(start = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = palette.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = palette.label,
                style = TextStyle(
                    brush = Brush.horizontalGradient(listOf(palette.primary, palette.secondary, palette.tertiary)),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp
                )
            )
        }

        // Phone shortcuts: search, and the library (My List / Downloads / Settings).
        if (shortcuts != null && !isTv) {
            ShortcutButtons(
                shortcuts,
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(end = 8.dp, top = 4.dp)
            )
        }
    }
}

/** Search and Library icon buttons, for the corner of a page on phones. */
@Composable
fun ShortcutButtons(shortcuts: BrowseShortcuts, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        HeroIconButton(Icons.Rounded.Search, "Search", shortcuts.onSearch)
        shortcuts.onLibrary?.let { HeroIconButton(Icons.Rounded.BookmarkBorder, "Library", it) }
    }
}

@Composable
private fun HeroIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .tvClickable(onClick = onClick, shape = CircleShape)
            .background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Color.White)
    }
}

@Composable
private fun HeroPage(
    palette: BrowsePalette,
    spec: HeroSpec,
    rank: Int,
    loading: Boolean,
    onOpen: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = spec.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Bottom fade into the page, and a colour wash from the left for text legibility.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.35f),
                    0.45f to Color.Transparent,
                    1f to palette.background
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to palette.secondary.copy(alpha = 0.55f), 0.65f to Color.Transparent)
            )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 20.dp, bottom = 34.dp)
                .widthIn(max = 560.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TagChip("${spec.badge} #$rank", palette.primary, Color.White)
                spec.tag?.let {
                    Spacer(Modifier.width(8.dp))
                    TagChip(it, palette.tertiary, Color.Black)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = spec.title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (spec.meta.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = spec.meta,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (spec.blurb.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = spec.blurb,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onOpen,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary, contentColor = Color.White),
                    modifier = Modifier.height(46.dp).tvFocusRing(RoundedCornerShape(24.dp))
                ) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("Watch Now", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(
                    onClick = onOpen,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.height(46.dp).tvFocusRing(RoundedCornerShape(24.dp))
                ) {
                    Text("Details", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ───────────────────────────── Cards ─────────────────────────────

/** A poster with the score, an optional badge strip along the bottom, and a title underneath. */
@Composable
fun PosterCard(
    palette: BrowsePalette,
    title: String,
    imageUrl: String?,
    subtitle: String?,
    score: Double?,
    strip: String?,
    stripColor: Color,
    cornerChip: String?,
    loading: Boolean,
    onClick: () -> Unit,
    width: Dp = 140.dp,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.width(width)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .tvClickable(onClick = onClick, shape = shape)
                .background(Color.White.copy(alpha = 0.06f))
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            score?.takeIf { it > 0.0 }?.let { value ->
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Star, null, tint = palette.primary, modifier = Modifier.size(11.dp))
                    Text(
                        text = String.format(Locale.US, "%.1f", value),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            }
            cornerChip?.let {
                TagChip(it, palette.secondary, Color.White, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
            }
            strip?.let {
                Text(
                    text = it,
                    color = Color.Black,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(stripColor)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
            if (loading) LoadingScrim(palette)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Landscape card: a still with a caption chip, for episodes and the calendar. */
@Composable
fun WideCard(
    palette: BrowsePalette,
    title: String,
    imageUrl: String?,
    bottomChip: String,
    topChip: String,
    topChipColor: Color,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.width(240.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .tvClickable(onClick = onClick, shape = shape)
                .background(Color.White.copy(alpha = 0.06f))
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))
                )
            )
            TagChip(bottomChip, palette.primary, Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
            TagChip(topChip, topChipColor, Color.Black, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
            if (loading) LoadingScrim(palette)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
fun TagChip(text: String, background: Color, content: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = content,
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(background)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun LoadingScrim(palette: BrowsePalette) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = palette.primary, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
    }
}

@Composable
fun PillChip(palette: BrowsePalette, text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text = text,
        color = if (selected) Color.White else Color.White.copy(alpha = 0.85f),
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        fontSize = 13.sp,
        maxLines = 1,
        modifier = Modifier
            .tvClickable(onClick = onClick, shape = shape)
            .background(if (selected) palette.primary else Color.White.copy(alpha = 0.08f))
            .border(1.dp, if (selected) palette.primary else Color.White.copy(alpha = 0.18f), shape)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

// ───────────────────────────── States ─────────────────────────────

@Composable
fun BrowseSkeleton() {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(380.dp).background(Color.White.copy(alpha = 0.05f)))
        Spacer(Modifier.height(24.dp))
        repeat(2) {
            Box(
                Modifier.padding(start = 16.dp).width(160.dp).height(20.dp)
                    .clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.08f))
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.padding(start = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(5) {
                    Box(
                        Modifier.width(140.dp).aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f))
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun BrowseError(palette: BrowsePalette, what: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Couldn't load $what right now", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text("Check your connection and try again.", color = Color.White.copy(alpha = 0.6f))
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = palette.primary, contentColor = Color.White),
            modifier = Modifier.tvFocusRing(RoundedCornerShape(24.dp))
        ) { Text("Retry") }
    }
}

// ───────────────────────────── Shelves of Movie ─────────────────────────────

/** A shelf of TMDB / addon titles. Opens the details page directly; no matching needed. */
@Composable
fun MovieShelf(
    palette: BrowsePalette,
    title: String,
    subtitle: String?,
    accent: Color,
    movies: List<Movie>,
    onOpen: (Movie) -> Unit,
) {
    BrowseSection(title, subtitle, accent) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(movies, key = { "${it.source}-${it.id}" }) { movie ->
                PosterCard(
                    palette = palette,
                    title = movie.title,
                    imageUrl = movie.posterUrl,
                    subtitle = movie.year?.toString(),
                    score = movie.rating,
                    strip = null,
                    stripColor = palette.tertiary,
                    cornerChip = null,
                    loading = false,
                    onClick = { onOpen(movie) }
                )
            }
        }
    }
}
