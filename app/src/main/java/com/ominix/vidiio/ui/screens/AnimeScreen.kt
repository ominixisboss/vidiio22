package com.ominix.vidiio.ui.screens

import androidx.compose.animation.AnimatedVisibility
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ominix.vidiio.data.anime.AniMedia
import com.ominix.vidiio.data.anime.AniScheduleEntry
import com.ominix.vidiio.data.anime.AnimeRepository
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.ui.components.tvClickable
import com.ominix.vidiio.ui.components.tvFocusRing
import com.ominix.vidiio.ui.tv.LocalIsTv
import com.ominix.vidiio.ui.viewmodel.AnimeHomeState
import com.ominix.vidiio.ui.viewmodel.AnimeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// The three storefronts this page borrows from: Crunchyroll's orange, Funimation's purple and
// HIDIVE's blue. Orange leads (actions), purple tints the backdrops, blue marks "new / airing".
private val AnimeOrange = Color(0xFFF47521)
private val AnimePurple = Color(0xFF6A3DE8)
private val AnimeBlue = Color(0xFF1AB4F5)
private val AnimeBackground = Color(0xFF0B0A12)

private val GENRES = listOf(
    "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Horror", "Mahou Shoujo", "Mecha",
    "Music", "Mystery", "Psychological", "Romance", "Sci-Fi", "Slice of Life", "Sports",
    "Supernatural", "Thriller",
)

/** The Anime page: a hero, shelves, an airing calendar and genre browsing, over AniList data. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeScreen(
    viewModel: AnimeViewModel,
    onOpenMovie: (Movie) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val resolving by viewModel.resolving.collectAsState()

    // One clock for the whole page, ticking each minute, so the "in 2d 4h" labels stay true.
    var nowSeconds by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            nowSeconds = System.currentTimeMillis() / 1000
        }
    }

    // If a title has no playable match, say so - never fall back to a search the user did not ask for.
    val context = LocalContext.current
    val open: (AniMedia) -> Unit = { media ->
        viewModel.open(media, onOpenMovie) { title ->
            Toast.makeText(context, "No playable match found for $title", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AnimeBackground)
    ) {
        // Soft brand glows behind everything, the same trick Home uses for its accent.
        Box(
            Modifier
                .size(420.dp)
                .align(Alignment.TopEnd)
                .background(Brush.radialGradient(listOf(AnimePurple.copy(alpha = 0.22f), Color.Transparent)))
        )
        Box(
            Modifier
                .size(360.dp)
                .align(Alignment.CenterStart)
                .background(Brush.radialGradient(listOf(AnimeOrange.copy(alpha = 0.14f), Color.Transparent)))
        )

        when {
            state.isLoadingEverything -> AnimeSkeleton()
            state.hasNothing -> AnimeError(onRetry = viewModel::refresh)
            else -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize()
            ) {
                AnimeContent(state, nowSeconds, resolving, open, viewModel::selectGenre)
            }
        }
    }
}

@Composable
private fun AnimeContent(
    state: AnimeHomeState,
    nowSeconds: Long,
    resolving: Int?,
    open: (AniMedia) -> Unit,
    onGenre: (String?) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        val hero = state.trending.orEmpty().filter { it.bannerImage != null || it.posterUrl != null }.take(6)
        if (hero.isNotEmpty()) item(key = "hero") {
            // Focusing the hero buttons brings the whole hero back into view, title and all.
            AnimeHero(hero, nowSeconds, resolving, open, onFocused = { scope.launch { listState.animateScrollToItem(0) } })
        }

        shelf("trending", "Trending Now", "What everyone is watching", state.trending, nowSeconds, resolving, open)

        state.newEpisodes?.takeIf { it.isNotEmpty() }?.let { entries ->
            item(key = "new") {
                Section("New Episodes", "Aired in the last day", accent = AnimeBlue) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(entries, key = { "new-${it.media.id}" }) { entry ->
                            EpisodeCard(
                                entry = entry,
                                caption = relativeAgo(nowSeconds - entry.airingAt),
                                captionColor = AnimeBlue,
                                loading = resolving == entry.media.id,
                                onClick = { open(entry.media) }
                            )
                        }
                    }
                }
            }
        }

        shelf("airing", "Airing This Season", "Simulcasts, with the next episode's countdown", state.airing, nowSeconds, resolving, open, accent = AnimeBlue)

        state.week?.takeIf { it.isNotEmpty() }?.let { week ->
            item(key = "calendar") { ScheduleSection(week, nowSeconds, resolving, open) }
        }

        shelf("popular", "Popular This Season", null, state.popularSeason, nowSeconds, resolving, open)

        item(key = "genres") {
            GenreBrowse(state.genre, state.genreResults, nowSeconds, resolving, open, onGenre)
        }

        shelf("top", "Top Rated", "Highest scored of the popular shows", state.topRated, nowSeconds, resolving, open)
        shelf("all", "All-Time Favourites", null, state.allTime, nowSeconds, resolving, open)
        shelf("next", "Coming Next Season", null, state.nextSeason, nowSeconds, resolving, open, accent = AnimePurple)
    }
}

/** One horizontal shelf of poster cards. Skipped while loading or when empty. */
private fun androidx.compose.foundation.lazy.LazyListScope.shelf(
    key: String,
    title: String,
    subtitle: String?,
    media: List<AniMedia>?,
    nowSeconds: Long,
    resolving: Int?,
    open: (AniMedia) -> Unit,
    accent: Color = AnimeOrange,
) {
    if (media.isNullOrEmpty()) return
    item(key = key) {
        Section(title, subtitle, accent) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(media, key = { "$key-${it.id}" }) { item ->
                    AnimeCard(item, nowSeconds, loading = resolving == item.id, onClick = { open(item) })
                }
            }
        }
    }
}

