package com.tuxungo.android.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream

/**
 * Downloads the strong model on demand — the only model the app ever
 * fetches, and only when the user taps the download button. Writes to
 * a ".part" file, verifies the exact byte count and the gzip stream,
 * then renames into place. Single download at a time; cancellable.
 */
object ModelDownload {

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "model-download").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var cancelled = false

    @Volatile
    var running = false
        private set

    fun start(
        context: Context,
        onProgress: (downloaded: Long, total: Long, bytesPerSecond: Long) -> Unit,
        onDone: (ok: Boolean, message: String) -> Unit
    ) {
        if (running) return
        running = true
        cancelled = false
        val appContext = context.applicationContext
        executor.execute {
            val result = runCatching { download(appContext, onProgress) }
            running = false
            mainHandler.post {
                when {
                    cancelled -> onDone(false, "已取消下载。")
                    result.isSuccess -> onDone(true, "下载完成。")
                    else -> onDone(
                        false,
                        "下载失败：${result.exceptionOrNull()?.message ?: "网络异常"}。" +
                            "已下载的部分没有保留，请检查网络后重试。"
                    )
                }
            }
        }
    }

    fun cancel() {
        cancelled = true
    }

    private fun download(
        context: Context,
        onProgress: (Long, Long, Long) -> Unit
    ) {
        val dest = EngineAssets.strongFile(context)
        val part = File(dest.parentFile, dest.name + ".part")
        part.delete()
        var url = URL(ModelStore.STRONG_URL)
        var connection: HttpURLConnection? = null
        // Follow redirects by hand: the model host may bounce through
        // a CDN, and HttpURLConnection does not follow cross-host hops.
        var redirects = 0
        while (true) {
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 20_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "TuxunGo")
            val code = conn.responseCode
            if (code in 301..308 || code == 300) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location == null || ++redirects > 5) {
                    throw IllegalStateException("服务器跳转异常（$code）")
                }
                url = URL(url, location)
                continue
            }
            if (code != 200) throw IllegalStateException("服务器返回 $code")
            connection = conn
            break
        }
        val conn = connection!!
        val total = ModelStore.STRONG_SIZE_BYTES
        conn.inputStream.use { input ->
            part.outputStream().use { output ->
                val buffer = ByteArray(256 * 1024)
                var downloaded = 0L
                var lastReportAt = System.currentTimeMillis()
                var lastReportBytes = 0L
                while (true) {
                    if (cancelled) {
                        part.delete()
                        return
                    }
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    downloaded += n
                    val now = System.currentTimeMillis()
                    if (now - lastReportAt >= 250) {
                        val dt = (now - lastReportAt) / 1000.0
                        val speed = ((downloaded - lastReportBytes) / dt).toLong()
                        val d = downloaded
                        mainHandler.post { onProgress(d, total, speed) }
                        lastReportAt = now
                        lastReportBytes = downloaded
                    }
                }
            }
        }
        conn.disconnect()
        if (cancelled) {
            part.delete()
            return
        }
        if (part.length() != total) {
            part.delete()
            throw IllegalStateException("文件大小不对（${part.length()} / $total 字节）")
        }
        // Full gzip drain: proves the archive is complete and intact.
        try {
            GZIPInputStream(FileInputStream(part)).use { stream ->
                val chunk = ByteArray(64 * 1024)
                while (stream.read(chunk) >= 0) {
                    // discard
                }
            }
        } catch (e: Exception) {
            part.delete()
            throw IllegalStateException("文件校验没通过，可能下载不完整")
        }
        if (!part.renameTo(dest)) {
            part.copyTo(dest, overwrite = true)
            part.delete()
        }
    }
}
