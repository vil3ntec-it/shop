package ir.vil3ntec.tohid.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 *  به‌روزرسانی — بدونِ Android Studio، بدونِ فروشگاه.
 *
 *  آخرین انتشار خوانده می‌شود و اگر تازه‌تر بود، نشانیِ فایلش به مرورگر
 *  داده می‌شود.
 *
 *  **چرا مرورگر و نه نصبِ داخلِ برنامه.** برای اینکه برنامه خودش فایل را
 *  بنشاند، باید اجازهٔ `REQUEST_INSTALL_PACKAGES` می‌داشت — یعنی «این
 *  برنامه می‌تواند برنامهٔ دیگری روی گوشی نصب کند». همان اجازه بود که
 *  گوشی سرِ هر نصب هشدار می‌داد و می‌پرسید از این منبع اجازه هست یا نه،
 *  و همان الگویی است که سنجشگرهای گوگل به آن حساس‌اند. مرورگر خودش این
 *  اجازه را دارد؛ کار همان‌قدر انجام می‌شود و برنامهٔ دکان چنین توانی
 *  ندارد.
 *
 *  دو چیز که عمداً این‌طوری‌اند:
 *    • نسخهٔ پیش‌نمایش هم دیده می‌شود. /releases/latest نسخه‌های پیش‌نمایش را
 *      نشان نمی‌دهد و اگر مخزنی فقط همان‌ها را داشته باشد، به‌روزرسانی هرگز
 *      چیزی پیدا نمی‌کرد.
 *    • مقایسه با شمارهٔ نسخه است، نه با نام فایل — پس نصبِ نسخهٔ عقب‌تر
 *      پیشنهاد نمی‌شود.
 */
object Updater {

  data class Release(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val size: Long,
  )

  /** owner/repo — از تنظیمات می‌آید تا هر وقت خواستی عوضش کنی */
  suspend fun check(repo: String, currentVersion: String): Result<Release?> =
    withContext(Dispatchers.IO) {
      runCatching {
        val slug = repo.trim().trim('/')
        require(Regex("^[\\w.-]+/[\\w.-]+$").matches(slug)) { "آدرس مخزن درست نیست" }

        val body = get("https://api.github.com/repos/$slug/releases?per_page=10")
        val list = org.json.JSONArray(body)
        var best: Release? = null

        for (i in 0 until list.length()) {
          val r = list.getJSONObject(i)
          if (r.optBoolean("draft")) continue
          val assets = r.getJSONArray("assets")
          var apk: JSONObject? = null
          for (j in 0 until assets.length()) {
            val a = assets.getJSONObject(j)
            if (a.getString("name").endsWith(".apk", true)) { apk = a; break }
          }
          if (apk == null) continue

          val release = Release(
            // شماره از **نام فایل** خوانده می‌شود، نه از برچسبِ انتشار.
            // برچسبِ این مخزن «tohid-native» است — یک اسم، نه یک شماره —
            // و مقایسه با آن همیشه شکست می‌خورد. برای همین به‌روزرسانی
            // هرگز چیزی پیدا نمی‌کرد و همیشه می‌گفت «نسخهٔ شما تازه است».
            version = versionOf(apk.getString("name"), r.getString("tag_name")),
            notes = r.optString("body", ""),
            apkUrl = apk.getString("browser_download_url"),
            size = apk.optLong("size"),
          )
          if (best == null) best = release
        }

        best?.takeIf { isNewer(it.version, currentVersion) }
      }
    }

  /**
   *  شمارهٔ نسخه را از نامِ فایل بیرون می‌کشد: `Tohid-Native-3.2.45.apk` →
   *  `3.2.45`. اگر نام شماره نداشت، سراغِ برچسب می‌رود.
   */
  fun versionOf(assetName: String, tag: String): String {
    val fromName = Regex("(\\d+(?:\\.\\d+)+)").find(assetName)?.value
    if (fromName != null) return fromName
    val fromTag = Regex("(\\d+(?:\\.\\d+)+)").find(tag)?.value
    return fromTag ?: tag.removePrefix("v")
  }

  /** ۱.۳.۰ تازه‌تر از ۱.۲.۹ است — مقایسهٔ عددی، نه الفبایی */
  fun isNewer(remote: String, local: String): Boolean {
    val a = remote.split(".", "-").mapNotNull { it.toIntOrNull() }
    val b = local.split(".", "-").mapNotNull { it.toIntOrNull() }
    for (i in 0 until maxOf(a.size, b.size)) {
      val x = a.getOrElse(i) { 0 }
      val y = b.getOrElse(i) { 0 }
      if (x != y) return x > y
    }
    // شماره یکی است ولی برچسب فرق دارد (مثلاً پیش‌نمایش) — تازه حساب نمی‌شود
    return false
  }

  /**
   *  نشانیِ فایل را به مرورگر می‌دهد.
   *
   *  از این‌جا به بعد کارِ مرورگر است: می‌گیردش و خودش به نصب‌کنندهٔ
   *  اندروید می‌سپاردش. برنامهٔ دکان نه فایل را نگه می‌دارد نه اجازهٔ
   *  نصبی دارد.
   */
  fun openDownload(context: Context, release: Release): Result<Unit> = runCatching {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.apkUrl)).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
  }

  private fun get(url: String): String {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
      setRequestProperty("Accept", "application/vnd.github+json")
      setRequestProperty("User-Agent", "tohid-app")
      connectTimeout = 15000
      readTimeout = 20000
    }
    return try {
      if (conn.responseCode !in 200..299) throw IllegalStateException("سرورِ به‌روزرسانی جواب نداد (${conn.responseCode})")
      conn.inputStream.bufferedReader().readText()
    } finally {
      conn.disconnect()
    }
  }
}
