<div align="center">

<img src="fastlane/metadata/android/en-US/images/icon.png" alt="AuraMusic app icon" width="160" />

# AuraMusic

### The next-generation, cross-platform YouTube Music streaming client featuring studio-grade Automix DJ transitions, Nothing OS Dot Matrix theme, Android Auto synchronized live lyrics, M3U playlist import, and dedicated Desktop apps.

<br/>

[![Latest release](https://img.shields.io/github/v/release/iammrwrath/AuraMusic?style=for-the-badge&labelColor=0d1117&color=6366f1)](https://github.com/iammrwrath/AuraMusic/releases)
[![Platforms](https://img.shields.io/badge/Platforms-Android%20%7C%20Windows%20%7C%20macOS%20%7C%20Linux-e11d48?style=for-the-badge&labelColor=0d1117)](https://github.com/iammrwrath/AuraMusic/releases)
[![License](https://img.shields.io/github/license/iammrwrath/AuraMusic?style=for-the-badge&labelColor=0d1117&color=10b981)](https://github.com/iammrwrath/AuraMusic/blob/main/LICENSE)
[![Downloads](https://img.shields.io/github/downloads/iammrwrath/AuraMusic/total?style=for-the-badge&labelColor=0d1117&color=f59e0b)](https://github.com/iammrwrath/AuraMusic/releases)

<br/>

[**Download**](#-download) · [**Highlights**](#-key-features) · [**Desktop App**](#-desktop-experience) · [**Nothing OS Theme**](#-nothing-os-dot-matrix-mode) · [**Automix DJ**](#️-studio-grade-automix-dj-transitions) · [**Build Instructions**](#️-build-from-source) · [**Credits**](#-framework-credits--acknowledgments)

</div>

---

> [!NOTE]
> ### 🚀 Version 13.8.6 Released!
> AuraMusic is now fully cross-platform with official support for **Android** and **Desktop (Windows, macOS, Linux)**, featuring the iconic **Nothing OS Dot Matrix** mode, **M3U playlist importing**, **Direct YouTube Music login**, and **seamless Automix transitions**.

---

## 🎧 Key Features

### 🔴 Nothing OS (Dot Matrix) Mode
* **Authentic NDot Typography**: Custom-rendered NDot 57 matrix fonts across headers, song titles, timestamps, and metadata.
* **Signature Nothing Red (`#D71921`)**: Striking high-contrast accenting set against pure OLED black backgrounds.
* **Retro-Futuristic Player**: Specialized dot-matrix Now Playing layout with animated audio indicators.
* **Toggle with 1 Tap**: Seamlessly switch between Material You dynamic theming and Nothing OS Dot Matrix mode in Appearance Settings.

### 💻 Desktop Experience (Compose Multiplatform)
* **Native Desktop Client**: Engineered with Compose Multiplatform for Windows, macOS, and Linux.
* **Glassmorphic OLED Interface**: Sleek dark UI with collapsible sidebar navigation, responsive library views, and fluid animations.
* **Direct YouTube Music Login**: Built-in interactive Google authentication modal with seamless cookie extraction and automatic session restoration.
* **Fallback Authentication**: Flexible "Open in Browser" and "Paste Cookie" modes ensure reliable logins even in restricted environments.
* **Integrated Desktop Audio Engine**: Powered by OpenJFX Media with synchronized lyrics, volume sliders, track scrubbing, and media key support.

### 🎚️ Studio-Grade Automix (DJ Transitions)
* **Constant Equal-Power Blending**: Sinusoidal $\\sin^2(t) + \\cos^2(t) \\equiv 1.0$ volume curves guarantee continuous acoustic energy with zero volume dip between tracks.
* **Adaptive Duration Scaling**: Dynamically scales crossfade length according to track duration (up to 15% cap) for punchy transitions on shorter songs and expansive blends on longer tracks.
* **Smart Cue-In Intro Trimming**: Skips generic encoder silence (~200ms) on incoming tracks so beats drop seamlessly on the downbeat.
* **Bass-Swap Crossover**: Automatically rolls off outgoing low frequencies past the 55% mark of transitions to eliminate low-end muddiness and kick clashes.
* **Granular Volume Steps**: 30-step smooth volume ramp with recycled ExoPlayer engine for artifact-free fading.

### 📁 M3U & M3U8 Playlist Importer
* **Local Playlist Import**: Direct file-picker support for `.m3u` and `.m3u8` playlist files.
* **Smart Track Matching**: Automatically scans your local library and Room database to link and reconstruct playlists effortlessly.
* **Instant Library Integration**: Imported playlists appear directly in your Library alongside saved YouTube Music playlists.

### 🚗 Android Auto Live Karaoke Lyrics
* **Car Head Unit Projection**: Real-time synchronized lyrics stream directly onto car displays via the MediaSession subtitle field.
* **Smart Interlude Indicators**: Displays `🎤 [Lyrics]` during vocals and `🎵 [Instrumental]` or `🎵 [Intro]` during musical pauses.
* **1-Tap In-Car Control**: Dedicated lyrics toggle button in car playback controls.
* **Optimized Local Caching**: Queries offline Room DB cache first before falling back to network providers.

### 👥 Listen Together (Synchronized Rooms)
* **Social Audio Sync**: Listen simultaneously with friends in synchronized rooms.
* **Low-Latency Protocol**: Engineered over WebSockets with standard Protobuf message serialization for rock-solid playback synchronization.

### ⚡ 120Hz Ultra-Smooth Interface
* **High Refresh Rate Support**: Seamlessly enables 120Hz/90Hz display modes for buttery-smooth animations.
* **Virtualized Lazy Lists**: Memoized Compose keys across all song, album, and playlist queues to eliminate frame drops and stutter.
* **High-Efficiency Bitmap Cache**: Expanded in-memory Coil image caching for instantaneous thumbnail rendering.

### 🤖 Flight Recorder & Autonomous AI Diagnostics
* **1-Tap Bug Reporting**: Automatically collects device metadata, playback states, and recent session logs into a pre-formatted GitHub issue.
* **Diagnostic Console**: Comprehensive in-app logs accessible directly from Settings → About.

### 🎵 Core Music & Audio Capabilities
* **YouTube Music Streaming**: Stream any song, video, or podcast directly with background playback and screen-off listening.
* **Offline Downloads**: High-quality caching and downloading for offline listening.
* **Synced & Translated Lyrics**: Real-time word-by-word synced lyrics and AI-powered translation.
* **Audio Processing**: ReplayGain audio normalization, equalizer, sleep timer, and tempo/pitch adjustment.
* **Material You Theming**: Dynamic theme engine matching system wallpaper plus 19 preset color schemes with OLED Black mode.
* **Privacy-First**: No ads, no telemetry, no tracking.

---

## 📲 Download

<div align="center">

### Android (Mobile)
| Package | Format | Compatibility | Download |
| :--- | :--- | :--- | :--- |
| **AuraMusic Universal** | `.apk` | Android 8.0+ (`arm64-v8a`, `armeabi-v7a`, `x86_64`) | [⬇️ Download AuraMusic.apk](https://github.com/iammrwrath/AuraMusic/releases/latest/download/AuraMusic.apk) |
| **AuraMusic v13.8.6** | `.apk` | Specific Release Version | [⬇️ Download v13.8.6](https://github.com/iammrwrath/AuraMusic/releases/download/v13.8.6/AuraMusic-v13.8.6.apk) |

<br/>

### Desktop (Windows / macOS / Linux)
| Platform | Package | Description | Download |
| :--- | :--- | :--- | :--- |
| **Windows** | Setup `.exe` | Recommended Windows Installer with Start Menu shortcut | [⬇️ Download Setup.exe](https://github.com/iammrwrath/AuraMusic/releases/download/v13.8.6/AuraMusic-Setup-13.8.4.exe) |
| **Windows** | Windows `.msi` | Windows MSI Installer package | [⬇️ Download .msi](https://github.com/iammrwrath/AuraMusic/releases/download/v13.8.6/AuraMusic-13.8.4.msi) |
| **Universal Desktop** | Standalone `.jar` | Cross-platform executable JAR (requires Java 21+) | [⬇️ Download Desktop.jar](https://github.com/iammrwrath/AuraMusic/releases/download/v13.8.6/AuraMusic-Desktop.jar) |

</div>

> [!TIP]
> **In-App Updates**: AuraMusic on Android includes a built-in auto-updater. Simply go to **Settings → Updater** to check for and install updates directly within the app!

---

## 🛠️ Build from Source

### Prerequisites
* **JDK 21** or later installed and configured on your PATH (`JAVA_HOME`).
* Android SDK (API 34+) for Android builds.

### Mobile (Android)
```bash
# Clone the repository
git clone https://github.com/iammrwrath/AuraMusic.git
cd AuraMusic

# Build debug APK
./gradlew :app:assembleFossDebug

# Build release APK
./gradlew :app:assembleFossRelease
# Compiled APK: app/build/outputs/apk/foss/release/app-foss-release-unsigned.apk
```

### Desktop (Windows / macOS / Linux)
```bash
# Run desktop app directly
./gradlew :desktop:run

# Package native distributions (Windows MSI/EXE)
./gradlew :desktop:packageDistributionForCurrentOS
```

---

## 🙏 Framework Credits & Acknowledgments

AuraMusic proudly stands on the shoulders of the open-source community. We express our deepest gratitude to the creators and maintainers of the foundational projects that made AuraMusic possible:

* **[Metrolist](https://github.com/MetrolistGroup/Metrolist)** (by Mo Agamy and contributors):  
  The core open-source Android architecture, YouTube Music streaming engine, Media3 service implementation, and Room database structure that form the bedrock of this application.
* **[BitChord](https://github.com/kushagrasinghx/BitChord)** (by Kushagra Singh):  
  Inspiration for the Automix transition paradigm and dynamic audio interface enhancements.
* **[InnerTune](https://github.com/z-huang/InnerTune)** & **[OuterTune](https://github.com/DD3Boh/OuterTune)**:  
  Pioneering open-source YouTube Music clients that originated the modern Android Compose music player design patterns.
* **[JetBrains Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform)**:  
  Enabling desktop UI rendering and seamless cross-platform sharing.

---

## 💬 Support & Bug Reports

* **Maintainer**: [@iammrwrath](https://github.com/iammrwrath)
* **Bug Reports & Feature Requests**: [Open an Issue](https://github.com/iammrwrath/AuraMusic/issues)
* **Direct Email Contact**: [`iammrwrath@gmail.com`](mailto:iammrwrath@gmail.com?subject=AuraMusic%20Inquiry)

---

## 📄 License

AuraMusic is licensed under the **GNU General Public License v3.0** (GPL-3.0). See the [LICENSE](LICENSE) file for complete details.
