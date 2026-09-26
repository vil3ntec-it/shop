package ir.vil3ntec.tohid.update

import android.content.Context
import ir.vil3ntec.tohid.desktop.DesktopContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 *  به‌روزرسانیِ برنامهٔ کامپیوتر — همان API و همان رفتارِ `Updater`ِ
 *  اندروید، با نصابِ هر سیستم به‌جای APK.
 *
 *  | سیستم | فایل | نصب |
 *  |---|---|---|
 *  | ویندوز | `Tohid-Windows.msi` | `msiexec` و بستنِ برنامه، تا فایل‌ها آزاد باشند |
 *  | مک | `Tohid-Mac-*.dmg` | بازکردنِ دیسک؛ کاربر برنامه را روی Applications می‌کشد |
 *  | لینوکس | `Tohid-Linux.deb` | نصب‌کنندهٔ بستهٔ سیستم |
 *
 *  نشانیِ ثابت زیرِ برچسبِ `tohid-desktop` است و `version.txt` آخر از همه
 *  منتشر می‌شود (`tohid-desktop.yml`)، پس هیچ‌وقت شماره‌ای اعلام نمی‌شود
 *  که فایلش نیامده.
 */
object Updater {

  private const val TAG = "tohid-desktop"

  private val os = System.getProperty("os.name").orEmpty().lowercase()
  private val arch = System.getProperty("os.arch").orEmpty().lowercase()

  /** نامِ نصابِ همین کامپیوتر */
  val assetName: String = when {
    os.contains("win") -> "Tohid-Windows.msi"
    os.contains("mac") -> if (arch.contains("aarch64") || arch.contains("arm")) "Tohid-Mac-AppleSilicon.dmg" else "Tohid-Mac-Intel.dmg"
    else -> "Tohid-Linux.deb"
  }

  fun stableApkUrl(repo: String) = "https://github.com/$repo/releases/download/$TAG/$assetName"

  private fun versionUrl(repo: String) = "https://github.com/$repo/releases/download/$TAG/version.txt"

  var lastNote: String? = null
    private set

  data class Release(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val size: Long,
    val sumsUrl: String = "",
  )

  suspend fun check(repo: String, currentVersion: String): Result<Release?> =
    withContext(Dispatchers.IO) {
      runCatching {
        val slug = repo.trim().trim('/')
        require(Regex("^[\\w.-]+/[\\w.-]+$").matches(slug)) { "آدرس مخزن درست نیست" }
        val text = get(versionUrl(slug)).trim().lineSequence().firstOrNull()?.trim().orEmpty()
        if (!Regex("^\\d+(\\.\\d+)+$").matches(text)) throw IllegalStateException("نسخه‌ای برای دانلود پیدا نشد")
        val url = stableApkUrl(slug)
        Release(
          version = text,
          notes = "",
          apkUrl = url,
          size = sizeOf(url),
          sumsUrl = "https://github.com/$slug/releases/download/$TAG/SHA256SUMS.txt",
        ).takeIf { isNewer(it.version, currentVersion) }
      }
    }

  fun versionOf(assetName: String, tag: String): String {
    val fromName = Regex("(\\d+(?:\\.\\d+)+)").find(assetName)?.value
    if (fromName != null) return fromName
    val fromTag = Regex("(\\d+(?:\\.\\d+)+)").find(tag)?.value
    return fromTag ?: tag.removePrefix("v")
  }

  fun isNewer(remote: String, local: String): Boolean {
    val a = remote.split(".", "-").mapNotNull { it.toIntOrNull() }
    val b = local.split(".", "-").mapNotNull { it.toIntOrNull() }
    for (i in 0 until maxOf(a.size, b.size)) {
      val x = a.getOrElse(i) { 0 }
      val y = b.getOrElse(i) { 0 }
      if (x != y) return x > y
    }
    return false
  }

  private fun target(context: Context, release: Release): File =
    File(File(context.cacheDir, "updates"), "${release.version}-$assetName")

  suspend fun download(
    context: Context,
    release: Release,
    onProgress: (Int) -> Unit,
  ): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      val out = target(context, release)
      out.parentFile.mkdirs()
      out.parentFile.listFiles()?.forEach { if (it.name != out.name) it.delete() }

