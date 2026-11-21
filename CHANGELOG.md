# Changelog

All notable changes to ZINOU TOP. Versions follow `MAJOR.MINOR.PATCH`;
`versionCode = MAJOR*10000 + MINOR*100 + PATCH`.

Format used by `scripts/release/make_release_assets.py` (it builds the in-app changelog of `update.json`):
bullets under `## [x.y.z]` go to the English changelog; bullets under a `###` heading containing `(ar)` go to
the Arabic changelog.

## [Unreleased]

## [1.0.0] - first official release
Based on upstream SmartTube commit `41fbc7c` (2026-11-19).

### Added
- ZINOU TOP rebranding: application name, independent package ID `com.zinou.top`
- New launcher icon (adaptive on Android 8+), Android TV banner and in-app logos (placeholder artwork)
- Branded splash screen (system starting window, no added delay)
- ZINOU TOP About screen: version, upstream commit, open source licenses, update controls
- GitHub update checker: update.json from GitHub Releases, HTTPS only, SHA-256 verification, Android Package Installer
- Release infrastructure: private signing key outside the repository, GitHub Actions build and release workflows, SHA256SUMS
- Installable next to SmartTube (no package or content-provider conflict)

### التغييرات (ar)
- هوية جديدة باسم ZINOU TOP (زينو توب) ومعرّف حزمة مستقل com.zinou.top
- أيقونة وبانر لشاشات Android TV وشاشة بدء جديدة
- شاشة «حول» جديدة مع الإصدار والتراخيص وخيارات التحديث
- نظام تحديث عبر GitHub Releases مع التحقق من SHA-256 وتثبيت عبر مثبّت حزم Android
- إمكانية التثبيت بجانب SmartTube بدون تعارض
