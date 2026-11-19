package com.tuxungo.android.ui

enum class GameMode { PVP, PVE, EVE }

data class GameConfig(
    val mode: GameMode,
    val width: Int,
    val height: Int,
    val handicap: Int = 0,
    val komi: Double = 7.5
)
