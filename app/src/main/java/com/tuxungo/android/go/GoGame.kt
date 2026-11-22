package com.tuxungo.android.go

enum class Stone {
    BLACK, WHITE;

    fun other(): Stone = if (this == BLACK) WHITE else BLACK
}

data class BoardSize(val width: Int, val height: Int) {
    init {
        require(width in 2..25 && height in 2..25) { "Board size must be between 2 and 25 per side" }
    }

    val area: Int get() = width * height
    val isSquare: Boolean get() = width == height
}

data class Move(val stone: Stone, val x: Int, val y: Int) {
    val isPass: Boolean get() = x < 0
    companion object {
        fun pass(stone: Stone) = Move(stone, -1, -1)
    }
}

data class ScoreResult(
    val blackStones: Int,
    val blackTerritory: Int,
    val whiteStones: Int,
    val whiteTerritory: Int,
    val komi: Double
) {
    val blackTotal: Double get() = (blackStones + blackTerritory).toDouble()
    val whiteTotal: Double get() = whiteStones + whiteTerritory + komi
    /** Positive means Black leads. */
    val lead: Double get() = blackTotal - whiteTotal
    val winner: Stone get() = if (lead > 0) Stone.BLACK else Stone.WHITE
}

/**
 * Pure-Kotlin Go rules core: captures, suicide ban, positional superko,
 * passing, resignation, undo and Chinese scoring. No Android dependencies,
 * so it is fully unit-testable.
 */
class GoGame(val size: BoardSize, val komi: Double = 7.5) {

    private val board = arrayOfNulls<Stone>(size.area)

    var current: Stone = Stone.BLACK
        private set

    /** Stones captured BY Black / BY White. */
    var capturesByBlack: Int = 0
        private set
    var capturesByWhite: Int = 0
        private set

    var consecutivePasses: Int = 0
        private set

    var finished: Boolean = false
        private set

    var resignedBy: Stone? = null
        private set

    var timeoutBy: Stone? = null
        private set

    private val _moves = mutableListOf<Move>()
    val moves: List<Move> get() = _moves

    var handicapStones: List<Pair<Int, Int>> = emptyList()
        private set

    private data class UndoEntry(
        val move: Move,
        val captured: List<Int>,
        val prevPasses: Int
    )

    private val undoStack = mutableListOf<UndoEntry>()
    private val seenPositions = mutableSetOf<String>()

    init {
        seenPositions.add(positionKey(board))
    }

    fun index(x: Int, y: Int): Int = y * size.width + x
    fun inBounds(x: Int, y: Int): Boolean = x in 0 until size.width && y in 0 until size.height
    fun stoneAt(x: Int, y: Int): Stone? = if (inBounds(x, y)) board[index(x, y)] else null
    fun lastMove(): Move? = _moves.lastOrNull { !it.isPass }

    private fun neighbors(idx: Int): List<Int> {
        val x = idx % size.width
        val y = idx / size.width
        val out = ArrayList<Int>(4)
        if (x > 0) out.add(idx - 1)
        if (x < size.width - 1) out.add(idx + 1)
        if (y > 0) out.add(idx - size.width)
        if (y < size.height - 1) out.add(idx + size.width)
        return out
    }

