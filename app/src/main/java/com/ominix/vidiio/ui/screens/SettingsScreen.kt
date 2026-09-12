package com.ominix.vidiio.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.ominix.vidiio.BuildConfig
import com.ominix.vidiio.data.repository.AppTheme
import com.ominix.vidiio.data.repository.ColorTheme
import com.ominix.vidiio.data.repository.HomeStyle
import com.ominix.vidiio.data.repository.MediaPlayerChoice
import com.ominix.vidiio.data.repository.SubtitleEdge
import com.ominix.vidiio.data.repository.SubtitleStyle
import com.ominix.vidiio.ui.components.tvClickable
import com.ominix.vidiio.ui.theme.*
import com.ominix.vidiio.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playbackQuality by viewModel.playbackQuality.collectAsState()
    val mediaPlayer by viewModel.mediaPlayer.collectAsState()
    val isVlcInstalled = remember { viewModel.isVlcInstalled() }
    val theme by viewModel.theme.collectAsState()
    val colorTheme by viewModel.colorTheme.collectAsState()
    val homeStyle by viewModel.homeStyle.collectAsState()
    val avoidCameraCutout by viewModel.avoidCameraCutout.collectAsState()
    val mouseToggle by viewModel.mouseToggle.collectAsState()
    val selectedSources by viewModel.selectedSources.collectAsState()
    val stremioAddons by viewModel.stremioAddons.collectAsState()
    val subdlApiKey by viewModel.subdlApiKey.collectAsState()
    val subtitleLanguages by viewModel.subtitleLanguages.collectAsState()
    val subtitleStyle by viewModel.subtitleStyle.collectAsState()
    val assNativeStyling by viewModel.assNativeStyling.collectAsState()
    val proxyEnabled by viewModel.proxyEnabled.collectAsState()
    val proxyType by viewModel.proxyType.collectAsState()
    val proxyHost by viewModel.proxyHost.collectAsState()
    val proxyPort by viewModel.proxyPort.collectAsState()
    val proxyUser by viewModel.proxyUser.collectAsState()
    val proxyPass by viewModel.proxyPass.collectAsState()

    var isAppearanceExpanded by rememberSaveable { mutableStateOf(false) }
    var isPlaybackExpanded by rememberSaveable { mutableStateOf(false) }
    var isSubtitlesExpanded by rememberSaveable { mutableStateOf(false) }
    var isPrivacyExpanded by rememberSaveable { mutableStateOf(false) }
    var isSourcesExpanded by rememberSaveable { mutableStateOf(false) }
    var isAddonsExpanded by rememberSaveable { mutableStateOf(false) }
    var isAboutExpanded by rememberSaveable { mutableStateOf(false) }

    var showProxyDialog by rememberSaveable { mutableStateOf(false) }
    var showAddAddonDialog by rememberSaveable { mutableStateOf(false) }
    var addonUrlToAdd by rememberSaveable { mutableStateOf("") }
    var showApiKeyDialog by rememberSaveable { mutableStateOf(false) }
    var apiKeyToSet by rememberSaveable { mutableStateOf("") }
    var showSubtitleLanguagesDialog by rememberSaveable { mutableStateOf(false) }
    var subtitleLanguagesToSet by rememberSaveable { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "Settings", 
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 0.dp)
        ) {
            // --- Appearance Dropdown ---
            item {
                ExpandableSettingsSection(
                    title = "Appearance",
                    summary = when (theme) {
                        AppTheme.DARK -> "Dark"
                        AppTheme.LIGHT -> "Light"
                        AppTheme.SYSTEM -> "System default"
                    } + " · " + colorTheme.name.lowercase().replaceFirstChar { it.titlecase() },
                    icon = Icons.Rounded.Palette,
                    expanded = isAppearanceExpanded,
                    onExpandedChange = { isAppearanceExpanded = it }
                ) {
                    PreferenceItem(
                        title = "Theme",
                        summary = when (theme) {
                            AppTheme.DARK -> "Dark"
                            AppTheme.LIGHT -> "Light"
                            AppTheme.SYSTEM -> "System default"
                        },
                        icon = Icons.Rounded.Brightness4,
                        onClick = { }
                    )
                    ThemeSelectionRow(
                        currentTheme = theme,
                        onThemeSelected = { viewModel.setTheme(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    PreferenceItem(
                        title = "Color Theme",
                        summary = colorTheme.name.lowercase().replaceFirstChar { it.titlecase() },
                        icon = Icons.Rounded.Palette,
                        onClick = { }
                    )
                    ColorThemeSelectionRow(
                        currentColorTheme = colorTheme,
                        onColorThemeSelected = { viewModel.setColorTheme(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    PreferenceItem(
                        title = "Home Layout",
                        summary = homeStyle.spec(MaterialTheme.colorScheme.primary).label + " — restyles the Home tab",
                        icon = Icons.Rounded.Dashboard,
                        onClick = { }
                    )
                    HomeStyleSelectionRow(
                        current = homeStyle,
                        onSelected = { viewModel.setHomeStyle(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    SwitchPreferenceItem(
                        title = "Dynamic Color",
                        summary = "Colors based on wallpaper (Android 12+)",
                        icon = Icons.Rounded.ColorLens,
                        checked = colorTheme == ColorTheme.DYNAMIC,
                        onCheckedChange = { viewModel.setDynamicColor(it) }
                    )
                }
            }

            // --- Playback Dropdown ---
            item {
                ExpandableSettingsSection(
                    title = "Playback",
                    summary = (when (mediaPlayer) {
                        MediaPlayerChoice.INTERNAL -> "Internal (ExoPlayer)"
                        MediaPlayerChoice.VLC -> "VLC Player"
                    }) + " · $playbackQuality",
                    icon = Icons.Rounded.PlayCircle,
                    expanded = isPlaybackExpanded,
                    onExpandedChange = { isPlaybackExpanded = it }
                ) {
                    PreferenceItem(
                        title = "Preferred Media Player",
                        summary = when (mediaPlayer) {
                            MediaPlayerChoice.INTERNAL -> "Internal Player (ExoPlayer)"
                            MediaPlayerChoice.VLC -> if (isVlcInstalled) "VLC Media Player" else "VLC Media Player (Not Installed)"
                        },
                        icon = Icons.Rounded.Tv,
                        onClick = {}
                    )
                    MediaPlayerSelectionRow(
                        currentChoice = mediaPlayer,
                        isVlcInstalled = isVlcInstalled,
                        onChoiceSelected = { choice -> viewModel.setMediaPlayer(choice) },
                        onInstallVlc = { viewModel.openVlcInPlayStore() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    PreferenceItem(
                        title = "Default Quality",
                        summary = playbackQuality,
                        icon = Icons.Rounded.HighQuality,
                        onClick = {}
                    )
                    QualitySelectionRow(
                        currentQuality = playbackQuality,
                        onQualitySelected = { viewModel.setPlaybackQuality(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    SwitchPreferenceItem(
                        title = "Keep camera hole clear",
                        summary = "Player fills the screen but stops short of the front camera",
                        icon = Icons.Rounded.AspectRatio,
                        checked = avoidCameraCutout,
                        onCheckedChange = { viewModel.setAvoidCameraCutout(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    SwitchPreferenceItem(
                        title = "Mouse Toggle",
                        summary = "Enable virtual pointer overlay for TV navigation",
                        icon = Icons.Rounded.Mouse,
                        checked = mouseToggle,
                        onCheckedChange = { viewModel.setMouseToggle(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    PreferenceItem(
                        title = "Clear Cache",
                        summary = "Clear image and data cache",
                        icon = Icons.Rounded.DeleteSweep,
                        onClick = { viewModel.clearCache() }
                    )
                }
            }

            // --- Subtitles & Captions Dropdown (Closable Subtitle Window) ---
            item {
                ExpandableSettingsSection(
                    title = "Subtitles & Captions",
                    summary = subtitleLanguages.joinToString(", ") { it.uppercase() } +
                        if (!subdlApiKey.isNullOrEmpty()) " · SubDL Connected" else "",
                    icon = Icons.Rounded.Subtitles,
                    expanded = isSubtitlesExpanded,
                    onExpandedChange = { isSubtitlesExpanded = it }
                ) {
                    PreferenceItem(
                        title = "Subtitle languages",
                        summary = subtitleLanguages.joinToString(", ") { it.uppercase() },
                        icon = Icons.Rounded.Language,
                        onClick = {
                            subtitleLanguagesToSet = subtitleLanguages.joinToString(", ")
                            showSubtitleLanguagesDialog = true
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    PreferenceItem(
                        title = "SubDL API Key",
                        summary = if (subdlApiKey.isNullOrEmpty()) "Not set" else "••••••••",
                        icon = Icons.Rounded.Key,
                        onClick = {
                            apiKeyToSet = subdlApiKey ?: ""
                            showApiKeyDialog = true
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    SwitchPreferenceItem(
                        title = "Keep ASS/SSA styling",
                        summary = if (assNativeStyling) {
                            "Fancy subtitles keep their own fonts and positioning."
                        } else {
                            "Fancy subtitles are shown as plain text so appearance settings apply."
                        },
                        icon = Icons.Rounded.Style,
                        checked = assNativeStyling,
                        onCheckedChange = { viewModel.setAssNativeStyling(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    SubtitleAppearanceSection(
                        style = subtitleStyle,
                        onStyleChange = { viewModel.setSubtitleStyle(it) }
                    )
                }
            }

            // --- Privacy & Proxy Dropdown ---
            item {
                ExpandableSettingsSection(
                    title = "Privacy & Proxy",
                    summary = when {
                        !proxyEnabled -> "Off"
                        proxyHost.isBlank() || proxyPort <= 0 -> "Unconfigured"
                        else -> "${proxyType.name} · $proxyHost:$proxyPort"
                    },
                    icon = Icons.Rounded.Shield,
                    expanded = isPrivacyExpanded,
                    onExpandedChange = { isPrivacyExpanded = it }
                ) {
                    SwitchPreferenceItem(
                        title = "Proxy (VPN)",
                        summary = when {
                            !proxyEnabled -> "Route scraper + torrent traffic through a SOCKS5/HTTP proxy"
                            proxyHost.isBlank() || proxyPort <= 0 -> "On, but not configured — open Proxy settings"
                            else -> "${proxyType.name} · $proxyHost:$proxyPort"
                        },
                        icon = Icons.Rounded.VpnKey,
                        checked = proxyEnabled,
                        onCheckedChange = {
                            viewModel.saveProxy(it, proxyType, proxyHost, proxyPort, proxyUser, proxyPass)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    PreferenceItem(
                        title = "Proxy settings",
                        summary = "Host, port, credentials — test the connection",
                        icon = Icons.Rounded.Tune,
                        onClick = { showProxyDialog = true }
                    )
                }
            }

            // --- Sources Dropdown ---
            item {
                ExpandableSettingsSection(
                    title = "Sources",
                    summary = "${selectedSources.size} of 9 scrapers enabled",
                    icon = Icons.Rounded.Public,
                    expanded = isSourcesExpanded,
                    onExpandedChange = { isSourcesExpanded = it }
                ) {
                    val sources = listOf(
                        "vidsrc" to "VidSrc",
                        "videasy" to "VidEasy",
                        "vadapav" to "Vadapav",
                        "vuflix" to "Vuflix",
                        "cinejoy" to "Cinejoy",
                        "movy" to "Movy",
                        "a111477" to "A111477",
                        "knaben" to "Knaben",
                        "tg" to "TorrentGalaxy"
                    )
                    sources.forEachIndexed { index, (id, name) ->
                        SourceToggleItem(
                            name = name,
                            enabled = selectedSources.contains(id),
                            onToggle = { viewModel.toggleSource(id) }
                        )
                        if (index < sources.size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }

            // --- Stremio Addons Dropdown ---
            item {
                ExpandableSettingsSection(
                    title = "Stremio Addons",
                    summary = "${stremioAddons.size} addons installed",
                    icon = Icons.Rounded.Extension,
                    expanded = isAddonsExpanded,
                    onExpandedChange = { isAddonsExpanded = it }
                ) {
                    stremioAddons.forEach { addonUrl ->
                        ListItem(
                            headlineContent = { Text(addonUrl, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingContent = {
                                IconButton(onClick = { viewModel.removeStremioAddon(addonUrl) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    PreferenceItem(
                        title = "Add Stremio Addon",
                        summary = "Enter manifest URL",
                        icon = Icons.Rounded.Add,
                        onClick = { showAddAddonDialog = true }
                    )
                }
            }

            // --- About Dropdown ---
            item {
                ExpandableSettingsSection(
                    title = "About Vidiio",
                    summary = "Version ${BuildConfig.VERSION_NAME}",
                    icon = Icons.Rounded.Info,
                    expanded = isAboutExpanded,
                    onExpandedChange = { isAboutExpanded = it }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SelectionContainer {
                            Column {
                                Text(
                                    "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp
                                )
                                Text(
                                    "Commit ${BuildConfig.GIT_SHA}${if (BuildConfig.DEBUG) " · debug" else ""}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "The app was made by OOZI 'for family and friends' enjoy!",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "A professional streaming experience powered by Stremio Addons and multiple sources.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Developed with ❤️ using Jetpack Compose", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    // Dialogs preserved
    if (showAddAddonDialog) {
        AlertDialog(
            onDismissRequest = { showAddAddonDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text("Add Stremio Addon") },
            text = {
                OutlinedTextField(
                    value = addonUrlToAdd,
                    onValueChange = { addonUrlToAdd = it },
                    label = { Text("Manifest URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = Color.Gray
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (addonUrlToAdd.isNotBlank()) {
                            viewModel.addStremioAddon(addonUrlToAdd)
                            addonUrlToAdd = ""
                            showAddAddonDialog = false
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddAddonDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    if (showSubtitleLanguagesDialog) {
        AlertDialog(
            onDismissRequest = { showSubtitleLanguagesDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text("Subtitle languages") },
            text = {
                Column {
                    Text(
                        "Two-letter codes, most wanted first. The order decides what " +
                            "providers are asked for and how the subtitle list is sorted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = subtitleLanguagesToSet,
                        onValueChange = { subtitleLanguagesToSet = it },
                        label = { Text("e.g. en, es, fr") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = Color.Gray
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setSubtitleLanguages(subtitleLanguagesToSet)
                        showSubtitleLanguagesDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSubtitleLanguagesDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text("Set Subdl API Key") },
            text = {
                OutlinedTextField(
                    value = apiKeyToSet,
                    onValueChange = { apiKeyToSet = it },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = Color.Gray
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setSubdlApiKey(apiKeyToSet)
                        showApiKeyDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    if (showProxyDialog) {
        ProxySettingsDialog(
            initialType = proxyType,
            initialHost = proxyHost,
            initialPort = if (proxyPort > 0) proxyPort.toString() else "",
            initialUser = proxyUser,
            initialPass = proxyPass,
            testResult = viewModel.proxyTestResult.collectAsState().value,
            onTest = { t, h, p, u, pw -> viewModel.testProxy(t, h, p.toIntOrNull() ?: 0, u, pw) },
            onSave = { t, h, p, u, pw ->
                viewModel.saveProxy(true, t, h, p.toIntOrNull() ?: 0, u, pw)
                viewModel.clearProxyTestResult()
                showProxyDialog = false
            },
            onDismiss = { viewModel.clearProxyTestResult(); showProxyDialog = false }
        )
    }
}

@Composable
fun ExpandableSettingsSection(
    title: String,
    summary: String? = null,
    icon: ImageVector,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val contentFocusRequester = remember { FocusRequester() }

    LaunchedEffect(expanded) {
        if (expanded) {
            delay(120)
            runCatching { contentFocusRequester.requestFocus() }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .tvClickable(onClick = { onExpandedChange(!expanded) }, shape = shape, focusScale = 1.02f),
            shape = shape,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!summary.isNullOrBlank()) {
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(modifier = Modifier.focusRequester(contentFocusRequester)) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        content()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProxySettingsDialog(
    initialType: com.ominix.vidiio.data.repository.ProxyType,
    initialHost: String,
    initialPort: String,
    initialUser: String,
    initialPass: String,
    testResult: String?,
    onTest: (com.ominix.vidiio.data.repository.ProxyType, String, String, String, String) -> Unit,
    onSave: (com.ominix.vidiio.data.repository.ProxyType, String, String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var type by remember { mutableStateOf(initialType) }
    var host by remember { mutableStateOf(initialHost) }
    var port by remember { mutableStateOf(initialPort) }
    var user by remember { mutableStateOf(initialUser) }
    var pass by remember { mutableStateOf(initialPass) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = Color.Gray,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = Color.Gray
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text("Proxy (VPN)") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.ominix.vidiio.data.repository.ProxyType.entries.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(if (t == com.ominix.vidiio.data.repository.ProxyType.SOCKS5) "SOCKS5" else "HTTP") }
                        )
                    }
                }
                OutlinedTextField(host, { host = it }, label = { Text("Host") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), colors = fieldColors)
                OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, label = { Text("Port") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors)
                OutlinedTextField(user, { user = it }, label = { Text("Username (optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), colors = fieldColors)
                OutlinedTextField(pass, { pass = it }, label = { Text("Password (optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), colors = fieldColors)
                TextButton(
                    onClick = { onTest(type, host, port, user, pass) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) { Text("Test connection") }
                testResult?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "Restart the app after saving for the change to take effect.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(type, host, port, user, pass) },
                enabled = host.isNotBlank() && (port.toIntOrNull() ?: 0) in 1..65535,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) { Text("Save & enable") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeStyleSelectionRow(
    current: HomeStyle,
    onSelected: (HomeStyle) -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HomeStyle.entries.forEach { style ->
            FilterChip(
                selected = current == style,
                onClick = { onSelected(style) },
                label = { Text(style.spec(accent).label) }
            )
        }
    }
}

@Composable
fun ColorThemeSelectionRow(
    currentColorTheme: ColorTheme,
    onColorThemeSelected: (ColorTheme) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ColorTheme.entries.forEach { theme ->
            val isSelected = currentColorTheme == theme
            val color = when (theme) {
                ColorTheme.RED -> VidiioRed
                ColorTheme.BLUE -> VidiioBlue
                ColorTheme.GREEN -> VidiioGreen
                ColorTheme.PURPLE -> VidiioPurple
                ColorTheme.ORANGE -> VidiioOrange
                ColorTheme.TEAL -> VidiioTeal
                ColorTheme.PINK -> VidiioPink
                ColorTheme.INDIGO -> VidiioIndigo
                ColorTheme.GOLD -> VidiioGold
                ColorTheme.MONO -> VidiioMono
                ColorTheme.DYNAMIC -> MaterialTheme.colorScheme.primary
            }
            
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color, RoundedCornerShape(10.dp))
                    .tvClickable(onClick = { onColorThemeSelected(theme) }, shape = RoundedCornerShape(10.dp))
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PreferenceItem(
    title: String,
    summary: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
        supportingContent = { Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) },
        leadingContent = { 
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            }
        },
        modifier = Modifier.tvClickable(onClick = onClick, shape = RoundedCornerShape(12.dp), focusScale = 1.02f),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun SwitchPreferenceItem(
    title: String,
    summary: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
        supportingContent = { Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) },
        leadingContent = { 
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            }
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
            )
        },
        modifier = Modifier.tvClickable(onClick = { onCheckedChange(!checked) }, shape = RoundedCornerShape(12.dp), focusScale = 1.02f),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun ThemeSelectionRow(
    currentTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppTheme.entries.forEach { theme ->
            val isSelected = currentTheme == theme
            val shape = RoundedCornerShape(24.dp)
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .tvClickable(onClick = { onThemeSelected(theme) }, shape = shape, focusScale = 1.05f),
                shape = shape,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                border = if (isSelected) null else BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Text(
                    text = theme.name.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                    modifier = Modifier.padding(vertical = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun MediaPlayerSelectionRow(
    currentChoice: MediaPlayerChoice,
    isVlcInstalled: Boolean,
    onChoiceSelected: (MediaPlayerChoice) -> Unit,
    onInstallVlc: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val options = listOf(
                MediaPlayerChoice.INTERNAL to "Internal (ExoPlayer)",
                MediaPlayerChoice.VLC to "VLC Player"
            )
            options.forEach { (choice, label) ->
                val isSelected = currentChoice == choice
                val shape = RoundedCornerShape(24.dp)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .tvClickable(onClick = { onChoiceSelected(choice) }, shape = shape, focusScale = 1.05f),
                    shape = shape,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                    border = if (isSelected) null else BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(vertical = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
        if (currentChoice == MediaPlayerChoice.VLC && !isVlcInstalled) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.error)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "VLC app is not installed on this device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    TextButton(onClick = onInstallVlc) {
                        Text("Install VLC", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun QualitySelectionRow(
    currentQuality: String,
    onQualitySelected: (String) -> Unit
) {
    val qualities = listOf("Auto", "1080p", "720p", "480p")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        qualities.forEach { quality ->
            val isSelected = currentQuality == quality
            val shape = RoundedCornerShape(24.dp)
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .tvClickable(onClick = { onQualitySelected(quality) }, shape = shape, focusScale = 1.05f),
                shape = shape,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                border = if (isSelected) null else BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Text(
                    text = quality,
                    modifier = Modifier.padding(vertical = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun SourceToggleItem(
    name: String,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    ListItem(
        headlineContent = { Text(name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium) },
        trailingContent = {
            Switch(
                checked = enabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
            )
        },
        modifier = Modifier.tvClickable(onClick = onToggle, shape = RoundedCornerShape(12.dp), focusScale = 1.02f),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

/**
 * Subtitle appearance controls, with a live preview.
 */
@Composable
private fun SubtitleAppearanceSection(
    style: SubtitleStyle,
    onStyleChange: (SubtitleStyle) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            "Subtitle appearance",
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF101010), RoundedCornerShape(12.dp))
                .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "The quick brown fox",
                color = Color(style.textColor),
                fontSize = (15 * style.textScale).sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .background(Color(style.backgroundColor), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            "Text size  " + (style.textScale * 100).toInt() + "%",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Slider(
            value = style.textScale,
            onValueChange = { onStyleChange(style.copy(textScale = it)) },
            valueRange = 0.5f..2.5f,
            steps = 7
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text("Text colour", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SUBTITLE_TEXT_COLORS.forEach { argb ->
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(Color(argb), RoundedCornerShape(8.dp))
                        .tvClickable(onClick = { onStyleChange(style.copy(textColor = argb)) }, shape = RoundedCornerShape(8.dp))
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (style.textColor == argb) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = "Selected",
                            tint = if (argb == 0xFFFFFFFFL) Color.Black else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text("Background", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SUBTITLE_BACKGROUNDS.forEach { entry ->
                FilterChip(
                    selected = style.backgroundColor == entry.second,
                    onClick = { onStyleChange(style.copy(backgroundColor = entry.second)) },
                    label = { Text(entry.first, fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text("Edge style", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            SubtitleEdge.entries.forEach { edge ->
                FilterChip(
                    selected = style.edge == edge,
                    onClick = { onStyleChange(style.copy(edge = edge)) },
                    label = { Text(subtitleEdgeLabel(edge), fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        TextButton(onClick = { onStyleChange(SubtitleStyle()) }) { Text("Reset to defaults") }
    }
}

private fun subtitleEdgeLabel(edge: SubtitleEdge): String =
    edge.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

private val SUBTITLE_TEXT_COLORS = listOf(
    0xFFFFFFFFL, 0xFFFFEB3BL, 0xFF00E5FFL, 0xFF69F0AEL, 0xFFFF8A80L, 0xFF000000L
)

private val SUBTITLE_BACKGROUNDS = listOf(
    "None" to 0x00000000L,
    "Dim" to 0x80000000L,
    "Solid" to 0xFF000000L
)
