# KSpeed Network

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![Minimum Android](https://img.shields.io/badge/Minimum_Android-12_(API_31)-3DDC84?logo=android&logoColor=white)

An LSPosed module that displays network upload and download speed before the system status icons.
This fork is based on [Net Speed Indicator by zazaulola](https://github.com/zazaulola/lsposed-netspeed-indicator).

```text
Network speed · Data Saver · DND · Battery / Duo
```

The indicator is a sibling before the system-icon container. Android handles icons appearing and
disappearing inside that container; no list of DND, Data Saver or other icon names is needed.

## Requirements

- Android **12 / API 31** or newer is the installation minimum.
- A working LSPosed-compatible framework, with this module enabled for **System UI**.
- A compatible SystemUI view hierarchy: `StatusIconContainer` in a horizontal `LinearLayout`.
  A minimum SDK alone does not guarantee compatibility with every ROM or Android version.

Upstream reported testing on Pixel 8 Pro / Android 16. Investigation for this fork used a Xiaomi
25010PN30G (`xuanyuan`) running an Android **17 / API 37** custom ROM. See the investigation report for
the distinction between measured existing behaviour, automated tests and pending device checks.

## Install

Build the debug APK or use a release from this fork when one is published:
[KSpeed Network releases](https://github.com/kadirjohn/kspeed-network-lsposed/releases).

1. Install `app/build/outputs/apk/debug/app-debug.apk`.
2. Disable the original **Net Speed Indicator** module if it is installed.
3. Enable **KSpeed Network** in LSPosed / Vector and select **System UI** as its scope.
4. Restart SystemUI or reboot, then open KSpeed Network to configure it.

The fork uses package `io.github.kadirjohn.kspeed` and provider `io.github.kadirjohn.kspeed.config`, so
it can be installed alongside the original without deleting its data. **Do not enable both hooks at
once.** Their preferences are separate; installation does not automatically import upstream settings.
During the local device investigation, the original settings were copied into the fork using ADB.

## Settings

All existing preference keys, types, defaults and options are retained:

- Display mode: download + upload, combined, or download only.
- Refresh interval: 0.5 / 1 / 2 / 3 seconds.
- Traffic source: total, Wi-Fi, or cellular.
- Units: automatic bytes/bits or fixed KB/s, MB/s, Kbit/s, Mbit/s; optional fixed-unit labels.
- Upload on top, idle-arrow dimming, hide when idle, and idle threshold.
- Text size; status-bar tint matching or separate custom download/upload colours.
- Optional notification-shade and lock-screen indicators.

The indicator keeps its original two-line appearance, but each arrow now stays next to its own value.
The previous 52 dp minimum width and full-width, end-aligned text rows have been removed.

## Duo Status Bar compatibility

Duo's edge-spacing setting can give its ring a **negative start margin**. At the measured size of
70 px and edge spacing of 78%, the ring overlaps its preceding sibling by 16 px. Upstream Net Speed
Indicator inserted directly before the battery/Duo, exposing its text to this overlap.

KSpeed Network is placed before the system-icon cluster. It also measures following siblings and
reserves the additional gap needed if a negative margin or translation reaches back over the speed
indicator, including when no system icons are visible. It changes only its own margin.

The compatibility adjustment is implemented entirely inside KSpeed Network.

## Build and check

Use JDK 17–21 and Android SDK platform 36. On a machine with Android Studio installed:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

The tests reproduce overlap with no visible icons, icon appearance/disappearance, translated rings,
RTL layout, and different upload/download text widths. Runtime code injected into SystemUI uses
platform views; AndroidX remains in the separate settings app.

Release signing uses a gitignored `keystore.properties`:

```properties
storeFile=release.jks
storePassword=...
keyAlias=kspeed
keyPassword=...
```

Then run `./gradlew :app:assembleRelease`. Without a release keystore the release APK is unsigned.
Keep the signing key safe; an APK signed by another key cannot update an existing installation.

## Implementation

- `StatusBarHook` hooks `StatusIconContainer.onFinishInflate` and `onMeasure`, then posts insertion
  into the horizontal parent **at** `indexOf(statusIcons)`, rather than after it. Other parent types
  are skipped because sibling order does not reserve space in an overlay layout.
- One tagged view is inserted per parent. Collapsed, shade and keyguard surfaces retain their
  existing detection and visibility settings.
- `StatusBarSpacing` checks visible following siblings after layout and during sampling, and updates
  only KSpeed's end margin. Its layout listener is removed when the view detaches.
- `NetSpeedView` samples `TrafficStats` or radio interfaces and mirrors the local clock's tint.
- `ConfigProvider` exposes only indicator settings over Binder; settings changes notify its observer.
- The app's update checker and source links target **this fork**, not the upstream release feed.

## Investigation and next work

The settings screen uses Material 3 with dynamic color (Monet) where the wallpaper supports it
(every installable device — minSdk 31), falling back to the Material baseline palette otherwise.
System-bar and display-cutout insets are applied from real `WindowInsets` data: the app bar keeps
its natural height below the status bar, and the preference list is padded above the navigation
bar and the keyboard. The launcher icon is a native adaptive icon (foreground mark + dark surface
background) with a matching `monochrome` layer for themed icons; it no longer draws the white
inner plate seen with the legacy icon.

## License and attribution

[MIT](LICENSE). Original work © 2026 zazaulola. KSpeed Network is maintained as a separate fork by
kadirjohn. The original copyright and license are retained.
