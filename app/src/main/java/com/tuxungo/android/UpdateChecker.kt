package com.tuxungo.android

import android.content.Context
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manual update check against the project's GitHub releases. One of
 * only two network paths in the app (this and the strong-model
 * download), and both only run when the user taps for them — games
 * and analysis stay fully offline. Returns a user-facing message.
 */
object UpdateChecker {

    fun check(context: Context): String {
        val current = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) {
            null
        } ?: "?"
        return try {
            val url = URL(
                "https://api.github.com/repos/liyuzhou79-star/TuxunGo/releases?per_page=1"
            )
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", "TuxunGo/$current")
            if (conn.responseCode != 200) {
                return "检查失败（服务器返回 ${conn.responseCode}），等会儿再试"
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONArray(body)
            if (arr.length() == 0) {
                return "还没有发布过正式版本，你用的就是最新（$current）"
            }
            val rel = arr.getJSONObject(0)
            val tag = rel.optString("tag_name").removePrefix("v")
            val pageUrl = rel.optString("html_url")
            if (isNewer(tag, current)) {
                "发现新版本 $tag（你现在是 $current）。下载页：$pageUrl"
            } else {
                "已是最新版本（$current）"
            }
        } catch (_: Exception) {
            "检查失败，可能是网络不通，等会儿再试"
        }
    }

    /** Dotted-version compare: is [a] strictly newer than [b]? */
    internal fun isNewer(a: String, b: String): Boolean {
        val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
