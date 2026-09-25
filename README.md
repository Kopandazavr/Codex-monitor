# Codex Watch

Codex Watch is an unofficial open-source client for viewing the Codex allowance
attached to a signed-in ChatGPT account. This repository is a **monorepo** and preserves the full history of the Codex Meter fork it was migrated from:

| Path | Platform | Notes |
|------|----------|--------|
| Repository root | Shared | Docs, license, changelog, CI, convenience script wrappers |
| [`android/`](android/) | **Android phone** | Supported target: phone app with One UI dashboard, home widgets, Samsung lock/AOD, notifications, optional live usage monitor. Historical Wear source is retired; see [`WEAR_RETIREMENT.md`](WEAR_RETIREMENT.md). |
| [`ios/`](ios/) | **iPhone / iPad** | Native SwiftUI + WidgetKit client with portable 2.8.0 behavior (meters, monthly Free-tier windows, history analytics, diagnostics, widgets) |

There is no shared backend. Each platform talks to ChatGPT/Codex endpoints
directly and stores credentials only on-device.

## Android — Current development line 2.9.0

Codex Watch keeps the fork-owned 2.9.x version line. The Android package identity is `dev.kopandazavr.codexwatch`; this is intentionally a new installed-app identity relative to the prior Codex Meter package. The internal Java namespace is temporarily retained for a bounded migration and does not define the installed package.

The 2.8.0 baseline adapts to Free-tier monthly Codex limits when a paid plan expires, adds opt-in diagnostic log tracing/export, and declutters usage-history analytics with customizable highlights. The current 2.9.x work builds on that baseline with fork-specific process monitoring, idle reminders, Samsung acceptance fixes, and Codex Watch branding.

### Live countdowns

Samsung lock-screen widgets can display the remaining time until each usage window resets. The countdown is driven locally by Android `Chronometer` views using the reset timestamp already cached from the usage response; it does not repeatedly contact the server merely to update seconds or minutes.

### Live usage monitor

Settings includes an optional, user-started live usage monitor that runs only until the next available usage reset. It shows the real five-hour and weekly allowance values, marks a missing window as unavailable, and refreshes whenever the app receives new usage data. Android 16 can promote the notification as a Live Update, while compatible Samsung firmware can also surface it in the Now Bar. The monitor can be stopped at any time and is cleared when the user signs out.

### Reset alerts

Users can choose silent, notification-sound, or alarm-sound alerts for the five-hour limit, weekly limit, or both. Alerts can be conditional on the most recently observed allowance being below a selected threshold. Android schedules the notification for the cached reset time and performs a normal background refresh after the alert fires.

### Widget surfaces

The app includes:

- Responsive home-screen widgets with ring, four-dial, and battery-list layouts, plus Adaptive / Dials / Progress bars layout preference and drag-reorderable meter slots (Codex 5-hour/weekly, next reset, reset credits).
- Both-window, five-hour-only, and weekly-only configurations (legacy metric mode; meters checklist supersedes this when customized).
- Optional reset-credit inventory, expiration, and redemption controls.
- Transparent through opaque backgrounds, including a Background off toggle and three One UI-style opacity steps.
- Samsung One UI presentation throughout the dashboard, settings, and widget configuration surfaces.
- Samsung lock/AOD providers for both usage windows together or dedicated five-hour and weekly views.
- High-resolution supersampled lock-screen geometry with native Android text overlays.
- Optional live time-to-reset labels on supported lock-screen hosts.

## Authentication and data handling

- Browser-based ChatGPT sign-in using OAuth authorization code + PKCE and a localhost loopback callback.
- Access-token refresh with refresh-token rotation preservation.
- Android Keystore AES-GCM encryption for locally stored tokens.
- Usage and reset-credit retrieval from the ChatGPT backend routes used by Codex.
- No analytics, advertisements, WebView, or application-level relay server.

## Compatibility

- Phone minimum Android 8.0 (API 26)
- Phone compile/target SDK Android 16 (API 36)
- Wear OS companion: retired / unsupported; historical source only
- Universal DEX APK with no native ABI libraries
- Standard Android home-screen widgets
- Private Samsung One UI lock/AOD integration on compatible Galaxy firmware

## Build from source

### Android

See [`android/README.md`](android/README.md). The Android project uses Gradle with the OneUI-Design and oneui-icons libraries so its dashboards use Samsung-style SESL components, typography, and iconography.

Requirements:

- JDK 17 or newer
- Android SDK Platform 36
- Android Build Tools 36.x
- `ANDROID_SDK_ROOT` or `ANDROID_HOME` configured
- A GitHub Packages token in `GH_ACCESS_TOKEN` (with `read:packages`) and your username in `GH_USERNAME` when the OneUI-Design dependencies are not already cached

From the repository root:

```bash
./run-tests.sh
./build.sh
```

Or from `android/` directly. `build.sh` assembles the supported phone APK with Gradle and signs it with a local development key under `android/.local-signing/`. That locally signed APK will not install over the distributed release build. Wear OS is not built or released.

### iOS

See [`ios/README.md`](ios/README.md). Requires Xcode 26+ and iOS/iPadOS 26+.
The iOS client currently retains its pre-migration internal project/scheme names while carrying portable Android 2.8.0 behavior: Free-tier monthly
windows, scrubbable usage-history analytics with customize, and opt-in
diagnostic log export.

```bash
cd ios
swift test --package-path CodexMeterCore
xcodebuild -project CodexMeter.xcodeproj -scheme CodexMeter \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

## Releases

Creating a `v*` tag that matches the Gradle `versionName` in `android/app/build.gradle.kts` runs the phone-only CI pipeline and publishes the signed phone APK plus its SHA-256 checksum to GitHub Releases. Wear OS is retired and is not built, tested, signed, versioned, or published. CI authenticates and decrypts the persistent PKCS#12 release keystore `android/ci/release-keystore.p12.enc` (alias `codexmeter`) using the `ANDROID_SIGNING_PASSWORD` repository Actions secret, so every phone release remains on the established signing lineage. Release notes are taken from the root `CHANGELOG.md`.

## Platform stability

The ChatGPT usage and reset-credit routes and Samsung's lock-screen metadata are implementation details rather than stable third-party Android SDK contracts. OpenAI or Samsung may change eligibility, routing, response fields, host behavior, or private metadata.

OpenAI, ChatGPT, Codex, Samsung, Galaxy, One UI, and related marks belong to their respective owners. This project is not affiliated with or endorsed by OpenAI or Samsung.
