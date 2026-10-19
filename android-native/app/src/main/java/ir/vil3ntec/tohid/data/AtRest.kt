package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.atrest.AtRestCodec
import ir.vil3ntec.tohid.core.atrest.AtRestEnvelope
import ir.vil3ntec.tohid.core.atrest.AtRestException
import ir.vil3ntec.tohid.core.atrest.AtRestFiles
import ir.vil3ntec.tohid.core.atrest.AtRestKeyring
import ir.vil3ntec.tohid.core.atrest.AtRestMigration
import java.io.File
import java.io.FileOutputStream

/**
 *  رمزگذاریِ دفتر روی همین دستگاه — SEC-01، فاز ۱ (پیش‌فرض **خاموش**).
 *
 *  ── چه چیزی رمز می‌شود (فاز ۱) ──────────────────────────────────────
 *   • دفترِ روی میز (`shop-data.json`) و دفترهای بایگانی‌شدهٔ هر حساب (`ledger-<حساب>.json`)
 *   • نسخهٔ ایمنیِ پیش از بازیابی (`before-restore.json`)
 *   • بایگانیِ ردیف‌های کهنه (`ledger-archive.json`)
 *   • نسخه‌های خودکارِ نیم‌ساعته (رونوشتِ بایت‌به‌بایتِ دفترند، پس خودشان رمزدار می‌شوند)
 *  کلیدِ داده (DEK) با AES-256-GCM فایل‌ها را رمز می‌کند و خودش فقط پیچیده
 *  در کلیدِ Keystore روی دیسک است (`AtRestKeyring`).
 *
 *  ── چه چیزی عمداً ساده می‌ماند ──────────────────────────────────────
 *   • پشتیبانی که **بیرون** می‌رود (فایلِ دستی، پوشهٔ قرینه، پشتیبانِ ابری):
 *     از متنِ بازشده ساخته می‌شود. بند ۱۰٫۳: پشتیبانِ دستگاه با کلیدِ دستگاه
 *     رمز نمی‌شود، وگرنه با از دست رفتنِ گوشی پشتیبان هم باز نمی‌شد.
 *   • توکن‌ها از پیش در Keystore رمزند (`TokenStore`).
 *
 *  ── قاعده‌ها ────────────────────────────────────────────────────────
 *   • خواننده‌ها **هر دو قالب** را می‌خوانند؛ روشن/خاموش بودن فقط نوشتن را عوض می‌کند.
 *   • خاموش (پیش‌فرض) ⇒ همان بایت‌هایی نوشته می‌شود که پیش‌تر؛ هیچ تماسی با Keystore.
 *   • روشن ولی کلید ساخته/باز نشد ⇒ دفتر **ساده** نوشته می‌شود و صفحهٔ امنیت
 *     می‌گوید؛ رمز هرگز جلوی ثبتِ فروش را نمی‌گیرد.
 *   • فایلِ رمزداری که باز نشد هرگز با دادهٔ خالی رونویسی نمی‌شود (`ShopStore.unreadable`).
 */
object AtRest {

  private const val PREFS = "tohid"

  /** کلیدِ تنظیم — مالِ **دستگاه**، نه حساب (کلید هم مالِ همین دستگاه است) */
  const val PREF_KEY = "atrest_encrypt"

  /** قفلِ فایل‌های غیرِ دفتر (بایگانی، نسخهٔ ایمنی) میانِ نویسنده‌شان و کوچ */
  val lock = Any()

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun wanted(context: Context): Boolean = runCatching { prefs(context).getBoolean(PREF_KEY, false) }.getOrDefault(false)

  /** با `commit` — تنظیم پیش از کوچ روی دیسک بنشیند */
  fun setWanted(context: Context, on: Boolean): Boolean = prefs(context).edit().putBoolean(PREF_KEY, on).commit()

  @Volatile private var ring: AtRestKeyring? = null
  @Volatile private var codecOnce: AtRestCodec? = null

  fun keyring(context: Context): AtRestKeyring = ring ?: synchronized(this) {
    ring ?: AtRestKeyring(
      File(File(context.applicationContext.filesDir, "atrest"), "keyring"),
      AtRestKeys.kek(context.applicationContext),
      Disk,
    ).also { ring = it }
  }

  private fun codec(context: Context): AtRestCodec = codecOnce ?: synchronized(this) {
    codecOnce ?: AtRestCodec(keyring(context)).also { codecOnce = it }
  }

