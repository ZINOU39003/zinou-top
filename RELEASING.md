# Releasing ZINOU TOP

A release = signed APKs + `update.json` + `SHA256SUMS.txt` attached to a **published** GitHub Release on
`ZINOU39003/zinou-top`. Installed apps read
`https://github.com/<owner>/zinou-top/releases/latest/download/update.json`, so an update becomes visible to users
only when the release is **published** (drafts and pre-releases are ignored).

## One-time setup (before v1.0.0)

1. **GitHub repository**: create `zinou-top` on your account and push the branches (see README → "Repository setup").
2. **zinou.properties**: set `github.owner=<your GitHub username>` (keep `github.repository=zinou-top`).
3. **Signing key**: create it on your computer, outside the repository (SIGNING.md §1) and back it up twice.
4. **Certificate pin**: run `keytool -list -v -keystore <your .jks> -alias zinou-top`, copy the `SHA256:` value of the
   certificate fingerprint into `release.cert.sha256=` in `zinou.properties` (public data, safe to commit).
5. Commit steps 2 and 4 on `zinou`.
6. (Optional, for CI releases) add the 3 repository secrets described in SIGNING.md §5.

## Every release

### 1. Prepare on `zinou`

- Bump the version in `smarttubetv/build.gradle`, `zinou { ... }` block:
  `versionName "1.0.1"` and `versionCode 10001` (`MAJOR*10000 + MINOR*100 + PATCH`; must always increase).
- Move the `[Unreleased]` notes in `CHANGELOG.md` to `## [1.0.1] - YYYY-MM-DD` (English bullets + optional `### … (ar)` bullets).
- Push; the **ZINOU TOP Build** workflow must be green. Test the debug APK on a TV box (docs/QA-REPORT.md checklist).

### 2. Merge to `main` and tag

```bash
git switch main
git merge --no-ff zinou -m "Release ZINOU TOP 1.0.1"
git tag -a v1.0.1 -m "ZINOU TOP 1.0.1"
git push origin main v1.0.1
```

### 3a. Build and publish with GitHub Actions (if signing secrets are configured)

Pushing the tag runs **ZINOU TOP Release**: signed build → package/version/certificate checks → **draft** release with
all assets. Open the draft, check the notes and assets, then click **Publish release**.

### 3b. Build and publish locally (no secrets on GitHub) — Windows PowerShell

```powershell
git switch main
git submodule update --init --recursive
.\gradlew.bat clean assembleZinouRelease                       # needs keystore.properties (SIGNING.md)

# previous update.json keeps the in-app changelog history (skip for v1.0.0)
gh release download --repo ZINOU39003/zinou-top --pattern update.json --dir prev

$bt = (Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\build-tools" | Sort-Object Name | Select-Object -Last 1).FullName
python scripts\release\make_release_assets.py `
  --apk-dir smarttubetv\build\outputs\apk\zinou\release --out dist `
  --aapt "$bt\aapt.exe" --apksigner "$bt\apksigner.bat" `
  --previous-update-json prev\update.json                      # omit this line for v1.0.0

gh release create v1.0.1 --repo ZINOU39003/zinou-top --draft `
  --title "ZINOU TOP 1.0.1" --notes-file dist\release-notes.md `
  dist\ZinouTop-v1.0.1*.apk dist\update.json dist\SHA256SUMS.txt
```

Review the draft on GitHub, then **Publish**.

`make_release_assets.py` refuses to continue if: the owner is still `YOUR_USERNAME`, the tag/version/versionCode do
not match `build.gradle`, the versionCode is not newer than the published one, an APK has another package/version,
or an APK is not signed with the pinned certificate.

### 4. After publishing

- From an installed older version: **About → Check for updates** must offer the new version and install it.
- Merge back if you released a hotfix from `main`: `git switch zinou && git merge main`.

## Assets of a release

| File | Purpose |
|---|---|
| `ZinouTop-vX.Y.Z.apk` | universal APK (all architectures) |
| `ZinouTop-vX.Y.Z-armeabi-v7a.apk`, `-arm64-v8a.apk`, `-x86.apk` | smaller per-architecture APKs (chosen automatically by the in-app updater) |
| `update.json` | read by the app: versions, changelog, download URLs, SHA-256 and sizes |
| `SHA256SUMS.txt` | checksums for manual verification (`sha256sum -c SHA256SUMS.txt --ignore-missing`) |

Never delete or replace assets of a published release: the app verifies SHA-256 values from `update.json`.
If something is wrong, publish a new version with a higher versionCode.
