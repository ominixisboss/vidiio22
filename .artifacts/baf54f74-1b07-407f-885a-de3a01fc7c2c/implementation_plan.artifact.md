# Implementation Plan - Advanced Playback Controls (PlayTorrio V3 Replica)

Upgrade the Vidiio player with advanced features: audio track selection, playback speed, subtitle sync, smart binge logic, and an episodes sidebar.

## Proposed Changes

### [Data & Models]
- [MODIFY] [Movie.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/data/model/Movie.kt): Add `Episode` data class and `seasons` to `Movie`.
- [MODIFY] [TMDBResponse.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/data/model/tmdb/TMDBResponse.kt): Add `TMDBSeason` and `TMDBEpisode` models.
- [MODIFY] [TMDBService.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/data/api/TMDBService.kt): Add `getTVSeasonDetails` method.
- [MODIFY] [MovieRepository.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/data/repository/MovieRepository.kt): Implement fetching seasons and episodes for TV shows.

### [ViewModel]
- [MODIFY] [DetailsViewModel.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/ui/viewmodel/DetailsViewModel.kt): Load episodes if the content is a TV show. Add `selectedEpisode` state.

### [UI Components]
- [MODIFY] [PlayerScreen.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/ui/screens/PlayerScreen.kt):
    - Implement a new `PlayerTopBar` with title and download button.
    - Add `PlayerSettingsDialog` for speed and audio track selection.
    - Enhance `SubtitleSheet` with sync controls.
    - Add `SkipButtons` (Intro/Outro) overlay.
    - Implement `EpisodesSidebar` drawer.
    - Add logic for Auto-Next when an episode ends.
    - Apply glassmorphism styling using `Blur` (if supported) or semi-transparent backgrounds with vibrant accents.

## Verification Plan

### Automated Tests
- N/A (UI focused task, will verify via build and manual check of logs if possible).

### Manual Verification
- Verify Audio Track selection menu shows available tracks.
- Verify Speed adjustment changes playback speed.
- Verify Subtitle Sync adjusts subtitle offset.
- Verify Skip buttons appear and work at appropriate times.
- Verify Episodes Sidebar allows switching episodes and triggers a new stream load.
- Verify Auto-Next loads the next episode automatically.
