# Implementation Plan - Vidiio V3 Upgrade

This plan outlines the steps to upgrade the Vidiio app with Stremio Addon support and external subtitle integration.

## Proposed Changes

### Stremio Addon Protocol

#### [NEW] [StremioModels.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/data/model/stremio/StremioModels.kt)
Define models for `Manifest`, `Catalog`, `Stream`, and `Resource`.

#### [NEW] [StremioService.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/data/api/StremioService.kt)
Retrofit interface for Stremio addon communication.

#### [NEW] [AddonManager.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/data/stremio/AddonManager.kt)
Logic to manage manifests, cache them, and fetch resources.

#### [MODIFY] [SettingsRepository.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/data/repository/SettingsRepository.kt)
Add storage for Stremio addon URLs.

#### [MODIFY] [MovieRepository.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/data/repository/MovieRepository.kt)
Integrate `AddonManager` to fetch streams from addons.

---

### External Subtitles

#### [NEW] [SubdlModels.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/data/model/subtitles/SubdlModels.kt)
Models for Subdl API.

#### [NEW] [SubdlService.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/data/api/SubdlService.kt)
Retrofit interface for Subdl API.

#### [MODIFY] [SettingsRepository.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/data/repository/SettingsRepository.kt)
Add storage for Subdl API key.

---

### UI & UX Improvements

#### [MODIFY] [SettingsScreen.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/ui/screens/SettingsScreen.kt)
Add UI for managing Stremio addons and entering Subdl API key.

#### [MODIFY] [SettingsViewModel.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/ui/viewmodel/SettingsViewModel.kt)
Handle addon management and API key updates.

#### [MODIFY] [PlayerScreen.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/ui/screens/PlayerScreen.kt)
Add subtitle selection icon and logic to load external subtitles into ExoPlayer.

#### [MODIFY] [DetailsViewModel.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/ui/viewmodel/DetailsViewModel.kt)
Add logic to fetch subtitle options.

---

### Wiring

#### [MODIFY] [VidiioApplication.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/VidiioApplication.kt)
Initialize and provide `AddonManager`, `StremioService`, and `SubdlService`.

## Verification Plan

### Automated Tests
- Unit tests for `AddonManager` to verify manifest parsing and stream fetching logic.
- Unit tests for `MovieRepository` integration.

### Manual Verification
1.  **Addon Installation**: Open Settings, enter a Stremio manifest URL (e.g., Torrentio), and verify it's saved.
2.  **Stream Fetching**: Select a movie, verify that streams from the installed Stremio addon appear in the source list.
3.  **Subtitles**:
    *   Enter a Subdl API key in Settings.
    *   Play a movie.
    *   Click the Subtitles icon.
    *   Select a subtitle and verify it's downloaded and displayed on the player.
