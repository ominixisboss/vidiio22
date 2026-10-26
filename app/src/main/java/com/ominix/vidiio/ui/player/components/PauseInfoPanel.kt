package com.ominix.vidiio.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ominix.vidiio.data.model.Episode
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.data.model.MovieType
import java.util.Locale

/**
 * What you are watching, shown while paused: the title, then the episode (name and synopsis)
 * for a series or the film's synopsis. Purely informational - it takes no focus, so the remote
 * keeps driving the controls underneath.
 */
@Composable
fun PauseInfoPanel(movie: Movie?, episode: Episode?, modifier: Modifier = Modifier) {
    if (movie == null) return
    val isSeries = movie.type == MovieType.TV_SHOW && episode != null

    val heading = if (isSeries) "S${episode!!.seasonNumber} · E${episode.episodeNumber}  ${episode.name}" else null
    val body = (if (isSeries) episode!!.overview else null)?.takeIf { it.isNotBlank() }
        ?: movie.synopsis?.takeIf { it.isNotBlank() }
    val meta = listOfNotNull(
        movie.rating?.takeIf { it > 0.0 }?.let { "★ " + String.format(Locale.US, "%.1f", it) },
        movie.year?.toString(),
        if (isSeries) "Series" else "Movie",
    ).joinToString("  •  ")

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.85f), 1f to Color.Transparent))
            .padding(start = 40.dp, end = 80.dp, top = 120.dp, bottom = 140.dp)
            .widthIn(max = 560.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Text("You're watching", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.6f))
        Spacer(Modifier.height(4.dp))
        Text(
            movie.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Text(meta, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
        if (heading != null) {
            Spacer(Modifier.height(14.dp))
            Text(heading, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (body != null) {
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f), maxLines = 7, overflow = TextOverflow.Ellipsis)
        }
    }
}
