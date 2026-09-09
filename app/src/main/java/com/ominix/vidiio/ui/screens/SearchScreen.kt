package com.ominix.vidiio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.ui.components.MovieItem
import com.ominix.vidiio.ui.components.RiveAnimation
import com.ominix.vidiio.ui.components.RiveLoader
import com.ominix.vidiio.ui.viewmodel.SearchUiState
import com.ominix.vidiio.ui.viewmodel.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onNavigateToDetails: (Movie) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val query by viewModel.query.collectAsState()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                        TextField(
                            value = query,
                            onValueChange = viewModel::onQueryChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 16.dp)
                                .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp)),
                            placeholder = { Text("Search movies, shows...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (val state = uiState) {
                is SearchUiState.Idle -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        RiveAnimation(
                            url = "https://public.rive.app/community/runtime-files/2042-4040-avatar-pack-use-case.riv",
                            modifier = Modifier.size(200.dp),
                            fallback = {
                                Icon(
                                    Icons.Rounded.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(100.dp).alpha(0.3f),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Find your next favorite",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Search for movies, TV shows and actors.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is SearchUiState.Loading -> {
                    RiveLoader(modifier = Modifier.align(Alignment.Center))
                }
                is SearchUiState.Success -> {
                    if (state.results.isEmpty()) {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            RiveAnimation(
                                url = "https://public.rive.app/community/runtime-files/2042-4040-avatar-pack-use-case.riv",
                                modifier = Modifier.size(200.dp),
                                fallback = {
                                    Icon(
                                        Icons.Rounded.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(100.dp).alpha(0.3f),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No results found for \"$query\"",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.results) { movie ->
                                MovieItem(movie = movie, onClick = { onNavigateToDetails(movie) })
                            }
                        }
                    }
                }
                is SearchUiState.Error -> {
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
