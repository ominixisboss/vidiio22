package com.ominix.vidiio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.unit.sp
import com.ominix.vidiio.ui.browse.BrowseHero
import com.ominix.vidiio.ui.browse.HeroSpec
import com.ominix.vidiio.ui.browse.FlixHeader
import com.ominix.vidiio.ui.browse.FlixTabs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.data.repository.BrowseKind
import com.ominix.vidiio.data.repository.BrowseList
import com.ominix.vidiio.data.repository.BrowseOptions
import com.ominix.vidiio.data.repository.BrowseSort
import com.ominix.vidiio.data.repository.Mood
import com.ominix.vidiio.data.repository.Provider
import com.ominix.vidiio.ui.browse.BrowseError
import com.ominix.vidiio.ui.browse.BrowsePalette
import com.ominix.vidiio.ui.browse.BrowseSkeleton
import com.ominix.vidiio.ui.browse.FlixAccent
import com.ominix.vidiio.ui.browse.FlixBackground
import com.ominix.vidiio.ui.browse.FlixCaption
import com.ominix.vidiio.ui.browse.FlixPoster
import com.ominix.vidiio.ui.browse.GradientTile
import com.ominix.vidiio.ui.browse.MoviesPalette
import com.ominix.vidiio.ui.browse.TileRow
import com.ominix.vidiio.ui.browse.TvPalette
import com.ominix.vidiio.ui.viewmodel.BrowseState
import com.ominix.vidiio.ui.viewmodel.Genre
import com.ominix.vidiio.ui.viewmodel.MediaBrowseViewModel

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

/** Wording and options for one of the two pages. */
private class PageStyle(
    val palette: BrowsePalette,
    val what: String,
    val lists: List<Pair<BrowseList, String>>,
    val genres: List<Genre>,
    val moods: List<Mood>,
    val providers: List<Provider>,
    val providerLabel: String,
    val typeLabel: String,
)

private fun styleFor(kind: BrowseKind) = when (kind) {
    BrowseKind.MOVIES -> PageStyle(
        palette = MoviesPalette, what = "movies",
        lists = listOf(
            BrowseList.TRENDING to "Trending Today", BrowseList.POPULAR to "Popular",
            BrowseList.TOP_RATED to "Top Rated", BrowseList.FRESH to "In Theaters",
            BrowseList.SOON to "Coming Soon"
        ),
        genres = MOVIE_GENRES, moods = BrowseOptions.movieMoods,
        providers = BrowseOptions.studios, providerLabel = "Studio", typeLabel = "Movie"
    )
    BrowseKind.TV -> PageStyle(
        palette = TvPalette, what = "TV shows",
        lists = listOf(
            BrowseList.TRENDING to "Trending Today", BrowseList.POPULAR to "Popular",
            BrowseList.TOP_RATED to "Top Rated", BrowseList.FRESH to "On The Air",
            BrowseList.SOON to "Airing Today"
        ),
        genres = TV_GENRES, moods = BrowseOptions.tvMoods,
        providers = BrowseOptions.networks, providerLabel = "Network", typeLabel = "Series"
    )
}

