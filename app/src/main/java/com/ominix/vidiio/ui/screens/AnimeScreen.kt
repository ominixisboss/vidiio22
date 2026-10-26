package com.ominix.vidiio.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ominix.vidiio.data.anime.AniMedia
import com.ominix.vidiio.data.anime.AniScheduleEntry
import com.ominix.vidiio.data.anime.AnimeRepository
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.ui.browse.AnimePalette
import com.ominix.vidiio.ui.browse.BrowseBackdrop
import com.ominix.vidiio.ui.browse.BrowseError
import com.ominix.vidiio.ui.browse.BrowseHero
import com.ominix.vidiio.ui.browse.BrowseSection
import com.ominix.vidiio.ui.browse.BrowseSkeleton
import com.ominix.vidiio.ui.browse.HeroSpec
import com.ominix.vidiio.ui.browse.MovieShelf
import com.ominix.vidiio.ui.browse.PillChip
import com.ominix.vidiio.ui.browse.PosterCard
import com.ominix.vidiio.ui.browse.WideCard
import com.ominix.vidiio.ui.viewmodel.AnimeHomeState
import com.ominix.vidiio.ui.viewmodel.AnimeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Palette = AnimePalette

private val GENRES = listOf(
    "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Horror", "Mahou Shoujo", "Mecha",
    "Music", "Mystery", "Psychological", "Romance", "Sci-Fi", "Slice of Life", "Sports",
    "Supernatural", "Thriller",
)

