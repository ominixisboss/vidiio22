package com.ominix.vidiio.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.data.repository.BrowseKind
import com.ominix.vidiio.data.repository.BrowseOptions
import com.ominix.vidiio.data.repository.BrowseSort
import com.ominix.vidiio.data.repository.Mood
import com.ominix.vidiio.data.repository.Provider
import com.ominix.vidiio.ui.browse.BrowseBackdrop
import com.ominix.vidiio.ui.browse.BrowseError
import com.ominix.vidiio.ui.browse.BrowseHero
import com.ominix.vidiio.ui.browse.BrowsePalette
import com.ominix.vidiio.ui.browse.BrowseSection
import com.ominix.vidiio.ui.browse.BrowseSkeleton
import com.ominix.vidiio.ui.browse.HeroSpec
import com.ominix.vidiio.ui.browse.MovieShelf
import com.ominix.vidiio.ui.browse.MoviesPalette
import com.ominix.vidiio.ui.browse.PillChip
import com.ominix.vidiio.ui.browse.PosterCard
import com.ominix.vidiio.ui.browse.TvPalette
import com.ominix.vidiio.ui.components.tvClickable
import com.ominix.vidiio.ui.viewmodel.BrowseState
import com.ominix.vidiio.ui.viewmodel.Genre
import com.ominix.vidiio.ui.viewmodel.MediaBrowseViewModel
import kotlinx.coroutines.launch
import java.util.Locale

private val MOVIE_GENRES = listOf(
    Genre(28, "Action"), Genre(12, "Adventure"), Genre(16, "Animation"), Genre(35, "Comedy"),
    Genre(80, "Crime"), Genre(99, "Documentary"), Genre(18, "Drama"), Genre(10751, "Family"),
    Genre(14, "Fantasy"), Genre(36, "History"), Genre(27, "Horror"), Genre(10402, "Music"),
    Genre(9648, "Mystery"), Genre(10749, "Romance"), Genre(878, "Sci-Fi"), Genre(53, "Thriller"),
    Genre(10752, "War"), Genre(37, "Western"), Genre(10770, "TV Movie"),
)

private val TV_GENRES = listOf(
    Genre(10759, "Action & Adventure"), Genre(16, "Animation"), Genre(35, "Comedy"),
    Genre(80, "Crime"), Genre(99, "Documentary"), Genre(18, "Drama"), Genre(10751, "Family"),
    Genre(10762, "Kids"), Genre(9648, "Mystery"), Genre(10763, "News"), Genre(10764, "Reality"),
    Genre(10765, "Sci-Fi & Fantasy"), Genre(10766, "Soap"), Genre(10767, "Talk"),
    Genre(10768, "War & Politics"), Genre(37, "Western"),
)

/** Wording and colours for one of the two pages. */
private class PageStyle(
    val palette: BrowsePalette,
    val what: String,
    val freshTitle: String,
    val freshSubtitle: String,
    val soonTitle: String,
    val soonSubtitle: String,
    val genres: List<Genre>,
    val typeLabel: String,
    val moods: List<Mood>,
    val providers: List<Provider>,
    val providerLabel: String,
)

private fun styleFor(kind: BrowseKind) = when (kind) {
    BrowseKind.MOVIES -> PageStyle(
        palette = MoviesPalette, what = "movies",
        freshTitle = "In Theaters", freshSubtitle = "Playing now",
        soonTitle = "Coming Soon", soonSubtitle = "Not out yet",
        genres = MOVIE_GENRES, typeLabel = "Movie",
        moods = BrowseOptions.movieMoods, providers = BrowseOptions.studios, providerLabel = "Studio"
    )
    BrowseKind.TV -> PageStyle(
        palette = TvPalette, what = "TV shows",
        freshTitle = "On The Air", freshSubtitle = "Currently airing new episodes",
        soonTitle = "Airing Today", soonSubtitle = "New episodes today",
        genres = TV_GENRES, typeLabel = "Series",
        moods = BrowseOptions.tvMoods, providers = BrowseOptions.networks, providerLabel = "Network"
    )
}

