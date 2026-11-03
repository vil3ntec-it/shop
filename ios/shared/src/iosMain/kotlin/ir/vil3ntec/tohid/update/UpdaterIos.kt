package ir.vil3ntec.tohid.update

import android.content.Context
import java.io.File

/*
 *  جایگزینِ iOS ِ `update/Updater.kt` (همان API، مثلِ ‎desktop/…/UpdaterDesk.kt‎).
 *
 *  آیفون فقط از App Store / TestFlight به‌روز می‌شود (قاعدهٔ ۲.۵.۲ ِ اپل؛ docs/IOS-PLAN-fa.md §۳). پس این‌جا
 *  نه نسخه‌ای پرسیده می‌شود، نه فایلی گرفته یا نصب می‌شود: `check` شکست می‌خورد با پیامی برای کاربر، و
 *  `UpdateManager` ِ مشترک که بررسیِ خودکارش بی‌صداست (`quiet`) هیچ نواری نشان نمی‌دهد و هیچ APK‌ای نمی‌گیرد.
 */
object Updater {

  private const val APP_STORE = "به‌روزرسانیِ آیفون از App Store است."

  fun stableApkUrl(repo: String) = ""

  var lastNote: String? = null
    private set

  data class Release(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val size: Long,
    val sumsUrl: String = "",
    val minVersion: String = "",
  )

  @Suppress("UNUSED_PARAMETER")
  suspend fun check(
    repo: String,
    currentVersion: String,
    server: String = "",
    shop: String = "",
    device: String = "",
  ): Result<Release?> = Result.failure(IllegalStateException(APP_STORE))

  /** همان قاعدهٔ اندروید و کامپیوتر (`ReleaseVersion` ِ مشترک) */
  fun versionOf(assetName: String, tag: String): String = ReleaseVersion.of(assetName, tag)

  fun isNewer(remote: String, local: String): Boolean = ReleaseVersion.isNewer(remote, local)

  @Suppress("UNUSED_PARAMETER")
  suspend fun download(
    context: Context,
    release: Release,
    onStatus: (String?) -> Unit = {},
    onProgress: (Int) -> Unit,
  ): Result<File> = Result.failure(IllegalStateException(APP_STORE))

  @Suppress("UNUSED_PARAMETER")
  fun readyFile(context: Context, release: Release): File? = null

  @Suppress("UNUSED_PARAMETER")
  fun canInstall(context: Context): Boolean = false

  @Suppress("UNUSED_PARAMETER")
  fun openInstallSettings(context: Context): Result<Unit> = Result.failure(IllegalStateException(APP_STORE))

  @Suppress("UNUSED_PARAMETER")
  fun openInBrowser(context: Context, url: String): Result<Unit> = Result.failure(IllegalStateException(APP_STORE))

  @Suppress("UNUSED_PARAMETER")
  fun install(context: Context, apk: File): Result<Unit> = Result.failure(IllegalStateException(APP_STORE))
}
