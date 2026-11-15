package ir.vil3ntec.tohid.update

import android.content.Context
import java.io.File

/**
 *  نسخهٔ فروشگاهی (`-Pstore=play` و `-Pstore=bazaar`) — **بی به‌روزرسانیِ درونِ برنامه**.
 *
 *  تصمیمِ صاحب‌کار (۱۴۰۵/۰۸/۲۳): برنامه‌ای که از Google Play یا کافه‌بازار نصب شده فقط از همان فروشگاه به‌روز می‌شود. پیش‌تر
 *  این فقط در اجرا خاموش بود (`StoreChannel.storeManaged(BuildConfig.STORE)`) و کدِ دانلود و نصب با نشانی‌های مخزن درونِ همان
 *  بسته می‌ماند؛ حالا `update/Updater.kt` ِ واقعی در `src/direct/java` است و فقط نسخهٔ مستقیم و پیش‌نمایش آن را کامپایل می‌کنند
 *  (`app/build.gradle.kts` ‹srcDir›). این پرونده همان API است تا صدازننده‌ها (`UpdateManager`، کارتِ «به‌روزرسانی»، نوارِ
 *  هشدار) بی تغییر کامپایل شوند — بی شبکه، بی نشانی و بی نصب: «نسخهٔ تازه‌ای نیست» و هر کارِ دیگر شکستِ بی‌صدا.
 *
 *  ⛔ هیچ نشانی و نامِ مخزنی این‌جا نمی‌آید (نگهبان: `server/test/store-build-updater.test.js`، که همسانیِ API با
 *  `src/direct/java/.../Updater.kt` را هم می‌سنجد).
 */
object Updater {

  /** نسخهٔ فروشگاهی پیوندِ دانلودِ بیرونی ندارد */
  @Suppress("UNUSED_PARAMETER")
  fun stableApkUrl(repo: String): String = ""

  var lastNote: String? = null
    private set

  data class Release(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val size: Long,
    val sumsUrl: String = "",
    val minVersion: String = "",
    val movedTo: String = "",
  )

  /** همیشه «نسخهٔ تازه‌ای نیست»: به‌روزرسانی کارِ خودِ فروشگاه است */
  @Suppress("UNUSED_PARAMETER")
  suspend fun check(
    repo: String,
    currentVersion: String,
    server: String = "",
    shop: String = "",
    device: String = "",
  ): Result<Release?> = Result.success(null)

  fun versionOf(assetName: String, tag: String): String = ReleaseVersion.of(assetName, tag)

  fun isNewer(remote: String, local: String): Boolean = ReleaseVersion.isNewer(remote, local)

  @Suppress("UNUSED_PARAMETER")
  suspend fun download(
    context: Context,
    release: Release,
    onStatus: (String?) -> Unit = {},
    onProgress: (Int) -> Unit,
  ): Result<File> = Result.failure(UnsupportedOperationException())

  @Suppress("UNUSED_PARAMETER")
  fun foreignPackage(context: Context, apk: File): String? = null

  @Suppress("UNUSED_PARAMETER")
  fun readyFile(context: Context, release: Release): File? = null

  @Suppress("UNUSED_PARAMETER")
  fun canInstall(context: Context): Boolean = false

  @Suppress("UNUSED_PARAMETER")
  fun openInstallSettings(context: Context): Result<Unit> = Result.failure(UnsupportedOperationException())

  @Suppress("UNUSED_PARAMETER")
  fun openInBrowser(context: Context, url: String): Result<Unit> = Result.failure(UnsupportedOperationException())

  @Suppress("UNUSED_PARAMETER")
  fun install(context: Context, apk: File): Result<Unit> = Result.failure(UnsupportedOperationException())
}
