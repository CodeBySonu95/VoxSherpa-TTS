# VoxSherpa TTS — Documentation

Welcome to the VoxSherpa TTS documentation folder.

## Contents

| File | Description |
|------|-------------|
| [VERSIONS.md](./VERSIONS.md) | GitHub vs Play Store edition differences & version policy |
| [CHANGELOG.md](./CHANGELOG.md) | Full version history and release notes |

## ⏳ GitHub Version Notice

If a feature you saw in the Play Store version is missing here — **don't worry, it's coming.**

The GitHub (open source) version is intentionally kept a few versions behind the Play Store release. New features are developed and stabilized on the Play Store version first, then the source code is pushed to GitHub after a short delay.

> **Just give it some time — the code will be here soon.**

This gap exists because:
- Play Store releases fund continued development
- Features need to be stable before open sourcing
- Solo developer — things take a little time!

If you're waiting on a specific feature, feel free to open an [Issue](https://github.com/CodeBySonu95/VoxSherpa/issues) and ask. No promises on timing, but your interest is noted.

## 🛠️ Model Conversion Guide: Piper to Sherpa-onnx

If you have Piper TTS models that do not include a `tokens.txt` file, you can easily make them Sherpa-onnx ready using our automated Python script on Google Colab:

1. Open [Google Colab](https://colab.research.google.com/) and create a new notebook.
2. Copy the conversion script from our Hugging Face repository: [sample.py](https://huggingface.co/CodeBySonu95/VoxSherpa-TTS/resolve/main/sample.py).
3. Paste the code into your notebook (or let Colab's Gemini assistant run it for you).
4. Click the **Play** icon to execute. The script will convert and automatically download the Sherpa-onnx ready `.onnx` and token files for VoxSherpa TTS.

Need help or have questions? Email us at **[codebysonu95@gmail.com](mailto:codebysonu95@gmail.com)**.

---

## About VoxSherpa TTS

VoxSherpa is an offline Text-to-Speech app for Android, powered by [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx). It supports multiple TTS engines including Kokoro and Piper VITS models — all running locally on your device, with no internet required for speech synthesis.

- **No cloud. No data sent. No subscription.**
- Works fully offline after model download
- Multiple voices and languages supported

---

*Developer: [CodeBySonu](https://github.com/CodeBySonu95)*
