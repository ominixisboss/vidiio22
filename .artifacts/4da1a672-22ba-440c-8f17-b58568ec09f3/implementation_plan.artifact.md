# Implement P2P Scrapers and Optimize Torrent Engine

This plan covers implementing and updating P2P scrapers (TorrentGalaxy, Knaben, 1337x, YTS) and optimizing the torrent engine for better performance and reliability, following the "PlayTorrio V3" standard.

## Proposed Changes

### [Scrapers]

#### [MODIFY] [TorrentGalaxyScraper.kt](file:///C:/Users/Roberto%20Hernandez%20Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/data/scraper/TorrentGalaxyScraper.kt)
- Update base URL to `https://torrentgalaxy.to` (with fallback to `.info`).
- Refine selectors to match latest site layout.
- Ensure `seeders` and `size` are correctly extracted.
- Implement parallel fetching of magnets for better performance.

#### [MODIFY] [KnabenScraper.kt](file:///C:/Users/Roberto%20Hernandez%20Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/data/scraper/KnabenScraper.kt)
- Refine scraping logic to match PlayTorrio's implementation.
- Ensure robust parsing of search results, specifically for seeders and file sizes.

#### [NEW] [OneThreeThreeSevenXScraper.kt](file:///C:/Users/Roberto%20Hernandez%20Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/data/scraper/OneThreeThreeSevenXScraper.kt)
- Implement search using `https://1337x.to/search/{query}/1/`.
- Handle mirrors for resilience.
- Extract magnet links, seeders, and size from search results (may require detail page fetch).

#### [NEW] [YtsScraper.kt](file:///C:/Users/Roberto%20Hernandez%20Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/data/scraper/YtsScraper.kt)
- Implement search using the YTS API: `https://yts.mx/api/v2/list_movies.json?query_term={query}`.
- Extract magnet links (or hashes to construct magnets), quality, and size.

### [Torrent Engine]

#### [MODIFY] [TorrentEngine.kt](file:///C:/Users/Roberto%20Hernandez%20Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/torrent/TorrentEngine.kt)
- Maximize libtorrent settings for DHT, PEX, and LSD.
- Add more aggressive peer discovery settings.
- Ensure `announce_to_all_trackers` and `announce_to_all_tiers` are enabled.

#### [MODIFY] [TorrentManager.kt](file:///C:/Users/Roberto%20Hernandez%20Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/torrent/TorrentManager.kt)
- Add a list of **Global High-Performance Trackers**.
- Automatically append these trackers to any magnet link or torrent being added.
- Implement a metadata pre-check or improved status messaging for better user feedback.

### [Application Setup]

#### [MODIFY] [VidiioApplication.kt](file:///C:/Users/Roberto%20Hernandez%20Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/VidiioApplication.kt)
- Instantiate and register `OneThreeThreeSevenXScraper` and `YtsScraper`.

---

## Verification Plan

### Automated Tests
- Run `TorrentGalaxyScraper` and `KnabenScraper` tests (if they exist) or create a simple unit test to verify scraping results for a known movie.
- Test `TorrentManager` with a sample magnet link to verify tracker integration and peer discovery.

### Manual Verification
- Search for a movie in the app and verify that TorrentGalaxy, Knaben, 1337x, and YTS sources appear with correct seeders and size.
- Start playing a torrent source and verify that it buffers quickly and the status overlay is descriptive.
