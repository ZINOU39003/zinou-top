# ZINOU TOP — Upstream (SmartTube) update strategy

ZINOU TOP is a **branding + distribution layer** on top of [SmartTube](https://github.com/yuliskov/SmartTube)
(MIT License). All SmartTube functionality comes from upstream; ZINOU changes are kept small and isolated so
upstream updates can be merged regularly.

Current base: see `upstream.commit` in [`zinou.properties`](zinou.properties) (also shown in the app's About screen).

## Branches

| Branch | Content | Rules |
|---|---|---|
| `upstream` | Exact mirror of `yuliskov/SmartTube` `master` | Fast-forward only. Never commit here. |
| `zinou` | Development: upstream + ZINOU layer | Upstream arrives only through reviewed merge branches / pull requests. |
| `main` | Stable, released code | Updated with `git merge --no-ff zinou` at release time. Release tags `vX.Y.Z` are created on `main`. |
| `merge/upstream-<sha>` | Temporary review branch for one upstream update | Created by the sync script; deleted after merging. |

```
yuliskov/SmartTube master
        │  (fast-forward)
        ▼
    upstream ──merge (reviewed)──► zinou ──merge --no-ff──► main ──tag vX.Y.Z──► GitHub Release
```

Never `rebase` published branches, never `push --force`. Merges keep a clear history of which upstream
commit each ZINOU TOP version contains.

## Updating from upstream

Requirements: Git (Git Bash on Windows), a clean working tree, branches `upstream` and `zinou` present.

```bash
git switch zinou
scripts/upstream/sync-upstream.sh            # or: scripts/upstream/sync-upstream.sh <commit-or-tag>
```

The script:

1. refuses to run with uncommitted changes;
2. fetches SmartTube (remote `smarttube`) and **fast-forwards** `upstream` — it stops if upstream history was rewritten;
3. prints a review report: new commits, files changed by **both** sides, and *sensitive* changes
   (Gradle files, manifests, workflows, update/network code, binaries, submodule moves with compare links);
4. creates `merge/upstream-<sha>` from `zinou` and runs `git merge --no-ff --no-commit upstream`:
   - **no conflicts** → updates `upstream.commit` in `zinou.properties`, stages everything, and stops so you can review, build and test before committing;
   - **conflicts** → lists every conflicted file **with the reason** and stops. Nothing is resolved automatically.

Then:

```bash
git diff --cached                          # review (especially the "sensitive" list)
git submodule update --init --recursive
./gradlew clean assembleZinouDebug         # build, install on a TV box, smoke-test
git commit -m "Merge SmartTube upstream <sha>"
git push -u origin merge/upstream-<sha>    # open a PR into zinou; CI must pass
```

### Security review of upstream changes

SmartTube's author has publicly reported that his development machine was once compromised (see the upstream
README). ZINOU TOP always builds from **source**, never from upstream binaries, and every upstream update is
reviewed before merging. Pay special attention to: new dependencies or repositories in `*.gradle`, new
permissions in manifests, new network endpoints, changes to update/download code, new binary files, and
submodule moves (`SharedModules`, `MediaServiceCore` — follow the compare links printed by the script).

## Where conflicts can happen (and how to resolve them)

ZINOU modifies very few upstream files. Every ZINOU line in an upstream file is marked with a `ZINOU TOP` comment.

| Upstream file | ZINOU change | Resolution |
|---|---|---|
| `smarttubetv/build.gradle` | `zinou` flavor, APK naming, `apply from: 'zinou-signing.gradle'`, debug-key block | Keep upstream changes, re-add the ZINOU blocks |
| `common/build.gradle`, `leanbackassistant/build.gradle` | `zinou` flavor (+ `apply from: 'zinou-config.gradle'`) | Same |
| `common/.../dialogs/AppUpdatePresenter.java` | update preflight / friendly errors / verified install hooks | Keep upstream changes, re-add the 3 `ZINOU TOP` hooks |
| `common/.../misc/AppDataSourceManager.java` | ZINOU About entry | Re-add the `ZinouAboutSettingsPresenter` branch |
| `README.md`, `.github/ISSUE_TEMPLATE/*.yml` | replaced by ZINOU versions | Automatic: `merge=ours` in `.gitattributes` |
| `.github/workflows/{CI,cleanup,stale,virustotal_scan}.yml`, `.github/FUNDING.yml` | deleted (they act on the upstream project) | `git rm <file>` (modify/delete conflict) |
| `.gitignore` | ZINOU section appended | Keep both |

Everything else ZINOU adds lives in **new files** that upstream never touches:
`*/src/zinou/**`, `common/src/main/java/.../common/zinou/**`, `ZinouAboutSettingsPresenter.java`,
`zinou_*.xml`, `zinou-*.gradle`, `zinou.properties`, `scripts/**`, `branding/**`, `.github/workflows/zinou-*.yml`, docs.

### After resolving

```bash
grep -rn "<<<<<<<\|>>>>>>>" --include=*.java --include=*.gradle --include=*.xml .   # must print nothing
git add <resolved files>
# set upstream.commit=<sha> in zinou.properties
git submodule update --init --recursive
./gradlew clean assembleZinouDebug :common:testZinouDebugUnitTest --tests 'com.liskovsoft.smartyoutubetv2.common.zinou.*'
git commit
```

To abandon an update: `git merge --abort && git switch zinou && git branch -D merge/upstream-<sha>`.

## Things that must stay working after every upstream merge

Package `com.zinou.top` · app name ZINOU TOP · ZINOU icons/banner/splash · ZINOU About · update check
(`zinou.properties` → `update.json`) · search provider authority `com.zinou.top` (installable next to SmartTube).
Use the checklist in [docs/QA-REPORT.md](docs/QA-REPORT.md).

## Runtime dependency on upstream

SmartTube's YouTube engine (`MediaServiceCore`) downloads `constants.json` from the upstream SmartTube GitHub
releases at runtime. ZINOU TOP keeps this unchanged on purpose: it is part of how YouTube playback keeps working,
and changing it would mean maintaining that data ourselves.
