# Add Multiple Color Themes (6 Themes)

The goal is to implement 6 different color themes (Red, Blue, Green, Purple, Orange, Teal) that users can choose from in the Settings. This will be independent of the Light/Dark/System mode.

## User Review Required

- The themes will primarily change the accent color (Primary, etc.) while maintaining the dark streaming aesthetic.
- The default theme remains "Red" (Netflix-inspired).

## Proposed Changes

### [Color Layer]

#### [MODIFY] [Color.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/ui/theme/Color.kt)
- Add 6 primary colors for the themes: Red, Blue, Green, Purple, Orange, Teal.
- Define consistent naming for these theme accents.

### [Data Layer]

#### [MODIFY] [SettingsRepository.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/data/repository/SettingsRepository.kt)
- Add `ColorTheme` enum with values: `RED`, `BLUE`, `GREEN`, `PURPLE`, `ORANGE`, `TEAL`.
- Add DataStore preference key for `color_theme`.
- Add `colorThemeFlow` and `setColorTheme(ColorTheme)`.

### [ViewModel Layer]

#### [MODIFY] [SettingsViewModel.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/ui/viewmodel/SettingsViewModel.kt)
- Expose `colorTheme` as a `StateFlow`.
- Add `setColorTheme(ColorTheme)` function.

### [UI Layer]

#### [MODIFY] [Theme.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/ui/theme/Theme.kt)
- Update `VidiioTheme` to accept `colorTheme: ColorTheme`.
- Implement logic to generate `ColorScheme` based on the selected `ColorTheme` and `darkTheme` state.

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/MainActivity.kt)
- Collect `colorTheme` from `SettingsRepository` and pass it to `VidiioTheme`.

#### [MODIFY] [SettingsScreen.kt](file:///C:/Users/Roberto Hernandez Jr/AndroidStudioProjects/vidiio/app/src/main/java/com/example/vidiio/ui/screens/SettingsScreen.kt)
- Add a "Color Theme" selection section in the Appearance settings.
- Use a row of color circles or buttons to select the theme.

## Verification Plan

### Automated Tests
- Build the project to ensure no compilation errors.
- (Optional) Add a unit test for `SettingsRepository` to verify `ColorTheme` persistence.

### Manual Verification
- Deploy the app.
- Go to Settings.
- Change the Color Theme and verify that the UI accents (icons, buttons, switches) change immediately.
- Test with different Light/Dark modes to ensure readability.
