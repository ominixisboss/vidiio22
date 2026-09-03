# Video Player Implementation Comparison: PlayTorrioV3 vs. Vidiio

This analysis compares the video player implementation in `PlayTorrioV3` (Flutter) with our current `PlayerScreen.kt` (Jetpack Compose).

## 1. Feature Comparison Table

| Feature | PlayTorrioV3 | Vidiio (Current) | Status |
| :--- | :--- | :--- | :--- |
| **Core Playback** | MediaKit (MPV based) | ExoPlayer (Media3) | ✅ Equivalent |
| **Transport Bar** | Play/Pause, Seek, -/+ 10s, Volume, Speed, Aspect, Audio, Sub, Sync, Episodes, Fullscreen | Seekbar (Slider), Time position/duration | ⚠️ Vidiio lacks many shortcuts in bar |
| **Center Controls** | None (in transport bar) | Big Play/Pause/Buffer, Seek -/+ 10s icons | ✅ Vidiio has better center UI |
| **Gestures** | Tap (controls), Double Tap (Fullscreen), Mouse Scroll (Volume) | Volume, Brightness, Seek, Aspect Ratio (Custom Overlay) | ✅ Vidiio has more advanced gestures |
| **Episodes Navigation** | Side Panel (switch without exiting) | Back to Details Screen | ❌ Missing in Vidiio |
| **Source Selection** | Side Panel | Bottom Sheet | ✅ Equivalent |
| **Subtitle Selection** | Menu + Search + Sync | Bottom Sheet (Subdl) | ⚠️ Vidiio lacks Sync and Embedded selection |
| **Audio Track Selection** | Menu | Not implemented in UI | ❌ Missing in Vidiio |
| **Intro/Outro Skip** | IntroDB integration + Skip Button | Not implemented | ❌ Missing in Vidiio |
| **Playback Speed** | Menu (0.5x - 3.0x) | Not implemented in UI | ❌ Missing in Vidiio |
| **Subtitle Sync** | Real-time offset + Manual sync baking | Not implemented | ❌ Missing in Vidiio |
| **Scrobbling** | Trakt & Simkl | Not implemented | ❌ Missing in Vidiio |
| **Discord RPC** | Full Rich Presence | Not implemented | ❌ Missing in Vidiio |
| **Download** | In-player Download Button | Home/Search only | ❌ Missing in Vidiio |
| **Continue Watching** | Periodic progress saving | Save on back/completion | ⚠️ Vidiio could be more robust |

## 2. Missing "1:1" Features & UI Elements

### UI Components
1.  **Episodes Sidebar/Menu**: A way to browse and switch episodes of the same series without exiting the player.
2.  **Audio Track Menu**: A menu to switch between multiple audio tracks (e.g., different languages).
3.  **Playback Speed Menu**: Options to speed up or slow down playback.
4.  **Subtitle Sync UI**: A bar or modal to adjust subtitle offset (ms).
5.  **Skip Intro/Outro Button**: A floating button that appears when a skip segment is detected.
6.  **Download Button**: Directly in the player top bar for convenience.

### Logic & Integrations
1.  **IntroDB / Skip Segments**: Integration with a service like IntroDB or similar to detect skips.
2.  **Scrobbling**: Automatic progress reporting to Trakt and Simkl.
3.  **Periodic Progress Saving**: Saving current position every 5-10 seconds to handle crashes or abrupt closes better.
4.  **Discord Rich Presence**: Showing "Watching [Title] - S01E05" on Discord.

## 3. Recommended Improvements for Vidiio

-   **Integrate Episodes Navigation**: Add an "Episodes" button to the top bar that opens a sidebar or sheet.
-   **Add Audio Track Selection**: Leverage `Player.setTrackSelectionParameters` to expose audio track switching.
-   **Implement Playback Speed**: Standard ExoPlayer feature that just needs a UI toggle.
-   **Add Skip Segment Logic**: This would significantly improve the UX for binge-watching.
-   **Enhance Subtitle Management**: Allow selecting between embedded tracks and external ones, and add offset control.
-   **Robust Progress Tracking**: Implement a background timer to save progress periodically.

---
*Analysis completed on 2026-08-30.*
