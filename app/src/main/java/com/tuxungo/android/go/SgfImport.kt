package com.tuxungo.android.go

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * SGF (FF[4]) import: parses SGF text into library-ready games.
 *
 * Scope, deliberately pragmatic: the main line of each game tree
 * (first variation), root-node game info (SZ / KM / HA / AB / AW /
 * PL / PB / PW / DT / RE), passes as [] or [tt]. Every game is
 * validated by replaying it on a real [GoGame]; when a move does
 * not replay (illegal point, colours out of order), the game is
 * truncated there and [ImportedGame.truncatedAt] says where —
 * better an honest partial record than a corrupt one.
 */
object SgfImport {

    data class ImportedGame(
        val title: String,
        val size: BoardSize,
        val komi: Double,
        val handicap: Int,
        val setupStones: List<Move>,
        val startPlayer: Stone,
        val moves: List<Move>,
        val resultText: String,
        val dateMillis: Long?,
        /** 1-based move number where replay stopped, null if whole. */
        val truncatedAt: Int?
    )

    fun parseAll(text: String): List<ImportedGame> {
        if (text.isBlank()) return emptyList()
        return parseTrees(text).mapNotNull { extract(it) }
    }

    /** "B+7.5" -> "黑方胜 7.5 子", "W+R" -> "白方胜（认输）", etc. */
    fun friendlyResult(re: String): String {
        val t = re.trim()
        if (t.isEmpty()) return ""
        if (t == "0" || t.equals("Draw", true)) return "和棋"
        if (t.equals("Void", true)) return "无结果"
        val black = t.startsWith("B+")
        val white = t.startsWith("W+")
        if (!black && !white) return t
        val who = if (black) "黑方胜" else "白方胜"
        val rest = t.substring(2).trim()
        return when {
            rest.equals("R", true) || rest.equals("Resign", true) ->
                "$who（认输）"
            rest.equals("T", true) || rest.equals("Time", true) ->
                "$who（超时）"
            rest.equals("F", true) || rest.equals("Forfeit", true) ->
                "$who（弃权）"
            rest.toDoubleOrNull() != null -> "$who $rest 子"
            else -> who
        }
    }

    // ---------------------------------------------------------------
    // Tree parsing
    // ---------------------------------------------------------------

    private class Node {
        val props = LinkedHashMap<String, MutableList<String>>()
    }

    private class Tree {
        val nodes = mutableListOf<Node>()
        val children = mutableListOf<Tree>()
    }

    private class Cursor(val text: String) {
        var i = 0
        fun eof() = i >= text.length
        fun peek() = text[i]
        fun next() = text[i++]
    }

    private fun skipWs(c: Cursor) {
        while (!c.eof() && c.peek().isWhitespace()) c.next()
    }

    private fun parseTrees(text: String): List<Tree> {
        val c = Cursor(text)
        val roots = mutableListOf<Tree>()
        while (true) {
            skipWs(c)
            if (c.eof()) break
            if (c.peek() == '(') {
                c.next()
                roots.add(parseTree(c))
            } else {
                c.next()
            }
        }
        return roots
    }

    private fun parseTree(c: Cursor): Tree {
        val tree = Tree()
        while (true) {
            skipWs(c)
            if (c.eof()) break
            when (c.peek()) {
                ')' -> {
                    c.next()
                    break
                }
                '(' -> {
                    c.next()
                    tree.children.add(parseTree(c))
                }
                ';' -> {
                    c.next()
                    tree.nodes.add(parseNode(c))
                }
                else -> c.next()
            }
        }
        return tree
    }

    private fun parseNode(c: Cursor): Node {
        val node = Node()
        while (true) {
            skipWs(c)
            if (c.eof() || c.peek() == ';' || c.peek() == '(' || c.peek() == ')') {
                break
            }
            val id = StringBuilder()
            while (!c.eof() && c.peek().isLetter()) id.append(c.next())
            if (id.isEmpty()) {
                c.next()
                continue
            }
            val values = mutableListOf<String>()
            while (true) {
                skipWs(c)
                if (c.eof() || c.peek() != '[') break
                c.next()
                val v = StringBuilder()
                while (!c.eof()) {
                    val ch = c.next()
                    if (ch == '\\' && !c.eof()) {
                        v.append(c.next())
                    } else if (ch == ']') {
                        break
                    } else {
                        v.append(ch)
                    }
                }
                values.add(v.toString())
            }
            if (values.isNotEmpty()) {
                node.props.getOrPut(id.toString().uppercase()) { mutableListOf() }
                    .addAll(values)
            }
        }
        return node
    }

    private fun mainLine(tree: Tree): List<Node> {
        val out = mutableListOf<Node>()
        var t: Tree? = tree
        while (t != null) {
            out.addAll(t.nodes)
            t = t.children.firstOrNull()
        }
        return out
    }

    // ---------------------------------------------------------------
    // Game extraction
    // ---------------------------------------------------------------

