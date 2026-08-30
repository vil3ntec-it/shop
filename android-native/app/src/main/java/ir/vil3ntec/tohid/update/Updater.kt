package ir.vil3ntec.tohid.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 *  به‌روزرسانی — بدونِ Android Studio، بدونِ فروشگاه.
 *
 *  آخرین انتشار خوانده می‌شود، فایلِ نصبی داخلِ خودِ برنامه گرفته می‌شود و
 *  به نصب‌کنندهٔ اندروید داده می‌شود. کاربر از برنامه بیرون نمی‌رود.
 *
 *  هزینه‌اش را بدانید: این کار اجازهٔ `REQUEST_INSTALL_PACKAGES` می‌خواهد
 *  و گوشی بارِ اول می‌پرسد که از این منبع اجازهٔ نصب هست یا نه. راهِ
 *  بی‌اجازه، سپردنِ کار به مرورگر بود؛ صاحبِ برنامه به‌روزرسانیِ داخلی را
 *  انتخاب کرد.
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
    /** نشانیِ فایلِ جمع‌های کنترلی، اگر انتشار داشته باشدش */
    val sumsUrl: String = "",
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

          // فایلِ جمع‌های کنترلی کنارِ خودِ APK منتشر می‌شود
          var sums = ""
          for (j in 0 until assets.length()) {
            val a = assets.getJSONObject(j)
            if (a.getString("name").equals("SHA256SUMS.txt", true)) {
              sums = a.getString("browser_download_url"); break
            }
          }

          val release = Release(
            // شماره از **نام فایل** خوانده می‌شود، نه از برچسبِ انتشار.
            // برچسبِ این مخزن «tohid-native» است — یک اسم، نه یک شماره —
            // و مقایسه با آن همیشه شکست می‌خورد. برای همین به‌روزرسانی
            // هرگز چیزی پیدا نمی‌کرد و همیشه می‌گفت «نسخهٔ شما تازه است».
            version = versionOf(apk.getString("name"), r.getString("tag_name")),
            notes = r.optString("body", ""),
            apkUrl = apk.getString("browser_download_url"),
            size = apk.optLong("size"),
            sumsUrl = sums,
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
   * دانلود با گزارشِ پیشرفت. فایل در پوشهٔ خودِ برنامه می‌نشیند تا اجازهٔ
   * حافظهٔ گوشی لازم نشود.
   */
  suspend fun download(
    context: Context,
    release: Release,
    onProgress: (Int) -> Unit,
  ): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      val dir = File(context.cacheDir, "updates").apply { mkdirs() }
      val out = File(dir, "tohid-${release.version}.apk")

      // فقط فایلِ نسخه‌های **دیگر** پاک می‌شوند. قبلاً همه پاک می‌شدند و
      // نتیجه‌اش این بود که اگر کاربر سرِ پرسشِ نصب «نه» می‌زد، دفعهٔ بعد
      // همان فایل از صفر گرفته می‌شد.
      dir.listFiles()?.forEach { if (it.name != out.name) it.delete() }

      // فایلِ کاملِ همین نسخه از قبل هست؟ دوباره گرفته نمی‌شود.
      if (release.size > 0 && out.length() == release.size) {
        onProgress(100)
        return@runCatching out
      }

      // نیمه‌کاره مانده؟ از همان‌جا ادامه داده می‌شود، نه از اول.
      val have = if (release.size > 0 && out.length() in 1 until release.size) out.length() else 0L
      if (have == 0L) out.delete()

      val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = 20000
        readTimeout = 60000
        if (have > 0) setRequestProperty("Range", "bytes=$have-")
      }

      // ۲۰۶ یعنی سرور ادامه را فرستاد؛ ۲۰۰ یعنی از اول فرستاده و آنچه
      // داریم به درد نمی‌خورد
      val resuming = have > 0 && conn.responseCode == 206
      var done = if (resuming) have else 0L
      val total = if (release.size > 0) release.size else conn.contentLengthLong + done

      conn.inputStream.use { input ->
        java.io.FileOutputStream(out, resuming).use { output ->
          val buffer = ByteArray(64 * 1024)
          var last = -1
          while (true) {
            val n = input.read(buffer)
            if (n <= 0) break
            output.write(buffer, 0, n)
            done += n
            if (total > 0) {
              val pct = ((done * 100) / total).toInt().coerceIn(0, 100)
              if (pct != last) { last = pct; onProgress(pct) }
            }
          }
        }
      }
      conn.disconnect()
      if (out.length() < 100_000) throw IllegalStateException("فایل ناقص دانلود شد")
      if (release.size > 0 && out.length() != release.size) {
        // نیمه‌کاره ماند؛ دفعهٔ بعد از همین‌جا ادامه داده می‌شود
        throw IllegalStateException("دانلود نیمه‌کاره ماند — دوباره بزنید تا ادامه پیدا کند")
      }
      verify(out, release)
      out
    }
  }

  /**
   *  سنجشِ درستیِ فایلِ گرفته‌شده.
   *
   *  تا حالا فقط اندازه سنجیده می‌شد، و اندازه چیزی را ثابت نمی‌کند:
   *  فایلی که وسطِ راه عوض شده باشد هم می‌تواند هم‌اندازه باشد. کنارِ هر
   *  انتشار یک `SHA256SUMS.txt` هست؛ حالا همان خوانده و مقایسه می‌شود و
   *  فایلِ ناجور پاک می‌شود، نه اینکه به نصب‌کننده داده شود.
   *
   *  اگر انتشار چنین فایلی نداشته باشد، جلوی به‌روزرسانی گرفته نمی‌شود —
   *  نبودنِ جمعِ کنترلی دلیلِ خراب بودن نیست.
   */
  private fun verify(file: File, release: Release) {
    if (release.sumsUrl.isBlank()) return

    val expected = runCatching { get(release.sumsUrl) }.getOrNull()
      ?.lines()
      ?.mapNotNull { line ->
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size >= 2) parts[0].lowercase() to parts[1].removePrefix("*") else null
      }
      ?.firstOrNull { (_, name) -> name.endsWith(".apk", true) }
      ?.first
      ?: return

    val digest = java.security.MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
      val buffer = ByteArray(64 * 1024)
      while (true) {
        val n = input.read(buffer)
        if (n <= 0) break
        digest.update(buffer, 0, n)
      }
    }
    val actual = digest.digest().joinToString("") { "%02x".format(it) }

    if (!actual.equals(expected, ignoreCase = true)) {
      file.delete()
      throw IllegalStateException("فایلِ به‌روزرسانی با نسخهٔ منتشرشده یکی نیست — نصب نشد")
    }
  }

  /** فایلِ آمادهٔ همین نسخه، اگر از قبل کامل گرفته شده باشد */
  fun readyFile(context: Context, release: Release): File? {
    val out = File(File(context.cacheDir, "updates"), "tohid-${release.version}.apk")
    return out.takeIf { it.isFile && release.size > 0 && it.length() == release.size }
  }

  /** فایل را به نصب‌کنندهٔ اندروید می‌دهد */
  fun install(context: Context, apk: File): Result<Unit> = runCatching {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
    val intent = Intent(Intent.ACTION_VIEW).apply {
      setDataAndType(uri, "application/vnd.android.package-archive")
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
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
