package com.tuxungo.android.engine

import android.content.Context
import android.net.Uri
import com.tuxungo.android.go.BoardSize
import com.tuxungo.android.go.Stone
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

data class ModelInfo(
    val id: String,
    val displayName: String,
    val archText: String,
    val sizeBytes: Long,
    val isBundled: Boolean
)

/**
 * The model library: two bundled models (a light one and a strong one)
 * plus user-uploaded KataGo models. Uploads are validated in stages —
 * gzip integrity, a header parse for text models, and a real engine
 * test-load — before they join the library. Metadata lives in
 * filesDir/models/index.json; the active choice lives in
 * SharedPreferences. A third bundled file, the human-style model, is
 * not a standalone library entry: engines load it alongside a normal
 * model when a side enables human-like play.
 */
object ModelStore {

    const val BUNDLED_ID = "bundled"
    const val STRONG_ID = "bundled_strong"
    private const val PREFS = "model_store"
    private const val KEY_ACTIVE = "active_id"

    private fun userDir(context: Context): File =
        File(context.filesDir, "models/user").apply { mkdirs() }

    private fun indexFile(context: Context): File =
        File(File(context.filesDir, "models").apply { mkdirs() }, "index.json")

    private fun readIndex(context: Context): JSONArray {
        val f = indexFile(context)
        if (!f.exists()) return JSONArray()
        return try {
            JSONArray(f.readText())
        } catch (_: Exception) {
            JSONArray()
        }
    }

    private fun writeIndex(context: Context, index: JSONArray) {
        indexFile(context).writeText(index.toString())
    }

    private fun bundledInfo(context: Context): ModelInfo {
        val file = EngineAssets.ensureDefaultModel(context)
        return ModelInfo(
            id = BUNDLED_ID,
            displayName = nickname(context, BUNDLED_ID) ?: "轻量模型",
            archText = "速度快 · 日常对局和分析都够用 · 内置",
            sizeBytes = file.length(),
            isBundled = true
        )
    }

    private fun strongInfo(context: Context): ModelInfo {
        val file = EngineAssets.ensureStrongModel(context)
        return ModelInfo(
            id = STRONG_ID,
            displayName = nickname(context, STRONG_ID) ?: "强力模型",
            archText = "棋力更强 · 形势判断更准，思考稍慢 · 内置",
            sizeBytes = file.length(),
            isBundled = true
        )
    }

    /** A user-chosen nickname for any model (bundled included), if set. */
    fun nickname(context: Context, id: String): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("nick_$id", null)?.takeIf { it.isNotBlank() }

    fun setNickname(context: Context, id: String, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("nick_$id", name).apply()
    }

    fun list(context: Context): List<ModelInfo> {
        val out = mutableListOf(bundledInfo(context), strongInfo(context))
        val index = readIndex(context)
        for (i in 0 until index.length()) {
            val o = index.optJSONObject(i) ?: continue
            val file = File(userDir(context), o.optString("fileName"))
            if (!file.exists()) continue
            out.add(
                ModelInfo(
                    id = o.optString("id"),
                    displayName = o.optString("displayName"),
                    archText = o.optString("archText"),
                    sizeBytes = file.length(),
                    isBundled = false
                )
            )
        }
        // Nicknames win over stored names everywhere the list is shown.
        return out.map { info ->
            nickname(context, info.id)?.let { info.copy(displayName = it) } ?: info
        }
    }

    fun activeId(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE, BUNDLED_ID) ?: BUNDLED_ID

