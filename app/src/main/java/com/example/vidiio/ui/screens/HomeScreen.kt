package com.example.vidiio.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircleFilled
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.vidiio.data.model.Movie
import com.example.vidiio.data.model.TOP10_LABEL
import com.example.vidiio.data.model.WatchProgress
import com.example.vidiio.ui.components.RiveLoader
import com.example.vidiio.ui.components.tvClickable
import com.example.vidiio.ui.viewmodel.HomeUiState
import com.example.vidiio.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToDetails: (Movie) -> Unit,
    onResumeWatching: (WatchProgress) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()
    val homeStyle by viewModel.homeStyle.collectAsState()
    val scrollState = rememberLazyListState()

    val themeAccent = MaterialTheme.colorScheme.primary
    val spec = remember(homeStyle, themeAccent) { homeStyle.spec(themeAccent) }
    val background = spec.background ?: MaterialTheme.colorScheme.background

    val topBarAlpha by remember {
        derivedStateOf {
            if (scrollState.firstVisibleItemIndex > 0) 0f
            else (1f - (scrollState.firstVisibleItemScrollOffset.toFloat() / 300f)).coerceIn(0f, 1f)
        }
    }
    val backgroundAlpha by remember {
        derivedStateOf {
            if (scrollState.firstVisibleItemIndex > 0) 1f
            else (scrollState.firstVisibleItemScrollOffset.toFloat() / 300f).coerceIn(0f, 1f)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(background)) {
        when (val state = uiState) {
            is HomeUiState.Loading -> {
                RiveLoader(modifier = Modifier.align(Alignment.Center))
            }
            is HomeUiState.Success -> {
                val heroMovies = remember(state.categories) {
                    state.categories.firstOrNull()?.movies?.take(5) ?: emptyList()
                }
                val rows = remember(state.categories) {
                    state.categories.filter { it.movies.isNotEmpty() }
                }

                LazyColumn(
                    state = scrollState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(spec.rowGap)
                ) {
                    if (heroMovies.isNotEmpty()) {
                        item {
                            if (spec.heroKind == HeroKind.CAROUSEL) {
                                HeroCarousel(heroMovies, spec, onNavigateToDetails)
                            } else {
                                HeroStatic(heroMovies.first(), spec, onNavigateToDetails)
                            }
                        }
                    }
                    if (continueWatching.isNotEmpty()) {
                        item {
                            ContinueWatchingRow(
                                entries = continueWatching,
                                spec = spec,
                                onResume = onResumeWatching,
                                onRemove = { viewModel.removeContinueWatching(it) }
                            )
                        }
                    }
                    items(rows) { category ->
                        when {
                            category.name == TOP10_LABEL && spec.showTop10 ->
                                Top10Section(category.movies, spec, onNavigateToDetails)
                            category.name == TOP10_LABEL -> Unit // hidden for this style
                            else -> CategorySection(category.name, category.movies, spec, onNavigateToDetails)
                        }
                    }
                }

                TopAppBar(
                    title = {
                        Text(
                            "VIDIIO",
                            color = spec.accent,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-1).sp,
                            fontSize = 24.sp,
                            modifier = Modifier.alpha(topBarAlpha)
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = background.copy(alpha = backgroundAlpha),
                        titleContentColor = spec.accent
                    )
                )
            }
            is HomeUiState.Error -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                    Button(onClick = { viewModel.refresh() }) { Text("Retry") }
                }
            }
        }
    }
}

/* ----------------------------- Hero variants ----------------------------- */

@Composable
private fun HeroButtons(movie: Movie, spec: HomeStyleSpec, center: Boolean, onClick: (Movie) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalArrangement = if (center) Arrangement.Center else Arrangement.Start
    ) {
        Button(
            onClick = { onClick(movie) },
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            modifier = if (center) Modifier.weight(1f).height(48.dp) else Modifier.height(48.dp)
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Play", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Button(
            onClick = { onClick(movie) },
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.DarkGray.copy(alpha = 0.8f),
                contentColor = Color.White
            ),
            modifier = (if (center) Modifier.weight(1f) else Modifier)
                .height(48.dp)
                .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
        ) {
            Icon(Icons.Rounded.Info, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Info", fontWeight = FontWeight.Bold)
        }
    }
}

