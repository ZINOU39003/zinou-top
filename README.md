# ZINOU TOP · زينو توب

**ZINOU TOP** is a video app for Android TV, TV boxes and compatible Android car screens.
It is based on the open-source project **[SmartTube](https://github.com/yuliskov/SmartTube)** (MIT License) and keeps
all of its features, with its own identity, package ID, signing key and GitHub-based updates.

> **⚠️ Signing key warning** — updates for `com.zinou.top` require the same package ID **and** the same release
> signing key. If the ZINOU TOP release keystore or its password is lost, updates can no longer be published
> normally. Never commit the keystore or `keystore.properties`. See [SIGNING.md](SIGNING.md).

| | |
|---|---|
| App name | ZINOU TOP (Arabic: زينو توب) |
| Package ID | `com.zinou.top` |
| Version | 1.0.0 (`versionCode` 10000) |
| Based on | SmartTube, upstream commit in [`zinou.properties`](zinou.properties) (`upstream.commit`) |
| Distribution | GitHub Releases of `ZINOU39003/zinou-top` — no server / VPS |

## Features

Everything SmartTube provides — unchanged: home, search (incl. voice), playback up to 4K/8K, HDR, 60 fps,
subtitles, audio/quality selection, login, subscriptions, history, playlists, channels, SponsorBlock, DeArrow,
remote control / D-pad navigation, Android TV channels and global search, casting from phone.

ZINOU TOP adds:

- ZINOU TOP branding: name, icons, TV banner, splash, About screen (English + Arabic)
- independent package `com.zinou.top`: installs **next to** SmartTube without conflicts
- update checker based on **GitHub Releases** with **SHA-256 verification** (see below)
- reproducible release tooling and GitHub Actions workflows

## Repository layout

| Path | What |
|---|---|
| `zinou.properties` | **single configuration**: GitHub owner/repository, upstream commit, certificate pin |
| `*/src/zinou/` | ZINOU flavor resources (name, icons, banner, splash, strings, search authority) |
| `common/src/main/java/.../common/zinou/` | update system (config, update.json validation, SHA-256, installer flow) |
| `common/zinou-config.gradle`, `smarttubetv/zinou-signing.gradle` | build configuration and signing guard |
| `branding/zinou/` | placeholder artwork generator |
| `scripts/release/` | creates `update.json`, `SHA256SUMS.txt`, release notes |
| `scripts/upstream/` | safe SmartTube update script |
| `.github/workflows/zinou-*.yml` | CI build and release |

SmartTube source code (packages `com.liskovsoft.*`) is intentionally **not renamed**, so upstream updates stay easy to merge.

## Build

Requirements: JDK 17, Android SDK (Android Studio), Git with submodules.

```bash
git clone --recurse-submodules https://github.com/ZINOU39003/zinou-top.git
cd zinou-top
./gradlew assembleZinouDebug          # Windows: .\gradlew.bat assembleZinouDebug
```

Output: `smarttubetv/build/outputs/apk/zinou/debug/` — `ZinouTop-v1.0.0-debug.apk` (universal) and per-ABI APKs
(`armeabi-v7a`, `arm64-v8a`, `x86`). Debug builds use the standard debug key and need no keystore.

Release build (`assembleZinouRelease`) needs your private key: see [SIGNING.md](SIGNING.md).
Tests: `./gradlew :common:testZinouDebugUnitTest --tests 'com.liskovsoft.smartyoutubetv2.common.zinou.*'` and
`python3 scripts/release/test_make_release_assets.py`.

The original SmartTube flavors (`stbeta`, `ststable`, `stfdroid`) are still present and unchanged.

## Repository setup (once)

1. Create an empty GitHub repository `zinou-top` (no README).
2. In `zinou.properties` set `github.owner=<your username>` and commit.
3. Push the branches:
   ```bash
   git remote add origin https://github.com/ZINOU39003/zinou-top.git
   git push -u origin upstream zinou main
   ```
4. Set `zinou` as the default branch (Settings → General) and protect `main`.

Branch model (`upstream` → `zinou` → `main`): [UPSTREAM.md](UPSTREAM.md).

## Update system

```
GitHub Release (published) ─ update.json ─► ZINOU TOP checks latest version (HTTPS, validated)
   ─► compare versionCode ─► pick the APK for the device architecture ─► download over HTTPS
   ─► verify SHA-256 + size + package + newer version ─► Android Package Installer (user confirms)
```

- Settings → About → **Check for updates**; automatic checks can be switched off / notify / show a dialog.
- Nothing is installed silently; Android itself also requires the same signing key for an update.
- While `github.owner` is still `YOUR_USERNAME`, the app contains no update URL and reports *"update source is not configured"*.

Details and the `update.json` format: [docs/UPDATE_SYSTEM.md](docs/UPDATE_SYSTEM.md). Publishing: [RELEASING.md](RELEASING.md).

## Documentation

| Document | Content |
|---|---|
| [SIGNING.md](SIGNING.md) | create the release key, build signed APKs, verify signatures, CI secrets |
| [RELEASING.md](RELEASING.md) | step-by-step release (CI or local) |
| [UPSTREAM.md](UPSTREAM.md) | branches and merging SmartTube updates |
| [SECURITY.md](SECURITY.md) | security model, secrets policy, network endpoints, reporting |
| [docs/UPDATE_SYSTEM.md](docs/UPDATE_SYSTEM.md) | update flow, `update.json`, error handling |
| [docs/QA-REPORT.md](docs/QA-REPORT.md) | test results and device test checklist |
| [CHANGELOG.md](CHANGELOG.md) | version history |

## License and attribution

ZINOU TOP is a modified version of **SmartTube** — Copyright (c) 2020-present yuliskov — distributed under the
**MIT License** ([LICENSE](LICENSE)). The license and copyright notices are kept in this repository and shown in the
app (About → Open Source Licenses). Bundled components keep their own licenses: ExoPlayer (Amazon port), AndroidX
Leanback/Fragment, ChatKit, FilePicker, Slidr, Apache Commons IO (Apache License 2.0), DoubleTapPlayerView (MIT),
J2V8 (EPL 1.0) — see the `LICENSE` files in each module. [PRIVACY.md](PRIVACY.md) is the upstream SmartTube privacy
statement; ZINOU-specific network behaviour is listed in [SECURITY.md](SECURITY.md).

ZINOU TOP is not affiliated with YouTube, Google or the SmartTube project.

---

<div dir="rtl">

## نبذة بالعربية

**زينو توب (ZINOU TOP)** تطبيق فيديو لشاشات Android TV وأجهزة TV Box وشاشات السيارات المتوافقة، مبني على المشروع
مفتوح المصدر **SmartTube** (رخصة MIT) مع الحفاظ على جميع وظائفه.

- معرّف الحزمة: `com.zinou.top` — يمكن تثبيته بجانب SmartTube.
- التحديثات عبر GitHub Releases مع التحقق من SHA-256، والتثبيت دائمًا عبر مثبّت حزم Android وبموافقة المستخدم.
- **تحذير مهم:** احتفظ بمفتاح التوقيع (keystore) وكلمة مروره في مكان آمن خارج المستودع مع نسختين احتياطيتين.
  فقدان المفتاح يعني عدم القدرة على إصدار تحديثات لنفس التطبيق. التفاصيل في [SIGNING.md](SIGNING.md).

</div>
