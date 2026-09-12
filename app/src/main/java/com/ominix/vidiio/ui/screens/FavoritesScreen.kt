package com.ominix.vidiio.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.data.model.MovieType
import com.ominix.vidiio.ui.components.GlassCard
import com.ominix.vidiio.ui.components.MovieItem
import com.ominix.vidiio.ui.components.RiveAnimation
import com.ominix.vidiio.ui.components.RiveLoader
import com.ominix.vidiio.ui.components.tvClickable
import com.ominix.vidiio.ui.viewmodel.FavoritesUiState
import com.ominix.vidiio.ui.viewmodel.FavoritesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    onMovieClick: (Movie) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedFilter by remember { mutableStateOf("All") }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("My List", fontWeight = FontWeight.Bold)
                        if (uiState is FavoritesUiState.Success) {
                            val count = (uiState as FavoritesUiState.Success).movies.size
                            if (count > 0) {
                                Spacer(Modifier.width(10.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "$count",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (val state = uiState) {
                is FavoritesUiState.Loading -> {
                    RiveLoader(modifier = Modifier.align(Alignment.Center))
                }
                is FavoritesUiState.Success -> {
                    val filteredMovies = remember(state.movies, selectedFilter) {
                        state.movies.filter { movie ->
                            when (selectedFilter) {
                                "Movies" -> movie.type == MovieType.MOVIE
                                "TV Shows" -> movie.type == MovieType.TV_SHOW
                                else -> true
                            }
                        }
                    }

                    if (state.movies.isEmpty()) {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            RiveAnimation(
                                url = "https://public.rive.app/community/runtime-files/2042-4040-avatar-pack-use-case.riv",
                                modifier = Modifier.size(200.dp),
                                fallback = {
                                    Icon(
                                        Icons.Rounded.FavoriteBorder,
                                        contentDescription = null,
                                        modifier = Modifier.size(100.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Your list is empty",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Explore and add your favorite movies and shows here.",
                                modifier = Modifier.padding(horizontal = 24.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("All", "Movies", "TV Shows").forEach { filter ->
                                    val isSelected = selectedFilter == filter
                                    Surface(
                                        modifier = Modifier.tvClickable(
                                            onClick = { selectedFilter = filter },
                                            shape = RoundedCornerShape(16.dp),
                                            focusScale = 1.05f
                                        ),
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent,
                                        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
                                    ) {
                                        Text(
                                            text = filter,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f),
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                contentPadding = PaddingValues(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredMovies) { movie ->
                                    MovieItem(
                                        movie = movie,
                                        onClick = { onMovieClick(movie) }
                                    )
                                }
                            }
                        }
                    }
                }
                is FavoritesUiState.Error -> {
                    Text(
                        text = "Error: ${state.message}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}
