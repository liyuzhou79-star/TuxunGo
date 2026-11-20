package com.tuxungo.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Whole-app backup: the game library (folders.json + every
 * games/*.json, verbatim) plus the small SharedPreferences
 * stores — settings, the in-progress game, per-side AI
 * choices, training progress, the series tally and more.
 * Imported model FILES are not included (they are large);
 * re-import a model after restoring and everything else
 * still lines up.
 */
object BackupStore {

    private const val FORMAT = 1

    private val PREFS_FILES = listOf(
        "tuxungo_settings", "tuxungo_game", "tuxungo_sideai",
        "setup_prefs", "guess_prefs", "tsumego_prefs",
        "model_speed", "series_store", "review_prefs"
    )

    fun exportJson(context: Context): String {
        val root = JSONObject()
        root.put("app", "TuxunGo")
        root.put("format", FORMAT)
        root.put("exportedAt", System.currentTimeMillis())

        val libDir = File(context.filesDir, "library")
        val lib = JSONObject()
        val foldersFile = File(libDir, "folders.json")
        lib.put(
            "folders",
            if (foldersFile.exists()) foldersFile.readText() else "[]"
        )
        val gamesArr = JSONArray()
        File(libDir, "games")
            .listFiles { f -> f.extension == "json" }
            ?.forEach { f ->
                gamesArr.put(
                    JSONObject()
                        .put("name", f.name)
                        .put("content", f.readText())
                )
            }
        lib.put("games", gamesArr)
        root.put("library", lib)

        val prefsRoot = JSONObject()
        for (name in PREFS_FILES) {
            val all = context
                .getSharedPreferences(name, Context.MODE_PRIVATE).all
            if (all.isEmpty()) continue
            val o = JSONObject()
            for ((k, v) in all) {
                val entry = JSONObject()
                when (v) {
                    is String -> entry.put("t", "s").put("v", v)
                    is Int -> entry.put("t", "i").put("v", v)
                    is Long -> entry.put("t", "l").put("v", v)
                    is Float -> entry.put("t", "f").put("v", v.toDouble())
                    is Boolean -> entry.put("t", "b").put("v", v)
                    is Set<*> -> {
                        val arr = JSONArray()
                        for (item in v) arr.put(item.toString())
                        entry.put("t", "ss").put("v", arr)
                    }
                    else -> continue
                }
                o.put(k, entry)
            }
            prefsRoot.put(name, o)
        }
        root.put("prefs", prefsRoot)
        return root.toString()
    }

    /** Writes the backup JSON into the shared cache; returns the file. */
    fun exportToFile(context: Context): File {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "tuxungo_backup.json")
        file.writeText(exportJson(context))
        return file
    }

    /**
     * Restores a backup produced by [exportJson], replacing the
     * current library and the backed-up preference stores.
     * Returns a human-readable summary.
     */
    fun importJson(context: Context, text: String): String {
        val root = JSONObject(text)
        require(root.optString("app") == "TuxunGo") {
            "这不是 TuxunGo 的备份文件"
        }
        var gameCount = 0
        val lib = root.optJSONObject("library")
        if (lib != null) {
            val libDir = File(context.filesDir, "library")
                .apply { mkdirs() }
            val gamesDir = File(libDir, "games").apply { mkdirs() }
            gamesDir.listFiles()?.forEach { it.delete() }
            val gamesArr = lib.optJSONArray("games") ?: JSONArray()
            for (i in 0 until gamesArr.length()) {
                val g = gamesArr.optJSONObject(i) ?: continue
                val name = g.optString("name")
                if (name.isBlank() || name.contains("/") ||
                    name.contains("..")
                ) {
                    continue
                }
                File(gamesDir, name).writeText(g.optString("content"))
                gameCount++
            }
            File(libDir, "folders.json")
                .writeText(lib.optString("folders", "[]"))
        }
        val prefsRoot = root.optJSONObject("prefs")
        if (prefsRoot != null) {
            for (name in PREFS_FILES) {
                val o = prefsRoot.optJSONObject(name) ?: continue
                val editor = context
                    .getSharedPreferences(name, Context.MODE_PRIVATE)
                    .edit()
                editor.clear()
                val keys = o.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val e = o.optJSONObject(k) ?: continue
                    when (e.optString("t")) {
                        "s" -> editor.putString(k, e.optString("v"))
                        "i" -> editor.putInt(k, e.optInt("v"))
                        "l" -> editor.putLong(k, e.optLong("v"))
                        "f" -> editor.putFloat(
                            k, e.optDouble("v").toFloat()
                        )
                        "b" -> editor.putBoolean(k, e.optBoolean("v"))
                        "ss" -> {
                            val arr = e.optJSONArray("v") ?: JSONArray()
                            val set = mutableSetOf<String>()
                            for (j in 0 until arr.length()) {
                                set.add(arr.optString(j))
                            }
                            editor.putStringSet(k, set)
                        }
                    }
                }
                editor.apply()
            }
        }
        return "已恢复 $gameCount 局棋谱和全部进度设置"
    }
}
