package com.tuxungo.android

import android.content.Context
import com.tuxungo.android.go.BoardSize
import com.tuxungo.android.go.Move
import com.tuxungo.android.go.Stone
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One saved game in the library (also the in-memory review input). */
data class LibraryGame(
    val id: String,
    val folderId: String?,
    val title: String,
    val createdAt: Long,
    val modeLabel: String,
    val size: BoardSize,
    val komi: Double,
    val handicap: Int,
    val customStones: List<Move>,
    val startPlayer: Stone,
    val moves: List<Move>,
    val resultText: String,
    /** Cached full-game analysis ("wr,lead,bx,by;..."), if computed. */
    val statsCache: String?
)

data class LibraryFolder(val id: String, val name: String)

/**
 * Game library: folders + saved games as JSON files under
 * filesDir/library/. Any game (AI, human, custom setup) can be saved.
 */
object LibraryStore {

    private fun root(context: Context) = File(context.filesDir, "library").apply { mkdirs() }
    private fun gamesDir(context: Context) = File(root(context), "games").apply { mkdirs() }
    private fun foldersFile(context: Context) = File(root(context), "folders.json")

    // ---------- folders ----------

    fun listFolders(context: Context): List<LibraryFolder> {
        val f = foldersFile(context)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let {
                    LibraryFolder(it.optString("id"), it.optString("name"))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun ensureFolder(context: Context, name: String): LibraryFolder {
        return listFolders(context).firstOrNull { it.name == name }
            ?: createFolder(context, name)
    }

    fun createFolder(context: Context, name: String): LibraryFolder {
        val folder = LibraryFolder(
            id = "f" + System.currentTimeMillis().toString(36),
            name = name.trim().ifBlank { "未命名" }
        )
        val all = listFolders(context) + folder
        saveFolders(context, all)
        return folder
    }

    fun renameFolder(context: Context, folderId: String, newName: String) {
        val name = newName.trim().ifBlank { return }
        saveFolders(
            context,
            listFolders(context).map {
                if (it.id == folderId) it.copy(name = name) else it
            }
        )
    }

    fun deleteFolder(context: Context, folderId: String) {
        saveFolders(context, listFolders(context).filter { it.id != folderId })
        // Games in the folder become uncategorized, never deleted.
        for (g in listGames(context)) {
            if (g.folderId == folderId) writeGame(context, g.copy(folderId = null))
        }
    }

    private fun saveFolders(context: Context, folders: List<LibraryFolder>) {
        val arr = JSONArray()
        for (f in folders) {
            arr.put(JSONObject().put("id", f.id).put("name", f.name))
        }
        foldersFile(context).writeText(arr.toString())
    }

    // ---------- games ----------

    fun listGames(context: Context): List<LibraryGame> {
        val dir = gamesDir(context)
        val files = dir.listFiles { f -> f.extension == "json" } ?: return emptyList()
        return files.mapNotNull { readGame(it) }
            .sortedByDescending { it.createdAt }
    }

    fun saveGame(context: Context, game: LibraryGame): LibraryGame {
        val saved = if (game.id.isBlank()) {
            game.copy(id = "g" + System.currentTimeMillis().toString(36))
        } else {
            game
        }
        writeGame(context, saved)
        return saved
    }

    fun deleteGame(context: Context, id: String) {
        File(gamesDir(context), "$id.json").delete()
    }

    /** Save a copy of a record under a fresh id, titled with a 副本 suffix. */
    fun duplicateGame(context: Context, game: LibraryGame): LibraryGame {
        val copy = game.copy(
            id = "",
            title = game.title + "（副本）",
            createdAt = System.currentTimeMillis()
        )
        return saveGame(context, copy)
    }

    fun moveToFolder(context: Context, id: String, folderId: String?) {
        val g = listGames(context).firstOrNull { it.id == id } ?: return
        writeGame(context, g.copy(folderId = folderId))
    }

    fun renameGame(context: Context, id: String, title: String) {
        val g = listGames(context).firstOrNull { it.id == id } ?: return
        writeGame(context, g.copy(title = title))
    }

    fun updateStatsCache(context: Context, id: String, statsCache: String) {
        if (id.isBlank()) return
        val g = listGames(context).firstOrNull { it.id == id } ?: return
        writeGame(context, g.copy(statsCache = statsCache))
    }

    private fun writeGame(context: Context, game: LibraryGame) {
        val o = JSONObject()
        o.put("id", game.id)
        o.put("folderId", game.folderId ?: JSONObject.NULL)
        o.put("title", game.title)
        o.put("createdAt", game.createdAt)
        o.put("modeLabel", game.modeLabel)
        o.put("width", game.size.width)
        o.put("height", game.size.height)
        o.put("komi", game.komi)
        o.put("handicap", game.handicap)
        o.put("startPlayer", if (game.startPlayer == Stone.BLACK) "B" else "W")
        o.put("customStones", encodeMoves(game.customStones))
        o.put("moves", encodeMoves(game.moves))
        o.put("resultText", game.resultText)
        o.put("statsCache", game.statsCache ?: JSONObject.NULL)
        File(gamesDir(context), "${game.id}.json").writeText(o.toString())
    }

    private fun readGame(file: File): LibraryGame? {
        return try {
            val o = JSONObject(file.readText())
            LibraryGame(
                id = o.optString("id"),
                folderId = if (o.isNull("folderId")) null else o.optString("folderId"),
                title = o.optString("title"),
                createdAt = o.optLong("createdAt"),
                modeLabel = o.optString("modeLabel"),
                size = BoardSize(o.optInt("width", 19), o.optInt("height", 19)),
                komi = o.optDouble("komi", 7.5),
                handicap = o.optInt("handicap", 0),
                customStones = decodeMoves(o.optString("customStones")),
                startPlayer = if (o.optString("startPlayer") == "W") Stone.WHITE else Stone.BLACK,
                moves = decodeMoves(o.optString("moves")),
                resultText = o.optString("resultText"),
                statsCache = if (o.isNull("statsCache")) null else o.optString("statsCache")
            )
        } catch (_: Exception) {
            null
        }
    }

    /** "B3,4;W5,6;Bp" — p marks a pass. */
    fun encodeMoves(moves: List<Move>): String = moves.joinToString(";") { m ->
        val c = if (m.stone == Stone.BLACK) "B" else "W"
        if (m.isPass) c + "p" else "$c${m.x},${m.y}"
    }

    fun decodeMoves(text: String): List<Move> {
        if (text.isBlank()) return emptyList()
        return text.split(";").mapNotNull { part ->
            if (part.length < 2) return@mapNotNull null
            val stone = if (part[0] == 'W') Stone.WHITE else Stone.BLACK
            val rest = part.substring(1)
            if (rest == "p") {
                Move.pass(stone)
            } else {
                val xy = rest.split(",")
                val x = xy.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                val y = xy.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                Move(stone, x, y)
            }
        }
    }
}
