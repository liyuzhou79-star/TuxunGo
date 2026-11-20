package com.tuxungo.android.ui

import com.tuxungo.android.go.Move
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
    val aiVisits: Int = 200,
    /** Custom start position (from the board editor): stones pre-placed
     * before the first move. Empty for normal and handicap games. */
    val customStones: List<Move> = emptyList(),
    /** Side to move in a custom start position. */
    val startPlayer: Stone = Stone.BLACK,
    /** Per-side main time in minutes; 0 = no clock. When the main time
     * runs out, byoyomi starts: 3 periods of 30 seconds each. */
    val clockMinutes: Int = 0,
    /** Byoyomi periods per side once the main time runs out. */
    val byoyomiPeriods: Int = 3,
    /** EVE only: an AI-vs-AI series — first to this many wins takes
     * the series, colours swap after every game. 0 = single game. */
    val seriesTarget: Int = 0
)
