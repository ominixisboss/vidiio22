# Implementation Plan - UI Development and Media3 Integration

Develop Home, Search, and Details screens with real data and integrate Media3 ExoPlayer for streaming.

## Proposed Changes

### Dependencies
- Add Media3 dependencies to `libs.versions.toml` and `app/build.gradle.kts`.

### Navigation
- [MODIFY] [NavDestinations.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/navigation/NavDestinations.kt): Update `VidiioRoute.Details` to accept a `Movie` object.
- [NEW] `VidiioRoute.Player`: Add a new route for the full-screen video player that accepts a `Movie`.

### ViewModels
- [NEW] `HomeViewModel.kt`: Fetch curated categories from `MovieRepository`.
- [NEW] `SearchViewModel.kt`: Handle search queries and fetch results from `MovieRepository`.
- [NEW] `DetailsViewModel.kt`: Fetch movie details and stream sources from `MovieRepository`.

### UI Screens
- [MODIFY] [HomeScreen.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/ui/screens/HomeScreen.kt): Implement horizontal carousels for categories.
- [MODIFY] [SearchScreen.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/ui/screens/SearchScreen.kt): Implement search bar and results grid.
- [MODIFY] [DetailsScreen.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/ui/screens/DetailsScreen.kt): Implement metadata display and "Play" button.
- [NEW] `PlayerScreen.kt`: Implement full-screen Media3 ExoPlayer with controls.

### App Integration
- [MODIFY] [VidiioApp.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/ui/VidiioApp.kt): Wire up the new screens and ViewModels in the navigation graph.

## Verification Plan

### Automated Tests
- Build the project: `./gradlew assembleDebug`

### Manual Verification
- Verify Home screen displays movie categories.
- Verify Search screen returns results for queries.
- Verify Details screen shows movie info and "Play" button works.
- Verify Video Player plays content with controls.
