<div align="center">

# TuxunGo

**A free, open-source, fully offline Android Go app powered by KataGo**

English | [简体中文](README.md)

[![GitHub release](https://img.shields.io/github/v/release/liyuzhou79-star/TuxunGo)](https://github.com/liyuzhou79-star/TuxunGo/releases)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
![Platform](https://img.shields.io/badge/platform-Android%2010%2B-green)

No accounts, no ads, no network needed: play against the AI, watch AI
vs AI games, or play a friend on the same phone.

</div>

## Features

### Play

- **Three modes**: human vs AI, AI vs AI, human vs human (same device)
- **AI hosting**: hand either color — or both — to the AI at any moment, each side with its **own model and strength** (e.g. the built-in net on one side, a big imported net on the other), switchable mid-game
- **Free-form boards**: 5–25 points per side, rectangular boards included; handicap and komi are free numeric inputs
- **Position editor**: set up any position by hand, then start from it with either color
- **Fullscreen mode**: board at maximum size with just analysis controls — ideal for spectating AI games
- **Endgame scoring**: the engine proposes dead stones (marked with red crosses), tap whole groups to flip them, then confirm the score

### Analysis & review

- **AI analysis**: candidate moves ranked on the board with the top choice unmistakable; win-rate bar, score lead, territory estimate, principal-variation ghosts
- **Position judgment**: one tap for territory, dead stones and a verdict
- **Full-game review**: per-move win-rate and score-lead chart with scrubbable replay; **blunders, brilliant moves and the single biggest swing** are marked automatically; stretches above 75% win rate are shaded green; black/white perspective switch; continue the game from any move
- **Background pre-computation**: while you play, every position's stats are quietly recorded — the post-game review opens (nearly) instantly
- **Annotated SGF export**: copy a review as SGF with per-move win-rate comments

### Game library

- Folders for your saved games: AI games, human games and custom-setup games alike
- Finished games are auto-saved to an "Auto-saved" folder, so a game is never lost for forgetting to collect it
- Tap any record to review it; analysis results are cached

### Model library

- Ships with a 10-block KataGo net, ready out of the box
- Import your own KataGo models (`.txt.gz` / `.bin.gz`, up to 99): integrity check, header parse and a real engine test-load — a broken file never enters the library
- Every model, including the built-in one, can be given a nickname

### Experience

- Real recorded stone sounds (synthesized alternatives included), placement vibration, four board skins, sakura-pink theme
- Games survive backgrounding and process death — pick up right where you left off
- **Fully offline**: engine and models live on the device; there is no server, no account, and nothing that needs a network

## Download

Grab the APK from [Releases](https://github.com/liyuzhou79-star/TuxunGo/releases) (Android 10+, arm64).

> Upgrading from the 0.1.0 test build to 1.0.0 requires one uninstall first (the signing key changed); every release after 1.0.0 upgrades in place.

## Stronger models (optional)

The built-in net is already capable. For more strength, download a net from the [official KataGo model library](https://katagotraining.org/networks/):

| Model | Size | Notes |
| --- | --- | --- |
| kata1-b18c384nbt | ~93 MB | Strong and comparatively light; best speed/strength balance on a phone |
| kata1-b28c512nbt | ~259 MB | Among the strongest mainstream nets; slower thinking on a phone |

Import the `.bin.gz` file in the app's model library, give it a name, then assign it to a side in the AI hosting panel and raise its strength.

> Do not download the newer `tf3` architecture nets — this engine version cannot load them, and the importer will reject them.

## Design principles

- Simple pages, no decorative clutter
- A small APK: one small built-in net; big nets are user-added and live in device storage
- Offline first, always

## Tech

- Kotlin + Jetpack Compose + Material 3
- [KataGo](https://github.com/lightvector/KataGo) v1.15.3 (MIT) compiled with the NDK, driven over GTP and the analysis protocol
- Pure-Kotlin rules engine (captures, suicide ban, positional superko, Chinese scoring) with unit tests

## Credits

- [KataGo](https://github.com/lightvector/KataGo) — engine and official models
- [Sabaki](https://github.com/SabakiHQ/Sabaki) — the "real recording" sound set (MIT, see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md))

## License

GPLv3, see [LICENSE](LICENSE). The KataGo engine and model files follow their own licenses.
