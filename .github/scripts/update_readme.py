#!/usr/bin/env python3
"""Update versioned download links in README.md from the Android build version."""

from __future__ import annotations

import re
import sys
from pathlib import Path


GRADLE_PATH = Path("app/build.gradle.kts")
README_PATH = Path("README.md")


def read_version() -> str:
    content = GRADLE_PATH.read_text(encoding="utf-8")
    match = re.search(r'^\s*versionName\s*=\s*"([^"]+)"', content, re.MULTILINE)
    if not match:
        raise RuntimeError(f"Could not find versionName in {GRADLE_PATH}")
    return match.group(1)


def update_readme(version: str) -> bool:
    content = README_PATH.read_text(encoding="utf-8")
    updated = content

    updated = re.sub(
        r"(### 🚀 Version )[^ ]+( Released!)",
        rf"\g<1>{version}\g<2>",
        updated,
        count=1,
    )
    updated = re.sub(
        r"\|\s*\*\*AuraMusic v[^|]+\|\s*`\.apk`\s*\|\s*Specific Release Version\s*\|\s*\[⬇️ Download v[^]]+\]\(https://github\.com/iammrwrath/AuraMusic/releases/download/v[^/]+/AuraMusic-v[^)]+\.apk\)\s*\|",
        f"| **AuraMusic v{version}** | `.apk` | Specific Release Version | [⬇️ Download v{version}](https://github.com/iammrwrath/AuraMusic/releases/download/v{version}/AuraMusic-v{version}.apk) |",
        updated,
        count=1,
    )
    updated = re.sub(
        r"https://github\.com/iammrwrath/AuraMusic/releases/download/v[^/]+/AuraMusic-Desktop\.jar",
        f"https://github.com/iammrwrath/AuraMusic/releases/download/v{version}/AuraMusic-Desktop.jar",
        updated,
        count=1,
    )
    updated = re.sub(
        r"(?m)^\| \*\*Windows\*\* \| Setup .*?\n\| \*\*Windows\*\* \| Windows .*?\n",
        "",
        updated,
    )

    if updated == content:
        return False

    README_PATH.write_text(updated, encoding="utf-8")
    return True


def main() -> int:
    version = read_version()
    changed = update_readme(version)
    print(f"README {'updated' if changed else 'already current'} for v{version}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError) as error:
        print(f"Error: {error}", file=sys.stderr)
        raise SystemExit(1)
