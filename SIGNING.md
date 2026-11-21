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

## 5. CI signing (later, optional)

Not configured yet. When needed, the standard approach is GitHub **Actions Secrets**:
the keystore is base64-encoded and stored as an encrypted repository secret together with the password and alias;
the workflow decodes it to a temporary file outside the repository only for the release job and writes
`keystore.properties` at run time. Secrets are never printed in logs and are not available to workflows
started from forks. The key must still be backed up offline: GitHub Secrets cannot be read back.
