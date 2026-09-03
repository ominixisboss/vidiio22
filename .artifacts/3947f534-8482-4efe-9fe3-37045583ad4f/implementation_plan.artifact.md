# Fix Delete/Clear functionality in Downloads

The goal is to ensure that deleting a download correctly stops active tasks, removes records from the database, and deletes physical files from storage.

## Proposed Changes

### [Download Component]

#### [MODIFY] [DownloadManager.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/download/DownloadManager.kt)
- Update `delete(id)` to delete physical files from storage before removing the database record.
- Ensure that if a download is active, it's cancelled and the service is notified.

#### [MODIFY] [DownloadService.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/download/DownloadService.kt)
- Improve task cancellation logic to ensure files are not being written to when a task is deleted.
- Ensure torrents are removed from the session if they are deleted/cancelled.

#### [MODIFY] [DownloadsScreen.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/ui/screens/DownloadsScreen.kt)
- Ensure the "Cancel" (Close) button also performs a delete if that's the intended behavior for clearing items from the list.
- Or, clarify the distinction between "Cancel" and "Delete". (I will make "Cancel" perform a delete if it's meant to "clear" the list).

#### [MODIFY] [DownloadDao.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/data/db/DownloadDao.kt)
- Add `deleteDownloadById` for convenience and to ensure atomicity if needed.

## Verification Plan

### Automated Tests
- N/A (Manual verification on device is preferred for file system operations)

### Manual Verification
1. Start a download (HTTP and Torrent).
2. Click "Delete" (or Cancel/Close) while it's downloading.
3. Verify the item disappears from the UI immediately.
4. Verify the active job in `DownloadService` is stopped.
5. Verify the partial file is deleted from the `Downloads/Vidiio` directory.
6. Complete a download.
7. Click "Delete" on the completed item.
8. Verify it disappears from the UI and the file is deleted from storage.
