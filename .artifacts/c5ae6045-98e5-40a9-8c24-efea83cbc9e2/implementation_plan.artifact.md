# Fix Sources Loading on Details Page

This plan aims to improve the reliability and quantity of stream sources displayed on the movie details page by increasing timeouts, ensuring parallel execution of scrapers, and verifying UI responsiveness.

## User Review Required

> [!NOTE]
> Increasing the timeout to 30 seconds might make the overall "Loading" state feel longer if many scrapers are slow, but since we are sending results to a `Flow` in real-time, the user should see sources appearing as they are found.

## Proposed Changes

### 1. Networking & Configuration

#### [MODIFY] [VidiioApplication.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/VidiioApplication.kt)
- Increase `okHttpClient` read timeout to 35 seconds (to accommodate the 30-second scraper timeout).

---

### 2. Data Repository

#### [MODIFY] [MovieRepository.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/data/repository/MovieRepository.kt)
- Wrap `getStreamSources` scraper calls in `supervisorScope`.
- Add `withTimeoutOrNull(30000)` to each scraper call.
- Ensure all sources from scrapers are emitted without artificial limits.
- Add logging to identify which scrapers are timing out or failing.

---

### 3. UI Layer

#### [MODIFY] [DetailsViewModel.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/ui/viewmodel/DetailsViewModel.kt)
- Optimize the source collection logic to avoid restarting it unnecessarily when other parts of the state change (like favorites).

#### [MODIFY] [DetailsScreen.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/ui/screens/DetailsScreen.kt)
- Verify `LazyColumn` handles large lists correctly (it should, by default).

## Verification Plan

### Automated Tests
- Build the project using `./gradlew :app:assembleDebug` to ensure no syntax errors.

### Manual Verification
- Check the logs while searching for a movie to see if all scrapers are being called and if any are timing out.
- Verify that the "Sources" list on the details page populates with multiple items and is scrollable.
