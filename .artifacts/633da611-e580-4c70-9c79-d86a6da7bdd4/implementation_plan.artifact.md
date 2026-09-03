# Torrent Playback Implementation Plan

The current torrent playback implementation has several issues that prevent it from working reliably:
1. `PlayerScreen.kt` incorrectly identifies torrent streams as HLS (`isM3u8 = true`).
2. `TorrentStreamServer.kt` serves files directly without waiting for the required pieces to be downloaded, leading to playback failures when ExoPlayer tries to read missing data.
3. `TorrentManager.kt` only prioritizes pieces at the start of the stream and doesn't adapt to the user's current playback position (e.g., after seeking).

## Proposed Changes

### [Component] Torrent Module

#### [MODIFY] [PlayerScreen.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/ui/screens/PlayerScreen.kt)
- Fix the `startPlayback` call for torrent sources by setting `isM3u8 = false`.

#### [MODIFY] [TorrentManager.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/torrent/TorrentManager.kt)
- Add `updateStreamPriority(startByte: Long)` to dynamically set piece deadlines based on the current requested byte range.
- Add `isRangeAvailable(startByte: Long, length: Long): Boolean` to check if the requested data is on disk.
- Add a blocking `waitForRange(startByte: Long, length: Int)` method to be used by the HTTP server.

#### [MODIFY] [TorrentStreamServer.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/torrent/TorrentStreamServer.kt)
- Pass `TorrentManager` to the server.
- In the `serve` method, call `torrentManager.updateStreamPriority(startByte)` to inform the engine of the player's position.
- Implement a blocking wait for the requested range (or at least the first part of it) before returning the response.

#### [MODIFY] [TorrentService.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/torrent/TorrentService.kt)
- Update `startStreaming` to pass the `torrentManager` instance to `TorrentStreamServer`.

## Verification Plan

### Automated Tests
- N/A (Torrent testing requires a live network or a complex mock environment).

### Manual Verification
1. Start a torrent stream from the UI.
2. Verify that the player starts after buffering.
3. Seek to a later point in the video and verify that the player waits for buffering and then continues playback correctly.
4. Check logs for any libtorrent or NanoHTTPD errors.
