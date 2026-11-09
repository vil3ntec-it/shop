package ir.vil3ntec.tohid.data

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/**
 *  DATA-SAFETY §۸ — پیش‌فاکتورهای همین دستگاه (`quotes.json`، POS-25) در هر پشتیبانِ کامل (دستی، پوشه، ابریِ دوازده‌ساعته).
 *
 *  ── اشکال ────────────────────────────────────────────────────────────
 *  پیش‌فاکتور (قیمتی که به مشتری داده شد، با روزِ آخرِ اعتبار) عمداً نه در دفتر است نه در همگام‌سازی (سندِ مالی
 *  نیست، `QuoteBook`) — ولی در پشتیبان هم نبود: با پاک کردن یا نصبِ دوبارهٔ برنامه همه می‌رفت و دکان‌دار باید از نو
 *  می‌نوشت. حالا `BackupBundle.write` آن را در `device/quotes.json` ِ ZIP می‌گذارد، پس با پشتیبانِ ابری به سرور
 *  می‌رسد و `CloudSettingsRestore` پس از ورود روی گوشیِ تازه برش می‌گرداند.
 *
 *  ⛔ بازیابی فقط وقتی این دستگاه **هیچ** پیش‌فاکتوری ندارد (`QuoteStore.update`، زیرِ همان قفل و با `DiskWrite`):
 *     پیش‌فاکتورِ موجود هرگز رونویسی یا با شماره‌های پشتیبان قاطی نمی‌شود. پروندهٔ ناخوانا هیچ اثری ندارد.
 *
 *  ── فاکتورهای نگه‌داشته و سبدِ جاری (`device/held-invoices.json`، `HeldBackup`) ─────────────────────
 *  با پرچمِ خاموشِ `shop.held_sync` (پیش‌فرض) در هیچ پشتیبانی نبودند. همان راه: کنار تا تأییدِ بازیابی، و فقط **پس از نشستنِ
 *  دفتر** ([commitHeld] از `BackupRestore.confirm` و `CloudSettingsRestore`، هرگز از `commitPhotos`) — دفترِ ردشده هیچ
 *  نگه‌داشته‌ای نمی‌نشاند. نشاندن از درِ خودِ دفترِ زنده (`HeldInvoiceStore.absorb`، زیرِ همان قفل و حافظه)، نه نوشتنِ پرونده
 *  زیرِ پایش (نخستین نگه‌داشتنِ بعدی پاکش می‌کرد). قاعده‌ها (همان دکان، دستگاهِ بی فاکتورِ باز، نه فروخته؛ راهِ ابری فقط
 *  پشتیبانِ همین دستگاه): `HeldBackupRules`.
 *  ⛔ پرچمِ `shop.held_sync` روشن روی این دستگاه ⇒ نگه‌داشته‌ها از سرور می‌آیند (S09)، نه از پشتیبانِ کهنه. آموزش هرگز.
 */
object DeviceDocsBundle {

  const val QUOTES_ENTRY = "device/quotes.json"
  const val HELD_ENTRY = "device/held-invoices.json"

  /** بیش از این، پروندهٔ پیش‌فاکتور نیست */
  const val MAX_BYTES = 2 * 1024 * 1024

  private fun staged(context: Context): File = File(context.cacheDir, "restore-quotes.json")
  private fun stagedHeld(context: Context): File = File(context.cacheDir, "restore-held.json")

  /** `BackupBundle.write`: پیش‌فاکتورهای خوانا و ناتهی ⇒ متنِ همان پرونده؛ هیچ ⇒ null */
  fun quotes(context: Context): String? {
    val book = runCatching { QuoteStore.read(context) }.getOrNull() ?: return null
    return if (book.items.isEmpty()) null else QuoteBookCodec.encode(book)
  }

  /** `BackupBundle.read`: تا تأییدِ بازیابی کنار می‌ماند (همان قاعدهٔ عکس‌ها و تنظیمات) */
  fun stageFrom(context: Context, input: InputStream) = stage(input, staged(context))

  /** `BackupBundle.read`: نگه‌داشته‌ها و سبدِ جاری — تا تأییدِ بازیابی و نشستنِ دفتر کنار */
  fun stageHeldFrom(context: Context, input: InputStream) = stage(input, stagedHeld(context))

