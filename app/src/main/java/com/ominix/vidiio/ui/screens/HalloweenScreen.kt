package com.ominix.vidiio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.ui.browse.BrowsePalette
import com.ominix.vidiio.ui.browse.PosterCard
import com.ominix.vidiio.ui.viewmodel.MediaBrowseViewModel

/** Pumpkin orange and witchy purple over near-black. */
internal val HalloweenPalette = BrowsePalette(
    primary = Color(0xFFFF7518), secondary = Color(0xFF7B2CBF), tertiary = Color(0xFFFFB347),
    background = Color(0xFF0C0810), label = "HALLOWEEN"
)

/** All 100 Halloween films as a numbered poster grid. */
@Composable
fun HalloweenScreen(
    viewModel: MediaBrowseViewModel,
    onOpenMovie: (Movie) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val films = state.halloween
    val palette = HalloweenPalette

    Box(modifier = modifier.fillMaxSize().background(palette.background)) {
        if (films == null) {
            CircularProgressIndicator(color = palette.primary, modifier = Modifier.align(Alignment.Center))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                        Text(
                            "Top 100 Halloween Movies",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = palette.primary
                        )
                        Text(
                            "Ranked by rating and how many people voted",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }
                }
                itemsIndexed(films, key = { _, movie -> "hw-${movie.id}" }) { index, movie ->
                    PosterCard(
                        palette = palette, title = movie.title, imageUrl = movie.posterUrl,
                        subtitle = movie.year?.toString(), score = movie.rating, strip = null,
                        stripColor = palette.tertiary, cornerChip = "#${index + 1}", loading = false,
                        onClick = { onOpenMovie(movie) }
                    )
                }
            }
        }
    }
}
