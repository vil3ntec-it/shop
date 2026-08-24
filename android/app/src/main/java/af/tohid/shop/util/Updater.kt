package af.tohid.shop.util

import af.tohid.shop.BuildConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * به‌روزرسانی از گیت‌هاب.
 *
 * برنامه آخرین Release مخزن را می‌بیند و اگر نسخه‌ی تازه‌تری باشد،
 * فایل APK آن را دانلود و نصب می‌کند. کد اجرایی از جای دیگری بارگذاری
 * نمی‌شود — فقط بسته‌ی رسمیِ امضاشده‌ی خودِ برنامه.
 */
object Updater {

    @Serializable
    data class Release(
        @SerialName("tag_name") val tagName: String = "",
        val name: String = "",
        val body: String = "",
        val prerelease: Boolean = false,
        val draft: Boolean = false,
        val assets: List<Asset> = emptyList(),
    )

    @Serializable
    data class Asset(
        val name: String = "",
        @SerialName("browser_download_url") val downloadUrl: String = "",
        val size: Long = 0,
    )

    data class Available(
        val version: String,
        val notes: String,
        val apkUrl: String,
        val sizeBytes: Long,
    )

    private val json = Json { ignoreUnknownKeys = true }
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
            val release = runCatching { json.decodeFromString(Release.serializer(), body) }
                .getOrNull() ?: return null
            if (release.draft || release.prerelease) return null

            val latest = release.tagName.trimStart('v', 'V')
            if (!isNewer(latest, currentVersion)) return null

            val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?: return null
            return Available(latest, release.body, apk.downloadUrl, apk.size)
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
