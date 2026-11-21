#!/usr/bin/env python3
"""Tests for make_release_assets.py. Run: python3 scripts/release/test_make_release_assets.py"""
import hashlib
import json
import os
import shutil
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_release_assets as m  # noqa: E402

GRADLE = """
android {
    productFlavors {
        stbeta {
            applicationId "org.smarttube.beta"
        }
        zinou {
            applicationId "com.zinou.top"
            versionCode %d
            versionName "%s"
            matchingFallbacks = ['ststable']
        }
    }
}
"""

CHANGELOG = """# Changelog

## [1.0.1] - 2026-12-01
### Fixed
- Fixed something
### Changed
- Improved update checks
### التغييرات (ar)
- إصلاح مشكلة

## [1.0.0] - 2026-11-21
### Added
- First release
"""


class ReleaseAssetsTest(unittest.TestCase):
    def setUp(self):
        self.root = tempfile.mkdtemp()
        os.makedirs(os.path.join(self.root, "smarttubetv"))
        self.apks = os.path.join(self.root, "apks")
        self.out = os.path.join(self.root, "dist")
        os.makedirs(self.apks)
        self.write_config("OWNER", 10001, "1.0.1")
        with open(os.path.join(self.root, "CHANGELOG.md"), "w", encoding="utf-8") as fh:
            fh.write(CHANGELOG)

    def tearDown(self):
        shutil.rmtree(self.root)

    def write_config(self, owner, code, name):
        with open(os.path.join(self.root, "zinou.properties"), "w") as fh:
            fh.write("github.owner=%s\ngithub.repository=zinou-top\nupstream.commit=41fbc7c\nrelease.cert.sha256=\n" % owner)
        with open(os.path.join(self.root, "smarttubetv", "build.gradle"), "w") as fh:
            fh.write(GRADLE % (code, name))

    def apk(self, name, content=None):
        with open(os.path.join(self.apks, name), "wb") as fh:
            fh.write(content or os.urandom(2048))

    def run_build(self, *extra):
        return m.main(["--apk-dir", self.apks, "--out", self.out, "--repo-root", self.root] + list(extra))

    def test_full_release_1_0_1(self):
        for n in ["ZinouTop-v1.0.1.apk", "ZinouTop-v1.0.1-armeabi-v7a.apk", "ZinouTop-v1.0.1-arm64-v8a.apk",
                  "ZinouTop-v1.0.1-x86.apk", "ZinouTop-v1.0.1-debug.apk"]:
            self.apk(n)
        self.assertEqual(0, self.run_build())
        update = json.load(open(os.path.join(self.out, "update.json"), encoding="utf-8"))
        base = "https://github.com/OWNER/zinou-top/releases/download/v1.0.1/"
        self.assertEqual([base + "ZinouTop-v1.0.1.apk"], update["package"]["downloadUrlList"])
        self.assertEqual([base + "ZinouTop-v1.0.1-arm64-v8a.apk", base + "ZinouTop-v1.0.1.apk"],
                         update["package"]["downloadUrlList_arm64-v8a"])
        self.assertEqual(10001, update["1.0.1"]["versionCode"])
        self.assertEqual(["Fixed something", "Improved update checks"], update["1.0.1"]["changelog"])
        self.assertEqual(["إصلاح مشكلة"], update["1.0.1"]["changelog_ar"])
        self.assertEqual(4, len(update["package"]["sha256"]))
        self.assertNotIn("ZinouTop-v1.0.1-debug.apk", update["package"]["sha256"])
        # SHA256SUMS matches the files
        for line in open(os.path.join(self.out, "SHA256SUMS.txt")):
            digest, name = line.split()
            with open(os.path.join(self.out, name), "rb") as fh:
                self.assertEqual(hashlib.sha256(fh.read()).hexdigest(), digest)
            if name.endswith(".apk"):
                self.assertEqual(update["package"]["sha256"][name], digest)
        notes = open(os.path.join(self.out, "release-notes.md"), encoding="utf-8").read()
        self.assertIn("SmartTube", notes)
        self.assertIn("41fbc7c", notes)

    def test_universal_only(self):
        self.apk("ZinouTop-v1.0.1.apk")
        self.assertEqual(0, self.run_build())
        update = json.load(open(os.path.join(self.out, "update.json")))
        self.assertEqual(["downloadUrlList", "sha256", "size"], sorted(update["package"].keys()))

    def test_refuses_placeholder_owner(self):
        self.write_config("YOUR_USERNAME", 10001, "1.0.1")
        self.apk("ZinouTop-v1.0.1.apk")
        self.assertEqual(1, self.run_build())
        self.assertFalse(os.path.exists(os.path.join(self.out, "update.json")))

    def test_missing_universal_apk(self):
        self.apk("ZinouTop-v1.0.1-arm64-v8a.apk")
        self.assertEqual(1, self.run_build())

    def test_version_and_tag_mismatch(self):
        self.apk("ZinouTop-v1.0.1.apk")
        self.assertEqual(1, self.run_build("--version", "1.0.2"))
        self.assertEqual(1, self.run_build("--tag", "v9.9.9"))

    def test_requires_cert_pin_when_apksigner_used(self):
        self.apk("ZinouTop-v1.0.1.apk")
        self.assertEqual(1, self.run_build("--apksigner", "/nonexistent/apksigner"))

    def test_previous_versions_kept_newer_rejected(self):
        self.apk("ZinouTop-v1.0.1.apk")
        prev = os.path.join(self.root, "prev.json")
        json.dump({"package": {}, "1.0.0": {"versionCode": 10000, "changelog": ["First release"]}}, open(prev, "w"))
        self.assertEqual(0, self.run_build("--previous-update-json", prev))
        update = json.load(open(os.path.join(self.out, "update.json")))
        self.assertEqual(10000, update["1.0.0"]["versionCode"])
        json.dump({"package": {}, "2.0.0": {"versionCode": 20000}}, open(prev, "w"))
        self.assertEqual(1, self.run_build("--previous-update-json", prev))

    def test_validator_rejects_bad_manifests(self):
        good = {"package": {"downloadUrlList": ["https://github.com/o/r/releases/download/v1/a.apk"],
                            "sha256": {"a.apk": "0" * 64}}, "1": {"versionCode": 1}}
        self.assertEqual(1, m.validate_update_json(good))
        bad_cases = [
            {"package": dict(good["package"], downloadUrlList=["http://github.com/o/r/a.apk"]), "1": {"versionCode": 1}},
            {"package": dict(good["package"], downloadUrlList=["https://evil.example/a.apk"]), "1": {"versionCode": 1}},
            {"package": dict(good["package"], sha256={"a.apk": "xyz"}), "1": {"versionCode": 1}},
            {"package": good["package"], "1": {"versionCode": "1"}},
            {"package": good["package"]},
            {"1": {"versionCode": 1}},
        ]
        for bad in bad_cases:
            with self.assertRaises(m.ReleaseError):
                m.validate_update_json(bad)

    def test_reads_real_build_gradle(self):
        repo = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
        gradle = os.path.join(repo, "smarttubetv", "build.gradle")
        if os.path.isfile(gradle):
            code, name = m.read_zinou_version(gradle)
            self.assertEqual(code, int(name.split(".")[0]) * 10000 + int(name.split(".")[1]) * 100 + int(name.split(".")[2]))


if __name__ == "__main__":
    unittest.main(verbosity=2)
