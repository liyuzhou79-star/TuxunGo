# Third-party notices

## Sabaki sound recordings

The "real recording" stone placement and capture sounds in
`app/src/main/res/raw/snd_real_*.wav` are converted from the sound
files of [Sabaki](https://github.com/SabakiHQ/Sabaki), an open-source
Go board application, and are used under the MIT License.

Copyright (c) 2015-2020 Yichuan Shen

The full license text is in `third_party_sabaki_LICENSE.md`.

## KataGo

The AI engine is [KataGo](https://github.com/lightvector/KataGo)
(MIT License), compiled from source in CI. The bundled neural network
is distributed by the KataGo training project.

## Board face texture

The board face textures (`assets/board/kaya_real.jpg` and
`assets/board/beige.jpg`) are derived from "Oak Veneer 01" by
Jenelle van Heerden, published by
[Poly Haven](https://polyhaven.com/a/oak_veneer_01) under the CC0
1.0 Universal public domain dedication. They were resized,
recompressed and (for the beige variant) recoloured for use as a
board surface; the grid and stones are drawn by the app itself.

## Calligraphy font (「绝」 badge)

The calligraphy 「绝」 and 「恶」 shown on the sealing move's and
the deliberate-blunder moves' stones are set in
[Ma Shan Zheng](https://fonts.google.com/specimen/Ma+Shan+Zheng),
a Chinese calligraphy typeface by the Ma Shan Zheng Project
Authors, licensed under the SIL Open Font License 1.1. Only the
「绝」 and 「恶」 glyphs are bundled (`res/font/jue_calligraphy.ttf`,
subset with fonttools); the full font is not included.
