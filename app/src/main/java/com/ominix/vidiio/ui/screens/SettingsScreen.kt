package com.ominix.vidiio.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ominix.vidiio.data.repository.AppTheme
import com.ominix.vidiio.data.repository.ColorTheme
import com.ominix.vidiio.data.repository.HomeStyle
import com.ominix.vidiio.ui.theme.*
import com.ominix.vidiio.ui.components.GlassCard
import com.ominix.vidiio.ui.viewmodel.SettingsViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playbackQuality by viewModel.playbackQuality.collectAsState()
    val theme by viewModel.theme.collectAsState()
    val colorTheme by viewModel.colorTheme.collectAsState()
    val dynamicColorEnabled by viewModel.dynamicColorEnabled.collectAsState()
    val homeStyle by viewModel.homeStyle.collectAsState()
    val avoidCameraCutout by viewModel.avoidCameraCutout.collectAsState()
    val selectedSources by viewModel.selectedSources.collectAsState()
    val stremioAddons by viewModel.stremioAddons.collectAsState()
    val subdlApiKey by viewModel.subdlApiKey.collectAsState()
    val subtitleLanguages by viewModel.subtitleLanguages.collectAsState()
    val proxyEnabled by viewModel.proxyEnabled.collectAsState()
    val proxyType by viewModel.proxyType.collectAsState()
    val proxyHost by viewModel.proxyHost.collectAsState()
    val proxyPort by viewModel.proxyPort.collectAsState()
    val proxyUser by viewModel.proxyUser.collectAsState()
    val proxyPass by viewModel.proxyPass.collectAsState()

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
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // --- Appearance Section ---
            item { SettingsHeader("Appearance", Icons.Rounded.Palette) }
            item {
                GlassCard {
                    Column {
                        PreferenceItem(
                            title = "Theme",
                            summary = when (theme) {
                                AppTheme.DARK -> "Dark"
                                AppTheme.LIGHT -> "Light"
                                AppTheme.SYSTEM -> "System default"
                            },
                            icon = Icons.Rounded.Brightness4,
                            onClick = { /* Could show dialog, but we have inline below */ }
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
                            summary = homeStyle.spec(MaterialTheme.colorScheme.primary).label +
                                " — restyles the Home tab",
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
                            summary = "Colors based on wallpaper",
                            icon = Icons.Rounded.ColorLens,
                            checked = dynamicColorEnabled,
                            onCheckedChange = { viewModel.setDynamicColor(it) }
                        )
                    }
                }
            }

            // --- Playback Section ---
            item { SettingsHeader("Playback", Icons.Rounded.PlayCircle) }
            item {
                GlassCard {
                    Column {
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
                        PreferenceItem(
                            title = "Subdl API Key",
                            summary = if (subdlApiKey.isNullOrEmpty()) "Not set" else "••••••••",
                            icon = Icons.Rounded.Key,
                            onClick = { 
                                apiKeyToSet = subdlApiKey ?: ""
                                showApiKeyDialog = true 
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        PreferenceItem(
                            title = "Subtitle languages",
                            summary = subtitleLanguages.joinToString(", ") { it.uppercase() },
                            icon = Icons.Rounded.Subtitles,
                            onClick = {
                                subtitleLanguagesToSet = subtitleLanguages.joinToString(", ")
                                showSubtitleLanguagesDialog = true
                            }
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
                        PreferenceItem(
                            title = "Clear Cache",
                            summary = "Clear image and data cache",
                            icon = Icons.Rounded.DeleteSweep,
                            onClick = { viewModel.clearCache() }
                        )
                    }
                }
            }

            // --- Privacy Section ---
            item { SettingsHeader("Privacy", Icons.Rounded.Shield) }
            item {
                GlassCard {
                    Column {
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
            }

            // --- Sources Section ---
            item { SettingsHeader("Sources", Icons.Rounded.Public) }
            item {
                GlassCard {
                    Column {
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
            }

            // --- Stremio Addons Section ---
            item { SettingsHeader("Stremio Addons", Icons.Rounded.Extension) }
            if (stremioAddons.isNotEmpty()) {
                items(stremioAddons.toList()) { addonUrl ->
                    GlassCard {
                        ListItem(
                            headlineContent = { Text(addonUrl, color = MaterialTheme.colorScheme.onSurface, maxLines = 1) },
                            trailingContent = {
                                IconButton(onClick = { viewModel.removeStremioAddon(addonUrl) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
            item {
                GlassCard {
                    PreferenceItem(
                        title = "Add Stremio Addon",
                        summary = "Enter manifest URL",
                        icon = Icons.Rounded.Add,
                        onClick = { showAddAddonDialog = true }
                    )
                }
            }

            // --- About Section ---
            item { SettingsHeader("About", Icons.Rounded.Info) }
            item {
                GlassCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Vidiio V3", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Version 3.0.0-alpha", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
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
            }
            
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color, RoundedCornerShape(10.dp))
                    .clickable { onColorThemeSelected(theme) }
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
fun SettingsHeader(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp
        )
    }
}

// Removed local GlassCard and using shared one

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
        modifier = Modifier.clickable(onClick = onClick),
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
        modifier = Modifier.clickable { onCheckedChange(!checked) },
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
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onThemeSelected(theme) },
                shape = RoundedCornerShape(24.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                border = if (isSelected) null else BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Text(
                    text = theme.name.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
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
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onQualitySelected(quality) },
                shape = RoundedCornerShape(24.dp),
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
        modifier = Modifier.clickable { onToggle() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
