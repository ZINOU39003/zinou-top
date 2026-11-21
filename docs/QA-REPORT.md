# ZINOU TOP 1.0.0 — QA report

Base: SmartTube `41fbc7c` · ZINOU TOP `1.0.0 (10000)` · package `com.zinou.top`

## Environment limits of the automated run

The preparation environment had no access to Google Maven, Maven Central or services.gradle.org, so **Gradle could
not run** (no APK was built there). Everything else was verified with real tools. Gradle builds run in the
**ZINOU TOP Build** workflow on GitHub and on the maintainer's computer.

## Automated results

| Area | Check | Result |
|---|---|---|
| Unit tests (JVM) | `ZinouUpdateManifestTest`: update.json validation + SHA-256 integrity (19 tests) | ✅ 19/19 |
| Release tooling | `test_make_release_assets.py` (9 tests: full release, universal-only, placeholder owner, missing APK, version/tag mismatch, cert pin required, older versionCode, validator) | ✅ 9/9 |
| End-to-end update | script `update.json` → Java validator → **real upstream engine** → SHA-256 → install gate (39 checks, below) | ✅ 39/39 |
| Compilation | all new/changed Java (update system, About, `AppUpdatePresenter`, `AppDataSourceManager` hook) against API stubs, Java 8 target | ✅ |
| Workflows | actionlint 1.7.12 + shellcheck; YAML parse | ✅ clean |
| Scripts | shellcheck `sync-upstream.sh` | ✅ clean |
| Upstream sync | simulated SmartTube update: clean merge (README kept via `merge=ours`, `upstream.commit` updated) and conflicts (modify/delete + content) listed with reasons, nothing auto-resolved | ✅ |
| Resources | all XML well-formed; every `zinou` override matches an existing upstream resource; no SmartTube name left in user-visible strings except legal attribution | ✅ |
| Signing | `keytool` command (PKCS12, RSA 4096, alias `zinou-top`) tested with a throwaway key; properties parsing; `.gitignore` rules (`git check-ignore`) | ✅ |
| Secrets | no keystore/password/token in the repository | ✅ |

### End-to-end update scenario 1.0.0 → 1.0.1

| Check | Result |
|---|---|
| Upstream engine on `arm64-v8a`, `armeabi-v7a`, `x86` picks the matching APK; `x86_64` falls back to universal | ✅ |
| Every URL offered by the engine is published and hashed | ✅ |
| Changelog in English / Arabic depending on device language | ✅ |
| Installed 10001 (same) or 10002 (newer) → no update offered, no downgrade | ✅ |
| SHA-256 OK for all 4 APKs; 1-byte tampering → mismatch; truncated download → mismatch | ✅ |
| Network: no internet/DNS → "unable to check"; 404 → "no release"; 503 → "unavailable"; HTML/garbage → invalid; HTTP APK URLs → invalid; redirect to HTTP → invalid; oversized → invalid | ✅ |
| Install gate: correct APK allowed; tampered, missing, invalid, foreign package (SmartTube), same/older versionCode, version not in update.json, offline without cached manifest → refused | ✅ |
| Engine "latest version" → "You're using the latest version"; download failure → ZINOU message | ✅ |

### Installation next to SmartTube

Merged `zinou` manifest uses only `${applicationId}`-derived names: providers `com.zinou.top`,
`com.zinou.top.androidx-startup`, `com.zinou.top.update_provider`; permission `com.zinou.top.ACCESS_VIDEO_DATA`.
SmartTube uses `org.smarttube.*` / `app.smarttube.*`. → no package, provider or permission conflict.

### Functional scope (code review)

Upstream files modified: 10 (132 inserted lines) — Gradle flavor/signing/config hooks, `AppUpdatePresenter`
(3 update hooks), `AppDataSourceManager` (About entry), README/.gitignore/issue templates. **No changes** to playback,
ExoPlayer, YouTube API (`MediaServiceCore`), search, sign-in, SponsorBlock, subtitles, navigation or the submodules.

## Device test checklist (run on a real Android TV box before publishing)

Install `ZinouTop-v1.0.0*.apk`, ideally on a device that also has SmartTube installed.

| # | Test | Expected |
|---|---|---|
| 1 | Install next to SmartTube | both installed; launcher shows **ZINOU TOP** with the ZINOU banner/icon |
| 2 | App info | name ZINOU TOP, package `com.zinou.top`, version 1.0.0 |
| 3 | Cold start | navy splash with ZINOU TOP / زينو توب, no extra delay |
| 4 | Home, Search (keyboard + voice), Android TV global search | results; global search suggestions labelled ZINOU TOP |
| 5 | Playback | 1080p/4K (8K if supported), 60 fps, HDR on HDR TV, audio track and quality menus |
| 6 | Subtitles | on/off, language, style |
| 7 | Sign-in | device code sign-in; subscriptions, history, playlists, channels load |
| 8 | SponsorBlock | segments skipped when enabled |
| 9 | Remote | D-pad navigation, back, play/pause, seek, long press; touch/mouse if available |
| 10 | Settings → About | ZINOU TOP 1.0.0, info, licenses; no donation/Telegram links; GitHub entry only when configured |
| 11 | Arabic system language | ZINOU strings in Arabic, layout readable |
| 12 | Update: owner not configured | Check for updates → "update source is not configured" |
| 13 | Update: configured, no release yet | "No published ZINOU TOP release was found." |
| 14 | Update: offline | "Unable to check for updates right now." — no crash |
| 15 | Update 1.0.0 → 1.0.1 (publish a test release) | update offered with changelog → verification → Android installer → app updated, settings kept |
| 16 | Signature check | `apksigner verify --print-certs` shows the pinned certificate |
