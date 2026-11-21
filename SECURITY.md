# Security — ZINOU TOP

## Reporting a vulnerability

Please do **not** open a public issue for security problems. Use GitHub's private
**Security → Report a vulnerability** on `YOUR_USERNAME/zinou-top` (enable *Private vulnerability reporting* in the
repository settings). Problems in SmartTube itself should also be reported upstream.

## Secrets policy

Never commit: keystores (`*.jks`, `*.keystore`, `*.p12`, `*.pfx`, `*.pem`), `keystore.properties`, passwords,
GitHub tokens, API keys. They are git-ignored, and the **ZINOU TOP Build** workflow fails if such a file is ever tracked.

| Secret | Where it lives |
|---|---|
| Release keystore + password | your computer (outside the repository) + 2 offline backups + password manager |
| Optional CI signing | GitHub encrypted secrets `ZINOU_KEYSTORE_BASE64`, `ZINOU_KEYSTORE_PASSWORD`, `ZINOU_KEY_ALIAS` |
| GitHub token for CI | the automatic `GITHUB_TOKEN` (contents: read for builds, write only in the release job) |

Public, safe to commit: `zinou.properties` (owner, repository, upstream commit, **certificate SHA-256 pin**).

## Release integrity

1. APKs are signed with the private ZINOU TOP key (SIGNING.md). Android refuses updates signed with another key.
2. `make_release_assets.py` refuses to publish APKs with a different package/version or a certificate that does not
   match `release.cert.sha256` (when run with `--apksigner`, as in the release workflow).
3. The release workflow creates a **draft**; a maintainer publishes it manually.
4. `SHA256SUMS.txt` and `update.json` carry SHA-256 for every APK; the app verifies before installing.
5. GitHub Actions are pinned to full commit SHAs; workflows use least-privilege permissions; checkout does not
   persist credentials.

## App update security

HTTPS only, no HTTP redirects, only `https://github.com/...*.apk` download URLs, strict `update.json` validation,
SHA-256 + size + package + version checks, Android Package Installer with user confirmation. No root, no silent
install, no installer bypass. Details: [docs/UPDATE_SYSTEM.md](docs/UPDATE_SYSTEM.md).

## Network endpoints (in addition to YouTube/Google services used by SmartTube)

| Endpoint | When | Why |
|---|---|---|
| `github.com/<owner>/zinou-top/releases/...` (+ GitHub download CDN) | update check / download | ZINOU TOP updates |
| `github.com/yuliskov/SmartTube/releases/download/latest/constants.json` | at runtime (upstream engine) | YouTube client constants maintained by SmartTube — kept unchanged |
| `api.qrserver.com` | only when the user opens a link from About (upstream helper) | shows the link as a QR code on the TV |
| SponsorBlock / DeArrow APIs | only if enabled by the user | upstream features |

ZINOU TOP does **not** include SmartTube's Firebase Crashlytics (only the upstream beta/stable flavors do) and adds
no analytics, no backend and no VPS.

## Upstream code

ZINOU TOP builds SmartTube from **source** and never ships upstream binaries. Every upstream update goes through a
review branch with a report of sensitive changes (UPSTREAM.md). The upstream author has publicly reported a past
compromise of his development machine; reviewing upstream diffs and submodule moves is therefore mandatory.
