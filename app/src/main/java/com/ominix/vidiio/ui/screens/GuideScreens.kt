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
import androidx.compose.ui.focus.focusRequester
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
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import com.ominix.vidiio.data.guide.GuideEntry
import java.time.format.TextStyle
import java.util.Locale

/**
 * The TV Guide: a strip of days (three back, eight ahead) with a "Today" jump, over the shows airing
 * that day grouped by time, each with its episode number and network. Streaming releases follow the
 * timed shows. Selecting a row opens that show's page.
 */
@Composable
fun TvGuideScreen(
    viewModel: TvGuideViewModel,
    onOpenShow: (Movie, Int?, Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    val days = remember { (-3L..8L).map { today.plusDays(it) } }
    val dayListState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = 2)
    var opening by remember { androidx.compose.runtime.mutableStateOf(false) }
    val todayFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(400)
        runCatching { todayFocus.requestFocus() }
    }

    fun open(entry: GuideEntry) {
        if (opening) return
        opening = true
        scope.launch {
            val match = runCatching { viewModel.resolve(entry) }.getOrNull()
            opening = false
            if (match != null) onOpenShow(match, entry.season, entry.episode)
            else android.widget.Toast.makeText(context, "Could not find ${entry.showName}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier.fillMaxSize().background(FlixBackground).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            FlixHeader("TV Guide", Modifier.weight(1f))
            Text(
                "Today",
                color = if (state.date == today) FlixAccent else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier
                    .padding(end = 14.dp)
                    .tvClickable(onClick = {
                        viewModel.select(today)
                        scope.launch { dayListState.animateScrollToItem(2) }
                    }, shape = RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
        LazyRow(
            state = dayListState,
            contentPadding = PaddingValues(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(days, key = { it.toString() }) { day ->
                val selected = day == state.date
                Column(
                    modifier = Modifier
                        .width(54.dp)
                        .then(if (day == today) Modifier.focusRequester(todayFocus) else Modifier)
                        .tvClickable(onClick = { viewModel.select(day) }, shape = RoundedCornerShape(27.dp))
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(Locale.getDefault()),
                        color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp
                    )
                    Box(
                        Modifier.padding(top = 4.dp).size(38.dp)
                            .then(if (selected) Modifier.background(FlixAccent, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(day.dayOfMonth.toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Text(
                        if (day == today) "TODAY" else day.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(Locale.getDefault()),
                        color = if (day == today) FlixAccent else Color.White.copy(alpha = 0.4f), fontSize = 10.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
        Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))

        val shows = state.shows
        when {
            shows == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = FlixAccent)
            }
            shows.isEmpty() -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (state.failed) "Couldn't load the guide. Check your connection." else "Nothing listed for this day.",
                    color = Color.White.copy(alpha = 0.6f)
                )
                if (state.failed) Text(
                    "Try again",
                    color = FlixAccent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .tvClickable(onClick = viewModel::retry, shape = RoundedCornerShape(20.dp))
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                )
            }
            else -> {
                val timeFormat = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
                val groups = remember(shows) { shows.groupBy { it.time?.withMinute(0)?.withSecond(0) } }
                LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
                    groups.forEach { (slot, entries) ->
                        item(key = "slot-${slot ?: "stream"}") {
                            Text(
                                text = slot?.format(timeFormat) ?: "Streaming",
                                color = FlixAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(start = 14.dp, top = 16.dp, bottom = 4.dp)
                            )
                        }
                        items(entries, key = { "g-${it.showName}" }) { entry ->
                            GuideRow(entry, timeFormat, onClick = { open(entry) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideRow(entry: GuideEntry, timeFormat: DateTimeFormatter, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvClickable(onClick = onClick, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = entry.posterUrl,
            contentDescription = entry.showName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(56.dp).height(82.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.06f))
        )
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(entry.showName, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = listOfNotNull(entry.episodeCode, entry.channel, entry.time?.format(timeFormat)).joinToString("  •  ")
            if (line.isNotEmpty()) Text(line, color = FlixAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            entry.episodeTitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        entry.rating?.takeIf { it > 0.0 }?.let {
            Text("★ " + String.format(Locale.US, "%.1f", it), color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