/**
 * A dedicated Movies or TV page, built like the Anime page: a hero, then shelves, then a filter
 * panel (genre, mood, decade, studio or network, sort). Every title opens its details page directly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaBrowseScreen(
    viewModel: MediaBrowseViewModel,
    onOpenMovie: (Movie) -> Unit,
    onOpenHalloween: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val style = remember(viewModel.kind) { styleFor(viewModel.kind) }

    Box(modifier = modifier.fillMaxSize().background(style.palette.background)) {
        BrowseBackdrop(style.palette)
        when {
            state.isLoadingEverything -> BrowseSkeleton()
            state.hasNothing -> BrowseError(style.palette, style.what, onRetry = viewModel::refresh)
            else -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize()
            ) {
                BrowseContent(style, state, viewModel, onOpenMovie, onOpenHalloween)
            }
        }
    }
}

@Composable
private fun BrowseContent(
    style: PageStyle,
    state: BrowseState,
    viewModel: MediaBrowseViewModel,
    onOpen: (Movie) -> Unit,
    onOpenHalloween: () -> Unit,
) {
    val palette = style.palette
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        val hero = state.trending.orEmpty().filter { !it.backdropUrl.isNullOrBlank() }.take(6)
        if (hero.isNotEmpty()) item(key = "hero") {
            val specs = remember(hero) { hero.map { it.toHeroSpec(style.typeLabel) } }
            BrowseHero(
                palette = palette,
                items = specs,
                loadingKey = null,
                onOpen = { index -> onOpen(hero[index]) },
                onFocused = {
                    // Wait out the focus scroll (which would leave the hero half off the top), then
                    // snap to the very top.
                    scope.launch {
                        kotlinx.coroutines.delay(150)
                        listState.scrollToItem(0)
                    }
                }
            )
        }

        // The seasonal list leads the Movies page whenever nothing is being filtered.
        state.halloween?.takeIf { state.filter.isEmpty }?.let { films ->
            item(key = "halloween") { HalloweenBanner(films, onOpen, onOpenHalloween) }
        }

        item(key = "filters") { FilterPanel(style, state, viewModel, onOpen) }

        if (state.filter.isEmpty) {
            shelf(palette, "trending", "Trending Today", "What people are watching right now", state.trending, onOpen)
            shelf(palette, "popular", "Popular", null, state.popular, onOpen)
            shelf(palette, "fresh", style.freshTitle, style.freshSubtitle, state.fresh, onOpen, accent = palette.tertiary)
            shelf(palette, "soon", style.soonTitle, style.soonSubtitle, state.soon, onOpen, accent = palette.secondary)
            shelf(palette, "top", "Top Rated", "Highest rated of all time", state.topRated, onOpen)
        }
    }
}

private fun LazyListScope.shelf(
    palette: BrowsePalette,
    key: String,
    title: String,
    subtitle: String?,
    movies: List<Movie>?,
    onOpen: (Movie) -> Unit,
    accent: Color = palette.primary,
) {
    if (movies.isNullOrEmpty()) return
    item(key = key) { MovieShelf(palette, title, subtitle, accent, movies, onOpen) }
}

// ───────────────────────────── Halloween ─────────────────────────────

/** A preview row of the top Halloween films with a tile that opens all 100. */
@Composable
private fun HalloweenBanner(films: List<Movie>, onOpen: (Movie) -> Unit, onSeeAll: () -> Unit) {
    val palette = HalloweenPalette
    BrowseSection("Top 100 Halloween Movies", "The best horror for Halloween night, ranked", palette.primary) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(films.take(12), key = { _, it -> "hw-${it.id}" }) { index, movie ->
                PosterCard(
                    palette = palette, title = movie.title, imageUrl = movie.posterUrl,
                    subtitle = movie.year?.toString(), score = movie.rating, strip = null,
                    stripColor = palette.tertiary, cornerChip = "#${index + 1}", loading = false,
                    onClick = { onOpen(movie) }
                )
            }
            item(key = "hw-all") { SeeAllTile(onSeeAll) }
        }
    }
}

