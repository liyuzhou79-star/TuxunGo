package com.tuxungo.android.ui

import com.tuxungo.android.go.Stone

enum class GameMode { PVP, PVE, EVE }

data class GameConfig(
    val mode: GameMode,
    val width: Int,
    val height: Int,
    val handicap: Int = 0,
    val komi: Double = 7.5,
    /** PVE only: which color the human plays. */
    val humanStone: Stone = Stone.BLACK,
    /** PVE only: KataGo search budget per move (difficulty). */
    val aiVisits: Int = 200
)
