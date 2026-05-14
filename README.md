# 🎬 Ryou Player

<div align="center">

![Ryou Player Banner](https://img.shields.io/badge/Ryou%20Player-v1.0.0-6750A4?style=for-the-badge&logo=android)
[![Build Status](https://github.com/ryoustream/ryoupro/actions/workflows/build.yml/badge.svg)](https://github.com/ryoustream/ryoupro/actions)
![Android](https://img.shields.io/badge/Android-9%2B-brightgreen?style=flat&logo=android)
![Min SDK](https://img.shields.io/badge/minSdk-28-blue)
![Target SDK](https://img.shields.io/badge/targetSdk-35-blue)
![Language](https://img.shields.io/badge/Language-Java-orange?logo=java)
![Architecture](https://img.shields.io/badge/Architecture-MVVM%20%2B%20Clean-purple)
![Material You](https://img.shields.io/badge/UI-Material%20You%203-blueviolet)

**A premium modern Android video player — Material You · ExoPlayer · Clean Architecture**

[Download APK](#-download) · [Build Locally](#-build-locally) · [GitHub Actions](#-github-actions-cicd) · [Contribute](#-contributing)

</div>

---

## ✨ Features

### 🎥 Video Playback
- **ExoPlayer (Media3)** based engine — smooth, hardware-accelerated
- Wide codec support: H.264, H.265/HEVC, AV1, VP8, VP9, MPEG-2/4
- Container support: MKV, MP4, AVI, MOV, FLV, WEBM, TS, 3GP, OGG
- HDR / HDR10 / HLG detection
- Hardware + software decoder selection
- Auto frame rate switching

### 📡 Streaming
- HLS (.m3u8)
- MPEG-DASH (.mpd)
- RTSP / RTMP
- HTTP / HTTPS direct streams
- Stream history management
- Auto-reconnect

### 🎧 Audio
- Multiple audio track support
- Audio passthrough (DTS, Dolby)
- AAC, MP3, FLAC, WAV, Opus, Vorbis

### 📝 Subtitles
- SRT, ASS, SSA, VTT, TTML, SUB
- Embedded & external subtitle files
- Subtitle sync, size, shadow, background customization
- Auto-detect encoding

### 📱 Playback Controls
- Gesture: swipe for brightness/volume, double-tap to seek
- Double-tap left/right: ±10 second skip
- Pinch-to-zoom aspect ratio
- Playback speed control (0.25× – 2.0×)
- Picture-in-Picture (PiP) — Android 8+
- Background playback via MediaSession
- Sleep timer
- A–B repeat
- Resume playback (saves position)

### 🗂️ Library
- Auto MediaStore scan
- Folder browsing
- Grid / List view toggle
- Search & filter
- Favorites
- Recently watched
- Stream history

### 🎨 UI / UX
- **Material You 3** with Dynamic Color
- Light / Dark / AMOLED mode
- Edge-to-edge display
- Android 15 inspired design
- Smooth animations

---

## 🏗️ Architecture

```
com.ryoustream.player/
├── data/
│   ├── local/
│   │   ├── dao/          # Room DAOs
│   │   ├── database/     # AppDatabase
│   │   └── entity/       # Room entities
│   └── repository/       # Repository implementations
├── domain/
│   ├── model/            # Domain models (MediaItem, NetworkStream…)
│   ├── repository/       # Repository interfaces
│   └── usecase/          # Business logic use cases
├── service/              # MediaPlaybackService (Media3 session)
├── ui/
│   ├── home/             # HomeFragment + HomeViewModel
│   ├── library/          # LibraryFragment + adapter
│   ├── player/           # PlayerActivity + PlayerViewModel
│   ├── settings/         # SettingsActivity + PreferenceFragment
│   └── stream/           # NetworkStreamActivity
├── di/                   # Hilt modules (DatabaseModule)
└── util/                 # TimeUtils, etc.
```

**Stack:**
| Layer | Technology |
|-------|-----------|
| UI | XML Layouts, Material You 3 |
| Architecture | MVVM + Clean Architecture |
| DI | Hilt (Dagger) |
| Database | Room |
| Player | Media3 (ExoPlayer) |
| Images | Glide |
| Navigation | Navigation Component |
| Network | OkHttp |
| Async | LiveData + Executors |

---

## 📥 Download

Download the latest APK from [GitHub Releases](https://github.com/ryoustream/ryoupro/releases).

| Build | Status |
|-------|--------|
| Debug APK | Available on every commit (Actions Artifacts) |
| Release APK | Available on every `main` push & release tags |

---

## 🔨 Build Locally

### Prerequisites
- JDK 17+
- Android SDK (API 35)
- No Android Studio required — Gradle CLI is enough

```bash
# Clone
git clone https://github.com/ryoustream/ryoupro.git
cd ryoupro

# Build Debug APK
./gradlew assembleDebug

# Build Release APK (requires signing.properties)
cp signing.properties.template signing.properties
# Edit signing.properties with your keystore info
./gradlew assembleRelease

# APK output location
ls app/build/outputs/apk/
```

---

## 🤖 GitHub Actions CI/CD

Every push automatically builds APKs via `.github/workflows/build.yml`.

### Workflow Triggers
| Event | Debug | Release |
|-------|-------|---------|
| Push to `main` | ✅ | ✅ |
| Push to `develop` | ✅ | ❌ |
| Pull Request | ✅ | ❌ |
| Tag `v*.*.*` | ✅ | ✅ + GitHub Release |
| Manual dispatch | ✅ | Optional |

### Setup GitHub Secrets

Go to `Settings → Secrets and variables → Actions` and add:

| Secret | Description |
|--------|-------------|
| `KEYSTORE_BASE64` | Base64-encoded keystore.jks |
| `KEYSTORE_PASSWORD` | Keystore store password |
| `KEY_ALIAS` | Key alias (`ryoustream`) |
| `KEY_PASSWORD` | Key password |

### Generate keystore

```bash
bash scripts/generate_keystore.sh
# Script outputs KEYSTORE_BASE64 value to copy into GitHub Secrets
```

### Version Format
```
v1.0.0-build52-api35-gitAbc1234
       ├── 52         = GitHub Actions run number
       ├── api35      = Target SDK
       └── Abc1234    = Short git hash
```

---

## ⚙️ Settings

| Category | Options |
|----------|---------|
| Playback | Decoder (Auto/Hardware/Software), Resume position, Speed |
| Subtitles | Size, Shadow, Background |
| Gestures | Enable/disable swipe controls |
| Appearance | Theme (System/Light/Dark), AMOLED mode |
| Network | Buffer size, Auto-reconnect |
| Storage | Cache size, Clear cache |

---

## 🗺️ Roadmap

- [ ] mpv-android / libmpv integration (advanced codec support)
- [ ] Chromecast support
- [ ] Android TV / Leanback UI
- [ ] Playlist management
- [ ] SMB / FTP network browser
- [ ] Torrent streaming
- [ ] GPU rendering optimization
- [ ] Equalizer / audio effects
- [ ] Lock screen widget

---

## 🤝 Contributing

Pull requests welcome! Please:
1. Fork the repo
2. Create a feature branch (`git checkout -b feat/awesome-feature`)
3. Commit your changes
4. Push and open a Pull Request

---

## 📄 License

```
Copyright 2026 RyouStream

Licensed under the Apache License, Version 2.0
```

---

<div align="center">
Made with ❤️ by RyouStream
</div>