/** Backdrop image with a slow, looping zoom + pan (Ken Burns). */
@Composable
fun KenBurnsImage(model: Any?, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "kenburns")
    val scale by t.animateFloat(
        initialValue = 1f, targetValue = 1.16f,
        animationSpec = infiniteRepeatable(tween(22000, easing = LinearEasing), RepeatMode.Reverse),
        label = "kbScale"
    )
    val pan by t.animateFloat(
        initialValue = -0.035f, targetValue = 0.035f,
        animationSpec = infiniteRepeatable(tween(30000, easing = LinearEasing), RepeatMode.Reverse),
        label = "kbPan"
    )
    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.graphicsLayer {
            scaleX = scale; scaleY = scale
            translationX = size.width * pan
        }
    )
}

@Composable
private fun heroScrim(bottom: Color) = Brush.verticalGradient(
    colors = listOf(
        Color.Black.copy(alpha = 0.35f),
        Color.Transparent,
        bottom.copy(alpha = 0.85f),
        bottom
    )
)

@Composable
fun HeroCarousel(movies: List<Movie>, spec: HomeStyleSpec, onMovieClick: (Movie) -> Unit) {
    val pagerState = rememberPagerState(pageCount = { movies.size })
    val scrim = spec.background ?: Color.Black

    LaunchedEffect(movies.size) {
        while (true) {
            delay(6000)
            pagerState.animateScrollToPage((pagerState.currentPage + 1) % movies.size)
        }
    }

    Box(modifier = Modifier.fillMaxWidth().height(spec.heroHeight)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val movie = movies[page]
            Box(Modifier.fillMaxSize().tvClickable(onClick = { onMovieClick(movie) }, focusScale = 1f)) {
                if (spec.kenBurns) {
                    KenBurnsImage(movie.backdropUrl ?: movie.posterUrl, Modifier.fillMaxSize())
                } else {
                    AsyncImage(
                        model = movie.backdropUrl ?: movie.posterUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Box(Modifier.fillMaxSize().background(heroScrim(scrim)))
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = movie.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    HeroButtons(movie, spec, center = true, onClick = onMovieClick)
                }
            }
        }
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(movies.size) { index ->
                val selected = pagerState.currentPage == index
                Box(
                    Modifier
                        .size(if (selected) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (selected) spec.accent else Color.White.copy(alpha = 0.4f))
                )
            }
        }
    }
}

