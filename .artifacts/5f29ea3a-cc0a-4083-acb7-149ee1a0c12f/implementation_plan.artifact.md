# New App Icon Implementation Plan

Redesign the "Vidiio" app icon using a modern, resolution-independent Vector Drawable approach with a fresh Purple/Indigo color palette as requested.

## Proposed Changes

### [Resource] Colors

#### [MODIFY] [colors.xml](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/res/values/colors.xml)
Add new colors for the icon:
- `icon_background_start`: Deep Indigo
- `icon_background_end`: Darker Indigo/Black
- `icon_accent`: Vibrant Amethyst/Purple

### [Resource] App Icon

#### [MODIFY] [ic_launcher_background.xml](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/res/drawable/ic_launcher_background.xml)
Replace the solid black background with a smooth radial or linear gradient using the new indigo palette.

#### [NEW] [ic_launcher_foreground.xml](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/res/drawable/ic_launcher_foreground.xml)
Create a new Vector Drawable for the foreground.
- **Concept**: A stylized "V" that incorporates a play button motif.
- **Style**: Modern, clean lines with subtle shadows or gradients for depth.

#### [DELETE] [Existing PNGs](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/res/drawable-*)
Remove the existing `ic_launcher_foreground.png` files from various density folders to ensure the new Vector version is used exclusively.

## Verification Plan

### Automated Tests
- Build the project to ensure no resource conflicts.

### Manual Verification
- Deploy to an emulator/device and check the icon on the home screen and in the app drawer.
- Verify the Adaptive Icon behavior (scrolling/wiggling on the launcher).
