package af.tohid.shop.util

import af.tohid.shop.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * به‌روزرسانی از گیت‌هاب.
 *
 * برنامه آخرین Release مخزن را می‌بیند و اگر نسخه‌ی تازه‌تری باشد،
 * فایل APK آن را دانلود و نصب می‌کند. کد اجرایی از جای دیگری بارگذاری
 * نمی‌شود — فقط بسته‌ی رسمیِ امضاشده‌ی خودِ برنامه.
 */
object Updater {

    data class Available(
        val version: String,
        val notes: String,
        val apkUrl: String,
        val sizeBytes: Long,
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** آخرین نسخه‌ی منتشرشده را می‌گیرد؛ null یعنی چیز تازه‌ای نیست. */
    fun check(currentVersion: String = BuildConfig.VERSION_NAME): Available? {
        val url = "https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest"
        val req = Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return null
            val body = res.body?.string() ?: return null
            val release = runCatching { JSONObject(body) }.getOrNull() ?: return null
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) return null

            val latest = release.optString("tag_name").trimStart('v', 'V')
            if (!isNewer(latest, currentVersion)) return null

            val assets = release.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name")
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                val url = asset.optString("browser_download_url")
                if (url.isBlank()) continue
                return Available(latest, release.optString("body"), url, asset.optLong("size"))
            }
            return null
        }
    }

    /** مقایسه‌ی نسخه‌ها به شکل ۱.۲.۳ */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split('.', '-').mapNotNull { it.toIntOrNull() }
        val b = current.split('.', '-').mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
