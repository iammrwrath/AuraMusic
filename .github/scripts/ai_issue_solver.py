#!/usr/bin/env python3
"""
Metrolist AI Issue Solver
Analyzes mobile diagnostic reports and GitHub Issues using Gemini,
identifies the root cause in the codebase, and generates a validated code patch.
"""

import os
import sys
import json
import re
import urllib.request
import urllib.error
import subprocess
from pathlib import Path

def get_env_var(name: str, default: str = "") -> str:
    return os.environ.get(name, default).strip()

def search_candidate_files(issue_text: str):
    """Finds source files referenced in the issue body or stack trace."""
    referenced_files = set()
    # Find Kotlin and Java files mentioned
    matches = re.findall(r'([A-Za-z0-9_]+\.(?:kt|java))', issue_text)
    for filename in set(matches):
        for path in Path("app/src/main").rglob(filename):
            referenced_files.add(str(path))
        for path in Path("innertube/src/main").rglob(filename):
            referenced_files.add(str(path))
    return list(referenced_files)

def call_gemini_api(api_key: str, prompt: str, primary_model: str = "gemini-3.8-flash") -> str:
    """Calls Gemini Flash model via REST API with fallback support."""
    models_to_try = [primary_model]
    for fallback in ["gemini-3.8-flash", "gemini-3-flash-preview", "gemini-2.5-flash"]:
        if fallback not in models_to_try:
            models_to_try.append(fallback)

    last_error = None
    for model in models_to_try:
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={api_key}"
        payload = {
            "contents": [
                {
                    "parts": [
                        {"text": prompt}
                    ]
                }
            ],
            "generationConfig": {
                "temperature": 0.2,
                "maxOutputTokens": 8192
            }
        }
        
        req = urllib.request.Request(
            url,
            data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST"
        )
        
        try:
            print(f"[*] Querying Gemini model: {model}...")
            with urllib.request.urlopen(req) as response:
                res_data = json.loads(response.read().decode("utf-8"))
                return res_data["candidates"][0]["content"]["parts"][0]["text"]
        except urllib.error.HTTPError as e:
            err_body = e.read().decode("utf-8", errors="ignore")
            print(f"[!] Model {model} HTTP Error {e.code}: {err_body}", file=sys.stderr)
            last_error = e
            if e.code == 404:
                continue
            raise

    if last_error:
        raise last_error
    raise RuntimeError("All Gemini model attempts failed.")

def main():
    api_key = get_env_var("GEMINI_API_KEY")
    if not api_key:
        print("ERROR: GEMINI_API_KEY environment variable is not set.", file=sys.stderr)
        sys.exit(1)
        
    model_name = get_env_var("GEMINI_MODEL", "gemini-3.8-flash")
    issue_title = get_env_var("ISSUE_TITLE", "Diagnostic Report")
    issue_body = get_env_var("ISSUE_BODY", "")
    issue_number = get_env_var("ISSUE_NUMBER", "0")

    print(f"[*] Processing Issue #{issue_number}: {issue_title}")
    print(f"[*] Configured Gemini Model: {model_name}")

    full_issue_text = f"{issue_title}\n\n{issue_body}"
    candidate_files = search_candidate_files(full_issue_text)

    if not candidate_files:
        # Default to primary playback / callback files if none detected
        candidate_files = [
            "app/src/main/kotlin/com/metrolist/music/playback/MediaLibrarySessionCallback.kt",
            "app/src/main/kotlin/com/metrolist/music/playback/MusicService.kt"
        ]

    print(f"[*] Candidate source files identified: {candidate_files}")

    files_context = []
    for fpath in candidate_files[:4]:
        if os.path.exists(fpath):
            with open(fpath, "r", encoding="utf-8", errors="ignore") as f:
                content = f.read()
                # Limit size per file if needed
                if len(content) > 60000:
                    content = content[:60000] + "\n... [truncated]"
                files_context.append(f"=== FILE: {fpath} ===\n{content}")

    code_context_str = "\n\n".join(files_context)

    prompt = f"""You are an expert Android Kotlin engineer working on the AuraMusic music app.
A user reported the following bug / diagnostic report from their mobile device:

--- ISSUE TITLE ---
{issue_title}

--- ISSUE DETAILS & FLIGHT RECORDER LOGS ---
{issue_body}

--- SOURCE CODE CONTEXT ---
{code_context_str}

Please perform the following:
1. Explain the root cause of the bug based on the logs, stack trace, and code.
2. Provide a Git unified diff patch (using standard `diff --git a/... b/...` format) to fix the issue.
   Make sure the diff paths match the exact repo path (e.g., `a/app/src/main/...` and `b/app/src/main/...`).
   Ensure the code is robust, handles null safety, edge cases, and compiles in Kotlin 2.x / Android.

Format your response strictly as follows:
## DIAGNOSIS
<Explanation of the bug and fix>

## PATCH
```diff
<Git unified diff here>
```
"""

    print(f"[*] Contacting Gemini API ({model_name}) for diagnosis and code patch...")
    response_text = call_gemini_api(api_key, prompt, primary_model=model_name)

    # Extract diagnosis and patch
    diag_match = re.search(r'## DIAGNOSIS\s+(.*?)(?=## PATCH|$)', response_text, re.DOTALL)
    diagnosis = diag_match.group(1).strip() if diag_match else "AI automated diagnosis completed."

    patch_match = re.search(r'```diff\s+(.*?)\s+```', response_text, re.DOTALL)
    if not patch_match:
        print("[!] No ```diff block found in response. Saving raw response as solution summary.")
        with open("ai_solution_summary.md", "w", encoding="utf-8") as f:
            f.write(f"### 🤖 AI Diagnosis for Issue #{issue_number}\n\n{response_text}\n")
        sys.exit(0)

    patch_content = patch_match.group(1).strip() + "\n"
    patch_file = "ai_fix.patch"
    with open(patch_file, "w", encoding="utf-8") as f:
        f.write(patch_content)

    print(f"[*] Patch written to {patch_file}. Attempting git apply...")

    # Apply the patch
    apply_proc = subprocess.run(["git", "apply", "--ignore-whitespace", "--recount", patch_file], capture_output=True, text=True)
    if apply_proc.returncode != 0:
        print(f"[!] Standard git apply failed: {apply_proc.stderr}")
        print("[*] Trying git apply with 3-way merge...")
        apply_proc = subprocess.run(["git", "apply", "-3", patch_file], capture_output=True, text=True)

    if apply_proc.returncode != 0:
        print(f"[ERROR] Failed to apply git patch: {apply_proc.stderr}", file=sys.stderr)
        with open("ai_solution_summary.md", "w", encoding="utf-8") as f:
            f.write(f"### 🤖 AI Diagnosis for Issue #{issue_number}\n\n{diagnosis}\n\n> ⚠️ Automated patch could not be cleanly applied by git: `{apply_proc.stderr.strip()}`\n")
        sys.exit(1)

    print("[✓] Patch applied successfully!")
    with open("ai_solution_summary.md", "w", encoding="utf-8") as f:
        f.write(f"### 🤖 AI Self-Healing Diagnosis for Issue #{issue_number}\n\n{diagnosis}\n\n```diff\n{patch_content}\n```\n")

if __name__ == "__main__":
    main()
