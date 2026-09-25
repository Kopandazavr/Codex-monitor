# Codex Monitor for Android

Native Android phone client for viewing the Codex allowance attached to a
signed-in ChatGPT account. This directory is the Android Gradle project of
the Codex Monitor monorepo. The iOS client lives under [`../ios/`](../ios/).

## Supported target

**Android phone only.** Wear OS support was retired by explicit user decision on
2026-09-25. The historical `wear/`, shared Wear contracts, and phone-side Wear
sync sources may remain for provenance, but they are unsupported and outside the
active Gradle/build/lint/test/version/release/acceptance graph. See
[`../WEAR_RETIREMENT.md`](../WEAR_RETIREMENT.md).

## Layout

| Path | Role |
|------|------|
| `app/` | Supported phone app (`dev.kopandazavr.codexmonitor`) |
| `shared/` | Shared phone/core contracts; historical Wear contracts are unsupported |
| `wear/` | Historical retired Wear OS companion source; do not build/test/version |
| `tests/` | Phone/core pure-Java and source-contract tests used by `./run-tests.sh` and CI |
| `vendor/m2/` | Cached One UI / SESL Maven artifacts |
| `ci/` | Encrypted release keystore material for GitHub Actions |

## Build and test

From this `android/` directory (or via the repo-root wrappers):

```bash
./run-tests.sh
./lint.sh
./build.sh
```

Requirements: JDK 17+, Android SDK Platform 36, Build Tools 36.x, and
`ANDROID_SDK_ROOT` / `ANDROID_HOME`. `vendor/m2` covers SESL deps offline;
optional `GH_USERNAME` / `GH_ACCESS_TOKEN` refresh GitHub Packages.

The supported local artifact lands in `android/dist/` as
`CodexMonitor-<version>.apk`. No Wear APK is produced. See the repository root
[`README.md`](../README.md) for product notes and release tagging.
