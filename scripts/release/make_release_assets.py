#!/usr/bin/env python3
"""
ZINOU TOP - build the GitHub Release assets from already-built, signed release APKs.

Produces in --out:
  ZinouTop-v<ver>.apk (+ per-ABI APKs)   copied from --apk-dir
  update.json                             read by the app's update checker
  SHA256SUMS.txt                          `sha256sum -c` compatible
  release-notes.md                        body for the GitHub Release

Never touches keys or passwords. Optional checks (used by the release workflow):
  --aapt <path>       package name / versionCode / versionName inside each APK
  --apksigner <path>  signing certificate SHA-256 must equal release.cert.sha256 (zinou.properties)

Usage (repository root):
  python3 scripts/release/make_release_assets.py --apk-dir smarttubetv/build/outputs/apk/zinou/release --out dist
Version, owner, repository and upstream commit are read from smarttubetv/build.gradle and zinou.properties.
"""
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
from urllib.parse import urlparse

PACKAGE_ID = "com.zinou.top"
ABIS = ["armeabi-v7a", "arm64-v8a", "x86"]
ALLOWED_HOSTS = {"github.com"}
NAME_RE = re.compile(r"^[A-Za-z0-9](?:[A-Za-z0-9._-]{0,98}[A-Za-z0-9_])?$")
SHA_RE = re.compile(r"^[0-9a-f]{64}$")
MAX_PREVIOUS_VERSIONS = 10


class ReleaseError(Exception):
    pass


def read_properties(path):
    props = {}
    if not os.path.isfile(path):
        return props
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line or line.startswith(("#", "!")) or "=" not in line:
                continue
            key, value = line.split("=", 1)
            props[key.strip()] = value.strip()
    return props


def read_zinou_version(gradle_path):
    """versionCode / versionName of the 'zinou' flavor in smarttubetv/build.gradle."""
    text = open(gradle_path, encoding="utf-8").read()
    block = re.search(r"\n\s*zinou\s*\{(.*?)\n\s*\}", text, re.S)
    if not block:
        raise ReleaseError("zinou flavor not found in " + gradle_path)
    code = re.search(r"versionCode\s+(\d+)", block.group(1))
    name = re.search(r'versionName\s+"([^"]+)"', block.group(1))
    if not code or not name:
        raise ReleaseError("versionCode/versionName missing in zinou flavor")
    return int(code.group(1)), name.group(1)


def sha256_file(path):
    digest = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def parse_changelog(path, version):
    """Bullets of '## [version]' in CHANGELOG.md. Sub-headings containing '(ar)' go to the Arabic list."""
    en, ar = [], []
    if not path or not os.path.isfile(path):
        return en, ar
    lines = open(path, encoding="utf-8").read().splitlines()
    inside, target = False, en
    header = re.compile(r"^##\s+\[?" + re.escape(version) + r"\]?(\s|$)")
    for line in lines:
        if line.startswith("## "):
            inside = bool(header.match(line))
            target = en
            continue
        if not inside:
            continue
        if line.startswith("### "):
            target = ar if "(ar)" in line.lower() else en
            continue
        stripped = line.strip()
        if stripped.startswith(("- ", "* ")):
            # plain text for the TV dialog: drop markdown emphasis / code marks
            target.append(re.sub(r"[`*]", "", stripped[2:]).strip())
    return en, ar


def aapt_badging(aapt, apk):
    out = subprocess.run([aapt, "dump", "badging", apk], capture_output=True, text=True, check=True).stdout
    m = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", out)
    if not m:
        raise ReleaseError("cannot read package info from " + apk)
    return m.group(1), int(m.group(2)), m.group(3)


def apk_cert_sha256(apksigner, apk):
    res = subprocess.run([apksigner, "verify", "--print-certs", apk], capture_output=True, text=True)
    if res.returncode != 0:
        raise ReleaseError("apksigner verify failed for %s:\n%s" % (apk, res.stdout + res.stderr))
    return single_cert_sha256(res.stdout, apk)


def single_cert_sha256(apksigner_output, apk="APK"):
    """All certificate SHA-256 digests printed by `apksigner verify --print-certs` must be one and the same.
    Newer apksigner versions print the signer once per scheme/section (e.g. 'Signer #1', 'Signer (minSdkVersion=..)')."""
    certs = {c.lower() for c in re.findall(r"certificate SHA-256 digest:\s*([0-9a-fA-F]{64})", apksigner_output)}
    if len(certs) != 1:
        raise ReleaseError("expected exactly one signing certificate in %s, found %d" % (apk, len(certs)))
    return certs.pop()


def normalize_fingerprint(value):
    return (value or "").replace(":", "").replace(" ", "").strip().lower()