@Composable
private fun Section(
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
private fun AnimeHero(
    items: List<AniMedia>,
    nowSeconds: Long,
    resolving: Int?,
    open: (AniMedia) -> Unit,
    onFocused: () -> Unit,
) {
    val isTv = LocalIsTv.current
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
            HeroPage(items[page], rank = page + 1, nowSeconds, loading = resolving == items[page].id, open)
        }

        // Page dots.
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
                            .background(if (i == pagerState.currentPage) AnimeOrange else Color.White.copy(alpha = 0.35f))
                    )
                }
            }
        }

        // The page's own mark, top left, clear of the status bar.
        Row(
            modifier = Modifier.statusBarsPadding().padding(start = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = AnimeOrange, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = "ANIME",
                style = TextStyle(
                    brush = Brush.horizontalGradient(listOf(AnimeOrange, AnimePurple, AnimeBlue)),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp
                )
            )
        }
    }
}

@Composable
private fun HeroPage(
    media: AniMedia,
    rank: Int,
    nowSeconds: Long,
    loading: Boolean,
    open: (AniMedia) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = media.bannerImage ?: media.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Bottom fade into the page, and a purple wash from the left for text legibility.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.35f),
                    0.45f to Color.Transparent,
                    1f to AnimeBackground
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to AnimePurple.copy(alpha = 0.55f),
                    0.65f to Color.Transparent
                )
            )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 20.dp, bottom = 34.dp)
                .widthIn(max = 560.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Chip("TRENDING #$rank", AnimeOrange, Color.White)
                media.nextAiringEpisode?.let {
                    Spacer(Modifier.width(8.dp))
                    Chip("EP ${it.episode} · ${formatCountdown(it.airingAt - nowSeconds)}", AnimeBlue, Color.Black)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = media.displayTitle,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = metaLine(media),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val blurb = cleanDescription(media.description)
            if (blurb.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = blurb,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { open(media) },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AnimeOrange, contentColor = Color.White),
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
                    onClick = { open(media) },
                    shape = RoundedCornerShape(24.dp),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.6f)))),
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

