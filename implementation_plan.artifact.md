# Replace FMovies with TMDB for Metadata and Discovery

The goal is to switch from FMovies to themoviedb.org (TMDB) as the primary source for home screen categories and universal search. FMovies will be completely removed. Other scrapers will remain as streaming sources.

## User Review Required

> [!IMPORTANT]
> **TMDB API Key Required**: I need a TMDB API Key to implement the integration. Please provide your TMDB API Key.
> 1. Go to [TMDB Settings](https://www.themoviedb.org/settings/api) to get your API Key.
> 2. Add `TMDB_API_KEY=your_key_here` to your `local.properties` file: [local.properties](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/local.properties)

## Proposed Changes

### Data Layer - TMDB Integration

#### [NEW] [TMDBService.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/data/api/TMDBService.kt)
- Create a Retrofit interface for TMDB API.
- Endpoints: `trending/movie/day`, `movie/popular`, `movie/top_rated`, `search/multi`, `movie/{id}`, `tv/{id}`.

#### [NEW] [TMDBScraper.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/data/scraper/TMDBScraper.kt)
- Implement the `Scraper` interface using `TMDBService`.
- Map TMDB response models to Vidiio `Movie` and `Category` models.
- Use `https://image.tmdb.org/t/p/w500/` for poster paths.

#### [MODIFY] [MovieRepository.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/data/repository/MovieRepository.kt)
- Update `getHomeCategories` to use TMDB as the primary source.
- Update `search` to use TMDB.
- Ensure `getStreamSources` still queries other scrapers.

### Removal of FMovies

#### [DELETE] [FMoviesScraper.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/data/scraper/FMoviesScraper.kt)

#### [MODIFY] [VidiioApplication.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/VidiioApplication.kt)
- Remove `FMoviesScraper` instantiation.
- Initialize `TMDBService` and `TMDBScraper`.
- Inject `TMDBScraper` into `MovieRepository`.

#### [MODIFY] [SettingsScreen.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/ui/screens/SettingsScreen.kt)
- Remove FMovies from the sources list.

#### [MODIFY] [SettingsRepository.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/data/repository/SettingsRepository.kt)
- Remove "fmovies" from default sources.

### Build Configuration

#### [MODIFY] [build.gradle.kts (app)](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/build.gradle.kts)
- Add logic to load `TMDB_API_KEY` from `local.properties` and add it to `BuildConfig`.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug` to ensure it builds.

### Manual Verification
- Verify Home screen shows TMDB categories (Trending, Popular, Top Rated).
- Verify Search returns TMDB results.
- Verify movie selection leads to streaming sources from other scrapers.