  private fun stage(input: InputStream, to: File) {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(8 * 1024)
    while (out.size() <= MAX_BYTES) {
      val n = input.read(buf, 0, buf.size)
      if (n <= 0) break
      out.write(buf, 0, n)
    }
    if (out.size() > MAX_BYTES) return
    runCatching { to.writeText(out.toString(Charsets.UTF_8.name())) }
  }

  fun dropStaged(context: Context) {
    runCatching { staged(context).delete() }
    runCatching { stagedHeld(context).delete() }
  }

  /** `BackupBundle.write`: نگه‌داشته‌ها و سبدِ جاریِ همین حساب روی این دستگاه؛ هیچ (یا آموزش) ⇒ null */
  fun held(context: Context): String? {
    if (TrainingMode.on) return null
    val dir = context.applicationContext?.filesDir ?: context.filesDir
    fun text(name: String): String? = runCatching { File(dir, name).takeIf { it.isFile }?.readText(Charsets.UTF_8) }.getOrNull()
    //  پرونده همان حافظه است: `HeldInvoiceStore` پیش از نشاندن در حافظه می‌نویسد، `CartStore` همیشه آخرین حالت را
    val book = HeldInvoiceCodec.decode(text(HeldInvoiceStore.FILE)) ?: return null
    val cart = SaleDraftCodec.decode(text(CartStore.FILE))
    val b = HeldBackupRules.of(
      book, cart, runCatching { ir.vil3ntec.tohid.sync.DocSeries.current(context) }.getOrDefault(""),
      LedgerOwner.currentUser(context), LedgerOwner.currentShop(context), System.currentTimeMillis(), machine(context),
    ) ?: return null
    return HeldBackupRules.encode(b)
  }

  private fun machine(context: Context): String =
    runCatching { HeldBackupRules.machineOf(ir.vil3ntec.tohid.ui.Platform.machineFingerprint(context)) }.getOrDefault("")

  /**
   *  پس از نشستنِ دفتر ([d] = دفترِ روی میز) — @return چند فاکتورِ نگه‌داشته نشست. هر شکستی ⇒ ۰ و هیچ تغییری.
   *  @param sameMachineOnly راهِ خودکارِ پس از ورود (`CloudSettingsRestore`): فقط پشتیبانی که همین دستگاه نوشت
   */
  suspend fun commitHeld(context: Context, d: ShopData, sameMachineOnly: Boolean = false): Int {
    val f = stagedHeld(context)
    if (!f.isFile) return 0
    val backup = HeldBackupRules.decode(runCatching { f.readText(Charsets.UTF_8) }.getOrNull())
    runCatching { f.delete() }
    if (backup == null || TrainingMode.on || HeldSync.claims(context)) return 0
    val dir = context.applicationContext?.filesDir ?: context.filesDir
    //  سبدِ همین حالا: صندوقِ باز (حافظه) یا پیش‌نویسِ روی دیسک
    val live = runCatching { HeldSync.cart?.snapshot() }.getOrNull()?.takeIf { it.lines.isNotEmpty() }
      ?: SaleDraftCodec.decode(runCatching { File(dir, CartStore.FILE).takeIf { it.isFile }?.readText(Charsets.UTF_8) }.getOrNull())
    val user = LedgerOwner.currentUser(context)
    val shop = LedgerOwner.currentShop(context)
    val machine = if (sameMachineOnly) machine(context) else null
    var added = 0
    val ok = HeldInvoices.of(context).absorb { mine ->
      val next = HeldBackupRules.restore(mine, backup, user, shop, live, d, machine) ?: return@absorb mine
      added = next.items.size - mine.items.size
      next
    }
    return if (ok) added else 0
  }

  /** پس از تأییدِ بازیابی (`BackupBundle.commitPhotos`) — @return چند پیش‌فاکتور نشست */
  fun commit(context: Context): Int {
    val f = staged(context)
    if (!f.isFile) return 0
    val book = runCatching { QuoteBookCodec.decode(f.readText()) }.getOrNull()
    f.delete()
    if (book == null || book.items.isEmpty()) return 0
    val wrote = QuoteStore.update(context) { mine -> if (mine.items.isEmpty()) book else null }
    return wrote?.items?.size ?: 0
  }
}