@Composable
private fun AnimeCard(
    media: AniMedia,
    nowSeconds: Long,
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
                model = media.posterUrl,
                contentDescription = media.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            media.score10?.let { score ->
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Star, null, tint = AnimeOrange, modifier = Modifier.size(11.dp))
                    Text(
                        text = String.format(Locale.US, "%.1f", score),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            }
            if (media.isMovie) {
                Chip("MOVIE", AnimePurple, Color.White, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
            }
            media.nextAiringEpisode?.let { next ->
                Text(
                    text = "EP ${next.episode} · ${formatCountdown(next.airingAt - nowSeconds)}",
                    color = Color.Black,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(AnimeBlue)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
            if (loading) LoadingScrim()
        }
        Text(
            text = media.displayTitle,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        val sub = listOfNotNull(media.mainStudio, media.popularity?.let { "${compactCount(it)} fans" }).firstOrNull()
        if (sub != null) {
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Landscape card for an episode: new releases and the calendar. */
@Composable
private fun EpisodeCard(
    entry: AniScheduleEntry,
    caption: String,
    captionColor: Color,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val media = entry.media
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
                model = media.bannerImage ?: media.posterUrl,
                contentDescription = media.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))
                )
            )
            Chip("EP ${entry.episode}", AnimeOrange, Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
            Chip(caption, captionColor, Color.Black, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
            if (loading) LoadingScrim()
        }
        Text(
            text = media.displayTitle,
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
private fun Chip(text: String, background: Color, content: Color, modifier: Modifier = Modifier) {
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
private fun LoadingScrim() {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = AnimeOrange, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
    }
}

// ───────────────────────────── Calendar ─────────────────────────────

@Composable
private fun ScheduleSection(
    week: List<AniScheduleEntry>,
    nowSeconds: Long,
    resolving: Int?,
    open: (AniMedia) -> Unit,
) {
    val startOfToday = remember(week) { AnimeRepository.startOfToday(nowSeconds * 1000) }
    val byDay = remember(week, startOfToday) {
        week.groupBy { ((it.airingAt - startOfToday) / 86_400L).toInt().coerceIn(0, 6) }
    }
    var selected by rememberSaveable { mutableLongStateOf(0L) }
    val day = selected.toInt()

    Section("Simulcast Calendar", "Your local time", AnimeOrange) {
        Column {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items((0..6).toList()) { index ->
                    val label = if (index == 0) "Today" else dayLabel(startOfToday + index * 86_400L)
                    val count = byDay[index]?.size ?: 0
                    PillChip(
                        text = if (count > 0) "$label · $count" else label,
                        selected = index == day,
                        onClick = { selected = index.toLong() }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            val entries = byDay[day].orEmpty()
            if (entries.isEmpty()) {
                Text(
                    "Nothing scheduled",
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(entries, key = { "cal-$day-${it.media.id}-${it.episode}" }) { entry ->
                        EpisodeCard(
                            entry = entry,
                            caption = timeLabel(entry.airingAt),
                            captionColor = if (entry.airingAt <= nowSeconds) Color.White.copy(alpha = 0.85f) else AnimeBlue,
                            loading = resolving == entry.media.id,
                            onClick = { open(entry.media) }
                        )
                    }
                }
            }
        }
    }
}

// ───────────────────────────── Genres ─────────────────────────────

@Composable
private fun GenreBrowse(
    selected: String?,
    results: List<AniMedia>?,
    nowSeconds: Long,
    resolving: Int?,
    open: (AniMedia) -> Unit,
    onGenre: (String?) -> Unit,
) {
    Section("Browse by Genre", null, AnimePurple) {
        Column {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(GENRES) { genre ->
                    PillChip(genre, selected = genre == selected, onClick = { onGenre(if (genre == selected) null else genre) })
                }
            }
            AnimatedVisibility(visible = selected != null) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    when {
                        results == null -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AnimeOrange, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                        }
                        results.isEmpty() -> Text(
                            "No ${selected.orEmpty()} shows found",
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        else -> LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(results, key = { "genre-${it.id}" }) { item ->
                                AnimeCard(item, nowSeconds, loading = resolving == item.id, onClick = { open(item) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PillChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text = text,
        color = if (selected) Color.White else Color.White.copy(alpha = 0.85f),
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        fontSize = 13.sp,
        maxLines = 1,
        modifier = Modifier
            .tvClickable(onClick = onClick, shape = shape)
            .background(if (selected) AnimeOrange else Color.White.copy(alpha = 0.08f))
            .border(1.dp, if (selected) AnimeOrange else Color.White.copy(alpha = 0.18f), shape)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

// ───────────────────────────── States ─────────────────────────────

@Composable
private fun AnimeSkeleton() {
    Column(Modifier.fillMaxSize().padding(top = 0.dp)) {
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
private fun AnimeError(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Couldn't load anime right now", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text("Check your connection and try again.", color = Color.White.copy(alpha = 0.6f))
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = AnimeOrange, contentColor = Color.White),
            modifier = Modifier.tvFocusRing(RoundedCornerShape(24.dp))
        ) { Text("Retry") }
    }
}

// ───────────────────────────── Formatting ─────────────────────────────

private fun metaLine(media: AniMedia): String = listOfNotNull(
    media.score10?.let { "★ " + String.format(Locale.US, "%.1f", it) },
    media.format?.replace('_', ' '),
    media.episodes?.let { "$it eps" },
    media.year?.toString(),
    media.genres.take(3).takeIf { it.isNotEmpty() }?.joinToString(", "),
).joinToString("  •  ")

/** AniList descriptions carry stray HTML and entities even when asked not to. */
internal fun cleanDescription(raw: String?): String = raw.orEmpty()
    .replace(Regex("<[^>]*>"), " ")
    .replace("&quot;", "\"").replace("&amp;", "&").replace("&#039;", "'")
    .replace(Regex("\\s+"), " ")
    .trim()

/** "2d 4h", "5h 12m", "12m", or "now". */
internal fun formatCountdown(seconds: Long): String {
    if (seconds <= 60) return "now"
    val d = seconds / 86_400
    val h = (seconds % 86_400) / 3_600
    val m = (seconds % 3_600) / 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        else -> "${m}m"
    }
}

/** "just now", "35m ago", "5h ago". */
internal fun relativeAgo(secondsAgo: Long): String {
    val minutes = secondsAgo / 60
    return when {
        minutes < 2 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        else -> "${minutes / 60}h ago"
    }
}

/** "1.2M", "412K", "950". */
internal fun compactCount(n: Int): String = when {
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
    n >= 1_000 -> "${n / 1_000}K"
    else -> n.toString()
}

private fun timeLabel(epochSeconds: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(epochSeconds * 1000))

private fun dayLabel(epochSeconds: Long): String =
    SimpleDateFormat("EEE d", Locale.getDefault()).format(Date(epochSeconds * 1000))
