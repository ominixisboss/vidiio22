# Implementation Plan - Source Selector & Enhanced Scrapers

Port scrapers from PlayTorrioV3 and implement a real-time source selector in the `DetailsScreen`.

## User Review Required

> [!IMPORTANT]
> The source selector will change the "Play" button behavior: instead of launching the player immediately, it will open a list of available stream sources.

## Proposed Changes

### Data Models

#### [MODIFY] [StreamSource.kt](file:///C:/Users/Roberto%20Hernandez%20Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/data/model/StreamSource.kt)
- Add `headers: Map<String, String>? = null` to handle Referer-protected streams.

### Scrapers

#### [NEW] [VidSrcScraper.kt](file:///C:/Users/Roberto%20Hernandez%20Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/data/scraper/VidSrcScraper.kt)
- Port from `vidsrc.dart`.
#### [NEW] [KnabenScraper.kt](file:///C:/Users/Roberto%20Hernandez%20Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/data/scraper/KnabenScraper.kt)
- Port from `knaben.dart`.
#### [NEW] [VidEasyScraper.kt](file:///C:/Users/Roberto%20Hernandez%20Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/data/scraper/VidEasyScraper.kt)
- Port from `videasy.dart`.
#### [NEW] [TorrentGalaxyScraper.kt](file:///C:/Users/Roberto%20Hernandez%20Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/data/scraper/TorrentGalaxyScraper.kt)
- Port from `torrent_galaxy.dart`.

*(And others as time permits)*

### UI Components

#### [NEW] [SourceSelectorBottomSheet.kt](file:///C:/Users/Roberto%20Hernandez%20Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/ui/components/SourceSelectorBottomSheet.kt)
- A `ModalBottomSheet` displaying a list of found `StreamSource`s.
- Features: Loading animation, quality badges, and source grouping.

### ViewModels & Logic

#### [MODIFY] [DetailsViewModel.kt](file:///C:/Users/Roberto%20Hernandez%20Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/ui/viewmodel/DetailsViewModel.kt)
- Add `sourcesFlow` to emit results as they are found.
- Add `onPlayClicked()` to trigger scraping.

#### [MODIFY] [DetailsScreen.kt](file:///C:/Users/Roberto%20Hernandez%20Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/ui/screens/DetailsScreen.kt)
- Integrate `SourceSelectorBottomSheet`.
- Update "Play" button logic.

## Verification Plan

### Automated Tests
- N/A for UI (Manual verification preferred for scraper results).

### Manual Verification
1. Open a movie detail page.
2. Click "Play".
3. Verify the "Source Selector" bottom sheet appears.
4. Verify sources appear in real-time as they are found.
5. Select a source and verify it launches the player.