    fun setActive(context: Context, id: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ACTIVE, id).apply()
    }

    /** How many user-imported models are in the library. */
    fun userCount(context: Context): Int = readIndex(context).length()

    /** The model file for any library id (falls back to the bundled model). */
    fun resolveFile(context: Context, id: String): File {
        if (id == STRONG_ID) return EngineAssets.ensureStrongModel(context)
        if (id != BUNDLED_ID) {
            val index = readIndex(context)
            for (i in 0 until index.length()) {
                val o = index.optJSONObject(i) ?: continue
                if (o.optString("id") == id) {
                    val file = File(userDir(context), o.optString("fileName"))
                    if (file.exists()) return file
                }
            }
        }
        return EngineAssets.ensureDefaultModel(context)
    }

    /** The model file new games and analyses should use. */
    fun resolveActiveFile(context: Context): File = resolveFile(context, activeId(context))

    fun rename(context: Context, id: String, newName: String) {
        if (id == BUNDLED_ID || id == STRONG_ID) {
            setNickname(context, id, newName)
            return
        }
        setNickname(context, id, newName)
        val index = readIndex(context)
        for (i in 0 until index.length()) {
            val o = index.optJSONObject(i) ?: continue
            if (o.optString("id") == id) {
                o.put("displayName", newName)
                writeIndex(context, index)
                return
            }
        }
    }

    fun delete(context: Context, id: String) {
        if (id == BUNDLED_ID || id == STRONG_ID) return
        val index = readIndex(context)
        val kept = JSONArray()
        for (i in 0 until index.length()) {
            val o = index.optJSONObject(i) ?: continue
            if (o.optString("id") == id) {
                File(userDir(context), o.optString("fileName")).delete()
            } else {
                kept.put(o)
            }
        }
        writeIndex(context, kept)
        if (activeId(context) == id) setActive(context, BUNDLED_ID)
    }

    sealed class ImportResult {
        data class Ok(val info: ModelInfo) : ImportResult()
        data class Failed(val reason: String) : ImportResult()
    }

    /**
     * Validates and imports the model at [uri]. Blocking; call off the
     * main thread. Failure reasons are written for end users.
     */
    fun importModel(
        context: Context,
        uri: Uri,
        sourceName: String,
        customName: String? = null
    ): ImportResult {
        val tmp = File(context.cacheDir, "model-import.bin")
        tmp.delete()
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            } ?: return ImportResult.Failed("读不到这个文件，请重新选择。")
        } catch (e: Exception) {
            return ImportResult.Failed("读取文件失败：${e.message ?: "未知错误"}")
        }
        if (tmp.length() < 100_000L) {
            tmp.delete()
            return ImportResult.Failed("文件只有 ${tmp.length()} 字节，太小了，不像 KataGo 模型文件。")
        }

        // Stage 1+2: integrity check and header parse.
        val isGzip = FileInputStream(tmp).use { it.read() == 0x1f && it.read() == 0x8b }
        var header: ModelHeader? = null
        var sawNonTextByte = false
        try {
            val raw: InputStream = if (isGzip) GZIPInputStream(FileInputStream(tmp)) else FileInputStream(tmp)
            raw.use { stream ->
                val head = ByteArray(8192)
                var filled = 0
                var scanned = 0
                // Drain the whole stream: for gzip this verifies the CRC
                // and proves the archive is complete.
                val chunk = ByteArray(64 * 1024)
                while (true) {
                    val n = stream.read(chunk)
                    if (n < 0) break
                    if (filled < head.size) {
                        val take = minOf(n, head.size - filled)
                        System.arraycopy(chunk, 0, head, filled, take)
                        filled += take
                    }
                    // A text model is printable ASCII from start to end.
                    // A .bin.gz model also STARTS with a text header (name,
                    // version), but its weights are binary and follow within
                    // the first kilobytes — so scan the first megabyte of
                    // decompressed bytes for any non-text byte.
                    if (!sawNonTextByte && scanned < 1_000_000) {
                        val scanN = minOf(n, 1_000_000 - scanned)
                        for (i in 0 until scanN) {
                            val b = chunk[i].toInt()
                            if (b != 9 && b != 10 && b != 13 && (b < 32 || b > 126)) {
                                sawNonTextByte = true
                                break
                            }
                        }
                        scanned += scanN
                    }
                }
                if (filled > 0) {
                    header = ModelHeaderParser.parse(String(head, 0, filled, Charsets.UTF_8))
                }
            }
        } catch (e: Exception) {
            tmp.delete()
            return ImportResult.Failed(
                if (isGzip) "这个 .gz 文件已损坏或不完整，解压校验没通过。"
                else "读取文件内容失败：${e.message ?: "未知错误"}"
            )
        }
        // NOT "header != null": binary models have a parseable text header
        // too, and misjudging one as text makes KataGo load it with the
        // wrong parser (the failure this scan exists to prevent).
        val isText = !sawNonTextByte

        // Stage 3: real engine test-load. KataGo picks its loader from the
        // file extension, so the probe file needs the right suffix. If the
        // first choice fails, try the other format before giving up.
        fun probeSuffix(text: Boolean): String = when {
            isGzip && text -> "probe.txt.gz"
            isGzip -> "probe.bin.gz"
            text -> "probe.txt"
            else -> "probe.bin"
        }

        fun tryProbe(text: Boolean): String? {
            val probe = File(context.cacheDir, probeSuffix(text))
            probe.delete()
            tmp.copyTo(probe, overwrite = true)
            val error: String? = try {
                val engine = KataEngine.start(
                    nativeLibDir = context.applicationInfo.nativeLibraryDir,
                    workDir = File(context.filesDir, "engine-probe").apply { mkdirs() },
                    modelFile = probe,
                    maxVisits = 1
                )
                try {
                    engine.setBoardSize(BoardSize(9, 9))
                    engine.genmove(Stone.BLACK, BoardSize(9, 9))
                } finally {
                    engine.quit()
                }
                null
            } catch (e: Exception) {
                e.message ?: "未知错误"
            }
            probe.delete()
            return error
        }

        var probeError = tryProbe(isText)
        var detectedText = isText
        if (probeError != null) {
            val altError = tryProbe(!isText)
            if (altError == null) {
                probeError = null
                detectedText = !isText
            }
        }
        if (probeError != null) {
            tmp.delete()
            return ImportResult.Failed("引擎实测加载失败，这个文件不是可用的 KataGo 模型。（$probeError）")
        }

        // Stage 4: file it into the library. The stored name must end with
        // the suffix matching the detected format, whatever the picked
        // file was called — KataGo chooses its loader by extension on
        // every later load too.
        val properSuffix = when {
            isGzip && detectedText -> ".txt.gz"
            isGzip -> ".bin.gz"
            detectedText -> ".txt"
            else -> ".bin"
        }
        var baseName = sourceName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "model" }
        for (suf in listOf(".txt.gz", ".bin.gz", ".txt", ".bin", ".gz")) {
            if (baseName.endsWith(suf)) {
                baseName = baseName.dropLast(suf.length)
                break
            }
        }
        if (baseName.isBlank()) baseName = "model"
        var finalName = baseName + properSuffix
        var counter = 1
        while (File(userDir(context), finalName).exists()) {
            finalName = "$baseName-$counter$properSuffix"
            counter++
        }
        val dest = File(userDir(context), finalName)
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
        val id = "u${System.currentTimeMillis()}"
        val displayName = customName?.trim()?.takeIf { it.isNotBlank() }
            ?: header?.name
            ?: baseName
        val archText = if (detectedText) {
            header?.describe() ?: "文本模型"
        } else {
            "二进制模型（引擎已验证可加载）"
        }
        val index = readIndex(context)
        index.put(
            JSONObject()
                .put("id", id)
                .put("fileName", finalName)
                .put("displayName", displayName)
                .put("archText", archText)
        )
        writeIndex(context, index)
        return ImportResult.Ok(
            ModelInfo(id, displayName, archText, dest.length(), isBundled = false)
        )
    }
}
