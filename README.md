<div align="center">

<img src="https://raw.githubusercontent.com/CodeBySonu95/VoxSherpa-TTS/main/fastlane/metadata/android/en-US/images/featureGraphic.png" width="100%" alt="VoxSherpa TTS Banner"/>

<br/>
<br/>

[![Join Beta](https://img.shields.io/badge/Join-Beta%20v2.1-brightgreen?style=for-the-badge&logo=android&logoColor=white)](https://docs.google.com/forms/d/e/1FAIpQLSd-_VgF3DlbiG2eU00Dw_cZECMinUIuSx_Nr6NJt1DeUcIwTQ/viewform?usp=publish-editor)
[![Support](https://img.shields.io/badge/💙_Support-This%20Project-FF5E5B?style=for-the-badge)](https://codebysonu95.github.io/VoxSherpa-TTS/assets/support.html)
[![Android](https://img.shields.io/badge/Android-11%2B-brightgreen?style=for-the-badge&logo=android&logoColor=white)](https://android.com)
[![License](https://img.shields.io/badge/License-GPL%20v3.0-blue?style=for-the-badge)](LICENSE)
[![Sherpa-ONNX](https://img.shields.io/badge/Powered%20by-Sherpa--ONNX-orange?style=for-the-badge)](https://github.com/k2-fsa/sherpa-onnx)
[![Downloads](https://img.shields.io/github/downloads/CodeBySonu95/VoxSherpa-TTS/total?style=for-the-badge&logo=android&logoColor=white&label=Downloads&color=blue)](https://github.com/CodeBySonu95/VoxSherpa-TTS/releases)

<h1>VoxSherpa TTS</h1>
<h3>Studio-quality offline neural text-to-speech for Android.<br/>Hindi · English · British · Japanese · Chinese · and more — No cloud. No limits. No compromise.</h3>

</div>

---

## 🏆 Featured In

> VoxSherpa TTS is listed in the **official README** of [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) — the core inference library powering this app.

[![Sherpa-ONNX](https://img.shields.io/badge/Featured%20in-Sherpa--ONNX%20Official%20README-orange?style=for-the-badge)](https://github.com/k2-fsa/sherpa-onnx#voxsherpa-tts)
[![HuggingFace](https://img.shields.io/badge/Models%20on-HuggingFace-FFD21E?style=for-the-badge&logo=huggingface&logoColor=black)](https://huggingface.co/CodeBySonu95/VoxSherpa-TTS)

---

## Why VoxSherpa?

Most TTS apps make you choose between **quality** and **privacy**. Cloud-based tools like ElevenLabs sound incredible — but they require internet, send your text to remote servers, and charge per character.

**VoxSherpa breaks that tradeoff.**

It runs two professional-grade neural engines entirely on your device:

| Engine | Quality | Speed | Best For |
|--------|---------|-------|----------|
| 🧠 **Kokoro-82M** | Studio-grade · rivals ElevenLabs | Slower on budget hardware | Audiobooks, voiceovers, professional content |
| ⚡ **Piper / VITS** | Natural · clear | Fast on any device | Daily use, quick synthesis |

---

## Screenshots

<div align="center">

| Generate | Models | Library | Settings |
|:---:|:---:|:---:|:---:|
| <img src="https://raw.githubusercontent.com/CodeBySonu95/VoxSherpa-TTS/main/fastlane/metadata/android/en-US/images/phoneScreenshots/1.jpg" width="180"/> | <img src="https://raw.githubusercontent.com/CodeBySonu95/VoxSherpa-TTS/main/fastlane/metadata/android/en-US/images/phoneScreenshots/2.jpg" width="180"/> | <img src="https://raw.githubusercontent.com/CodeBySonu95/VoxSherpa-TTS/main/fastlane/metadata/android/en-US/images/phoneScreenshots/3.jpg" width="180"/> | <img src="https://raw.githubusercontent.com/CodeBySonu95/VoxSherpa-TTS/main/fastlane/metadata/android/en-US/images/phoneScreenshots/4.jpg" width="180"/> |

</div>

---

## Features

### 🎙️ Dual Neural Engine
- **Kokoro-82M** — 82 million parameter neural model. Multilingual support including Hindi, English, British English, French, Spanish, Chinese, Japanese and 50+ more languages. Same architecture used by top-tier commercial TTS services.
- **Piper / VITS** — Fast, lightweight, natural. Generates speech in seconds on any Android device.

### 🔒 100% Offline & Private
- All processing happens on your device
- No internet required after model download
- No account, no telemetry, no data collection
- Your text never leaves your phone

### 📦 Model Management
- Download models directly from the app
- Import your own `.onnx` models from local storage
- Multiple models installed simultaneously
- Smart storage tracking

### 🎧 Audio Controls
- Real-time waveform visualization
- Adjustable speed and pitch
- Play, pause, and replay generated audio
- Export as WAV with correct sample rate per model

### 📚 Speech Library
- Save all generated audio locally
- Favorites system for quick access
- View generation history with timestamps
- Voice model attribution per recording

### ⚙️ Smart Settings
- **Smart Punctuation** — natural pauses after sentence breaks
- **Emotion Tags** — `[whisper]`, `[angry]`, `[happy]` support
- Per-model voice selection (Kokoro supports 100+ speakers)
- Theme-aware UI

---

## Technical Architecture

```
User Text
    │
    ├─── Kokoro Engine (KokoroEngine.java)
    │         └── Sherpa-ONNX JNI → ONNX Runtime → CPU/NNAPI
    │                   └── kokoro-multi-lang-v1_0 (82M params, FP32)
    │
    └─── Piper / VITS Engine (VoiceEngine.java)
              └── Sherpa-ONNX JNI → ONNX Runtime → CPU
                        └── VITS model (language-specific)
```

**Built with:**
- [Sherpa-ONNX](https://github.com/k2-fsa/sherpa-onnx) — on-device neural inference
- [Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M) — multilingual neural TTS model
- [Piper](https://github.com/rhasspy/piper) — fast local TTS
- Android AudioTrack API — low-latency PCM playback

---

## Performance

Generation speed depends entirely on your device's processor:

| Device Tier | Kokoro | Piper |
|-------------|--------|-------|
| 🟢 Flagship (Snapdragon 8 Gen 3) | ~20–40 sec/min audio | ~5 sec/min audio |
| 🟡 Mid-range (8-core) | ~60–90 sec/min audio | ~10 sec/min audio |
| 🔴 Budget (6-core) | ~2–3 min/min audio | ~20 sec/min audio |

> Kokoro prioritizes **quality over speed** by design. It uses the same 82M parameter architecture that powers premium commercial TTS — running it entirely offline on a mobile CPU is genuinely pushing the hardware limits.

---

## Installation

### 🧪 Help Me Reach Google Play — Join the Beta!

I've submitted **VoxSherpa TTS V2.1** to Google Play, but according to Play Store rules, I need at least **12 testers for 14 days** before I can publish to production.

If you find this project useful and want early access to V2.1 — I'd really appreciate your help. All you need to do is install the app and keep it for 14 days. You don't have to do anything else.

**What's new in V2.4:**
- 🔊 System-wide TTS engine — use VoxSherpa in any app (Chrome, WhatsApp, etc.)
- 📄 PDF to Audio
- 📑 TXT to Audio

**How to join:**
1. Fill out the form below with your Gmail
2. I'll add you manually to the closed test
3. You'll receive a Play Store opt-in link

[![Join Beta](https://img.shields.io/badge/Join-Beta%20v2.1-brightgreen?style=for-the-badge&logo=android&logoColor=white)](https://docs.google.com/forms/d/e/1FAIpQLSd-_VgF3DlbiG2eU00Dw_cZECMinUIuSx_Nr6NJt1DeUcIwTQ/viewform?usp=publish-editor)

> Source code for V2.0 and V2.1 will be pushed to GitHub after beta testing is complete.

## Model Import (Technical Users)

VoxSherpa supports importing custom `.onnx` models without any server:

1. Place your `.onnx` model + `tokens.txt` on device storage
2. Open **Models tab** → tap **+** → **Import Local Model**
3. Select your files

Compatible with any Sherpa-ONNX compatible TTS model.

---

## Contributing

VoxSherpa is open source. Contributions welcome:

- 🐛 Bug reports via [Issues](../../issues)
- 💡 Feature requests via [Discussions](../../discussions)
- 🔧 Pull requests for fixes and improvements

---

## License

```
Copyright (C) 2025 CodeBySonu95

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

https://www.gnu.org/licenses/gpl-3.0.html
```

---

## Acknowledgements

- [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) — the inference engine that makes this possible
- [hexgrad/Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M) — the neural model behind studio-quality synthesis
- [rhasspy/piper](https://github.com/rhasspy/piper) — fast local TTS engine

---

<div align="center">

**Built with obsession. Runs without internet.**

*VoxSherpa — Because your voice deserves to stay yours.*

</div>
