# Contributing to VoxSherpa TTS

Thanks for your interest in contributing! VoxSherpa TTS is an open-source project and contributions are welcome — but please read this guide carefully before opening a PR.

---

## What You Can Contribute

### ✅ Contributions Welcome

- Engine classes — `KokoroEngine.java`, `VoiceEngine.java`, `Sonic.java`
- Helper classes — `KokoroVoiceHelper.java`, `AudioEmotionHelper.java`, `TtsLocaleHelper.java`, etc.
- Bug fixes in utility or helper files
- `engine-lib` module improvements
- Documentation and README updates
- New language or model support
- Performance improvements in engine/helper layer

### ❌ Please Do Not Modify

- Activities — any `*Activity.java` file
- Fragments — any `*Fragment.java` file
- `app/build.gradle` (unless discussed first in an issue)

> PRs that modify Activities or Fragments will not be merged. These files are tightly coupled to the app's internal structure and are managed separately.

---

## Branch Targets

| Contribution Type | Target Branch |
|---|---|
| Engine / helper bug fixes | `main` |
| New features (engine layer) | `alpha` |
| AAR / library module work | `engine-lib` |

When in doubt, open an issue first and ask.

---

## Before Opening a PR

1. **Open an issue first** for any non-trivial change — discuss before you code
2. Make sure `./gradlew :app:assembleDebug` builds clean
3. Test on a real Android device if possible
4. Keep PRs focused — one fix or feature per PR
5. Squash commits if possible — clean history is appreciated

---

## Reporting Bugs

Open an [Issue](../../issues) with:

- Device model and Android version
- VoxSherpa version
- Steps to reproduce
- Expected vs actual behavior
- Logcat output if available (crash reports especially)

---

## engine-lib Module

The `engine-lib` module exposes VoxSherpa's engine classes as a reusable AAR for downstream Android apps via JitPack:

```kotlin
implementation("com.github.CodeBySonu95:VoxSherpa-TTS:v2.9.1")
```

PRs for this module should target the `engine-lib` branch.

> **Current status:** The primary focus right now is the VoxSherpa app itself. The `engine-lib` module is not actively maintained or tested by the core team at this time. It is provided as-is for downstream consumers who want to build on top of the engine layer.
>
> That said, improving `engine-lib` into a stable, well-documented library is a goal for the future. Community contributions to this module are very welcome in the meantime.

---

## License

By contributing, you agree that your contributions will be licensed under the [GNU GPL v3.0](LICENSE).

---

*VoxSherpa TTS is maintained by [CodeBySonu95](https://github.com/CodeBySonu95).*
