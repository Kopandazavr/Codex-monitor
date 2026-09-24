# Codex Monitor for Android

Native Android + Wear OS client for viewing the Codex allowance attached to a
signed-in ChatGPT account. This directory is the **Android** Gradle project of
the Codex Monitor monorepo. The iOS client lives under [`../ios/`](../ios/).

## Layout

| Path | Role |
|------|------|
| `app/` | Phone app (`dev.kopandazavr.codexmonitor`; internal Java namespace currently retained as `dev.kopandazavr.codexmonitor`) |
| `wear/` | Wear OS companion (`dev.kopandazavr.codexmonitor`) |
| `shared/` | Shared phone↔watch contracts |
| `tests/` | Pure-Java/source-contract tests used by `./run-tests.sh` and CI |
| `vendor/m2/` | Cached One UI / SESL Maven artifacts |
| `ci/` | Encrypted release keystore material for GitHub Actions |

## Build and test

From this `android/` directory (or via the repo-root wrappers):

```bash
./run-tests.sh
./lint.sh
./build.sh
```

Requirements: JDK 17+, Android SDK Platforms 36 and 37.0, Build Tools 36.x, and
`ANDROID_SDK_ROOT` / `ANDROID_HOME`. `vendor/m2` covers SESL deps offline;
optional `GH_USERNAME` / `GH_ACCESS_TOKEN` refresh GitHub Packages.

Signed local APKs land in `android/dist/` as `CodexMonitor-<version>.apk` and
`CodexMonitor-Wear-<version>.apk`. See the repository root [`README.md`](../README.md)
for product notes and release tagging.
