#!/usr/bin/env python3
"""
Metrolist / AuraMusic AI Issue Solver
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
    """
    Finds and ranks source files referenced in the issue body, stack traces, and flight recorder logs.
    Ranks files by relevance so the most pertinent source files are prioritized.
    """
    all_files = []
    for root in ["app/src/main", "innertube/src/main"]:
        root_path = Path(root)
        if not root_path.exists():
            continue
        for p in root_path.rglob("*"):
            if p.is_file() and p.suffix in (".kt", ".java"):
                all_files.append(p.as_posix())

    scores = {f: 0 for f in all_files}

    # 1. Direct stack trace matches (e.g., at com.metrolist...ClassName(File.kt:123))
    st_matches = re.findall(r'([A-Za-z0-9_]+\.(?:kt|java))', issue_text)
    for fn in set(st_matches):
        for f in all_files:
            if f.endswith("/" + fn):
                scores[f] += 10000

    # 2. Log tag matches (e.g., [20:40:28.344] W/MusicService :)
    tag_matches = re.findall(r'\[\d{2}:\d{2}:\d{2}\.\d{3}\]\s+([EWDIV])\/([A-Za-z0-9_]+)', issue_text)
    for level, tag in tag_matches:
        pts = {"E": 5000, "W": 4000, "D": 1200, "I": 1000, "V": 800}.get(level, 500)
        tag_lower = tag.lower()
        for f in all_files:
            stem_lower = Path(f).stem.lower()
            if stem_lower == tag_lower or stem_lower.startswith(tag_lower):
                scores[f] += pts

    # 3. Domain error keywords in issue text
    playback_keywords = [
        "ExoPlaybackException", "Source error", "MatroskaExtractor", "EOFException",
        "errorCode=2000", "AudioTrack", "DefaultExtractorInput", "ProgressiveMediaPeriod"
    ]
    if any(k in issue_text for k in playback_keywords):
        for f in all_files:
            if "MusicService.kt" in f:
                scores[f] += 15000
            elif "InnerTubeXPlayer.kt" in f:
                scores[f] += 10000
            elif "PlayerConnection.kt" in f:
                scores[f] += 8000
            elif "DownloadUtil.kt" in f:
                scores[f] += 6000

    discord_keywords = ["Discord", "DiscordService", "Ktor", "presence"]
    if any(k in issue_text for k in discord_keywords):
        for f in all_files:
            if "DiscordService.kt" in f:
                scores[f] += 12000

    quickjs_keywords = ["QuickJs", "QuickJS", "cipher", "player-accessed-on-wrong-thread"]
    if any(k in issue_text for k in quickjs_keywords):
        for f in all_files:
            if "QuickJsEngine.kt" in f:
                scores[f] += 15000

    # 4. PascalCase identifiers mentioned in text (excluding common noisy keywords)
    ignore_words = {
        "Android", "Google", "Pixel", "FlightRecorder", "Diagnostics", "AuraMusic",
        "Metrolist", "Exception", "String", "Error", "Caused", "Thread", "DefaultDispatcher",
        "Handler", "Looper", "HandlerThread", "ThreadPoolExecutor"
    }
    class_matches = re.findall(r'\b([A-Z][A-Za-z0-9_]{3,})\b', issue_text)
    for cls in class_matches:
        if cls in ignore_words:
            continue
        for f in all_files:
            if Path(f).stem == cls:
                scores[f] += 150

    ranked_files = [f for f, s in sorted(scores.items(), key=lambda x: x[1], reverse=True) if s > 0]

    # Ensure core playback files are available if playback issues occurred
    if any(k in issue_text for k in playback_keywords):
        fallback_playback = [
            "app/src/main/kotlin/com/metrolist/music/playback/MusicService.kt",
            "app/src/main/kotlin/com/metrolist/music/playback/PlayerConnection.kt"
        ]
        for fb in fallback_playback:
            if fb in all_files and fb not in ranked_files[:5]:
                ranked_files.insert(0, fb)

    return ranked_files[:5]

def call_gemini_api(api_key: str, prompt: str, primary_model: str = "gemini-2.5-flash") -> str:
    """Calls Gemini Flash model via REST API with fallback support."""
    models_to_try = [primary_model]
    for fallback in ["gemini-2.5-flash", "gemini-2.0-flash", "gemini-2.5-pro", "gemini-1.5-flash", "gemini-3.6-flash", "gemini-3-flash"]:
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
            with urllib.request.urlopen(req, timeout=60) as response:
                res_data = json.loads(response.read().decode("utf-8"))
                return res_data["candidates"][0]["content"]["parts"][0]["text"]
        except urllib.error.HTTPError as e:
            err_body = e.read().decode("utf-8", errors="ignore")
            print(f"[!] Model {model} HTTP Error {e.code}: {err_body}", file=sys.stderr)
            last_error = e
            if e.code in (404, 400, 429, 500, 503):
                continue
            raise
        except Exception as e:
            print(f"[!] Model {model} unexpected error: {e}", file=sys.stderr)
            last_error = e
            continue

    if last_error:
        raise last_error
    raise RuntimeError("All Gemini model attempts failed.")

def extract_patch(response_text: str) -> str:
    """Extracts a Git unified diff patch from the model response."""
    # Check ```diff ... ```
    match = re.search(r'```(?:diff|patch)?\s+(diff --git .*?)```', response_text, re.DOTALL)
    if match:
        return match.group(1).strip() + "\n"

    match = re.search(r'```(?:diff|patch)?\s+(--- [ab]/.*?)```', response_text, re.DOTALL)
    if match:
        return match.group(1).strip() + "\n"

    match = re.search(r'```diff\s+(.*?)\s+```', response_text, re.DOTALL)
    if match and ("--- " in match.group(1) or "@@" in match.group(1)):
        return match.group(1).strip() + "\n"

    return ""

def main():
    issue_title = get_env_var("ISSUE_TITLE", "Diagnostic Report")
    issue_body = get_env_var("ISSUE_BODY", "")
    issue_number = get_env_var("ISSUE_NUMBER", "0")
    model_name = get_env_var("GEMINI_MODEL", "gemini-2.5-flash")

    api_key = get_env_var("GEMINI_API_KEY")
    if not api_key:
        print("WARNING: GEMINI_API_KEY environment variable is not set.", file=sys.stderr)
        with open("ai_solution_summary.md", "w", encoding="utf-8") as f:
            f.write(
                f"### 🤖 AI Self-Healing Issue Solver\n\n"
                f"Issue #{issue_number} was acknowledged and logged by the automated pipeline.\n\n"
                f"> ℹ️ **Notice**: `GEMINI_API_KEY` is not currently configured in repository secrets.\n"
                f"> To enable automated real-time diagnosis and automated code PR generation, add your Gemini API key in **Settings ➔ Secrets and variables ➔ Actions ➔ New repository secret** as `GEMINI_API_KEY`.\n"
            )
        sys.exit(0)

    print(f"[*] Processing Issue #{issue_number}: {issue_title}")
    print(f"[*] Configured Gemini Model: {model_name}")

    full_issue_text = f"{issue_title}\n\n{issue_body}"
    candidate_files = search_candidate_files(full_issue_text)

    if not candidate_files:
        candidate_files = [
            "app/src/main/kotlin/com/metrolist/music/playback/MusicService.kt",
            "app/src/main/kotlin/com/metrolist/music/playback/PlayerConnection.kt"
        ]

    print(f"[*] Top ranked candidate source files: {candidate_files}")

    files_context = []
    for fpath in candidate_files:
        if os.path.exists(fpath):
            with open(fpath, "r", encoding="utf-8", errors="ignore") as f:
                content = f.read()
                if len(content) > 50000:
                    content = content[:50000] + "\n... [truncated for context limit]"
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
   Ensure the code is robust, handles null safety, edge cases, and compiles cleanly in Kotlin 2.x / Android.
   CRITICAL REQUIREMENT: You MUST include the unified diff code block inside ```diff ... ```. Do not only describe the changes.

Format your response strictly as follows:
## DIAGNOSIS
<Explanation of the bug and fix>

## PATCH
```diff
<Git unified diff here>
```
"""

    print(f"[*] Contacting Gemini API ({model_name}) for diagnosis and code patch...")
    try:
        response_text = call_gemini_api(api_key, prompt, primary_model=model_name)
    except Exception as e:
        print(f"[!] Gemini API query failed: {e}", file=sys.stderr)
        with open("ai_solution_summary.md", "w", encoding="utf-8") as f:
            f.write(
                f"### 🤖 AI Self-Healing Issue Solver\n\n"
                f"Issue #{issue_number} was analyzed, but the automated diagnosis query encountered an error:\n\n"
                f"> ⚠️ `{e}`\n\n"
                f"A team member will review this diagnostic report manually.\n"
            )
        sys.exit(0)

    # Extract diagnosis and patch
    diag_match = re.search(r'## DIAGNOSIS\s+(.*?)(?=## PATCH|$)', response_text, re.DOTALL)
    diagnosis = diag_match.group(1).strip() if diag_match else "AI automated diagnosis completed."

    patch_content = extract_patch(response_text)

    # Multi-turn repair: if no diff block returned, ask Gemini explicitly for the patch
    if not patch_content:
        print("[!] No ```diff block found in first response. Querying Gemini for patch correction...")
        repair_prompt = f"""You previously analyzed this issue and provided the following diagnosis:

{diagnosis}

However, you did NOT provide the required Git unified diff patch (` ```diff ... ``` `) against the provided candidate files:
{candidate_files}

Please provide the code patch NOW as a standard unified diff against one or more of these files.
Respond ONLY with:
## DIAGNOSIS
{diagnosis}

## PATCH
```diff
diff --git a/... b/...
--- a/...
+++ b/...
...
```
"""
        try:
            repair_response = call_gemini_api(api_key, repair_prompt, primary_model=model_name)
            patch_content = extract_patch(repair_response)
            if patch_content:
                print("[*] Successfully extracted diff from repair response!")
                diag_match_repair = re.search(r'## DIAGNOSIS\s+(.*?)(?=## PATCH|$)', repair_response, re.DOTALL)
                if diag_match_repair:
                    diagnosis = diag_match_repair.group(1).strip()
        except Exception as e:
            print(f"[!] Repair query failed: {e}")

    if not patch_content:
        print("[!] No patch could be generated. Saving raw response as solution summary.")
        with open("ai_solution_summary.md", "w", encoding="utf-8") as f:
            f.write(
                f"### 🤖 AI Diagnosis for Issue #{issue_number}\n\n"
                f"> ℹ️ **Status**: Diagnostic analysis completed. No automated code patch was generated for this diagnostic log.\n\n"
                f"{response_text}\n"
            )
        sys.exit(0)

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
        if os.path.exists(patch_file):
            os.remove(patch_file)
        with open("ai_solution_summary.md", "w", encoding="utf-8") as f:
            f.write(
                f"### 🤖 AI Diagnosis for Issue #{issue_number}\n\n"
                f"{diagnosis}\n\n"
                f"```diff\n{patch_content}\n```\n\n"
                f"> ⚠️ Automated patch could not be automatically applied cleanly: `{apply_proc.stderr.strip()}`. Please review manually.\n"
            )
        sys.exit(0)

    print("[OK] Patch applied successfully!")
    with open("ai_solution_summary.md", "w", encoding="utf-8") as f:
        f.write(
            f"### 🤖 AI Self-Healing Diagnosis for Issue #{issue_number}\n\n"
            f"{diagnosis}\n\n"
            f"```diff\n{patch_content}\n```\n"
        )

if __name__ == "__main__":
    main()
