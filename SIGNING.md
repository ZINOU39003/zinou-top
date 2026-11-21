# ZINOU TOP — Release Signing

> ## ⚠️ CRITICAL: never lose the release keystore
>
> Android accepts an update only if it has the **same package ID** and is signed with the **same key**:
>
> **`com.zinou.top` + the ZINOU TOP release key = the basis of every future update.**
>
> - If the release keystore (or its password) is lost, you can **no longer publish updates** for `com.zinou.top`.
>   Users would have to uninstall the app (losing its settings) and install a new app under a different package ID.
> - If the key is changed after the first release, Android refuses the new APK as an update
>   (`INSTALL_FAILED_UPDATE_INCOMPATIBLE` / "App not installed").
> - Do not change the key alias (`zinou-top`) after the first release.
>
> **تحذير مهم:** إذا فُقد ملف الـ Release Keystore أو كلمة مروره، فلن يمكن إصدار تحديثات لـ `com.zinou.top` بالطريقة الطبيعية.
> استمرار التحديثات يعتمد على أمرين معًا: نفس الـ Package ID `com.zinou.top` + نفس مفتاح التوقيع.

## Rules

- The keystore, its passwords and `keystore.properties` are **never** committed. They are git-ignored
  (`keystore.properties`, `*.jks`, `*.keystore`, `*.p12`, `*.pfx`, `*.pem`, `*.jks.base64`, `*.keystore.base64`).
- Keep the keystore **outside** the repository folder.
- Keep at least **two offline backups** (e.g. two encrypted USB drives) and store the password in a password manager.
- Debug builds use the standard Android debug key and need no keystore.
  Release builds of the `zinou` flavor **require** the private key; without it the build stops with a clear message
  (an unsigned ZINOU TOP release is never produced).

## Key parameters

| Parameter | Value |
|---|---|
| Keystore type | PKCS12 |
| Key alias | `zinou-top` (permanent) |
| Algorithm | RSA 4096, SHA256withRSA |
| Validity | 10000 days (~27 years) |
| Suggested location (Windows) | `C:\Users\<you>\ZinouTopKeys\zinou-top-release.jks` |

PKCS12 uses **one** password: `keyPassword` must be the same as `storePassword`.

## 1. Create the keystore (once, on your own computer)

Requires a JDK (`keytool`), e.g. the one bundled with Android Studio
(`C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe`).

Windows (PowerShell):

```powershell
mkdir "$env:USERPROFILE\ZinouTopKeys"
keytool -genkeypair -v `
  -keystore "$env:USERPROFILE\ZinouTopKeys\zinou-top-release.jks" `
  -storetype PKCS12 `
  -alias zinou-top `
  -keyalg RSA -keysize 4096 -sigalg SHA256withRSA `
  -validity 10000 `
  -dname "CN=ZINOU TOP, O=ZINOU, C=DZ"
```

keytool then asks for the password interactively (recommended: it is not saved in shell history).
Non-interactive form, only with placeholders replaced on your machine:
`... -storepass YOUR_STORE_PASSWORD -keypass YOUR_STORE_PASSWORD`

Record the certificate fingerprint (keep it with your backups):

```powershell
keytool -list -v -keystore "$env:USERPROFILE\ZinouTopKeys\zinou-top-release.jks" -alias zinou-top
```

Copy the `SHA256:` certificate fingerprint into `zinou.properties`:

```properties
release.cert.sha256=AB:CD:...   # 64 hex characters, colons allowed - public data, safe to commit
```

The release tooling refuses to publish APKs signed with any other certificate.

## 2. Configure the build

Copy `keystore.properties.example` to `keystore.properties` (repository root) and fill it:

```properties
storeFile=C:/Users/YOUR_WINDOWS_USER/ZinouTopKeys/zinou-top-release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=zinou-top
keyPassword=YOUR_STORE_PASSWORD
```

Use forward slashes `/` in `storeFile` and no trailing spaces.

## 3. Build

```powershell
.\gradlew.bat assembleZinouDebug     # debug key, no keystore needed
.\gradlew.bat assembleZinouRelease   # ZINOU TOP release key
```

Release APKs: `smarttubetv/build/outputs/apk/zinou/release/`
(`ZinouTop-v1.0.0.apk` universal, plus `-armeabi-v7a`, `-arm64-v8a`, `-x86`).

## 4. Verify the signature

```powershell
# apksigner is in the Android SDK: <sdk>\build-tools\<version>\
apksigner verify --verbose --print-certs smarttubetv\build\outputs\apk\zinou\release\ZinouTop-v1.0.0.apk
```

Check that:
- `Verifies` is printed, with v1/v2 schemes verified (v1 is needed for Android < 7.0);
- `Signer #1 certificate DN: CN=ZINOU TOP, O=ZINOU, C=DZ`;
- `Signer #1 certificate SHA-256 digest` equals the SHA256 fingerprint from `keytool -list -v`
  (same bytes; keytool prints them with colons).

Check the package ID: `aapt dump badging <apk>` → `package: name='com.zinou.top'`.

## 5. CI signing (optional)

The workflow `.github/workflows/zinou-release.yml` is ready but signs **only** if these repository secrets exist
(Settings → Secrets and variables → Actions → New repository secret). Without them it stops with an explanation,
and you release locally instead (RELEASING.md).

| Secret | Value |
|---|---|
| `ZINOU_KEYSTORE_BASE64` | base64 of the `.jks` file |
| `ZINOU_KEYSTORE_PASSWORD` | the keystore password (PKCS12: also the key password) |
| `ZINOU_KEY_ALIAS` | `zinou-top` |

Create the base64 text on Windows without writing it to the repository:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:USERPROFILE\ZinouTopKeys\zinou-top-release.jks")) | Set-Clipboard
```

Paste it into the secret, then clear the clipboard. During a release the job decodes the key to a temporary file
outside the repository, writes `keystore.properties` at run time and deletes both at the end. Secrets are masked in
logs and not available to pull requests from forks. Use a GitHub *environment* named `release` with required
reviewers if more people get write access. GitHub secrets cannot be read back — keep your offline backups.

Trade-off: storing the key in GitHub is convenient, but anyone with admin access to the repository could misuse it.
Signing locally keeps the key only on your machine.
