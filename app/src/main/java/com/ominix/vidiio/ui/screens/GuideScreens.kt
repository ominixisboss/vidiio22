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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.ui.browse.FlixAccent
import com.ominix.vidiio.ui.browse.FlixBackground
import com.ominix.vidiio.ui.browse.FlixHeader
import com.ominix.vidiio.ui.browse.FlixPoster
import com.ominix.vidiio.ui.components.tvClickable
import com.ominix.vidiio.ui.viewmodel.CollectionViewModel
import com.ominix.vidiio.ui.viewmodel.TvGuideViewModel
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** A week strip (three days back, four ahead) over the shows airing on the picked day. */
@Composable
fun TvGuideScreen(
    viewModel: TvGuideViewModel,
    onOpenMovie: (Movie) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val days = remember { (-3L..4L).map { LocalDate.now().plusDays(it) } }

    Column(modifier.fillMaxSize().background(FlixBackground).statusBarsPadding()) {
        FlixHeader("TV Guide", Modifier.padding(top = 14.dp, bottom = 8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(days, key = { it.toString() }) { day ->
                val selected = day == state.date
                Column(
                    modifier = Modifier
                        .width(48.dp)
                        .tvClickable(onClick = { viewModel.select(day) }, shape = RoundedCornerShape(24.dp))
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp
                    )
                    Box(
                        Modifier.padding(top = 6.dp).size(38.dp)
                            .then(if (selected) Modifier.border(2.dp, FlixAccent, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(day.dayOfMonth.toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
        Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))

        val shows = state.shows
        when {
            shows == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = FlixAccent)
            }
            shows.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (state.failed) "Couldn't load the guide. Check your connection." else "Nothing listed for this day.",
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
                items(shows, key = { "${it.source}-${it.id}" }) { show ->
                    GuideRow(show, onClick = { onOpenMovie(show) })
                }
            }
        }
    }
}

@Composable
private fun GuideRow(show: Movie, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvClickable(onClick = onClick, shape = RoundedCornerShape(6.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = show.posterUrl,
            contentDescription = show.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(64.dp).height(94.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.06f))
        )
        Column(Modifier.padding(start = 14.dp)) {
            Text(show.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(show.rating?.takeIf { it > 0.0 }?.let { "★ " + String.format(Locale.US, "%.1f", it) }, show.year?.toString())
            if (meta.isNotEmpty()) Text(meta.joinToString("  •  "), color = FlixAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            show.synopsis?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A studio's best films as a poster grid. */
@Composable
fun CollectionScreen(
    title: String,
    viewModel: CollectionViewModel,
    onOpenMovie: (Movie) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val films = state.films

    Box(modifier.fillMaxSize().background(FlixBackground)) {
        if (films == null) {
            CircularProgressIndicator(color = FlixAccent, modifier = Modifier.align(Alignment.Center))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    FlixHeader(title, Modifier.padding(bottom = 4.dp))
                }
                if (films.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        if (state.failed) "Couldn't load this list." else "Nothing here yet.",
                        color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 14.dp)
                    )
                }
                items(films, key = { "c-${it.source}-${it.id}" }) { movie ->
                    FlixPoster(movie, onClick = { onOpenMovie(movie) }, width = null)
                }
            }
        }
    }
}