/**
 * The Anime page: a hero, One Pace, shelves, an airing calendar and genre browsing, over AniList
 * data. Titles open the normal details page (after matching them to a playable entry).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeScreen(
    viewModel: AnimeViewModel,
    onOpenMovie: (Movie) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val resolving by viewModel.resolving.collectAsState()
    val context = LocalContext.current

    // One clock for the whole page, ticking each minute, so the "in 2d 4h" labels stay true.
    var nowSeconds by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            nowSeconds = System.currentTimeMillis() / 1000
        }
    }

    // If a title has no playable match, say so - never fall back to a search nobody asked for.
    val open: (AniMedia) -> Unit = { media ->
        viewModel.open(media, onOpenMovie) { title ->
            Toast.makeText(context, "No playable match found for $title", Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Palette.background)) {
        BrowseBackdrop(Palette)
        when {
            state.isLoadingEverything -> BrowseSkeleton()
            state.hasNothing -> BrowseError(Palette, "anime", onRetry = viewModel::refresh)
            else -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize()
            ) {
                AnimeContent(state, nowSeconds, resolving, open, onOpenMovie, viewModel::selectGenre)
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
    onOpenMovie: (Movie) -> Unit,
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
            val specs = remember(hero, nowSeconds / 60) { hero.map { it.toHeroSpec(nowSeconds) } }
            // Focusing the hero buttons brings the whole hero back into view, title and all.
            BrowseHero(
                palette = Palette,
                items = specs,
                loadingKey = resolving,
                onOpen = { index -> open(hero[index]) },
                onFocused = { scope.launch { listState.animateScrollToItem(0) } }
            )
        }

        // Bundled anime addons, One Pace first and prominent: these open straight to details.
        state.addons.orEmpty().forEach { category ->
            item(key = "addon-${category.name}") {
                MovieShelf(
                    palette = Palette,
                    title = category.name,
                    subtitle = "Every arc, in order",
                    accent = Palette.secondary,
                    movies = category.movies,
                    onOpen = onOpenMovie
                )
            }
        }

        shelf("trending", "Trending Now", "What everyone is watching", state.trending, nowSeconds, resolving, open)

        state.newEpisodes?.takeIf { it.isNotEmpty() }?.let { entries ->
            item(key = "new") {
                BrowseSection("New Episodes", "Aired in the last day", Palette.tertiary) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(entries, key = { "new-${it.media.id}" }) { entry ->
                            WideCard(
                                palette = Palette,
                                title = entry.media.displayTitle,
                                imageUrl = entry.media.bannerImage ?: entry.media.posterUrl,
                                bottomChip = "EP ${entry.episode}",
                                topChip = relativeAgo(nowSeconds - entry.airingAt),
                                topChipColor = Palette.tertiary,
                                loading = resolving == entry.media.id,
                                onClick = { open(entry.media) }
                            )
                        }
                    }
                }
            }
        }

        shelf("airing", "Airing This Season", "Simulcasts, with the next episode's countdown", state.airing, nowSeconds, resolving, open, accent = Palette.tertiary)

        state.week?.takeIf { it.isNotEmpty() }?.let { week ->
            item(key = "calendar") { ScheduleSection(week, nowSeconds, resolving, open) }
        }

        shelf("popular", "Popular This Season", null, state.popularSeason, nowSeconds, resolving, open)

        item(key = "genres") {
            GenreBrowse(state.genre, state.genreResults, nowSeconds, resolving, open, onGenre)
        }

        shelf("top", "Top Rated", "Highest scored of the popular shows", state.topRated, nowSeconds, resolving, open)
        shelf("all", "All-Time Favourites", null, state.allTime, nowSeconds, resolving, open)
        shelf("next", "Coming Next Season", null, state.nextSeason, nowSeconds, resolving, open, accent = Palette.secondary)
    }
}

/** One horizontal shelf of poster cards. Skipped while loading or when empty. */
private fun LazyListScope.shelf(
    key: String,
    title: String,
    subtitle: String?,
    media: List<AniMedia>?,
    nowSeconds: Long,
    resolving: Int?,
    open: (AniMedia) -> Unit,
    accent: Color = Palette.primary,
) {
    if (media.isNullOrEmpty()) return
    item(key = key) {
        BrowseSection(title, subtitle, accent) {
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
private fun AnimeCard(media: AniMedia, nowSeconds: Long, loading: Boolean, onClick: () -> Unit) {
    PosterCard(
        palette = Palette,
        title = media.displayTitle,
        imageUrl = media.posterUrl,
        subtitle = listOfNotNull(media.mainStudio, media.popularity?.let { "${compactCount(it)} fans" }).firstOrNull(),
        score = media.score10,
        strip = media.nextAiringEpisode?.let { "EP ${it.episode} · ${formatCountdown(it.airingAt - nowSeconds)}" },
        stripColor = Palette.tertiary,
        cornerChip = if (media.isMovie) "MOVIE" else null,
        loading = loading,
        onClick = onClick
    )
}

private fun AniMedia.toHeroSpec(nowSeconds: Long) = HeroSpec(
    key = id,
    title = displayTitle,
    imageUrl = bannerImage ?: posterUrl,
    badge = "TRENDING",
    tag = nextAiringEpisode?.let { "EP ${it.episode} · ${formatCountdown(it.airingAt - nowSeconds)}" },
    meta = metaLine(this),
    blurb = cleanDescription(description),
)

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

    BrowseSection("Simulcast Calendar", "Your local time", Palette.primary) {
        Column {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items((0..6).toList()) { index ->
                    val label = if (index == 0) "Today" else dayLabel(startOfToday + index * 86_400L)
                    val count = byDay[index]?.size ?: 0
                    PillChip(
                        palette = Palette,
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
                        WideCard(
                            palette = Palette,
                            title = entry.media.displayTitle,
                            imageUrl = entry.media.bannerImage ?: entry.media.posterUrl,
                            bottomChip = "EP ${entry.episode}",
                            topChip = timeLabel(entry.airingAt),
                            topChipColor = if (entry.airingAt <= nowSeconds) Color.White.copy(alpha = 0.85f) else Palette.tertiary,
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
    BrowseSection("Browse by Genre", null, Palette.secondary) {
        Column {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(GENRES) { genre ->
                    PillChip(Palette, genre, selected = genre == selected, onClick = { onGenre(if (genre == selected) null else genre) })
                }
            }
            AnimatedVisibility(visible = selected != null) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    when {
                        results == null -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Palette.primary, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
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
