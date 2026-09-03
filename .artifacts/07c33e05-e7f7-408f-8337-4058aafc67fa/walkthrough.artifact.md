# Settings Screen and Persistence Implementation Walkthrough

Implemented a robust settings management system for the Vidiio app, featuring persistent storage, reactive UI updates, and deep integration with the app's core functionality (theming, ad-blocking, and content sources).

## Key Components

### 1. Persistence Layer (`SettingsRepository`)
- **Jetpack DataStore (Preferences)**: Used for efficient, non-blocking storage of user preferences.
- **Settings Managed**:
    - Ad-blocking (Boolean)
    - Default Playback Quality (String)
    - App Theme (Dark, Light, System)
    - Dynamic Color (Android 12+)
    - Content Sources (Cinejoy, Popcorn)

### 2. UI Layer (`SettingsScreen` & `SettingsViewModel`)
- **Material 3 Design**: Clean, adaptive layout using `LazyColumn` for sections.
- **Components**: Utilized `Switch`, `RadioButton`, and `ListItem` for a native feel.
- **Sections**:
    - **Appearance**: Controls theme and dynamic color.
    - **Playback**: Configures default quality.
    - **Sources**: Toggles available scrapers.
    - **Data Management**: Ad-blocking toggle and "Clear Cache" button.

### 3. Integration & Navigation
- **Navigation 3**: Added `VidiioRoute.Settings` and integrated it into the `NavigationSuiteScaffold` for easy access from the main UI.
- **Theming**: `MainActivity` now observes settings to dynamically apply the selected theme and dynamic color scheme.
- **Ad-Blocking**: `PlayerScreen` (WebView) consumes the ad-blocking setting to intercept requests and inject ad-hiding scripts.
- **Dynamic Content**: `MovieRepository` filters scrapers based on the enabled sources in settings.

## Verification
- **Build**: Successfully ran `./gradlew :app:assembleDebug`.
- **Logic**: All state is handled via `Flow` and `StateFlow`, ensuring the UI reacts immediately to preference changes.
- **Cache**: Implemented Coil cache clearing using the `ImageLoader` API.

## Screenshots/UI Structure
- **TopAppBar**: "Settings" title with a back button.
- **LazyColumn**:
    - Appearance (Palette icon) -> Theme selection row -> Dynamic Color switch.
    - Playback (Quality icon) -> Quality selection row.
    - Sources (Public icon) -> Individual switches for Cinejoy and PopcornMovies.
    - Data Management (Security icon) -> Ad-blocking switch -> Clear Cache (Delete icon).