def validate_update_json(data, allowed_hosts=ALLOWED_HOSTS):
    """Python mirror of ZinouUpdateManifest.parse() (Java) - same rules."""
    if not isinstance(data, dict) or not isinstance(data.get("package"), dict):
        raise ReleaseError("update.json: missing 'package'")
    pkg = data["package"]
    versions = {k: v for k, v in data.items() if k != "package"}
    if not versions:
        raise ReleaseError("update.json: no version entries")
    for name, entry in versions.items():
        code = entry.get("versionCode") if isinstance(entry, dict) else None
        if not isinstance(code, int) or isinstance(code, bool) or code <= 0:
            raise ReleaseError("update.json: invalid versionCode for " + name)
    urls = []
    for key, value in pkg.items():
        if key == "downloadUrl":
            urls.append(value)
        elif key.startswith("downloadUrlList"):
            if not isinstance(value, list) or not value:
                raise ReleaseError("update.json: %s must be a non-empty list" % key)
            urls.extend(value)
    if not urls:
        raise ReleaseError("update.json: no download URLs")
    sha = pkg.get("sha256")
    if not isinstance(sha, dict):
        raise ReleaseError("update.json: missing sha256")
    sizes = pkg.get("size")
    for url in urls:
        parsed = urlparse(url)
        if parsed.scheme != "https" or (parsed.hostname or "").lower() not in allowed_hosts:
            raise ReleaseError("update.json: URL not allowed: " + url)
        if parsed.query or parsed.fragment or parsed.username:
            raise ReleaseError("update.json: unexpected URL parts: " + url)
        name = parsed.path.rsplit("/", 1)[-1]
        if not name.lower().endswith(".apk") or len(name) <= 4:
            raise ReleaseError("update.json: not an APK URL: " + url)
        if not SHA_RE.match(str(sha.get(name, "")).lower()):
            raise ReleaseError("update.json: missing/invalid sha256 for " + name)
        if sizes is not None and not (isinstance(sizes.get(name), int) and sizes[name] > 0):
            raise ReleaseError("update.json: missing/invalid size for " + name)
    return max(v["versionCode"] for v in versions.values())