@Composable
fun HeroStatic(movie: Movie, spec: HomeStyleSpec, onMovieClick: (Movie) -> Unit) {
    val scrim = spec.background ?: Color.Black
    val inset = spec.heroKind == HeroKind.SPOTLIGHT
    val corner = if (inset) 20.dp else 0.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(spec.heroHeight)
            .then(if (inset) Modifier.padding(horizontal = 12.dp, vertical = 8.dp) else Modifier)
            .tvClickable(onClick = { onMovieClick(movie) }, shape = RoundedCornerShape(corner), focusScale = 1f)
    ) {
        if (spec.kenBurns) {
            KenBurnsImage(movie.backdropUrl ?: movie.posterUrl, Modifier.fillMaxSize())
        } else {
            AsyncImage(
                model = movie.backdropUrl ?: movie.posterUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Box(Modifier.fillMaxSize().background(heroScrim(scrim)))

        val alignment = if (spec.heroCenterText) Alignment.BottomCenter else Alignment.BottomStart
        Column(
            modifier = Modifier
                .align(alignment)
                .padding(bottom = if (spec.heroKind == HeroKind.COMPACT) 20.dp else 32.dp),
            horizontalAlignment = if (spec.heroCenterText) Alignment.CenterHorizontally else Alignment.Start
        ) {
            Text(
                text = movie.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = if (spec.heroCenterText) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            movie.synopsis?.takeIf { spec.heroKind == HeroKind.BANNER }?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(0.85f)
                )
            }
            Spacer(Modifier.height(14.dp))
            if (spec.heroKind == HeroKind.COMPACT) {
                Button(
                    onClick = { onMovieClick(movie) },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = spec.accent,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.padding(horizontal = 24.dp).height(46.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Play", fontWeight = FontWeight.Bold)
                }
            } else {
                HeroButtons(movie, spec, center = spec.heroCenterText, onClick = onMovieClick)
            }
        }
    }
}

/* ----------------------------- Rows ----------------------------- */

@Composable
private fun RowHeader(text: String, spec: HomeStyleSpec) {
    Text(
        text = if (spec.uppercaseHeaders) text.uppercase() else text,
        fontSize = spec.rowHeaderSize,
        fontWeight = spec.rowHeaderWeight,
        letterSpacing = if (spec.uppercaseHeaders) 1.sp else 0.sp,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun HomeCard(
    movie: Movie,
    spec: HomeStyleSpec,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var appeared by remember { mutableStateOf(!spec.staggerIn) }
    LaunchedEffect(Unit) { appeared = true }
    val appear by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(350),
        label = "cardAppear"
    )
    val shape = RoundedCornerShape(spec.cardCorner)

    Column(modifier = modifier.width(spec.cardWidth)) {
        Box(
            modifier = Modifier
                .width(spec.cardWidth)
                .height(spec.cardHeight)
                .graphicsLayer {
                    alpha = appear
                    translationY = (1f - appear) * 28.dp.toPx()
                }
                .tvClickable(onClick = onClick, shape = shape, focusScale = 1.08f)
                .border(0.5.dp, Color.White.copy(alpha = 0.12f), shape)
        ) {
            AsyncImage(
                model = if (spec.card == CardKind.LANDSCAPE) (movie.backdropUrl ?: movie.posterUrl) else movie.posterUrl,
                contentDescription = movie.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (spec.ratingBadge) {
                movie.rating?.takeIf { it > 0.0 }?.let { rating ->
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Star, null, tint = spec.accent, modifier = Modifier.size(11.dp))
                        Text(
                            text = String.format("%.1f", rating),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }
                }
            }
        }
        if (spec.cardShowTitle) {
            Text(
                text = movie.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp).width(spec.cardWidth)
            )
        }
    }
}

@Composable
fun CategorySection(
    name: String,
    movies: List<Movie>,
    spec: HomeStyleSpec,
    onMovieClick: (Movie) -> Unit
) {
    Column {
        RowHeader(name, spec)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(movies) { movie ->
                HomeCard(movie, spec, onClick = { onMovieClick(movie) })
            }
        }
    }
}

@Composable
fun Top10Section(
    movies: List<Movie>,
    spec: HomeStyleSpec,
    onMovieClick: (Movie) -> Unit
) {
    Column {
        RowHeader("Top 10 This Week", spec)
        LazyRow(
            contentPadding = PaddingValues(start = 24.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(movies.take(10)) { index, movie ->
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${index + 1}",
                        fontSize = 88.sp,
                        fontWeight = FontWeight.Black,
                        color = spec.accent.copy(alpha = 0.4f),
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    HomeCard(movie, spec, onClick = { onMovieClick(movie) })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContinueWatchingRow(
    entries: List<WatchProgress>,
    spec: HomeStyleSpec,
    onResume: (WatchProgress) -> Unit,
    onRemove: (String) -> Unit
) {
    Column {
        RowHeader("Continue Watching", spec)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(entries) { entry ->
                val shape = RoundedCornerShape(spec.cardCorner.coerceAtLeast(8.dp))
                Column(modifier = Modifier.width(240.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(135.dp)
                            .tvClickable(
                                onClick = { onResume(entry) },
                                onLongClick = { onRemove(entry.id) },
                                shape = shape
                            )
                            .border(0.5.dp, Color.White.copy(alpha = 0.15f), shape)
                    ) {
                        AsyncImage(
                            model = entry.backdropUrl ?: entry.posterUrl,
                            contentDescription = entry.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
                        Icon(
                            Icons.Rounded.PlayCircleFilled,
                            contentDescription = "Resume",
                            tint = Color.White,
                            modifier = Modifier.align(Alignment.Center).size(44.dp)
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.Black.copy(alpha = 0.6f))
                                .clickable { onRemove(entry.id) }
                                .padding(3.dp)
                        ) {
                            Icon(Icons.Rounded.Close, "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                        LinearProgressIndicator(
                            progress = { entry.progressFraction },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(3.dp),
                            color = spec.accent,
                            trackColor = Color.White.copy(alpha = 0.25f)
                        )
                    }
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    entry.episodeLabel?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
