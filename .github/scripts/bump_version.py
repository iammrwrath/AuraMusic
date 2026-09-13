#!/usr/bin/env python3
"""
Metrolist / AuraMusic Version Bumper
Increments versionCode and the patch component of versionName in app/build.gradle.kts.
Writes new_version and version_code to GITHUB_OUTPUT for GitHub Actions workflows.
"""

import os
import re
import sys
from pathlib import Path

def bump_version(gradle_path: Path = Path("app/build.gradle.kts"), dry_run: bool = False):
    if not gradle_path.exists():
        print(f"Error: File not found at {gradle_path}", file=sys.stderr)
        sys.exit(1)

    content = gradle_path.read_text(encoding="utf-8")

    code_match = re.search(r'^\s*versionCode\s*=\s*(\d+)', content, re.MULTILINE)
    name_match = re.search(r'^\s*versionName\s*=\s*"([^"]+)"', content, re.MULTILINE)

    if not code_match or not name_match:
        print("Error: Could not find versionCode or versionName in build.gradle.kts", file=sys.stderr)
        sys.exit(1)

    old_code = int(code_match.group(1))
    old_name = name_match.group(1)

    new_code = old_code + 1

    # Semantic patch increment: 13.8.19 -> 13.8.20
    parts = old_name.split(".")
    if len(parts) >= 3 and parts[-1].isdigit():
        new_patch = int(parts[-1]) + 1
        new_name = ".".join(parts[:-1] + [str(new_patch)])
    elif len(parts) == 2 and parts[-1].isdigit():
        new_name = f"{old_name}.1"
    else:
        new_name = f"{old_name}.1"

    print(f"Bumping version: {old_name} (code {old_code}) -> {new_name} (code {new_code})")

    if not dry_run:
        new_content = re.sub(
            r'^\s*versionCode\s*=\s*\d+',
            f'        versionCode = {new_code}',
            content,
            count=1,
            flags=re.MULTILINE
        )
        new_content = re.sub(
            r'^\s*versionName\s*=\s*"[^"]+"',
            f'        versionName = "{new_name}"',
            new_content,
            count=1,
            flags=re.MULTILINE
        )
        gradle_path.write_text(new_content, encoding="utf-8")
        print(f"Updated {gradle_path} successfully.")

    # Export to GitHub Actions output if available
    github_output = os.environ.get("GITHUB_OUTPUT")
    if github_output and os.path.exists(github_output):
        with open(github_output, "a", encoding="utf-8") as f:
            f.write(f"new_version={new_name}\n")
            f.write(f"version_code={new_code}\n")
            f.write(f"old_version={old_name}\n")
            f.write(f"old_version_code={old_code}\n")
        print(f"Exported to GITHUB_OUTPUT: new_version={new_name}, version_code={new_code}")

    return new_name, new_code

if __name__ == "__main__":
    dry_run = "--dry-run" in sys.argv
    bump_version(dry_run=dry_run)
