package com.tuxungo.android.engine

import android.content.Context
import java.io.File

/**
 * Where the app's model files live on disk. The light model and the
 * human-style model ship inside the APK as assets and are copied out
 * on first use; the strong model is NOT bundled — it is downloaded
 * on demand into the same directory (see ModelStore / ModelDownload).
 */
object EngineAssets {

    // The assets use a neutral ".model" name on purpose: the APK packager
    // decompresses ".gz" assets and renames them, which breaks both the
    // lookup and the gzip format KataGo expects. On disk the copies keep
    // the real names so KataGo gunzips them when loading.
    private const val ASSET_DEFAULT = "models/default.model"
    private const val NAME_DEFAULT = "default-v1.txt.gz"
    const val NAME_STRONG = "strong-v1.bin.gz"
    private const val ASSET_HUMAN = "models/human.model"
    private const val NAME_HUMAN = "human-v0.bin.gz"

    fun modelsDir(context: Context): File =
        File(context.filesDir, "models").apply { mkdirs() }

    fun defaultFile(context: Context): File = File(modelsDir(context), NAME_DEFAULT)

    /** The strong model's file — present only after a download (or left
     *  over from a version that bundled it). */
    fun strongFile(context: Context): File = File(modelsDir(context), NAME_STRONG)

    fun humanFile(context: Context): File = File(modelsDir(context), NAME_HUMAN)

    /** Size of a bundled asset without copying it out. */
    fun assetSize(context: Context, asset: String): Long = try {
        context.assets.openFd(asset).use { it.length }
    } catch (e: Exception) {
        -1L
    }

    private fun ensure(context: Context, asset: String, name: String): File {
        val target = File(modelsDir(context), name)
        val size = assetSize(context, asset)
        if (!target.exists() || target.length() != size) {
            context.assets.open(asset).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return target
    }

    /** The light bundled model (fast, for everyday play). */
    fun ensureDefaultModel(context: Context): File =
        ensure(context, ASSET_DEFAULT, NAME_DEFAULT)

    /** The human-style model, used together with a normal model for
     *  human-like move selection (KataGo's -human-model). */
    fun ensureHumanModel(context: Context): File =
        ensure(context, ASSET_HUMAN, NAME_HUMAN)

    fun engineWorkDir(context: Context): File =
        File(context.filesDir, "engine").apply { mkdirs() }
}
