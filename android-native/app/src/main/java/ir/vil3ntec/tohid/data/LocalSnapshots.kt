package ir.vil3ntec.tohid.data

import android.annotation.SuppressLint
import android.os.Build
import ir.vil3ntec.tohid.core.backup.BackupRetention
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files

/**
 *  نسخه‌های خودکارِ دفتر روی خودِ دستگاه — کارِ فایل‌ها، جدا از زمان‌بندی
 *  (`AutoBackup`) تا بی WorkManager آزموده شود (`LocalSnapshotsTest`).
 *
 *  ── فقط اگر دفتر عوض شده (OPS-02: «هر ۱۵ تا ۳۰ دقیقه») ─────────────
 *  نسخهٔ تازه فقط وقتی ساخته می‌شود که دفتر با تازه‌ترین نسخه بایت‌به‌بایت
 *  فرق دارد. دکانِ بسته یا گوشیِ بی‌کار هر نیم ساعت فایلِ تکراری نمی‌سازد
 *  و نسخه‌های روزهای قبل را هم با نسخه‌های تکراری از فهرست بیرون نمی‌راند.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  ── هرگز جلوی ثبتِ فروش را نمی‌گیرد ────────────────────────────────
 *  دفتر را `ShopStore` با «فایلِ کناری + جابه‌جاییِ اتمی» (`DiskWrite`)
 *  می‌نویسد. روی ویندوز اگر خوانندهٔ دیگری فایل را بی `FILE_SHARE_DELETE`
 *  باز کرده باشد، آن جابه‌جایی رد می‌شود — یعنی رونوشت‌گیریِ هم‌زمان
 *  می‌توانست ذخیرهٔ یک فروش را بشکند. پس این‌جا دفتر با `Files.newInputStream`
 *  خوانده می‌شود که روی ویندوز «حذف/جایگزینی» را هم اجازه می‌دهد؛ خواننده
 *  همان نسخهٔ قبلی را تا آخر می‌خواند و نویسنده منتظر نمی‌ماند. (اندرویدِ
 *  پیش از ۸ لینوکس است و این مشکل را اصلاً ندارد.) آزمونش روی ویندوزِ
 *  واقعی در `tohid-desktop.yml` ← `disk-windows` می‌دود.
 *  ──────────────────────────────────────────────────────────────────
 */
object LocalSnapshots {

  const val PREFIX = "auto-"
  const val SUFFIX = ".json"

  data class Snapshot(val file: File, val at: Long, val bytes: Long)

  private val lock = Any()

  /** نسخه‌های موجود، تازه‌ترین اول (فایلِ نیمه‌کارهٔ `.tmp` نسخه نیست) */
  fun list(dir: File): List<Snapshot> =
    dir.listFiles().orEmpty()
      .filter { it.isFile && it.name.startsWith(PREFIX) && it.name.endsWith(SUFFIX) }
      .map { Snapshot(it, it.lastModified(), it.length()) }
      .sortedWith(compareByDescending<Snapshot> { it.at }.thenByDescending { it.file.name })

  /**
   *  رونوشتِ دفتر با نامِ `name`، اگر دفتر هست و با تازه‌ترین نسخه فرق دارد.
   *  نسخهٔ ساخته‌شده را برمی‌گرداند، یا null اگر کاری نبود.
   */
  fun takeIfChanged(ledger: File, dir: File, name: String): File? = synchronized(lock) {
    if (!ledger.exists() || ledger.length() == 0L) return null
    val newest = list(dir).firstOrNull()
    if (newest != null && same(ledger, newest.file)) return null
    val target = File(dir, name)
    DiskWrite.stream(target) { out -> openShared(ledger).use { it.copyTo(out) } }
    target
  }

  /** پاک کردنِ نسخه‌هایی که قاعدهٔ «۶ آخر + ۷ روزانه + ۴ هفتگی» نگه نمی‌دارد */
  fun prune(
    dir: File,
    utcOffsetMinutes: (Long) -> Int,
    policy: BackupRetention.Policy = BackupRetention.Policy(),
  ): Int = synchronized(lock) {
    val all = list(dir)
    val keep = BackupRetention.keep(all.map { BackupRetention.Snap(it.file.name, it.at) }, utcOffsetMinutes, policy)
    all.count { it.file.name !in keep && runCatching { it.file.delete() }.getOrDefault(false) }
  }

  /** دو فایل بایت‌به‌بایت یکی‌اند؟ اول اندازه، بعد محتوا (بی خواندنِ همه در حافظه) */
  fun same(a: File, b: File): Boolean {
    if (a.length() != b.length()) return false
    return try {
      openShared(a).use { x -> openShared(b).use { y -> equal(x, y) } }
    } catch (e: IOException) {
      false
    }
  }

  private fun equal(x: InputStream, y: InputStream): Boolean {
    val bx = ByteArray(64 * 1024)
    val by = ByteArray(64 * 1024)
    while (true) {
      val n = fill(x, bx)
      val m = fill(y, by)
      if (n != m) return false
      if (n == 0) return true
      for (i in 0 until n) if (bx[i] != by[i]) return false
    }
  }

  /** پر کردنِ بافر تا جای ممکن (‎`readNBytes`‎ در اندرویدِ پیش از ۱۳ نیست) */
  private fun fill(s: InputStream, buf: ByteArray): Int {
    var total = 0
    while (total < buf.size) {
      val r = s.read(buf, total, buf.size - total)
      if (r < 0) break
      total += r
    }
    return total
  }

  /** خواندنی که جایگزین شدنِ فایل را روی ویندوز نمی‌بندد */
  internal fun openShared(f: File): InputStream =
    if (Build.VERSION.SDK_INT >= 26) Api26.open(f) else FileInputStream(f)

  @SuppressLint("NewApi")
  private object Api26 {
    fun open(f: File): InputStream = Files.newInputStream(f.toPath())
  }
}
