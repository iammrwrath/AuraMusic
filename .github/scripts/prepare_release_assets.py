#!/usr/bin/env python3
"""
Metrolist / AuraMusic Release Asset Packager
Organizes built release APKs, creates universal and versioned assets,
and packages them into a compressed ZIP bundle for GitHub Releases.
"""

import os
import sys
import shutil
import zipfile
import argparse
from pathlib import Path

def prepare_assets(version: str, apk_dir: Path, out_dir: Path):
    if not out_dir.exists():
        out_dir.mkdir(parents=True, exist_ok=True)

    # Locate release APK
    source_apk = None
    possible_names = ["app-foss-release.apk", "app-release.apk"]
    for name in possible_names:
        candidate = apk_dir / name
        if candidate.exists() and candidate.stat().st_size > 1_000_000:
            source_apk = candidate
            break

    if not source_apk:
        # Search for any *.apk in apk_dir
        apks = [f for f in apk_dir.glob("*.apk") if f.stat().st_size > 1_000_000]
        if apks:
            source_apk = apks[0]

    if not source_apk or not source_apk.exists():
        print(f"Error: No release APK found in {apk_dir}", file=sys.stderr)
        sys.exit(1)

    print(f"[*] Found source release APK: {source_apk} ({source_apk.stat().st_size} bytes)")

    clean_version = version.lstrip("v")
    target_universal = out_dir / "AuraMusic.apk"
    target_versioned = out_dir / f"AuraMusic-v{clean_version}.apk"
    target_zip = out_dir / f"AuraMusic-v{clean_version}.zip"

    # 1. Copy universal APK
    shutil.copy2(source_apk, target_universal)
    print(f"[OK] Created {target_universal} ({target_universal.stat().st_size} bytes)")

    # 2. Copy versioned APK
    shutil.copy2(source_apk, target_versioned)
    print(f"[OK] Created {target_versioned} ({target_versioned.stat().st_size} bytes)")

    # 3. Create ZIP bundle
    with zipfile.ZipFile(target_zip, 'w', zipfile.ZIP_DEFLATED) as zf:
        zf.write(target_versioned, arcname=f"AuraMusic-v{clean_version}.apk")
        zf.write(target_universal, arcname="AuraMusic.apk")
    print(f"[OK] Created ZIP bundle {target_zip} ({target_zip.stat().st_size} bytes)")

    print("\n--- All release assets prepared successfully! ---")
    for asset in [target_universal, target_versioned, target_zip]:
        print(f"  - {asset.name} ({asset.stat().st_size} bytes)")

def main():
    parser = argparse.ArgumentParser(description="Prepare AuraMusic release assets for GitHub Releases")
    parser.add_argument("--version", required=True, help="Release version (e.g. 13.8.20 or v13.8.20)")
    parser.add_argument("--apk-dir", default="app/build/outputs/apk/foss/release", help="Directory containing built APK")
    parser.add_argument("--out-dir", default="release_assets", help="Directory to save packaged release assets")

    args = parser.parse_args()
    prepare_assets(
        version=args.version,
        apk_dir=Path(args.apk_dir),
        out_dir=Path(args.out_dir)
    )

if __name__ == "__main__":
    main()