def build(args):
    root = args.repo_root
    props = read_properties(os.path.join(root, "zinou.properties"))
    owner = args.owner or props.get("github.owner", "")
    repo = args.repository or props.get("github.repository", "")
    upstream_commit = props.get("upstream.commit", "")
    if not NAME_RE.match(owner or "") or owner == "YOUR_USERNAME":
        raise ReleaseError("Set your real GitHub username in zinou.properties (github.owner) before releasing.")
    if not NAME_RE.match(repo or ""):
        raise ReleaseError("Invalid github.repository in zinou.properties")

    gradle_code, gradle_name = read_zinou_version(os.path.join(root, "smarttubetv", "build.gradle"))
    version = args.version or gradle_name
    version_code = args.version_code or gradle_code
    if (version, version_code) != (gradle_name, gradle_code):
        raise ReleaseError("version mismatch: build.gradle has %s (%d), requested %s (%d)"
                           % (gradle_name, gradle_code, version, version_code))
    if not re.match(r"^\d+\.\d+\.\d+$", version):
        raise ReleaseError("versionName must be MAJOR.MINOR.PATCH, got " + version)
    tag = args.tag or "v" + version
    if tag != "v" + version:
        raise ReleaseError("tag %s does not match version %s" % (tag, version))

    universal = "ZinouTop-v%s.apk" % version
    files = {"universal": universal}
    for abi in ABIS:
        files[abi] = "ZinouTop-v%s-%s.apk" % (version, abi)

    if not os.path.isfile(os.path.join(args.apk_dir, universal)):
        raise ReleaseError("missing %s in %s (run assembleZinouRelease first)" % (universal, args.apk_dir))
    present = {k: v for k, v in files.items() if os.path.isfile(os.path.join(args.apk_dir, v))}
    missing_abis = [k for k in ABIS if k not in present]
    if missing_abis:
        print("warning: no APK for " + ", ".join(missing_abis) + " (universal APK will be used)", file=sys.stderr)

    expected_cert = normalize_fingerprint(args.expected_cert or props.get("release.cert.sha256", ""))
    if args.apksigner and not SHA_RE.match(expected_cert):
        raise ReleaseError("release.cert.sha256 in zinou.properties is not set: the signing certificate cannot be checked.")

    os.makedirs(args.out, exist_ok=True)
    base_url = "https://github.com/%s/%s/releases/download/%s/" % (owner, repo, tag)
    sha256, sizes = {}, {}
    for name in present.values():
        src = os.path.join(args.apk_dir, name)
        if args.aapt:
            pkg, code, vname = aapt_badging(args.aapt, src)
            if (pkg, code, vname) != (PACKAGE_ID, version_code, version):
                raise ReleaseError("%s: got %s %d %s, expected %s %d %s"
                                   % (name, pkg, code, vname, PACKAGE_ID, version_code, version))
        if args.apksigner:
            cert = apk_cert_sha256(args.apksigner, src)
            if cert != expected_cert:
                raise ReleaseError("%s is signed with an unexpected certificate (%s). Refusing to publish." % (name, cert))
        dst = os.path.join(args.out, name)
        if os.path.abspath(src) != os.path.abspath(dst):
            shutil.copyfile(src, dst)
        sha256[name] = sha256_file(dst)
        sizes[name] = os.path.getsize(dst)

    pkg = {"downloadUrlList": [base_url + universal]}
    for abi in ABIS:
        if abi in present:
            pkg["downloadUrlList_" + abi] = [base_url + present[abi], base_url + universal]
    pkg["sha256"] = sha256
    pkg["size"] = sizes

    changelog_en, changelog_ar = parse_changelog(os.path.join(root, "CHANGELOG.md"), version)
    entry = {"versionCode": version_code, "changelog": changelog_en or ["ZINOU TOP " + version]}
    if changelog_ar:
        entry["changelog_ar"] = changelog_ar

    update = {"package": pkg, version: entry}
    if args.previous_update_json:
        previous = json.load(open(args.previous_update_json, encoding="utf-8"))
        published = [v["versionCode"] for k, v in previous.items()
                     if k != "package" and isinstance(v, dict) and isinstance(v.get("versionCode"), int)]
        if published and max(published) >= version_code:
            raise ReleaseError("versionCode %d is not newer than the already published %d" % (version_code, max(published)))
        older = sorted(((k, v) for k, v in previous.items()
                        if k != "package" and isinstance(v, dict) and isinstance(v.get("versionCode"), int)
                        and v["versionCode"] < version_code),
                       key=lambda kv: kv[1]["versionCode"], reverse=True)[:MAX_PREVIOUS_VERSIONS]
        for k, v in older:
            update[k] = {key: v[key] for key in ("versionCode", "changelog", "changelog_ar") if key in v}

    latest = validate_update_json(update)
    if latest != version_code:
        raise ReleaseError("previous update.json contains a newer versionCode than this release")

    update_path = os.path.join(args.out, "update.json")
    with open(update_path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(update, fh, ensure_ascii=False, indent=2)
        fh.write("\n")

    with open(os.path.join(args.out, "SHA256SUMS.txt"), "w", encoding="utf-8", newline="\n") as fh:
        for name in sorted(list(present.values()) + ["update.json"]):
            fh.write("%s  %s\n" % (sha256_file(os.path.join(args.out, name)), name))

    write_release_notes(os.path.join(args.out, "release-notes.md"), version, version_code, upstream_commit,
                        changelog_en, changelog_ar, present, sha256, sizes)
    print("ZINOU TOP %s (%d): %d APK(s), update.json, SHA256SUMS.txt, release-notes.md -> %s"
          % (version, version_code, len(present), args.out))
    return update


def write_release_notes(path, version, version_code, upstream_commit, en, ar, present, sha256, sizes):
    lines = ["# ZINOU TOP %s" % version, ""]
    if en:
        lines += ["## Changes", ""] + ["- " + x for x in en] + [""]
    if ar:
        lines += ['<div dir="rtl">', "", "## التغييرات", ""] + ["- " + x for x in ar] + ["", "</div>", ""]
    lines += ["## Which APK?", "",
              "| File | Devices | Size | SHA-256 |", "|---|---|---|---|"]
    labels = {"universal": "All devices (recommended if unsure)", "armeabi-v7a": "Most Android TV boxes (32-bit ARM)",
              "arm64-v8a": "64-bit ARM devices", "x86": "x86 devices / emulators"}
    for key in ["universal"] + ABIS:
        if key in present:
            name = present[key]
            lines.append("| `%s` | %s | %.1f MB | `%s` |" % (name, labels[key], sizes[name] / 1048576.0, sha256[name]))
    lines += ["",
              "Package: `%s` · versionCode `%d` · signed with the ZINOU TOP release key." % (PACKAGE_ID, version_code),
              "Existing installs update in place from **About → Check for updates**; the app verifies SHA-256 "
              "before opening the Android Package Installer.", "",
              "Verify a download: `sha256sum -c SHA256SUMS.txt --ignore-missing`", "",
              "ZINOU TOP is based on the open-source [SmartTube](https://github.com/yuliskov/SmartTube) project "
              "(MIT License)" + (", upstream commit `%s`." % upstream_commit if upstream_commit else ".")]
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(lines) + "\n")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--apk-dir", required=True)
    parser.add_argument("--out", default="dist")
    parser.add_argument("--repo-root", default=".")
    parser.add_argument("--version")
    parser.add_argument("--version-code", type=int)
    parser.add_argument("--tag")
    parser.add_argument("--owner")
    parser.add_argument("--repository")
    parser.add_argument("--previous-update-json")
    parser.add_argument("--aapt")
    parser.add_argument("--apksigner")
    parser.add_argument("--expected-cert")
    args = parser.parse_args(argv)
    try:
        build(args)
    except (ReleaseError, subprocess.CalledProcessError, OSError, ValueError) as e:
        print("ERROR: %s" % e, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
