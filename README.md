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

Download the signed APK from:
[KSpeed Network releases](https://github.com/kadirjohn/kspeed-network-lsposed/releases).

1. Install the release APK (or build `app/build/outputs/apk/debug/app-debug.apk` for local development).
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
- A live status-bar preview that fades out as the settings header collapses.
- Optional main-status-bar cellular-icon hiding, preserving Quick Settings and lock-screen icons.
- Optional removal of spacing left by icons collapsed by Duo or other modules.

On Neoteric Android 17, cellular signal, VoLTE and VoWiFi share a group: hiding cellular icons hides
that group, rather than only VoWiFi. These new icon controls default to off and do not disable calls.
The per-container exclusion API is ROM-dependent; its main-bar/Quick Settings behaviour was checked
on the Xiaomi 25010PN30G running Neoteric Android 17.

Official releases use a dedicated release signing key. A locally debug-signed installation cannot be
updated directly with a release-signed APK. Export settings before switching signing identities.

The indicator keeps its original two-line appearance, but each arrow now stays next to its own value.
The previous 52 dp minimum width and full-width, end-aligned text rows have been removed.

## VPN and live speed measurement

KSpeed counts the underlying internet interfaces once. VPN interfaces (such as `tun0`) are excluded,
even when Android also labels a VPN as Wi-Fi or cellular. Multiple network entries referring to the
same interface are deduplicated. Wi-Fi/cellular selection follows the same accounting rule.

Values represent network-layer traffic, including protocol/VPN overhead and other applications.
They need not match a single app's payload speed or Speedtest's averaging window exactly. Combined
mode adds upload and download; the other modes show their respective directions.

Fresh interface counters are used when readable. Otherwise KSpeed uses Android's per-interface
counters and samples them no faster than once per second, while the UI may still refresh every 0.5 s.
This avoids treating a repeated cached reading as zero and dividing a one-second counter increase by
half a second. The idle threshold is always in KB/s, regardless of the chosen display unit. Network,
source and counter-backend changes reset the sampling baseline to prevent false spikes.

There is no double-counting toggle. The implementation does not fall back to blanket all-interface
totals if physical-interface measurement is unavailable. VPN exclusion, deduplication, cached reads,
source changes and counter resets have regression tests. Device testing confirmed the original 500 ms
traffic/idle oscillation; the replacement was installed without changing the user's 500 ms refresh or
50 KB/s idle threshold. Exact parity with Speedtest or all VPN/ROM combinations is not claimed.

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
RTL layout, different upload/download text widths, idle reappearance and the first drawn frame.
Runtime code injected into SystemUI uses
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

## Diagnostic logging

Debug logging is disabled by default. For a temporary ADB investigation:

```bash
adb shell setprop log.tag.KSpeed DEBUG
adb logcat -v time -s KSpeed:D
```

Messages cover visibility transitions and their idle threshold, actual spacing changes and update
failures. Successful traffic samples do not generate log lines, and the module writes no log files.
Disable debug messages afterwards with `adb shell setprop log.tag.KSpeed INFO`.

## Implementation

- `StatusBarHook` hooks `StatusIconContainer.onFinishInflate` and `onMeasure`, then posts insertion
  into the horizontal parent **at** `indexOf(statusIcons)`, rather than after it. Other parent types
  are skipped because sibling order does not reserve space in an overlay layout.
- One tagged view is inserted per parent. Collapsed, shade and keyguard surfaces retain their
  existing detection and visibility settings.
- `StatusBarSpacing` checks visible following siblings before drawing, after their layout is current,
  and updates only KSpeed's end margin. If the margin changes, drawing waits for the resulting layout.
  Stale bounds from a hidden indicator are ignored; the listener is removed when the view detaches.
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
