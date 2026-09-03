# Implementation Plan - Settings Redesign and Skip Intro Improvement

This plan covers the redesign of the Settings page and the improvements to the "Skip Intro" button in the player.

## User Review Required

> [!NOTE]
> The Settings page will be reorganized into five clear categories: Appearance, Playback, Sources, Stremio Addons, and About.
> Glassmorphism effects will be applied using semi-transparent surfaces to give it a modern look.

## Proposed Changes

### UI Components

#### [MODIFY] [SettingsScreen.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/ui/screens/SettingsScreen.kt)
- Reorganize settings into categories.
- Implement a modern Material 3 layout with better typography.
- Add category icons.
- Apply semi-transparent card backgrounds for a "Glassmorphism" effect.
- Add an "About" section with app version and developer info.

#### [MODIFY] [PlayerScreen.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/ui/screens/PlayerScreen.kt)
- Add `isIntroDismissed` state to prevent the "Skip Intro" button from reappearing after manual dismissal.
- Implement auto-dismiss logic (5-7 seconds) for the "Skip Intro" overlay.

#### [MODIFY] [PlayerOverlays.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/ui/player/components/PlayerOverlays.kt)
- Update `SkipButton` UI to be cleaner and less intrusive.
- Use a glassmorphism-inspired design for the skip button.

## Verification Plan

### Manual Verification
- Open Settings page:
    - Verify all categories are present.
    - Check if "Theme", "Dynamic Color", "Playback Quality", "Sources", and "Addons" still work.
    - Check the "About" section.
- Play a video:
    - Verify "Skip Intro" appears at the correct time (5s - 90s).
    - Verify "Skip Intro" can be dismissed manually using the 'X' button and doesn't reappear immediately.
    - Verify "Skip Intro" auto-dismisses after ~7 seconds.
