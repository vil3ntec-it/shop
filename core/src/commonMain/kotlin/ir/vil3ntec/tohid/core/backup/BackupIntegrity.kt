package ir.vil3ntec.tohid.core.backup

/*
 *  OPS-02 — «بازیابیِ آزموده»: پشتیبانی که کامل نیست یا مالِ این دکان نیست هرگز روی دفتر نمی‌نشیند.
 *
 *  ── چرا ─────────────────────────────────────────────────────────────
 *  `ZipInputStream` ِ جاوا (و shim ِ آیفون) قلم‌ها را پشتِ سرِ هم از سرآیندِ محلی می‌خواند و به فهرستِ پایانیِ
 *  ZIP (central directory) نگاه نمی‌کند. فایلی که درست سرِ مرزِ یک قلم بریده شده (کپیِ نیمه‌کاره روی USB، دانلودِ
 *  قطع‌شده) برایش «تمام» است: دفتر، بی تنظیمات و بی بخشی از عکس‌ها، «پشتیبانِ سالم» دیده و بازیابی می‌شد
 *  (`BackupRestoreDrillTest`، سرخ پیش از این). هر ZIP ِ این برنامه — اندروید، کامپیوتر و آیفون — با رکوردِ
 *  پایانی (END، `PK\5\6`) و شمارِ قلم‌ها بسته می‌شود؛ [ZipTail.complete] همان را با قلم‌های خوانده‌شده می‌سنجد.
 *
 *  ── مُهرِ بسته ──────────────────────────────────────────────────────
 *  `bundle.json` ِ هر پشتیبانِ کامل: نسخهٔ قالب ([BackupStamp.FORMAT_VERSION]) و دکان/حسابِ صاحبِ دفتر
 *  (`LedgerOwner`). پشتیبانِ کهنه‌تر (بی مُهر) = نسخهٔ ۰ و بی صاحب ⇒ همان رفتارِ پیشین. نسخهٔ تازه‌تر ⇒ رد با
 *  «برنامه را به‌روز کنید» (نیمه‌خوانده نمی‌نشیند، همان قاعدهٔ `OwnerExport.parse` و `LedgerRecovery.NEWER_FORMAT`).
 *
 *  Kotlin ِ خالص (commonMain)؛ آزمون: `BackupIntegrityTest` و سرتاسری `BackupRestoreDrillTest` (کامپیوتر).
 */

/** رکوردِ پایانیِ ZIP — آیا فایل تا ته رسیده و همهٔ قلم‌هایش خوانده شد */
object ZipTail {

  /** END (۲۲ بایت) + بیشینهٔ توضیحِ ZIP — بیش از این از ته لازم نیست */
  const val KEEP = 22 + 0xFFFF

  private const val END_SIG = 0x06054b50L

  /**
   *  @param tail آخرین [KEEP] بایتِ فایل (یا همهٔ آن اگر کوتاه‌تر است)
   *  @param entries شمارِ قلم‌هایی که خواننده از سرآیندهای محلی خواند (پوشه‌ها هم)
   *  @return `true` فقط اگر رکوردِ END درست سرِ جایش است (توضیحش تا تهِ فایل) و همان شمارِ قلم را می‌گوید.
   *    ZIP64 (بیش از ۶۵٬۵۳۴ قلم: شمار ‎0xFFFF‎) ⇒ کافی است دست‌کم همان‌قدر خوانده شده باشد.
   */
  fun complete(tail: ByteArray, entries: Int): Boolean {
    if (tail.size < 22) return false
    var i = tail.size - 22
    val stop = maxOf(0, tail.size - KEEP)
    while (i >= stop) {
      if (u32(tail, i) == END_SIG && i + 22 + u16(tail, i + 20) == tail.size) {
        val total = u16(tail, i + 10)
        return if (total == 0xFFFF) entries >= 0xFFFF else total == entries
      }
      i--
    }
    return false
  }

  private fun u16(b: ByteArray, at: Int): Int = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)
  private fun u32(b: ByteArray, at: Int): Long = u16(b, at).toLong() or (u16(b, at + 2).toLong() shl 16)

  /** نگه‌داشتِ آخرین [KEEP] بایتِ جریانی که از آن می‌گذرد — بی خواندنِ همهٔ فایل در حافظه */
  class Tail(private val cap: Int = KEEP) {
    private val ring = ByteArray(cap)
    private var total = 0L

    fun add(b: ByteArray, off: Int, len: Int) {
      if (len <= 0) return
      //  تکهٔ بلندتر از پنجره: سرش فقط شمرده می‌شود
      val skip = if (len > cap) len - cap else 0
      total += skip
      for (k in skip until len) {
        ring[(total % cap).toInt()] = b[off + k]
        total++
      }
    }

    fun bytes(): ByteArray {
      val n = minOf(total, cap.toLong()).toInt()
      val start = total - n
      return ByteArray(n) { ring[((start + it) % cap).toInt()] }
    }
  }
}

/** مُهرِ `bundle.json` و قاعدهٔ «پشتیبانِ دکانِ دیگر» */
object BackupStamp {

  const val ENTRY = "bundle.json"

  /** ۱ = نخستین قالبِ مُهردار (۱۴۰۵/۰۸/۱۹)؛ بی مُهر = ۰ */
  const val FORMAT_VERSION = 1

  data class Stamp(val formatVersion: Int, val user: String, val shop: String)

  /** پشتیبانِ پیش از مُهر */
  val LEGACY = Stamp(0, "", "")

  enum class Verdict { OK, NEWER_FORMAT, OTHER_SHOP }

  /**
   *  @param deskUser / [deskShop] صاحبِ دفترِ روی میز (`LedgerOwner`)؛ خالی = بی‌صاحب
   *
   *  همان قاعدهٔ `LedgerOwner`: دفترِ یک دکان هرگز به دکانِ دیگر نمی‌رود. هر دو دکان معلوم ⇒ همان دکان باید
   *  باشد (کارمند و صاحبِ یک دکان یک دفتر دارند)؛ دکانِ یکی نامعلوم ولی هر دو حساب معلوم ⇒ همان حساب. پشتیبانِ
   *  بی مُهر (کهنه) یا دفترِ بی‌صاحب ⇒ داوری نیست (همان رفتارِ پیشین) — ردِ پشتیبانِ قدیمیِ خودِ کاربر بدتر بود.
   */
  fun judge(stamp: Stamp, deskUser: String, deskShop: String): Verdict = when {
    stamp.formatVersion > FORMAT_VERSION -> Verdict.NEWER_FORMAT
    stamp.shop.isNotBlank() && deskShop.isNotBlank() -> if (stamp.shop == deskShop) Verdict.OK else Verdict.OTHER_SHOP
    stamp.user.isNotBlank() && deskUser.isNotBlank() -> if (stamp.user == deskUser) Verdict.OK else Verdict.OTHER_SHOP
    else -> Verdict.OK
  }
}
