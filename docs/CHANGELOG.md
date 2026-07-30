# Changelog — VoxSherpa TTS

All notable changes to VoxSherpa TTS are documented here.

> **Note:** Versions 3.1 through 3.9 were skipped entirely due to a critical technical issue discovered during development. These versions were never publicly released. Development resumed at v4.0 with the Dialogue Engine Update.


---

## [4.15] — Android 11+ Update

- Minimum SDK updated to 30 (Android 11+)
- Resolved ANR (Application Not Responding) issues
- Improved system stability

---

## [4.14] — Performance & ANR Fixes

- Fixed critical ANR issues
- Reduced ad frequency for better user experience

---

## [4.13] — System TTS Fixes

- Fixed Android System TTS engine compatibility issues
- Improved background speech synthesis stability

---

## [4.12] — Supertonic V3 & EPUB Reader

- Added Supertonic V3 engine support
- Voice clones now work seamlessly as Android System TTS
- Improved EPUB reading & playback support
- Added direct website link for online EPUB library
- Fixed various app crashes

---

## [4.11] — Library Fixes

- Resolved crashes related to the local speech library
- Improved audio library file handling

---

## [4.10] — Testing Release

- Minimum SDK updated to 25 for testing pipeline

---

## [4.9] — EPUB Support

- Added native EPUB file support for document reading
- Fixed custom Chinese model crash issue

---

## [4.8] — Website Integration & Online Library

- Connected to Online Library featuring 1,000+ Piper voice models
- Download and import models directly from the web library
- Fixed Chinese number pronunciation issues
- Various bug fixes and optimizations

---

## [4.7] — Voice Clone Beta

- Introduced Voice Cloning (Beta)
- Bug fixes and stability improvements

---

## [4.6] — Voice Cloning Initial Release

- Added Voice Cloning support (English language)

---

## [4.3 – 4.5] — Major Updates & Compliance

- Google Play Policy compliance updates
- Performance optimizations and minor bug fixes

---

## [4.2] — Experience Update

- UI/UX improvements across the app
- General bug fixes and stability enhancements

---

## [4.1] — Productivity Tools

- Text History tracker
- Speech-to-Text button integration
- Quick Paste button
- Clear Text button

---

## [4.0] — Dialogue Engine Update

- Added multi-speaker voice generation
- Piper now supports multiple speakers in a single script
- New `[speaker]` tag system for dialogue synthesis
- Voice Style & Tone controls
- Adjustable sentence gap / silence timing
- Improved conversational speech flow
- Enhanced script parsing engine
- Better natural pause handling
- UI refinements and stability improvements

---

## [3.0] — MMS Models Update

- Added MMS (Massively Multilingual Speech) voice model support
- Integrated 1,138+ MMS voice models
- Expanded language and voice coverage
- Improved overall model compatibility

---

## [2.9.1] — Chinese Text Crash Hotfix

- Fixed Kokoro engine crash on Chinese text input
- Improved multilingual character support
- Stability improvements

---

## [2.9] — System TTS Update

- Exposed all downloaded models to Android System TTS
- Bug fixes and optimizations

---

## [2.8] — Voice Samples

- System TTS upgrade
- Added voice sample preview for all models

---

## [2.7] — Filter & Share

- Filter voice models by category
- Share any text directly to VoxSherpa TTS via system share sheet

---

## [2.6] — Media Notification

- MediaStyle notification with full playback controls
- Pitch control in System TTS
- Speed control in System TTS
- Improved performance and stability
- Bug fixes and UI improvements

**Internal Changes:**
- Dropped 32-bit (x86/armeabi) support
- Minimum SDK updated (dropped Android 9 & 10 support)

---

## [2.5] — Stable Release

- Bug fixes and stability improvements
- Improved overall performance

---

## [2.4] — Stability Update

- Improved System TTS support with better language detection
- Enhanced UI and user experience
- Improved compatibility for large screen devices
- Bug fixes

---

## [2.3] — Playback Upgrade

- Interactive audio seeking
- New mini player controls
- Smoother and faster UI performance
- Fixed generation cancellation delay issue

---

## [2.2] — Core Improvements

- Regenerate audio on voice change
- Improved smart punctuation handling
- Enhanced emotion tags
- Added pitch control
- Added Send Feedback feature
- UI/UX improvements

---

## [1.0] — Foundation

Initial public release of VoxSherpa TTS.

- Text to Audio conversion
- Piper engine support (fast models)
- Kokoro engine support (high-quality voices)
- Save audio as `.wav`
- Favorites support
- Speed control
- Model download manager
- Import custom models
- Chunk-based playback
- Smart pause handling (punctuation-aware)
- System TTS integration
- PDF to Audio conversion

---

*Developer: [CodeBySonu](https://github.com/CodeBySonu95)*
