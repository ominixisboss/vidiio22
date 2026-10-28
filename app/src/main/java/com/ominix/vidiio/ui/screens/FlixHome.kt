package com.ominix.vidiio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.data.model.WatchProgress
import com.ominix.vidiio.data.repository.BrowseOptions
import com.ominix.vidiio.ui.browse.FlixAccent
import com.ominix.vidiio.ui.browse.FlixHeader
import com.ominix.vidiio.ui.browse.FlixPoster
import com.ominix.vidiio.ui.browse.FlixTabs
import com.ominix.vidiio.ui.browse.LocalBrowseShortcuts
import com.ominix.vidiio.ui.components.tvClickable
import com.ominix.vidiio.ui.tv.LocalIsTv

/** One shelf of posters, optionally flagged "TODAY" for episodes airing today. */
data class FlixShelf(val title: String, val movies: List<Movie>, val todayRibbon: Boolean = false)

/** A curated studio list shown as a wide card ("Top MovieLists"). */
private class MovieList(val title: String, val count: String?, val studioId: Int?, val colors: List<Color>)

private val MovieLists = listOf(
    MovieList("Top 100 Halloween Movies", "100", null, listOf(Color(0xFF7B2CBF), Color(0xFFFF7518))),
    MovieList("Marvel Studios", null, 420, listOf(Color(0xFF8E0F24), Color(0xFFF2506B))),
    MovieList("Disney", null, 2, listOf(Color(0xFF2A2FC0), Color(0xFF4F9BFF))),
    MovieList("Pixar", null, 3, listOf(Color(0xFF3B2BB0), Color(0xFF8E5CE0))),
    MovieList("A24", null, 41077, listOf(Color(0xFF1F1F1F), Color(0xFF5A5A5A))),
    MovieList("Blumhouse", null, 3172, listOf(Color(0xFF5A0F0F), Color(0xFFEF6B1C))),
    MovieList("Studio Ghibli", null, 10342, listOf(Color(0xFF136F63), Color(0xFF7BD389))),
)

/**
 * Home in the streaming-guide style: a Featured / Movies / TV Shows / TV Guide strip, then dense rows
 * of rated posters, movie lists and the latest TV, with no hero.
 */
@Composable
fun FlixHomeContent(
    heroMovies: List<Movie>,
    shelves: List<FlixShelf>,
    continueWatching: List<WatchProgress>,
    spec: HomeStyleSpec,
    listState: LazyListState,
    onOpen: (Movie) -> Unit,
    onResume: (WatchProgress) -> Unit,
    onRemove: (String) -> Unit,
    onOpenMovies: () -> Unit,
    onOpenTv: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenHalloween: () -> Unit,
    onOpenCollection: (String, Int) -> Unit,
) {
    val heroScope = androidx.compose.runtime.rememberCoroutineScope()
    val shortcuts = LocalBrowseShortcuts.current?.takeIf { !LocalIsTv.current }
    // The hero arrives after the first frame; keep the page at the very top when it does, so the slider shows.
    androidx.compose.runtime.LaunchedEffect(heroMovies.isNotEmpty()) {
        if (heroMovies.isNotEmpty()) {
            kotlinx.coroutines.delay(200)
            listState.scrollToItem(0)
        }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(spec.rowGap)
        ) {
            if (heroMovies.isNotEmpty()) item(key = "hero") {
                // Focus scrolling can stop short of the top; once the slider is focused, snap there.
                Box(
                    Modifier.onFocusChanged { focus ->
                        if (focus.hasFocus) heroScope.launch {
                            kotlinx.coroutines.delay(150)
                            listState.scrollToItem(0)
                        }
                    }
                ) { HeroCarousel(heroMovies, spec, onOpen) }
            }

            item(key = "tabs") {
                Column {
                    // Phones overlay search / library shortcuts on the top right; leave them room.

                    FlixTabs(
                        selected = 0,
                        labels = listOf("Featured", "Movies", "TV Shows", "TV Guide"),
                        onSelect = { index ->
                            when (index) {
                                1 -> onOpenMovies()
                                2 -> onOpenTv()
                                3 -> onOpenGuide()
                            }
                        }
                    )
                }
            }

            if (continueWatching.isNotEmpty()) {
                item(key = "continue") {
                    ContinueWatchingRow(entries = continueWatching, spec = spec, onResume = onResume, onRemove = onRemove)
                }
            }

            shelves.forEachIndexed { index, shelf ->
                if (shelf.movies.isEmpty()) return@forEachIndexed
                item(key = "shelf-$index-${shelf.title}") { PosterShelf(shelf, onOpen) }
                // The curated lists sit after the first two shelves, like a streaming guide.
                if (index == 1) item(key = "lists") { MovieListsRow(onOpenHalloween, onOpenCollection) }
            }
        }

    }
}

@Composable
private fun PosterShelf(shelf: FlixShelf, onOpen: (Movie) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlixHeader(shelf.title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(shelf.movies, key = { "${shelf.title}-${it.source}-${it.id}" }) { movie ->
                Box {
                    FlixPoster(movie, onClick = { onOpen(movie) }, width = if (shelf.todayRibbon) 132.dp else 118.dp)
                    if (shelf.todayRibbon) {
                        Text(
                            "TODAY",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFE5242F))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MovieListsRow(onOpenHalloween: () -> Unit, onOpenCollection: (String, Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlixHeader("Top MovieLists")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(MovieLists, key = { it.title }) { list ->
                val shape = RoundedCornerShape(6.dp)
                Box(
                    modifier = Modifier
                        .width(240.dp)
                        .height(120.dp)
                        .tvClickable(
                            onClick = { list.studioId?.let { onOpenCollection(list.title, it) } ?: onOpenHalloween() },
                            shape = shape
                        )
                        .clip(shape)
                        .background(Brush.linearGradient(list.colors))
                        .padding(12.dp)
                ) {
                    list.count?.let {
                        Text(it, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopStart))
                    }
                    Text(
                        list.title,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.BottomStart)
                    )
                }
            }
        }
    }
}
