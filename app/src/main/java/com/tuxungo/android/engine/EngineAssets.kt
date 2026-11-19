package com.tuxungo.android.engine

import android.content.Context
import java.io.File

/** Copies the bundled default model out of assets into writable storage once. */
object EngineAssets {

    // The asset uses a neutral ".model" name on purpose: the APK packager
    // decompresses ".gz" assets and renames them, which breaks both the
    // lookup and the gzip format KataGo expects. On disk the copy keeps
    // the real ".txt.gz" name so KataGo gunzips it when loading.
    private const val ASSET_MODEL = "models/default.model"
    private const val MODEL_NAME = "default-v1.txt.gz"

    fun ensureDefaultModel(context: Context): File {
        val dir = File(context.filesDir, "models").apply { mkdirs() }
        val target = File(dir, MODEL_NAME)
        val assetSize = try {
            context.assets.openFd(ASSET_MODEL).use { it.length }
        } catch (e: Exception) {
            -1L
        }
        if (!target.exists() || target.length() != assetSize) {
            context.assets.open(ASSET_MODEL).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return target
    }

    fun engineWorkDir(context: Context): File =
        File(context.filesDir, "engine").apply { mkdirs() }
}
