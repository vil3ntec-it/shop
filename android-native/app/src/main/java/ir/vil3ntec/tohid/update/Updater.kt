package ir.vil3ntec.tohid.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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

  /** برچسبِ انتشارِ نسخهٔ بومی — نامِ ثابتی که فایل‌ها زیرش می‌نشینند */
  private const val TAG = "tohid-native"

  /**
   *  نشانیِ ثابتِ فایلِ نصبی.
   *
   *  نامِ نسخه‌دار با هر ساخت عوض و فایلِ قبلی پاک می‌شود، پس هر لینکی که
   *  جایی فرستاده شده باشد می‌میرد. این یکی همیشه هست.
   */
  fun stableApkUrl(repo: String) = "https://github.com/$repo/releases/download/$TAG/Tohid-Native.apk"

  private fun versionUrl(repo: String) = "https://github.com/$repo/releases/download/$TAG/version.txt"

  /** یادداشتِ آخرین دانلود — مثلاً اینکه جمعِ کنترلی نخواند */
  var lastNote: String? = null
    private set

  data class Release(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val size: Long,
    /** نشانیِ فایلِ جمع‌های کنترلی، اگر انتشار داشته باشدش */
    val sumsUrl: String = "",
  )

  /**
   *  owner/repo — از تنظیمات می‌آید تا هر وقت خواستی عوضش کنی.
   *
   *  دو راه امتحان می‌شود، چون راهِ اول همیشه باز نیست:
   *
   *  ۱) `api.github.com` — یادداشتِ انتشار و اندازهٔ دقیق را می‌دهد، ولی
   *     برای کسی که وارد نشده ساعتی ۶۰ درخواست بیشتر نمی‌پذیرد و پشتِ
   *     یک اینترنتِ مشترک، این سهمیه زود تمام می‌شود. آن‌وقت ۴۰۳ می‌دهد
   *     و به‌روزرسانی بی‌صدا شکست می‌خورد.
   *
   *  ۲) فایلِ `version.txt` که کنارِ خودِ نصبی منتشر می‌شود — یک فایلِ
   *     ساده روی همان مسیرِ دانلود، بی‌سهمیه و بی‌احراز هویت.
   *
   *  اگر اولی نشد، دومی جوابِ کار را می‌دهد؛ فقط یادداشتِ انتشار همراهش
   *  نیست، که چیزِ مهمی نیست.
   */
  suspend fun check(repo: String, currentVersion: String): Result<Release?> =
    withContext(Dispatchers.IO) {
      runCatching {
        val slug = repo.trim().trim('/')
        require(Regex("^[\\w.-]+/[\\w.-]+$").matches(slug)) { "آدرس مخزن درست نیست" }

        val viaApi = runCatching { fromApi(slug) }
        val best = viaApi.getOrNull() ?: runCatching { fromFile(slug) }.getOrNull()
        ?: throw IllegalStateException(
          viaApi.exceptionOrNull()?.message ?: "نسخه‌ای برای دانلود پیدا نشد"
        )

        best.takeIf { isNewer(it.version, currentVersion) }
      }
    }

  /** راهِ اول: فهرستِ انتشارها از API */
  private fun fromApi(slug: String): Release? {
    val body = get("https://api.github.com/repos/$slug/releases?per_page=10")
    val list = org.json.JSONArray(body)

    for (i in 0 until list.length()) {
      val r = list.getJSONObject(i)
      if (r.optBoolean("draft")) continue
      val assets = r.getJSONArray("assets")

      // فایلِ نسخه‌دار ترجیح دارد: شمارهٔ نسخه از نامش خوانده می‌شود و
      // «Tohid-Native.apk» شماره‌ای در نامش ندارد
      var apk: JSONObject? = null
      var plain: JSONObject? = null
      var sums = ""
      for (j in 0 until assets.length()) {
        val a = assets.getJSONObject(j)
        val name = a.getString("name")
        when {
          name.equals("SHA256SUMS.txt", true) -> sums = a.getString("browser_download_url")
          name.endsWith(".apk", true) && Regex("\\d+(\\.\\d+)+").containsMatchIn(name) ->
            if (apk == null) apk = a
          name.endsWith(".apk", true) -> if (plain == null) plain = a
        }
      }
      val chosen = apk ?: plain ?: continue

      return Release(
        // شماره از **نام فایل** خوانده می‌شود، نه از برچسبِ انتشار.
        // برچسبِ این مخزن «tohid-native» است — یک اسم، نه یک شماره —
        // و مقایسه با آن همیشه شکست می‌خورد.
        version = versionOf(chosen.getString("name"), r.getString("tag_name")),
        notes = r.optString("body", ""),
        apkUrl = chosen.getString("browser_download_url"),
        size = chosen.optLong("size"),
        sumsUrl = sums,
      )
    }
    return null
  }

  /** راهِ دوم: فایلِ کوچکِ `version.txt` کنارِ خودِ نصبی */
  private fun fromFile(slug: String): Release? {
    val text = get(versionUrl(slug)).trim().lineSequence().firstOrNull()?.trim().orEmpty()
    if (!Regex("^\\d+(\\.\\d+)+$").matches(text)) return null
    return Release(
      version = text,
      notes = "",
      apkUrl = stableApkUrl(slug),
      size = 0,
      sumsUrl = "https://github.com/$slug/releases/download/$TAG/SHA256SUMS.txt",
    )
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

      if (!looksLikeApk(out)) {
        out.delete()
        throw IllegalStateException("چیزی که گرفته شد فایلِ نصبی نیست — دوباره بزنید")
      }
      if (release.size > 0 && out.length() != release.size) {
        // نیمه‌کاره ماند؛ دفعهٔ بعد از همین‌جا ادامه داده می‌شود
        throw IllegalStateException("دانلود نیمه‌کاره ماند — دوباره بزنید تا ادامه پیدا کند")
      }
      lastNote = verify(out, release)
      out
    }
  }

  /**
   *  آیا چیزی که گرفته شد اصلاً فایلِ نصبی است.
   *
   *  هر APK یک فایلِ zip است و هر zip با «PK» شروع می‌شود. اگر جایی وسطِ
   *  راه صفحهٔ ورودِ یک شبکه یا یک پیامِ خطای HTML را به‌جای فایل داده
   *  باشد، همین‌جا معلوم می‌شود، نه سرِ پنجرهٔ نصب.
   */
  private fun looksLikeApk(file: File): Boolean {
    if (file.length() < 1_000_000) return false
    return runCatching {
      file.inputStream().use { it.read() == 'P'.code && it.read() == 'K'.code }
    }.getOrDefault(false)
  }

  /**
   *  سنجشِ جمعِ کنترلی — هشدار می‌دهد، جلو نمی‌گیرد.
   *
   *  تا حالا اگر جمعِ کنترلی نمی‌خواند، فایل پاک می‌شد و به‌روزرسانی
   *  همان‌جا می‌مرد. حالا فقط یادداشت می‌شود و کار جلو می‌رود.
   *
   *  چرا این‌طوری بی‌احتیاطی نیست: نگهبانِ واقعی، خودِ اندروید است. فایلی
   *  که با کلیدِ همین برنامه امضا نشده باشد اصلاً روی نصبِ فعلی نمی‌نشیند
   *  و گوشی ردش می‌کند. جمعِ کنترلی فقط خرابیِ وسطِ دانلود را می‌گیرد، و
   *  فایلِ خراب هم به‌هرحال نصب نمی‌شود.
   *
   *  اگر انتشار چنین فایلی نداشته باشد، چیزی گفته نمی‌شود — نبودنِ جمعِ
   *  کنترلی دلیلِ خراب بودن نیست.
   */
  private fun verify(file: File, release: Release): String? {
    if (release.sumsUrl.isBlank()) return null

    val name = release.apkUrl.substringAfterLast('/')
    val lines = runCatching { get(release.sumsUrl) }.getOrNull()
      ?.lines()
      ?.mapNotNull { line ->
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size >= 2) parts[0].lowercase() to parts[1].removePrefix("*") else null
      }
      ?: return null

    // خطِ همین فایل، وگرنه هر خطی که مالِ یک نصبی باشد
    val expected = lines.firstOrNull { (_, n) -> n.equals(name, true) }?.first
      ?: lines.firstOrNull { (_, n) -> n.endsWith(".apk", true) }?.first
      ?: return null

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

    return if (actual.equals(expected, ignoreCase = true)) null
    else "هشدار: جمعِ کنترلیِ فایل با نسخهٔ منتشرشده یکی نیست. نصب ادامه دارد؛ اگر گوشی نصب را رد کرد، یک بار دیگر دانلود کنید."
  }

  /** فایلِ آمادهٔ همین نسخه، اگر از قبل کامل گرفته شده باشد */
  fun readyFile(context: Context, release: Release): File? {
    val out = File(File(context.cacheDir, "updates"), "tohid-${release.version}.apk")
    return out.takeIf { it.isFile && release.size > 0 && it.length() == release.size }
  }

  /**
   *  آیا گوشی اجازهٔ نصب از این برنامه را داده است.
   *
   *  از اندروید ۸ به بعد داشتنِ `REQUEST_INSTALL_PACKAGES` در مانیفست
   *  کافی نیست؛ کاربر باید یک بار در تنظیماتِ گوشی هم اجازه بدهد. تا آن
   *  اجازه نباشد، پنجرهٔ نصب یا اصلاً باز نمی‌شود یا بی‌صدا بسته می‌شود —
   *  و از بیرون این‌طور دیده می‌شود که «به‌روزرسانی کار نمی‌کند».
   */
  fun canInstall(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
      runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)

  /** کاربر را می‌برد سرِ همان صفحهٔ اجازه، نه ته تنظیمات */
  fun openInstallSettings(context: Context): Result<Unit> = runCatching {
    val direct = Intent(
      Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
      Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(direct) }.getOrElse {
      context.startActivity(
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      )
    }
  }

  /**
   *  راهِ آخر: همان فایل با مرورگر گرفته شود.
   *
   *  اگر دانلودِ داخلی به هر دلیلی نگیرد — نت، فیلتر، سهمیه — کاربر
   *  نباید دستش خالی بماند. مرورگر فایل را در «دانلودها» می‌گذارد و از
   *  همان‌جا نصب می‌شود.
   */
  fun openInBrowser(context: Context, url: String): Result<Unit> = runCatching {
    context.startActivity(
      Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
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
      instanceFollowRedirects = true
      setRequestProperty("Accept", "application/vnd.github+json")
      setRequestProperty("User-Agent", "tohid-app")
      connectTimeout = 15000
      readTimeout = 20000
    }
    return try {
      val code = conn.responseCode
      if (code !in 200..299) throw IllegalStateException(reason(code))
      conn.inputStream.bufferedReader().readText()
    } finally {
      conn.disconnect()
    }
  }

  /**
   *  دلیلِ خطا به فارسی.
   *
   *  «جواب نداد (۴۰۳)» به کسی که پشتِ دخل ایستاده چیزی نمی‌گوید. ۴۰۳ روی
   *  گیت‌هاب تقریباً همیشه یعنی سهمیهٔ ساعتی تمام شده — چیزی که خودش
   *  درست می‌شود و راهِ دومِ به‌روزرسانی هم دورش می‌زند.
   */
  private fun reason(code: Int): String = when (code) {
    403, 429 -> "گیت‌هاب فعلاً جواب نمی‌دهد (سهمیهٔ ساعتی تمام شده) — کمی بعد دوباره بزنید"
    404 -> "فایلِ نسخهٔ تازه روی گیت‌هاب پیدا نشد"
    in 500..599 -> "گیت‌هاب خطا داد ($code) — کمی بعد دوباره بزنید"
    else -> "سرورِ به‌روزرسانی جواب نداد ($code)"
  }
}
