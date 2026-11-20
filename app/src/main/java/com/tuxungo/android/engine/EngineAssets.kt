package com.tuxungo.android.engine

import android.content.Context
import java.io.File

/** Copies the bundled models out of assets into writable storage once. */
object EngineAssets {

    // The assets use a neutral ".model" name on purpose: the APK packager
    // decompresses ".gz" assets and renames them, which breaks both the
    // lookup and the gzip format KataGo expects. On disk the copies keep
    // the real names so KataGo gunzips them when loading.
    private const val ASSET_DEFAULT = "models/default.model"
    private const val NAME_DEFAULT = "default-v1.txt.gz"
    private const val ASSET_STRONG = "models/strong.model"
    private const val NAME_STRONG = "strong-v1.bin.gz"
    private const val ASSET_HUMAN = "models/human.model"
    private const val NAME_HUMAN = "human-v0.bin.gz"

    private fun ensure(context: Context, asset: String, name: String): File {
        val dir = File(context.filesDir, "models").apply { mkdirs() }
        val target = File(dir, name)
        val assetSize = try {
            context.assets.openFd(asset).use { it.length }
        } catch (e: Exception) {
            -1L
        }
        if (!target.exists() || target.length() != assetSize) {
            context.assets.open(asset).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return target
    }

    /** The light bundled model (fast, for everyday play). */
    fun ensureDefaultModel(context: Context): File =
        ensure(context, ASSET_DEFAULT, NAME_DEFAULT)

    /** The strong bundled model (slower, judges positions more reliably). */
    fun ensureStrongModel(context: Context): File =
        ensure(context, ASSET_STRONG, NAME_STRONG)

    /** The human-style model, used together with a normal model for
     *  human-like move selection (KataGo's -human-model). */
    fun ensureHumanModel(context: Context): File =
        ensure(context, ASSET_HUMAN, NAME_HUMAN)

    fun engineWorkDir(context: Context): File =
        File(context.filesDir, "engine").apply { mkdirs() }
}