    /** Flood fill the group containing [start]; returns group stones and liberty count. */
    private fun groupAndLiberties(b: Array<Stone?>, start: Int): Pair<Set<Int>, Int> {
        val color = b[start] ?: return emptySet<Int>() to 0
        val group = mutableSetOf<Int>()
        val liberties = mutableSetOf<Int>()
        val stack = ArrayDeque<Int>()
        stack.add(start)
        group.add(start)
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            for (n in neighbors(cur)) {
                val s = b[n]
                if (s == null) liberties.add(n)
                else if (s == color && group.add(n)) stack.add(n)
            }
        }
        return group to liberties.size
    }

    private fun positionKey(b: Array<Stone?>): String {
        val sb = StringBuilder(size.area)
        for (s in b) sb.append(if (s == null) '.' else if (s == Stone.BLACK) 'B' else 'W')
        return sb.toString()
    }

    /** Places handicap stones for standard square boards. Returns false when unsupported. */
    fun setupHandicap(count: Int): Boolean {
        if (_moves.isNotEmpty() || handicapStones.isNotEmpty()) return false
        if (!size.isSquare) return false
        val points = handicapPoints(size.width, count) ?: return false
        for ((x, y) in points) board[index(x, y)] = Stone.BLACK
        handicapStones = points
        current = Stone.WHITE
        seenPositions.clear()
        seenPositions.add(positionKey(board))
        return true
    }

    /**
     * Loads an arbitrary start position (from the board editor). Returns
     * false — leaving the game untouched — when stones overlap, sit out
     * of bounds, or any group has no liberties (such a position cannot
     * occur in a real game and KataGo would reject it too).
     */
    fun loadPosition(stones: List<Move>, toMove: Stone): Boolean {
        if (_moves.isNotEmpty() || handicapStones.isNotEmpty()) return false
        val next = arrayOfNulls<Stone>(size.area)
        for (s in stones) {
            if (s.isPass || !inBounds(s.x, s.y)) return false
            val idx = index(s.x, s.y)
            if (next[idx] != null) return false
            next[idx] = s.stone
        }
        // Every group must have at least one liberty.
        val seen = BooleanArray(size.area)
        for (i in next.indices) {
            if (next[i] == null || seen[i]) continue
            val (group, libs) = groupAndLiberties(next, i)
            for (g in group) seen[g] = true
            if (libs == 0) return false
        }
        next.copyInto(board)
        current = toMove
        seenPositions.clear()
        seenPositions.add(positionKey(board))
        return true
    }

    fun isLegal(x: Int, y: Int): Boolean {
        if (finished || !inBounds(x, y)) return false
        return simulate(x, y) != null
    }

    /** Returns the board after playing at (x,y), or null when the move is illegal. */
    private fun simulate(x: Int, y: Int): Array<Stone?>? {
        val idx = index(x, y)
        if (board[idx] != null) return null
        val next = board.copyOf()
        next[idx] = current
        val enemy = current.other()
        // Remove captured enemy groups.
        val checked = mutableSetOf<Int>()
        for (n in neighbors(idx)) {
            if (next[n] == enemy && n !in checked) {
                val (group, libs) = groupAndLiberties(next, n)
                checked.addAll(group)
                if (libs == 0) for (g in group) next[g] = null
            }
        }
        // Suicide is illegal.
        val (_, ownLibs) = groupAndLiberties(next, idx)
        if (ownLibs == 0) return null
        // Positional superko: the resulting position must be new.
        if (positionKey(next) in seenPositions) return null
        return next
    }

    fun play(x: Int, y: Int): Boolean {
        if (finished || !inBounds(x, y)) return false
        val next = simulate(x, y) ?: return false
        val captured = mutableListOf<Int>()
        for (i in board.indices) {
            if (board[i] != null && next[i] == null) captured.add(i)
        }
        val mover = current
        next.copyInto(board)
        if (mover == Stone.BLACK) capturesByBlack += captured.size else capturesByWhite += captured.size
        val move = Move(mover, x, y)
        _moves.add(move)
        undoStack.add(UndoEntry(move, captured, consecutivePasses))
        consecutivePasses = 0
        seenPositions.add(positionKey(board))
        current = current.other()
        return true
    }

    fun pass(): Boolean {
        if (finished) return false
        val move = Move.pass(current)
        _moves.add(move)
        undoStack.add(UndoEntry(move, emptyList(), consecutivePasses))
        consecutivePasses++
        current = current.other()
        // 2.64.0: two passes in a row no longer finish the game
        // (the user asked for that auto-finish to be removed). The
        // counter stays for the AI-vs-AI path in GameScreen, which
        // still needs a way to wrap a game up.
        return true
    }

    fun resign(stone: Stone = current) {
        if (finished) return
        resignedBy = stone
        finished = true
    }

    /** Ends the game: [stone] ran out of time (main time + byoyomi). */
    fun timeout(stone: Stone = current) {
        if (finished) return
        timeoutBy = stone
        finished = true
    }

    /** Ends the game for scoring (both sides agree the game is over). */
    fun finishScoring() {
        if (finished) return
        finished = true
    }

    /** Dead stones agreed for the final score (see [finishScoringWith]). */
    var scoringDead: Set<Int>? = null
        private set

    /** Finish the game, scoring with [dead] stones removed as captured.
     * Also allowed on a game that already finished by passes (where
     * no dead-stone agreement exists yet), so the judged count can
     * replace the raw count after the fact. */
    fun finishScoringWith(dead: Set<Int>) {
        if (finished && scoringDead != null) return
        if (finished && (resignedBy != null || timeoutBy != null)) return
        scoringDead = dead
        finished = true
    }

    /** Indices of the connected same-color group containing (x, y). */
    fun groupAt(x: Int, y: Int): Set<Int> {
        if (!inBounds(x, y)) return emptySet()
        val start = index(x, y)
        if (board[start] == null) return emptySet()
        return groupAndLiberties(board, start).first
    }

    /**
     * Chinese scoring with the [dead] stones treated as removed: their
     * points become empty and join the surrounding territory regions.
     */
    fun scoreWithDead(dead: Set<Int>): ScoreResult {
        if (dead.isEmpty()) return score()
        val eff = Array(size.area) { i -> if (i in dead) null else board[i] }
        var blackStones = 0
        var whiteStones = 0
        var blackTerritory = 0
        var whiteTerritory = 0
        val visited = BooleanArray(size.area)
        for (i in eff.indices) {
            val s = eff[i]
            if (s == Stone.BLACK) blackStones++
            else if (s == Stone.WHITE) whiteStones++
            else if (!visited[i]) {
                val region = mutableListOf<Int>()
                val borders = mutableSetOf<Stone>()
                val stack = ArrayDeque<Int>()
                stack.add(i)
                visited[i] = true
                while (stack.isNotEmpty()) {
                    val cur = stack.removeLast()
                    region.add(cur)
                    for (n in neighbors(cur)) {
                        val ns = eff[n]
                        if (ns == null && !visited[n]) {
                            visited[n] = true
                            stack.add(n)
                        } else if (ns != null) borders.add(ns)
                    }
                }
                if (borders.size == 1) {
                    if (borders.first() == Stone.BLACK) blackTerritory += region.size
                    else whiteTerritory += region.size
                }
            }
        }
        return ScoreResult(blackStones, blackTerritory, whiteStones, whiteTerritory, komi)
    }

    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false
        val entry = undoStack.removeAt(undoStack.lastIndex)
        _moves.removeAt(_moves.lastIndex)
        finished = false
        resignedBy = null
        timeoutBy = null
        consecutivePasses = entry.prevPasses
        current = entry.move.stone
        if (!entry.move.isPass) {
            board[index(entry.move.x, entry.move.y)] = null
            val enemy = entry.move.stone.other()
            for (idx in entry.captured) board[idx] = enemy
            if (entry.move.stone == Stone.BLACK) capturesByBlack -= entry.captured.size
            else capturesByWhite -= entry.captured.size
            // The position we return to stays in seenPositions (it was seen);
            // the position we leave is forgotten so it can be reached again.
            seenPositions.remove(positionKeyAfterUndo(entry))
        }
        return true
    }

    private fun positionKeyAfterUndo(entry: UndoEntry): String {
        // Rebuild the key of the position as it was right after the undone move:
        // the played stone present, the captured stones absent.
        val copy = board.copyOf()
        copy[index(entry.move.x, entry.move.y)] = entry.move.stone
        for (idx in entry.captured) copy[idx] = null
        return positionKey(copy)
    }

    /** Chinese scoring: stones on board + surrounded empty territory, plus komi for White. */
    fun score(): ScoreResult {
        var blackStones = 0
        var whiteStones = 0
        var blackTerritory = 0
        var whiteTerritory = 0
        val visited = BooleanArray(size.area)
        for (i in board.indices) {
            val s = board[i]
            if (s == Stone.BLACK) blackStones++
            else if (s == Stone.WHITE) whiteStones++
            else if (!visited[i]) {
                // Flood fill one empty region and note which colors border it.
                val region = mutableListOf<Int>()
                val borders = mutableSetOf<Stone>()
                val stack = ArrayDeque<Int>()
                stack.add(i)
                visited[i] = true
                while (stack.isNotEmpty()) {
                    val cur = stack.removeLast()
                    region.add(cur)
                    for (n in neighbors(cur)) {
                        val ns = board[n]
                        if (ns == null && !visited[n]) {
                            visited[n] = true
                            stack.add(n)
                        } else if (ns != null) borders.add(ns)
                    }
                }
                if (borders.size == 1) {
                    if (borders.first() == Stone.BLACK) blackTerritory += region.size
                    else whiteTerritory += region.size
                }
            }
        }
        return ScoreResult(blackStones, blackTerritory, whiteStones, whiteTerritory, komi)
    }

    companion object {
        /** Standard handicap point coordinates (0-based) for 9/13/19 square boards. */
        fun handicapPoints(boardSide: Int, count: Int): List<Pair<Int, Int>>? {
            if (count !in 2..9) return null
            val coords: Triple<Int, Int, Int> = when (boardSide) {
                19 -> Triple(3, 9, 15)
                13 -> Triple(3, 6, 9)
                9 -> Triple(2, 4, 6)
                else -> return null
            }
            val (lo, mid, hi) = coords
            val corners = listOf(lo to lo, hi to hi, hi to lo, lo to hi)
            val center = mid to mid
            val edgeMids = listOf(lo to mid, hi to mid, mid to lo, mid to hi)
            return when (count) {
                2 -> corners.take(2)
                3 -> corners.take(3)
                4 -> corners
                5 -> corners + center
                6 -> corners + edgeMids.take(2)
                7 -> corners + edgeMids.take(2) + center
                8 -> corners + edgeMids
                9 -> corners + edgeMids + center
                else -> null
            }
        }
    }
}
