# DesktopMode-Launcher

Desktop-style Android home launcher focused on freeform windows and taskbar workflows.

## Features

- Fullscreen launcher UI that hides status and navigation bars when resumed.
- Desktop long-press context menu for wallpaper/color customization.
- Bottom taskbar with app menu plus built-in **Back** and **Home** navigation buttons.
- Home button minimizes running windows by sending user to home screen.
- Back button uses an accessibility service global back action.
- Permission shortcuts for overlay, accessibility, and usage access settings.
- Taskbar app list shows currently active/minimized apps from recent app tasks.

## Permissions / setup

1. Set the app as default home launcher.
2. Enable accessibility service for global Back support.
3. Enable usage access so recent apps can populate taskbar.
4. Enable overlay permission to allow always-on-top elements in future overlay surfaces.

## Build

```bash
./gradlew assembleDebug
```

## Notes

Android system-level "always on top" behavior can vary by OEM and desktop mode implementation.