/**
 * A Movies or TV page in the streaming-guide style: rows of gradient tiles (ready-made lists,
 * streaming services, genres, moods, decades, studios or networks, sort) over a poster grid of
 * whatever is picked. Tiles in different rows combine; every poster opens its details page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaBrowseScreen(
    viewModel: MediaBrowseViewModel,
    onOpenMovie: (Movie) -> Unit,
    onOpenHalloween: () -> Unit = {},
    onTab: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val style = remember(viewModel.kind) { styleFor(viewModel.kind) }

    Box(modifier = modifier.fillMaxSize().background(FlixBackground)) {
        when {
            state.isLoadingEverything -> BrowseSkeleton()
            state.hasNothing -> BrowseError(style.palette, style.what, onRetry = viewModel::refresh)
            else -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize()
            ) {
                BrowseContent(style, state, viewModel, onOpenMovie, onOpenHalloween, onTab)
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
    onTab: (Int) -> Unit,
) {
    val filter = state.filter
    val results: List<Movie>? = if (filter.isEmpty) {
        when (state.list) {
            BrowseList.TRENDING -> state.trending
            BrowseList.POPULAR -> state.popular
            BrowseList.TOP_RATED -> state.topRated
            BrowseList.FRESH -> state.fresh
            BrowseList.SOON -> state.soon
        }
    } else state.filterResults
    val isMovies = viewModel.kind == BrowseKind.MOVIES
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    // The filters live at the bottom, so bring the results into view when one is picked.
    val resultsIndex = 2
    LaunchedEffect(filter) {
        if (!filter.isEmpty) gridState.animateScrollToItem(resultsIndex)
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(110.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        full("tabs") {
            FlixTabs(
                selected = if (isMovies) 1 else 2,
                labels = listOf("Featured", "Movies", "TV Shows", "TV Guide"),
                onSelect = onTab
            )
        }

        full("browse") {
            Column(Modifier.padding(top = 4.dp)) {
                FlixCaption(if (isMovies) "Movies" else "TV Shows")
                TileRow {
                    var i = 0
                    if (isMovies && state.halloween != null) {
                        item(key = "halloween") { GradientTile("Top 100 Halloween Movies", 1, false, onOpenHalloween) }
                        i++
                    }
                    items(style.lists, key = { it.first.name }) { (list, label) ->
                        GradientTile(label, i + style.lists.indexOf(list to label), filter.isEmpty && state.list == list, { viewModel.setList(list) })
                    }
                    items(BrowseOptions.streamers, key = { "s${it.id}" }) { s ->
                        GradientTile(
                            "Popular on ${s.label}", BrowseOptions.streamers.indexOf(s) + 2,
                            filter.streamer == s, { viewModel.setStreamer(if (filter.streamer == s) null else s) }
                        )
                    }
                    if (!filter.isEmpty) {
                        item(key = "clear") { GradientTile("Clear filters", 2, false, viewModel::clearFilters) }
                    }
                }
            }
        }

        when {
            results == null -> full("loading") {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = FlixAccent, strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
                }
            }
            results.isEmpty() -> full("empty") {
                Text(
                    "Nothing matches those filters. Try removing one.",
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }
            else -> items(results, key = { "r-${it.source}-${it.id}" }) { movie ->
                FlixPoster(
                    movie = movie, onClick = { onOpen(movie) }, width = null,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        // Everything for narrowing the list sits together at the bottom.
        full("filters-title") {
            Column(Modifier.padding(top = 20.dp)) {
                FlixHeader("Find something")
                Text(
                    "Mix genre, mood, decade and ${style.providerLabel.lowercase(Locale.US)}",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 14.dp, top = 2.dp)
                )
            }
        }
        full("genre") {
            FlixCaption("Genre")
            TileRow {
                items(style.genres, key = { it.id }) { g ->
                    GradientTile(g.name, style.genres.indexOf(g), filter.genreId == g.id, {
                        viewModel.setGenre(if (filter.genreId == g.id) null else g)
                    })
                }
            }
        }
        full("mood") {
            FlixCaption("Mood")
            TileRow {
                items(style.moods, key = { it.label }) { m ->
                    GradientTile(m.label, style.moods.indexOf(m) + 1, filter.mood == m, {
                        viewModel.setMood(if (filter.mood == m) null else m)
                    })
                }
            }
        }
        full("decade") {
            FlixCaption("Decade")
            TileRow {
                items(BrowseOptions.decades, key = { it.startYear }) { d ->
                    GradientTile(d.label, BrowseOptions.decades.indexOf(d) + 2, filter.decade == d, {
                        viewModel.setDecade(if (filter.decade == d) null else d)
                    })
                }
            }
        }
        full("provider") {
            FlixCaption(style.providerLabel)
            TileRow {
                items(style.providers, key = { it.id }) { p ->
                    GradientTile(p.label, style.providers.indexOf(p) + 3, filter.provider == p, {
                        viewModel.setProvider(if (filter.provider == p) null else p)
                    })
                }
            }
        }
        full("sort") {
            FlixCaption("Sort")
            TileRow {
                items(BrowseSort.entries.toList(), key = { it.name }) { s ->
                    GradientTile(s.label, s.ordinal, filter.sort == s, { viewModel.setSort(s) })
                }
                if (!filter.isEmpty) {
                    item(key = "clear2") { GradientTile("Clear filters", 2, false, viewModel::clearFilters) }
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

/** A grid item spanning the full width, for the tile rows between the posters. */
private fun LazyGridScope.full(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { androidx.compose.foundation.layout.Column { content() } }
}