      if (release.size > 0 && out.length() == release.size) {
        onProgress(100)
        return@runCatching out
      }
      val have = if (release.size > 0 && out.length() in 1 until release.size) out.length() else 0L
      if (have == 0L) out.delete()

      val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = 20000
        readTimeout = 60000
        if (have > 0) setRequestProperty("Range", "bytes=$have-")
      }
      val resuming = have > 0 && conn.responseCode == 206
      if (conn.responseCode !in 200..299) throw IllegalStateException(reason(conn.responseCode))
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

      if (out.length() < 1_000_000) {
        out.delete()
        throw IllegalStateException("چیزی که گرفته شد فایلِ نصبی نیست — دوباره بزنید")
      }
      if (release.size > 0 && out.length() != release.size) {
        throw IllegalStateException("دانلود نیمه‌کاره ماند — دوباره بزنید تا ادامه پیدا کند")
      }
      val bad = verify(out, release)
      if (bad != null) {
        //  روی کامپیوتر هیچ لایهٔ دیگری امضا را نمی‌سنجد (برخلافِ اندروید)،
        //  پس فایلِ ناجور نصب نمی‌شود
        out.delete()
        throw IllegalStateException(bad)
      }
      lastNote = null
      out
    }
  }

  private fun verify(file: File, release: Release): String? {
    if (release.sumsUrl.isBlank()) return null
    val lines = runCatching { get(release.sumsUrl) }.getOrNull()
      ?.lines()
      ?.mapNotNull { line ->
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size >= 2) parts[0].lowercase() to parts[1].removePrefix("*") else null
      } ?: return null
    val expected = lines.firstOrNull { (_, n) -> n.equals(assetName, true) }?.first ?: return null
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
    else "جمعِ کنترلیِ فایل با نسخهٔ منتشرشده یکی نیست — دوباره دانلود کنید"
  }

  fun readyFile(context: Context, release: Release): File? =
    target(context, release).takeIf { it.isFile && release.size > 0 && it.length() == release.size }

  /** روی کامپیوتر اجازهٔ جدایی برای نصب نیست */
  fun canInstall(context: Context): Boolean = true

  fun openInstallSettings(context: Context): Result<Unit> = Result.success(Unit)

  fun openInBrowser(context: Context, url: String): Result<Unit> = runCatching {
    check(DesktopContext.openExternal(url)) { "مرورگر باز نشد" }
  }

  /**
   *  نصب. روی ویندوز برنامه باید بسته شود تا نصاب فایل‌هایش را عوض کند —
   *  پس نصاب اجرا می‌شود و برنامه چند ثانیه بعد بسته می‌شود (دفتر همان
   *  لحظه روی دیسک است؛ هیچ چیزی گم نمی‌شود).
   */
  fun install(context: Context, apk: File): Result<Unit> = runCatching {
    when {
      os.contains("win") -> {
        ProcessBuilder("msiexec", "/i", apk.absolutePath).start()
        Thread { Thread.sleep(1500); Runtime.getRuntime().halt(0) }.start()
      }
      os.contains("mac") -> ProcessBuilder("open", apk.absolutePath).start()
      else -> check(DesktopContext.openExternal(apk.toURI().toString())) { "نصب‌کننده باز نشد" }
    }
    Unit
  }

  private fun sizeOf(url: String): Long = runCatching {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
      requestMethod = "HEAD"
      instanceFollowRedirects = true
      connectTimeout = 15000
      readTimeout = 15000
    }
    try { conn.contentLengthLong.takeIf { conn.responseCode in 200..299 } ?: 0L } finally { conn.disconnect() }
  }.getOrDefault(0L)

  private fun get(url: String): String {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
      instanceFollowRedirects = true
      setRequestProperty("User-Agent", "tohid-desktop")
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

  private fun reason(code: Int): String = when (code) {
    403, 429 -> "گیت‌هاب فعلاً جواب نمی‌دهد — کمی بعد دوباره بزنید"
    404 -> "فایلِ نسخهٔ تازه روی گیت‌هاب پیدا نشد"
    in 500..599 -> "گیت‌هاب خطا داد ($code) — کمی بعد دوباره بزنید"
    else -> "سرورِ به‌روزرسانی جواب نداد ($code)"
  }
}
