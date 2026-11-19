# TuxunGo

[中文](README.md) | English

**TuxunGo is a free and open-source Android Go (Weiqi / Baduk) app powered by the KataGo engine.**

No account, no ads, no network required: play against the AI, watch AI-vs-AI games, or play a local two-player game on one device.

> Current version: 0.1.0 (in development, no release builds yet)

## Planned features (0.1.0)

- **Three game modes**: Human vs AI, AI vs AI, and local Human vs Human
- **Move recommendations**: candidate moves for every position, with the top choice clearly marked, plus win rate and score lead for each candidate
- **Position judgment**: live win-rate bar, score estimate and territory map
- **Fully custom board sizes**: width and height can be set independently; unusual sizes are marked as experimental
- **Your own AI models**: upload KataGo weight files (.bin.gz); the app validates them itself and manages them in a model library
- **Game records**: games are saved locally as SGF files

## Principles

- Simple pages, no decorative clutter
- A small installer: one small built-in model so the app works out of the box; larger models are added by the user and stored on the device, not in the APK
- Fully offline

## Tech

- Kotlin + Jetpack Compose + Material 3
- AI engine: [KataGo](https://github.com/lightvector/KataGo) (MIT license), built with the NDK and bundled in the app
- Targets modern Android (Android 10 and above)

## License

GPLv3, see [LICENSE](LICENSE). The KataGo engine and its model files follow their own licenses.
