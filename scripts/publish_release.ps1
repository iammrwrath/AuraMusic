param(
    [string]$Token = $env:GITHUB_TOKEN,
    [string]$TagName = "v13.6.3",
    [string]$Title = "AuraMusic v13.6.3",
    [string]$ApkPath = "app\build\outputs\apk\foss\debug\app-foss-debug.apk"
)

if (-not $Token) {
    Write-Error "Please supply -Token or set $env:GITHUB_TOKEN before running this script."
    exit 1
}

$repoOwner = "iammrwrath"
$repoName = "AuraMusic"

$headers = @{
    Authorization = "Bearer $Token"
    "User-Agent" = "PowerShell"
    "Content-Type" = "application/json"
    Accept = "application/vnd.github+json"
}

$changelog = @"
## 🎧 AuraMusic $TagName

### ✨ New Features
- **Studio-Grade Automix DJ Transitions**: Equal-power sinusoidal crossfade, smart cue-in intro trimming, and bass-swap crossover to prevent low-end clash.
- **Android Auto Live Lyrics Projection**: Real-time synchronized karaoke lyrics projected directly onto car displays via MediaSession subtitle.
- **Flight Recorder & AI Diagnostics**: One-tap diagnostic report generator with auto-filled GitHub issue creation for automated issue fixing.
- **Rebranded Experience**: Dedicated creator maintainer profile (@iammrwrath) and framework credits honoring Metrolist and BitChord upstream foundations.

### ⚡ Performance & Stability
- Ultra-smooth 120Hz scrolling optimizations with key-based memoization.
- ExoPlayer crossfade volume granularity increased to 30 continuous steps.
- Cleaned background coroutine jobs on service termination.
"@

Write-Host "Creating GitHub release $TagName for $repoOwner/$repoName..."

$releaseBody = @{
    tag_name = $TagName
    target_commitish = "main"
    name = $Title
    body = $changelog
    draft = $false
    prerelease = $false
} | ConvertTo-Json

try {
    $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$repoOwner/$repoName/releases" -Method POST -Headers $headers -Body $releaseBody
    Write-Host "Release created: $($release.html_url)"
    $uploadUrlTemplate = $release.upload_url
} catch {
    Write-Host "Release may already exist, fetching existing release..."
    $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$repoOwner/$repoName/releases/tags/$TagName" -Method GET -Headers $headers
    $uploadUrlTemplate = $release.upload_url
}

$uploadBase = $uploadUrlTemplate -replace '\{.*\}', ''

if (Test-Path $ApkPath) {
    Write-Host "Uploading AuraMusic.apk..."
    $uploadHeaders = @{
        Authorization = "Bearer $Token"
        "User-Agent" = "PowerShell"
        "Content-Type" = "application/vnd.android.package-archive"
    }
    
    $uploadUri = "$uploadBase`?name=AuraMusic.apk"
    Invoke-RestMethod -Uri $uploadUri -Method POST -Headers $uploadHeaders -InFile $ApkPath
    Write-Host "Uploaded AuraMusic.apk successfully!"
} else {
    Write-Error "APK file not found at $ApkPath. Please build it first."
}

Write-Host "All done! Release is live at: $($release.html_url)"