  /**
   *  بایت‌های بازِ یک فایل، رمزدار یا ساده.
   *  @throws AtRestException اگر رمزدار است و باز نشد — **هرگز** دادهٔ خالی
   */
  fun readBytes(context: Context, f: File): ByteArray = codec(context).open(f.readBytes())

  fun readText(context: Context, f: File): String = String(readBytes(context, f), Charsets.UTF_8)

  /** آخرین شکستِ رمز (روشن ولی ساده نوشته شد) — برای صفحهٔ امنیت */
  @Volatile var sealError: String? = null
    private set

  /** بایت‌هایی که روی دیسک می‌روند: رمزدار اگر روشن است و کلید هست؛ وگرنه ساده */
  fun encode(context: Context, plain: ByteArray): ByteArray {
    if (!wanted(context)) return plain
    return try {
      codec(context).seal(plain).also { sealError = null }
    } catch (e: AtRestException) {
      sealError = e.message ?: "کلیدِ رمز در دسترس نیست"
      plain
    }
  }

  /** همان `DiskWrite.text`، با رمز اگر روشن است */
  fun writeText(context: Context, f: File, text: String) =
    DiskWrite.bytes(f, encode(context, text.toByteArray(Charsets.UTF_8)))

  /** فایل‌هایی که فاز ۱ پوشش می‌دهد — دفترِ روی میز اول */
  fun files(context: Context): List<File> {
    val dir = context.applicationContext.filesDir
    val vaults = dir.listFiles().orEmpty().filter {
      it.isFile && it.name.startsWith("ledger-") && it.name.endsWith(".json") && it.name != "ledger-summary.json"
    }.sortedBy { it.name }
    val snaps = runCatching { AutoBackup.list(context).map { it.file } }.getOrDefault(emptyList())
    return listOf(File(dir, "shop-data.json"), File(dir, "before-restore.json")) + vaults + snaps
  }

  /**
   *  هم‌سو کردنِ فایل‌ها با تنظیم: روشن ⇒ ساده‌ها رمز می‌شوند؛ خاموش ⇒
   *  رمزدارها ساده می‌شوند (راهِ برگشت). یک بار در هر بازشدن و با هر تغییرِ تنظیم.
   *
   *  @param ledgerLock همان قفلی که نوشتنِ دفتر زیرش است (`SyncV1Engine`)
   */
  fun converge(context: Context, ledgerLock: Any): AtRestMigration.Report {
    val want = wanted(context)
    val all = files(context)
    //  دفترِ روی میز زیرِ قفلِ نوشتنِ دفتر؛ بقیه زیرِ قفلِ خودشان — تا رمز کردنِ
    //  نسخه‌های خودکار ثبتِ فروش را منتظر نگذارد
    val live = synchronized(ledgerLock) { AtRestMigration.convertAll(all.take(1), want, codec(context), Disk) }
    val rest = synchronized(lock) { AtRestMigration.convertAll(all.drop(1), want, codec(context), Disk) }
    return AtRestMigration.Report(live.converted + rest.converted, live.already + rest.already, live.failed + rest.failed)
  }

  data class Status(
    val wanted: Boolean,
    val key: AtRestKeyring.State,
    val sealed: Int,
    val plain: Int,
    val sealError: String?,
  )

  /** برای صفحهٔ امنیت؛ فقط سرآیندِ فایل‌ها خوانده می‌شود */
  fun status(context: Context): Status {
    val fs = files(context).filter { it.isFile }
    val sealed = fs.count { AtRestEnvelope.isSealed(it) }
    val want = wanted(context)
    //  وقتی خاموش است و هیچ فایلِ رمزداری نیست، Keystore اصلاً لمس نمی‌شود
    val key = if (!want && sealed == 0) AtRestKeyring.State.None else keyring(context).state()
    return Status(want, key, sealed, fs.size - sealed, sealError)
  }

  /** نوشتن روی دیسک با همان `DiskWrite` (fsync + جابه‌جاییِ اتمی که روی ویندوز هم جایگزین می‌کند) */
  private object Disk : AtRestFiles {
    override fun read(f: File): ByteArray = f.readBytes()

    override fun writeSynced(f: File, bytes: ByteArray) {
      f.absoluteFile.parentFile?.mkdirs()
      FileOutputStream(f).use { out ->
        out.write(bytes)
        out.flush()
        out.fd.sync()
      }
    }

    override fun replace(from: File, to: File) = DiskWrite.move(from, to)

    override fun syncDir(dir: File) = DiskWrite.syncDir(dir)

    override fun delete(f: File): Boolean = f.delete()
  }
}
