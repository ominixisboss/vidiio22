package com.ominix.vidiio.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.ui.components.tvClickable
import java.util.Locale

/** The streaming-guide layout, in the app theme's own colours: gradient category tiles, rated posters. */
val FlixAccent: Color
    @Composable get() = MaterialTheme.colorScheme.primary
val FlixBackground: Color
    @Composable get() = MaterialTheme.colorScheme.background


/** A rounded gradient category tile. [index] picks the colour so neighbours differ. */
@Composable
fun GradientTile(text: String, index: Int, selected: Boolean, onClick: () -> Unit, width: Dp = 122.dp) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .width(width)
            .height(64.dp)
            .tvClickable(onClick = onClick, shape = shape)
            .background(Brush.linearGradient(tileGradient(index)), shape)
            .then(if (selected) Modifier.border(3.dp, Color.White, shape) else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            lineHeight = 17.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** A scrolling row of tiles. */
@Composable
fun TileRow(content: LazyListScope.() -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

/** A poster with the star score bottom-left over a fade, like a streaming guide. */
@Composable
fun FlixPoster(movie: Movie, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp? = 118.dp, corner: Dp = 6.dp) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .aspectRatio(2f / 3f)
            .tvClickable(onClick = onClick, shape = shape)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
    ) {
        AsyncImage(
            model = movie.posterUrl,
            contentDescription = movie.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(44.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))))
        )
        movie.rating?.takeIf { it > 0.0 }?.let { value ->
            Row(
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Star, null, tint = FlixAccent, modifier = Modifier.size(15.dp))
                Text(
                    text = String.format(Locale.US, "%.1f", value),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }
    }
}

/** A section title in the guide style. */
@Composable
fun FlixHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(horizontal = 14.dp)
    )
}

/** A small caption above a tile row. */
@Composable
fun FlixCaption(text: String) {
    Text(
        text = text.uppercase(Locale.US),
        color = Color.White.copy(alpha = 0.5f),
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 14.dp, bottom = 4.dp)
    )
}

/** The Featured / Movies / TV Shows / TV Guide strip across the top of Home. */
@Composable
fun FlixTabs(selected: Int, labels: List<String>, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .tvClickable(onClick = { onSelect(i) }, shape = RoundedCornerShape(4.dp))
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = label,
                            color = if (i == selected) Color.White else Color.White.copy(alpha = 0.55f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1
                        )
                        Box(
                            Modifier
                                .padding(top = 6.dp)
                                .width(if (i == selected) 56.dp else 0.dp)
                                .height(3.dp)
                                .background(FlixAccent)
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
    }
}

/** Tile colours drawn from the theme: the accent, its deeper shade, and the theme's dark neutrals. */
@Composable
private fun tileGradient(index: Int): List<Color> {
    val accent = MaterialTheme.colorScheme.primary
    val deep = Color(
        red = accent.red * 0.45f, green = accent.green * 0.45f, blue = accent.blue * 0.45f, alpha = 1f
    )
    val surface = MaterialTheme.colorScheme.surfaceVariant
    return when (index % 4) {
        0 -> listOf(accent, deep)
        1 -> listOf(surface, surface.copy(alpha = 0.55f))
        2 -> listOf(deep, accent.copy(alpha = 0.75f))
        else -> listOf(surface.copy(alpha = 0.85f), deep)
    }
}
