# Player Fixes: WebView Gestures & Immersive Mode

This plan addresses the issues where the `WebView` blocks custom player gestures and the immersive mode fails to hide system bars correctly.

## Proposed Changes

### [Player Component]

#### [MODIFY] [PlayerScreen.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/ui/screens/PlayerScreen.kt)
- Update `DisposableEffect` to use `WindowCompat.setDecorFitsSystemWindows(window, false)` and ensure system bars are hidden with `WindowInsetsControllerCompat`.
- Replace existing `pointerInput` modifiers with a unified custom gesture handler using `PointerEventPass.Initial`.
- The new handler will allow single taps to pass through to the `WebView` while intercepting and consuming drags (for volume/brightness) and double-taps (for seeking).
- Ensure the overlay controls are correctly layered on top of the `WebView`.

### [Main Activity]

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/MainActivity.kt)
- Ensure `enableEdgeToEdge()` is used (already present, but will double-check if any additional configuration is needed for true immersive mode).

## Verification Plan

### Manual Verification
- **Immersive Mode**: Open the player and verify that the status bar and navigation bar are hidden. Swipe from edges should show them temporarily.
- **Gestures**:
    - Swipe vertically on the left side to adjust brightness.
    - Swipe vertically on the right side to adjust volume.
    - Double-tap on the left/right sides to seek back/forward.
    - Single-tap to toggle the overlay controls (Back and Source buttons).
    - Single-tap on the video itself (inside WebView) to play/pause.
- **Source Selection**: Verify the "Source" button still opens the bottom sheet and selecting a source works.
