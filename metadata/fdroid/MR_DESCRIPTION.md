# New app: unicanta

`unicanta` is a fork of [samolego/Canta](https://github.com/samolego/Canta), an Android
debloating tool that removes preinstalled system apps through Shizuku, a device owner
implementation, or root.

This submission uses a **different application ID** (`io.github.a303562915.unicanta`) and a
different signing key from the upstream `io.github.samolego.canta`, so the two can coexist in
the index and install side by side.

## What differs from upstream

- Package name `io.github.a303562915.unicanta` (upstream: `io.github.samolego.canta`).
- A community layer: shared presets ("方案") with votes, per-package description corrections,
  comments, and a recommendations tab. Backed by our own Supabase project.
- Update/distribution channel is GitHub Releases plus the download page below; the app
  detects an F-Droid install (see `CantaPlatform.isInstalledFromFdroid()`) and then leaves
  updates to the store instead of downloading our APK, because our signature can never replace
  an F-Droid-signed build.
- Desktop (JVM) and web (Wasm) clients exist in the same repository. **The F-Droid build only
  produces the Android APK** — the `prebuild` step drops the `:webApp` and `:desktopApp` Gradle
  modules so the build server does not need Node.js, Yarn or Binaryen distributions.

Full list of changes: https://github.com/303562915/unicanta#readme

## Anti-features: Tracking

The app sends anonymous usage events to a **self-hosted** endpoint (no third-party analytics
SDK, no account, no identifiers beyond model/ROM/SDK and the app's own version):

- `install` / `uninstall` events with the list of removed system packages, **only** when the
  user leaves the "share anonymous uninstall data" setting enabled (it can be turned off).
- Update diagnostics: one row per check/download/install attempt with the failure code, the
  stage, the model and the ROM, so update failures can be diagnosed without asking the user
  what their screen said.

Hence `AntiFeatures: Tracking`. The endpoint, payload shape and the opt-out switch are
documented in `docs/` of the source repository.

## Permissions, justified

The app is a package manager front-end, so it asks for the package-visibility and installer
permissions that role requires. All of them are declared in `androidApp/src/main/AndroidManifest.xml`:

- `QUERY_ALL_PACKAGES` / `GET_INSTALLED_APPS` — the whole point of the app is listing every
  installed package so the user can pick ones to remove; without full package visibility the
  list would be a random subset. HyperOS/MIUI/ColorOS additionally gate it behind their own
  runtime switch, which the app requests.
- `REQUEST_INSTALL_PACKAGES` — only used by the built-in updater, which downloads the APK from
  our GitHub release and hands it to the system installer. **On an F-Droid install this path is
  disabled entirely**: `CantaPlatform.isInstalledFromFdroid()` makes the update check, the
  startup auto-update and the About-page updater show "update it in the store" instead of
  downloading anything.
- `INTERNET` / `ACCESS_NETWORK_STATE` — community features (presets, votes, comments) and the
  update check described above.
- `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED` — optional scheduled re-checks and the
  post-uninstall notification.
- Removal itself needs **Shizuku** (installed separately), **device owner** mode or **root**;
  the app ships no exploit and no privilege escalation of its own.
- No `android:sharedUserId`, no NDK code (`ndk: false`).

## Build notes

- Only the Android APK is built. The Android application lives in `:androidApp`; because the
  recipe's `gradle` field only takes flavor names, `gradle: - yes` runs `gradle assembleRelease`
  at the repository root, and the root `build.gradle.kts` has a delegating task that depends on
  `:androidApp:assembleRelease`. The result is picked up through
  `output: androidApp/build/outputs/apk/release/*.apk`.
- `prebuild` drops the `:webApp` (Wasm) and `:desktopApp` (JVM) Gradle modules, so the build
  server never needs Node.js, Yarn or Binaryen distributions.
- No keystore is shipped: `androidApp/build.gradle.kts` leaves the release variant unsigned when
  `key.properties` is absent, so the build server signs the APK with its own key.
- `dependenciesInfo` is disabled for both APK and bundle for reproducible builds.
- `versionName` / `versionCode` come from `gradle.properties`, so `UpdateCheckMode: Tags` with
  `AutoUpdateMode: Version` tracks our `vX.Y.Z` tags.
- Localized store listing (title, short/full description, screenshots) lives in the source
  repository under `metadata/<locale>/` in the fastlane layout.
- The build needs a recent Gradle (AGP 9.4.1 / Kotlin 2.4). If the build server's system
  `gradle` is older, please tell me which version is available and I'll adjust the recipe.

## Upstream review points

1. This is a fork of an app that is already in F-Droid (`io.github.samolego.canta`). The
   application ID and the signing key both differ, so this listing neither replaces nor upgrades
   the upstream one — both can be indexed and installed side by side. **The original author has
   not been contacted.** If the reviewer requires that notification before inclusion, I will open
   a thread at github.com/samolego/Canta/issues and link it in this MR.
2. Whether `UpdateCheckMode: Tags` needs a `VercodeOperation` — our `versionCode` is monotonic
   and independent from the tag name.