    private fun coordVal(ch: Char): Int = when (ch) {
        in 'a'..'z' -> ch - 'a'
        in 'A'..'Z' -> 26 + (ch - 'A')
        else -> -1
    }

    /** Board point for a value, or null for pass / out-of-board. */
    private fun point(v: String, size: BoardSize): Pair<Int, Int>? {
        val t = v.trim()
        if (t.length != 2) return null
        if (t == "tt" && size.width <= 19 && size.height <= 19) return null
        val x = coordVal(t[0])
        val y = coordVal(t[1])
        if (x < 0 || y < 0 || x >= size.width || y >= size.height) return null
        return x to y
    }

    private fun isPassValue(v: String, size: BoardSize): Boolean {
        val t = v.trim()
        return t.isEmpty() || (t == "tt" && size.width <= 19 && size.height <= 19)
    }

    private fun extract(tree: Tree): ImportedGame? {
        val line = mainLine(tree)
        if (line.isEmpty()) return null
        val root = line.first()
        fun prop(node: Node, key: String) = node.props[key]?.firstOrNull()

        var w = 19
        var h = 19
        prop(root, "SZ")?.let {
            val parts = it.trim().split(':')
            w = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 19
            h = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: w
        }
        if (w !in 2..25 || h !in 2..25) return null
        val size = BoardSize(w, h)
        val komi = prop(root, "KM")?.trim()?.toDoubleOrNull() ?: 7.5
        val handicap = prop(root, "HA")?.trim()?.toIntOrNull() ?: 0

        // Setup stones and PL live before the first move node.
        val setup = mutableListOf<Move>()
        var pl: Stone? = null
        for (node in line) {
            if (node.props.containsKey("B") || node.props.containsKey("W")) break
            node.props["AB"]?.forEach { v ->
                point(v, size)?.let { setup.add(Move(Stone.BLACK, it.first, it.second)) }
            }
            node.props["AW"]?.forEach { v ->
                point(v, size)?.let { setup.add(Move(Stone.WHITE, it.first, it.second)) }
            }
            node.props["AE"]?.forEach { v ->
                point(v, size)?.let { pt ->
                    setup.removeAll { it.x == pt.first && it.y == pt.second }
                }
            }
            prop(node, "PL")?.let {
                pl = if (it.trim().startsWith("W", true)) Stone.WHITE else Stone.BLACK
            }
        }

        // Moves, in main-line order. An unreadable coordinate stops
        // the collection and counts as the truncation point.
        val rawMoves = mutableListOf<Move>()
        var badCoordAt: Int? = null
        moveLoop@ for (node in line) {
            for ((key, stone) in listOf("B" to Stone.BLACK, "W" to Stone.WHITE)) {
                val v = node.props[key]?.firstOrNull() ?: continue
                if (isPassValue(v, size)) {
                    rawMoves.add(Move.pass(stone))
                } else {
                    val pt = point(v, size)
                    if (pt == null) {
                        badCoordAt = rawMoves.size + 1
                        break@moveLoop
                    }
                    rawMoves.add(Move(stone, pt.first, pt.second))
                }
            }
        }

        val startPlayer = pl ?: when {
            rawMoves.isNotEmpty() -> rawMoves.first().stone
            setup.any { it.stone == Stone.BLACK } &&
                setup.none { it.stone == Stone.WHITE } -> Stone.WHITE
            else -> Stone.BLACK
        }

        // Validate by replaying on a real game.
        val game = GoGame(size, komi)
        if (setup.isNotEmpty() && !game.loadPosition(setup, startPlayer)) {
            return null
        }
        val played = mutableListOf<Move>()
        var truncatedAt: Int? = null
        for ((idx, m) in rawMoves.withIndex()) {
            val good = if (game.current != m.stone) {
                false
            } else if (m.isPass) {
                game.pass()
            } else {
                game.play(m.x, m.y)
            }
            if (!good) {
                truncatedAt = idx + 1
                break
            }
            played.add(m)
        }
        if (truncatedAt == null && badCoordAt != null) {
            truncatedAt = badCoordAt
        }

        val pb = prop(root, "PB")?.trim().orEmpty()
        val pw = prop(root, "PW")?.trim().orEmpty()
        val title = when {
            pb.isNotEmpty() && pw.isNotEmpty() -> "$pb vs $pw"
            pb.isNotEmpty() -> pb
            pw.isNotEmpty() -> pw
            else -> "导入的棋谱"
        }
        val dateMillis = prop(root, "DT")?.trim()?.take(10)?.let {
            runCatching {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it)?.time
            }.getOrNull()
        }
        return ImportedGame(
            title = title,
            size = size,
            komi = komi,
            handicap = handicap,
            setupStones = setup,
            startPlayer = startPlayer,
            moves = played,
            resultText = friendlyResult(prop(root, "RE").orEmpty()),
            dateMillis = dateMillis,
            truncatedAt = truncatedAt
        )
    }
}
