package com.ominix.vidiio.ui.screens

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.data.repository.BrowseKind
import com.ominix.vidiio.ui.browse.BrowseBackdrop
import com.ominix.vidiio.ui.browse.BrowseError
import com.ominix.vidiio.ui.browse.BrowseHero
import com.ominix.vidiio.ui.browse.BrowsePalette
import com.ominix.vidiio.ui.browse.BrowseSection
import com.ominix.vidiio.ui.browse.BrowseSkeleton
import com.ominix.vidiio.ui.browse.HeroSpec
import com.ominix.vidiio.ui.browse.MoviesPalette
import com.ominix.vidiio.ui.browse.MovieShelf
import com.ominix.vidiio.ui.browse.PillChip
import com.ominix.vidiio.ui.browse.TvPalette
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
    Genre(10752, "War"), Genre(37, "Western"),
)

private val TV_GENRES = listOf(
    Genre(10759, "Action & Adventure"), Genre(16, "Animation"), Genre(35, "Comedy"),
    Genre(80, "Crime"), Genre(99, "Documentary"), Genre(18, "Drama"), Genre(10751, "Family"),
    Genre(10762, "Kids"), Genre(9648, "Mystery"), Genre(10764, "Reality"),
    Genre(10765, "Sci-Fi & Fantasy"), Genre(10768, "War & Politics"), Genre(37, "Western"),
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
)

private fun styleFor(kind: BrowseKind) = when (kind) {
    BrowseKind.MOVIES -> PageStyle(
        palette = MoviesPalette, what = "movies",
        freshTitle = "In Theaters", freshSubtitle = "Playing now",
        soonTitle = "Coming Soon", soonSubtitle = "Not out yet",
        genres = MOVIE_GENRES, typeLabel = "Movie"
    )
    BrowseKind.TV -> PageStyle(
        palette = TvPalette, what = "TV shows",
        freshTitle = "On The Air", freshSubtitle = "Currently airing new episodes",
        soonTitle = "Airing Today", soonSubtitle = "New episodes today",
        genres = TV_GENRES, typeLabel = "Series"
    )
}

/**
 * A dedicated Movies or TV page, built like the Anime page: a hero, then shelves, then genre
 * browsing. Every title opens its details page directly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaBrowseScreen(
    viewModel: MediaBrowseViewModel,
    onOpenMovie: (Movie) -> Unit,
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
                BrowseContent(style, state, onOpenMovie, viewModel::selectGenre)
            }
        }
    }
}

@Composable
private fun BrowseContent(
    style: PageStyle,
    state: BrowseState,
    onOpen: (Movie) -> Unit,
    onGenre: (Genre?) -> Unit,
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
                onFocused = { scope.launch { listState.animateScrollToItem(0) } }
            )
        }

        shelf(palette, "trending", "Trending Today", "What people are watching right now", state.trending, onOpen)
        shelf(palette, "popular", "Popular", null, state.popular, onOpen)
        shelf(palette, "fresh", style.freshTitle, style.freshSubtitle, state.fresh, onOpen, accent = palette.tertiary)
        shelf(palette, "soon", style.soonTitle, style.soonSubtitle, state.soon, onOpen, accent = palette.secondary)

        item(key = "genres") {
            GenreBrowse(palette, style.genres, state.genre, state.genreResults, onOpen, onGenre)
        }

        shelf(palette, "top", "Top Rated", "Highest rated of all time", state.topRated, onOpen)
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

@Composable
private fun GenreBrowse(
    palette: BrowsePalette,
    genres: List<Genre>,
    selected: Genre?,
    results: List<Movie>?,
    onOpen: (Movie) -> Unit,
    onGenre: (Genre?) -> Unit,
) {
    BrowseSection("Browse by Genre", null, palette.secondary) {
        Column {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(genres, key = { it.id }) { genre ->
                    PillChip(palette, genre.name, selected = genre == selected, onClick = { onGenre(if (genre == selected) null else genre) })
                }
            }
            AnimatedVisibility(visible = selected != null) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    when {
                        results == null -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = palette.primary, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                        }
                        results.isEmpty() -> Text(
                            "Nothing found for ${selected?.name.orEmpty()}",
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        else -> LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(results, key = { "genre-${it.source}-${it.id}" }) { movie ->
                                com.ominix.vidiio.ui.browse.PosterCard(
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
