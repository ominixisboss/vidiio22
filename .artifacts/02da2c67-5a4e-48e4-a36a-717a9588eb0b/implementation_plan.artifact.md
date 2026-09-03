# Fix UI Issues: Adaptive Navigation and Immersive Playback

This plan addresses the UI issues reported in the Vidiio app:
1.  **Adaptive Navigation**: Ensuring the `NavigationRail` is shown on large screens (Tablets/Foldables).
2.  **Immersive Playback**: Hiding the navigation bar/rail on the `Player` screen.

## Proposed Changes

### UI Components

#### [MODIFY] [VidiioApp.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/ui/VidiioApp.kt)

-   Calculate the current `WindowAdaptiveInfo` using `currentWindowAdaptiveInfo()`.
-   Determine the current route to identify if the user is on the `Player` screen.
-   Explicitly set the `layoutType` of `NavigationSuiteScaffold`:
    -   Use `NavigationSuiteType.None` when on the `Player` screen to hide navigation.
    -   Use `NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo)` for all other screens to ensure adaptive behavior (Rail on large screens).

## Verification Plan

### Automated Tests
-   Run `./gradlew :app:assembleDebug` to ensure the project still builds.

### Manual Verification
-   Verify the `VidiioAppTabletPreview` in Android Studio shows a Navigation Rail.
-   Verify the `VidiioAppPhonePreview` shows a Bottom Navigation Bar.
-   (Simulated) Verify that navigating to the `Player` screen hides the navigation components.
