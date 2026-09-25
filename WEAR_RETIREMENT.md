# Wear OS retirement policy

Effective 2026-09-25, Codex Monitor supports **Android phone only**.

This is a durable user decision and a hard project constraint:

- Do not build, lint, test, sign, publish, release, version, or perform acceptance for a Wear OS companion.
- Do not include Wear OS in estimates, scope sizing, implementation planning, verification matrices, release checklists, or agent work calculations.
- Do not install Android SDK/toolchain components solely for Wear OS.
- Do not require phone version bumps or source changes to keep any historical Wear module/version aligned.
- Do not spend implementation or review effort keeping Wear-specific code current.
- The existing `android/wear/`, shared Wear contracts, and phone-side Wear synchronization code are historical/unsupported source. They may remain in the repository for provenance, but they are outside the active product and verification graph.
- Future agents should ignore historical Wear code unless the user explicitly reverses this retirement decision and asks to restore Wear support.

The active Android Gradle graph, build script, lint script, tests, CI, release publishing, and canonical project process must remain phone-only.
