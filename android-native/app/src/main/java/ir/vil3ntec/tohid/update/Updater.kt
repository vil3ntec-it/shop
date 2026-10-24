package ir.vil3ntec.tohid.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    /** §۳۸.۹: حداقل نسخهٔ پشتیبانی‌شده (فقط از سرورِ خودمان)؛ خالی ⇒ حداقلی نیست */
    val minVersion: String = "",
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
  suspend fun check(
    repo: String,
    currentVersion: String,
    server: String = "",
    //  F0-27: شناسهٔ دکان و دستگاه — سرور با آن‌ها کانالِ آزمایشی را می‌شناسد
    shop: String = "",
    device: String = "",
  ): Result<Release?> =
    withContext(Dispatchers.IO) {
      runCatching {
        //  پیش‌نمایش: فقط برچسبِ `tohid-native-preview` روی GitHub — نه سرور، نه پایدار
        if (ir.vil3ntec.tohid.BuildConfig.PREVIEW) {
          return@runCatching fromPreview()
            ?.takeIf { ReleaseFeed.previewBuild(it.version) > ReleaseFeed.previewBuild(currentVersion) }
        }
        //  راهِ اصلی: سرورِ خودمان (`/api/releases`) — برنامه به github.com وصل
        //  نمی‌شود و نامِ مخزن لازم نیست. GitHub فقط وقتی پرسیده می‌شود که
        //  سرور هنوز این مسیر را ندارد (سرورِ به‌روزنشده).
        if (server.isNotBlank()) {
          val viaServer = runCatching { fromServer(server.trimEnd('/'), shop, device) }
          viaServer.getOrNull()?.let { return@runCatching it.takeIf { r -> isNewer(r.version, currentVersion) } }
        }

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

  /**
   *  سرورِ خودمان — همان انتشار، از دیسکِ سرور.
   *
   *  F0-27: `tag`ِ پاسخ می‌گوید فایل‌ها زیرِ کدام برچسب‌اند (`tohid-native` یا
   *  `tohid-native-beta`)؛ نصبیِ آزمایشی `….apk.beta` است. سرورِ قدیمی `tag`
   *  نمی‌دهد ⇒ همان `tohid-native`، مثلِ پیش. خواندنِ پاسخ: `ReleaseFeed`.
   */
  private fun fromServer(server: String, shop: String, device: String): Release? {
    val latest = ReleaseFeed.parseLatest(get(ReleaseFeed.latestUrl(server, TAG, shop, device)), TAG) ?: return null
    val chosen = ReleaseFeed.androidInstaller(latest.assets) ?: return null
    return Release(
      version = latest.version,
      notes = latest.notes,
      apkUrl = ReleaseFeed.fileUrl(server, latest.tag, chosen.name),
      size = chosen.size,
      sumsUrl = if (latest.hasSums) ReleaseFeed.fileUrl(server, latest.tag, ReleaseFeed.SUMS) else "",
      minVersion = latest.minVersion,
    )
  }

  /**
   *  راهِ اول: انتشارِ همین برچسب از API.
   *
   *  ⛔ F0-27: پیش از این فهرستِ ده انتشارِ تازه خوانده و **اولین** انتشاری که
   *  هر `.apk`ی داشت برداشته می‌شد — برنامهٔ مدیریت (`tohid-admin`) یا یک
   *  `v*`ِ قدیمی هم. حالا فقط `releases/tags/tohid-native`، و `ReleaseFeed`
   *  باز هم `tag_name` را می‌سنجد. این راه کانال نمی‌شناسد ⇒ همیشه پایدار.
   */
  private fun fromApi(slug: String): Release? {
    val pick = ReleaseFeed.githubPick(get("https://api.github.com/repos/$slug/releases/tags/$TAG"), TAG)
      ?: return null
    return Release(
      // شماره از **نام فایل** خوانده می‌شود، نه از برچسبِ انتشار.
      // برچسبِ این مخزن «tohid-native» است — یک اسم، نه یک شماره —
      // و مقایسه با آن همیشه شکست می‌خورد.
      version = versionOf(pick.apk.name, TAG),
      notes = pick.notes,
      apkUrl = pick.apk.url,
      size = pick.apk.size,
      sumsUrl = pick.sumsUrl,
    )
  }

  /**
   *  نسخهٔ پیش‌نمایش (`BuildConfig.PREVIEW`): نامِ نصبی ثابت، نسخه از `label`ِ آن
   *  (`ReleaseFeed.previewPick`). دانلود همان `ResumableDownload` و سنجش همان
   *  `verify` با `SHA256SUMS.txt` (و امضایش، اگر کلید سنجاق شده باشد).
   */
  private fun fromPreview(): Release? {
    val p = ReleaseFeed.previewPick(
      get("https://api.github.com/repos/vil3ntec-it/shop/releases/tags/${ReleaseFeed.PREVIEW_TAG}")
    ) ?: return null
    val apk = p.assets.first { it.name == ReleaseFeed.PREVIEW_ASSET }
    return Release(
      version = p.version,
      notes = p.notes,
      apkUrl = apk.url,
      size = apk.size,
      sumsUrl = p.assets.first { it.name == ReleaseFeed.SUMS }.url,
    )
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
   *
   * قطع شدن دیگر یعنی «دوباره بزنید» نیست: `ResumableDownload` در همین یک بار
   * زدن خودش از بایت‌هایی که روی دیسک است ادامه می‌دهد (Range + If-Range)،
   * بی‌اینترنت صبر می‌کند و نیمه با بسته شدنِ برنامه هم می‌ماند. سنجشِ
   * جمعِ کنترلی و امضا (`verify`) همان است که بود.
   *
   * @param onStatus یک خطِ کوتاه زیرِ نوار — «ادامه از ۴۲٪»، «منتظرِ اینترنت…»
   */
  suspend fun download(
    context: Context,
    release: Release,
    onStatus: (String?) -> Unit = {},
    onProgress: (Int) -> Unit,
  ): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      val dir = File(context.cacheDir, "updates").apply { mkdirs() }
      val out = File(dir, "tohid-${release.version}.apk")

      // فقط فایلِ نسخه‌های **دیگر** پاک می‌شوند (نیمه و یادداشتِ همین نسخه می‌مانند).
      // قبلاً همه پاک می‌شدند و نتیجه‌اش این بود که اگر کاربر سرِ پرسشِ نصب
      // «نه» می‌زد، دفعهٔ بعد همان فایل از صفر گرفته می‌شد.
      dir.listFiles()?.forEach { if (!ResumableDownload.belongsTo(it, out)) it.delete() }

      // فایلِ کاملِ همین نسخه از قبل هست؟ دوباره گرفته نمی‌شود.
      if (release.size > 0 && out.length() == release.size) {
        onProgress(100)
        return@runCatching out
      }
      // نیمه‌ای که نسخهٔ پیشینِ برنامه با همین نام گذاشته بود ⇒ از همان‌جا
      ResumableDownload.adoptLegacyPartial(out, release.size)

      var last = -1
      ResumableDownload.fetch(
        url = release.apkUrl,
        out = out,
        expected = release.size,
        online = { ir.vil3ntec.tohid.data.repo.Backend.isOnline(context) },
        reason = ::reason,
        userAgent = "tohid-app",
      ) { event ->
        if (event is ResumableDownload.Event.Progress) {
          val pct = ResumableDownload.percent(event.done, event.total)
          if (pct >= 0 && pct != last) { last = pct; onProgress(pct) }
        }
        onStatus(ResumableDownload.statusText(event))
      }

      if (!looksLikeApk(out)) {
        out.delete()
        throw IllegalStateException("چیزی که گرفته شد فایلِ نصبی نیست — دوباره بزنید")
      }
      if (release.size > 0 && out.length() != release.size) {
        out.delete()
        throw IllegalStateException("اندازهٔ فایلِ گرفته‌شده با انتشار نمی‌خواند — دوباره بزنید")
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
   *  سنجشِ فایلِ گرفته‌شده پیش از نصب.
   *
   *  ── سه قاعده ──────────────────────────────────────────────────────
   *  ۱) جمعِ کنترلی **نمی‌خواند** ⇒ فایل پاک و نصب رد می‌شود. پیش‌تر فقط
   *     هشدار می‌داد، به این امید که اندروید با امضای برنامه جلویش را
   *     بگیرد؛ ولی کلیدِ امضای اندروید در مخزنِ عمومی بود، پس آن نگهبان
   *     نبود.
   *  ۲) نسخه کلیدِ به‌روزرسانیِ سنجاق‌شده دارد (`UPDATE_PUBLIC_KEY`) ⇒
   *     `SHA256SUMS.txt` باید امضای همان کلید را داشته باشد؛ بی امضا یا با
   *     امضای نادرست، نصب رد می‌شود. شرح سرِ `ReleaseSignature`.
   *  ۳) کلید سنجاق نشده و انتشار فهرستی ندارد ⇒ مثلِ قبل جلو می‌رود؛
   *     نبودنِ فهرست در انتشارهای قدیمی دلیلِ خراب بودن نیست.
   *  ──────────────────────────────────────────────────────────────────
   *
   *  @return یادداشتی برای کاربر، یا null
   *  @throws IllegalStateException وقتی فایل نباید نصب شود (و پاک شده است)
   */
  private fun verify(file: File, release: Release): String? {
    val pinnedKey = ir.vil3ntec.tohid.BuildConfig.UPDATE_PUBLIC_KEY
    val strict = ReleaseSignature.pinned(pinnedKey)
    fun reject(why: String): Nothing {
      file.delete()
      throw IllegalStateException(why)
    }

    if (release.sumsUrl.isBlank()) {
      if (strict) reject("این نسخه فهرستِ امضاشده ندارد؛ برای امنیت نصب نشد")
      return null
    }
    val text = runCatching { get(release.sumsUrl) }.getOrNull()
    if (text == null) {
      if (strict) reject("فهرستِ جمعِ کنترلی گرفته نشد؛ برای امنیت نصب نشد — دوباره بزنید")
      return null
    }
    if (strict) {
      val signature = runCatching { get(release.sumsUrl + ".sig") }.getOrNull().orEmpty()
      if (!ReleaseSignature.verify(text, signature, pinnedKey)) {
        reject("امضای این نسخه با کلیدِ برنامه نمی‌خواند؛ نصب نشد")
      }
    }

    val lines = text.lines().mapNotNull { line ->
      val parts = line.trim().split(Regex("\\s+"))
      if (parts.size >= 2) parts[0].lowercase() to parts[1].removePrefix("*") else null
    }
    val name = release.apkUrl.substringAfterLast('/')
    // خطِ همین فایل، وگرنه هر خطی که مالِ یک نصبی باشد
    val expected = lines.firstOrNull { (_, n) -> n.equals(name, true) }?.first
      ?: lines.firstOrNull { (_, n) -> n.endsWith(".apk", true) }?.first
    if (expected == null) {
      if (strict) reject("این نسخه در فهرستِ امضاشده نیست؛ نصب نشد")
      return null
    }

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
      reject("فایلِ گرفته‌شده با نسخهٔ منتشرشده یکی نیست؛ پاک شد — دوباره بزنید")
    }
    return null
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
