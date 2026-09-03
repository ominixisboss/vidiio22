# Enhance TorrentManager and Player for Vidiio V3

Enhance the `TorrentManager` and `Player` to support sequential streaming, multi-file selection, and real-time stats overlay.

## Proposed Changes

### Torrent
#### [MODIFY] [TorrentManager.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/torrent/TorrentManager.kt)
- Update `TorrentStatus` to include `uploadRate` and `bufferProgress`.
- Enable strict sequential downloading on `TorrentHandle`.
- Implement `getMetadata(magnetUrl: String)` to retrieve file lists.
- Implement `startStreaming(fileIndex: Int)` to prioritize and download a specific file.
- Add buffering logic that waits for a minimum number of pieces/percentage before signaling ready.

#### [MODIFY] [TorrentService.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/torrent/TorrentService.kt)
- Add `getMetadata` method.
- Update `startStreaming` to accept an optional file index.

#### [MODIFY] [TorrentStreamServer.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/torrent/TorrentStreamServer.kt)
- Ensure robust Range request handling for seeking.

### UI
#### [MODIFY] [PlayerScreen.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/ui/screens/PlayerScreen.kt)
- Implement a file selection dialog/sheet if a torrent contains multiple media files.
- Add a real-time stats overlay displaying Peers, Speed (D/U), and Buffer Progress.
- Ensure proper cleanup of torrent resources on dispose.

## Verification Plan

### Automated Tests
- Build the app using `./gradlew :app:assembleDebug` to ensure no regressions.

### Manual Verification
- Test streaming a magnet link with multiple files (e.g., a TV show pack).
- Verify that the file selection list appears.
- Verify that the stats overlay updates in real-time during playback.
- Verify that seeking works correctly.
- Verify that resources are released when exiting the player.
