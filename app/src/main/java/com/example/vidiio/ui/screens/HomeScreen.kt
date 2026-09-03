package com.example.vidiio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.vidiio.data.model.Movie
import com.example.vidiio.ui.components.MovieItem
import com.example.vidiio.ui.components.RiveLoader
import com.example.vidiio.ui.theme.VidiioTheme
import com.example.vidiio.ui.viewmodel.HomeUiState
import com.example.vidiio.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToDetails: (Movie) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberLazyListState()

    // Calculate alpha based on scroll position
    val topBarAlpha by remember {
        derivedStateOf {
            if (scrollState.firstVisibleItemIndex > 0) {
                0f
            } else {
                // Fade out over the first 200 pixels
                val scrollOffset = scrollState.firstVisibleItemScrollOffset.toFloat()
                (1f - (scrollOffset / 300f)).coerceIn(0f, 1f)
            }
        }
    }

    // Background alpha (fades in as logo fades out)
    val backgroundAlpha by remember {
        derivedStateOf {
            if (scrollState.firstVisibleItemIndex > 0) {
                1f
            } else {
                val scrollOffset = scrollState.firstVisibleItemScrollOffset.toFloat()
                (scrollOffset / 300f).coerceIn(0f, 1f)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val state = uiState) {
            is HomeUiState.Loading -> {
                RiveLoader(modifier = Modifier.align(Alignment.Center))
            }
            is HomeUiState.Success -> {
                val featuredMovie = state.categories.firstOrNull()?.movies?.firstOrNull()

                LazyColumn(
                    state = scrollState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    item {
                        featuredMovie?.let {
                            FeaturedHeroSection(
                                movie = it,
                                onMovieClick = onNavigateToDetails
                            )
                        }
                    }
                    items(state.categories) { category ->
                        CategorySection(
                            name = category.name,
                            movies = category.movies,
                            onMovieClick = onNavigateToDetails,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }

                // Top Bar Overlay with fading logo and appearing background
                TopAppBar(
                    title = {
                        Text(
                            "VIDIIO",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-1).sp,
                            fontSize = 24.sp,
                            modifier = Modifier.alpha(topBarAlpha)
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background.copy(alpha = backgroundAlpha),
                        titleContentColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
            is HomeUiState.Error -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                    Button(onClick = { viewModel.refresh() }) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

@Composable
fun FeaturedHeroSection(
    movie: Movie,
    onMovieClick: (Movie) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(500.dp)
            .clickable { onMovieClick(movie) }
    ) {
        AsyncImage(
            model = movie.posterUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.4f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.9f),
                            Color.Black
                        ),
                        startY = 0f
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = movie.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = { onMovieClick(movie) },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Play", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = { onMovieClick(movie) },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.DarkGray.copy(alpha = 0.8f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f).height(48.dp).border(
                        width = 0.5.dp,
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(24.dp)
                    )
                ) {
                    Icon(Icons.Rounded.Info, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Info", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CategorySection(
    name: String,
    movies: List<Movie>,
    onMovieClick: (Movie) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(movies) { movie ->
                MovieItem(movie = movie, onClick = { onMovieClick(movie) })
            }
        }
    }
}