@Composable
private fun SeeAllTile(onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .width(140.dp)
            .aspectRatio(2f / 3f)
            .tvClickable(onClick = onClick, shape = shape)
            .background(Brush.verticalGradient(listOf(HalloweenPalette.secondary, HalloweenPalette.primary))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(12.dp)) {
            Text("See all", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text("100", color = Color.White, fontWeight = FontWeight.Black, fontSize = 40.sp)
            Text("Halloween movies", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
        }
    }
}

// ───────────────────────────── Filters ─────────────────────────────

@Composable
private fun FilterPanel(
    style: PageStyle,
    state: BrowseState,
    viewModel: MediaBrowseViewModel,
    onOpen: (Movie) -> Unit,
) {
    val palette = style.palette
    val filter = state.filter

    BrowseSection("Find something", "Mix genre, mood, decade and ${style.providerLabel.lowercase(Locale.US)}", palette.secondary) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipRow("Genre") {
                items(style.genres, key = { it.id }) { g ->
                    PillChip(palette, g.name, selected = filter.genreId == g.id, onClick = {
                        viewModel.setGenre(if (filter.genreId == g.id) null else g)
                    })
                }
            }
            ChipRow("Mood") {
                items(style.moods, key = { it.label }) { m ->
                    PillChip(palette, m.label, selected = filter.mood == m, onClick = {
                        viewModel.setMood(if (filter.mood == m) null else m)
                    })
                }
            }
            ChipRow("Decade") {
                items(BrowseOptions.decades, key = { it.startYear }) { d ->
                    PillChip(palette, d.label, selected = filter.decade == d, onClick = {
                        viewModel.setDecade(if (filter.decade == d) null else d)
                    })
                }
            }
            ChipRow(style.providerLabel) {
                items(style.providers, key = { it.id }) { p ->
                    PillChip(palette, p.label, selected = filter.provider == p, onClick = {
                        viewModel.setProvider(if (filter.provider == p) null else p)
                    })
                }
            }
            ChipRow("Sort") {
                items(BrowseSort.entries.toList(), key = { it.name }) { s ->
                    PillChip(palette, s.label, selected = filter.sort == s, onClick = { viewModel.setSort(s) })
                }
                if (!filter.isEmpty) {
                    item(key = "clear") { PillChip(palette, "Clear filters", selected = false, onClick = viewModel::clearFilters) }
                }
            }

            AnimatedVisibility(visible = !filter.isEmpty) {
                FilterResults(palette, state, onOpen)
            }
        }
    }
}

@Composable
private fun ChipRow(label: String, content: LazyListScope.() -> Unit) {
    Column {
        Text(
            label.uppercase(Locale.US),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f),
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun FilterResults(palette: BrowsePalette, state: BrowseState, onOpen: (Movie) -> Unit) {
    val results = state.filterResults
    Column {
        Spacer(Modifier.height(4.dp))
        when {
            results == null -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = palette.primary, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
            }
            results.isEmpty() -> Text(
                "Nothing matches those filters. Try removing one.",
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            else -> LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(results, key = { "f-${it.source}-${it.id}" }) { movie ->
                    PosterCard(
                        palette = palette, title = movie.title, imageUrl = movie.posterUrl,
                        subtitle = movie.year?.toString(), score = movie.rating, strip = null,
                        stripColor = palette.tertiary, cornerChip = null, loading = false,
                        onClick = { onOpen(movie) }
                    )
                }
            }
        }
    }
}

private fun Movie.toHeroSpec(typeLabel: String) = HeroSpec(
    key = "$source-$id",
    title = title,
    imageUrl = backdropUrl ?: posterUrl,
    badge = "TRENDING",
    tag = null,
    meta = listOfNotNull(
        rating?.takeIf { it > 0.0 }?.let { "★ " + String.format(Locale.US, "%.1f", it) },
        typeLabel,
        year?.toString(),
    ).joinToString("  •  "),
    blurb = synopsis.orEmpty().trim(),
)
