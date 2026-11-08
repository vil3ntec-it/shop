package ir.vil3ntec.tohid.core.sync

/**
 *  «هیچ داده‌ای با خروج، حذف یا نصبِ دوباره گم نمی‌شود» — منطقِ خالصِ مشترکِ گوشی، کامپیوتر و آیفون.
 *
 *  ── ریشهٔ گم شدن (گزارشِ صاحب‌کار، ۱۴۰۵/۰۸/۱۷) ───────────────────────────
 *  «چه کسی روی این دستگاه است» سه جا نوشته می‌شد و هر راهِ ورود فقط یکی‌شان را عوض می‌کرد:
 *   • `LedgerOwner` (دفترِ روی میز = حساب@دکان) — ورودِ رمز/کد/گوگل/کدِ شاگرد، ولی نه برگهٔ «کدِ ایمیلی»؛
 *   • کلیدِ Sync v1 (صف، سایه، cursor) = `SyncStateV1.currentAccount` — **فقط** برگهٔ «کدِ ایمیلی»، وگرنه
 *     **نامِ نمایشیِ** حساب (که خروج پاکش می‌کرد) یا شناسهٔ دستگاه؛
 *   • هیچ‌کدام با خروج پاک یا با ورودِ بعدی هم‌تراز نمی‌شد.
 *  پس پس از «خروج ⇒ ورود با حسابِ دیگر (کدِ شاگرد)» دفترِ خالیِ شاگرد با **سایه و cursor ِ صاحب** کار می‌کرد:
 *  Snapshot هرگز گرفته نمی‌شد (شاگرد دکانِ خالی می‌دید) و نخستین ذخیره برای هر ردیفِ صاحب DELETE می‌ساخت؛
 *  و پس از برگشتن با «کدِ ایمیلی» دفترِ واقعیِ صاحب در بایگانی می‌ماند و دفترِ خالیِ شاگرد روی میز.
 *  ──────────────────────────────────────────────────────────────────────────
 */

/**
 *  خروج از حساب فقط وقتی آزاد است که سرور **همه‌چیز** را گرفته باشد.
 *
 *  پیش‌تر فقط شمارِ صفِ همان کلید شمرده می‌شد: تغییری که هنوز op نشده بود (تفاضلِ دفتر با سایه)، دفتری که هرگز
 *  با سرور هم‌تراز نشده بود و opی که سرور همین حالا رد کرد «صفر» دیده می‌شدند.
 */
object SignOutSafety {

  /** چرا خروج نگه داشته شد — هر کدام پیامِ خودش را دارد */
  enum class Reason { NONE, OFFLINE, NOT_SIGNED_IN, REJECTED, FAILED, NOT_RECONCILED }

  /**
   *  نتیجهٔ «همه را بفرست و بسنج» پیش از خروج.
   *
   *  @param queued opهایی که پس از فرستادن هنوز در صف‌اند
   *  @param legacy شمارِ راهِ قدیم (`AutoSync.pendingCount`)
   *  @param rejected opهایی که سرور **در همین تلاش** نپذیرفت (از صف بیرون رفتند ولی به سرور نرسیدند)
   *  @param online اینترنت بود و نشست زنده
   *  @param failed فرستادن خطا داد (شبکه، ۵xx، نسخهٔ کهنهٔ سرور…)
   *  @param reconciled دفترِ این حساب دست‌کم یک بار با سرور هم‌تراز شده (یا اصلاً ردیفی ندارد)
   */
  data class Flush(
    val queued: Int,
    val legacy: Int = 0,
    val rejected: Int = 0,
    val online: Boolean = true,
    val signedIn: Boolean = true,
    val failed: Boolean = false,
    val reconciled: Boolean = true,
  )

  sealed interface Verdict
  data object Allow : Verdict
  data class Hold(val pending: Int, val reason: Reason) : Verdict

  fun pendingOf(f: Flush): Int =
    f.queued.coerceAtLeast(0) + f.legacy.coerceAtLeast(0) + f.rejected.coerceAtLeast(0)

  fun decide(f: Flush): Verdict {
    val pending = pendingOf(f)
    val reason = when {
      f.rejected > 0 -> Reason.REJECTED
      pending > 0 && !f.signedIn -> Reason.NOT_SIGNED_IN
      pending > 0 && !f.online -> Reason.OFFLINE
      pending > 0 -> Reason.FAILED
      //  صفِ خالی ولی دفتر هرگز به سرور نرسیده (مثلاً آفلاین ساخته شد و هم‌ترازی هنوز نشده) ⇒ «صفر» دروغ است
      !f.reconciled -> if (!f.online) Reason.OFFLINE else Reason.NOT_RECONCILED
      else -> Reason.NONE
    }
    return if (reason == Reason.NONE) Allow else Hold(pending.coerceAtLeast(1), reason)
  }

  /**
   *  خروجِ اجباری فقط با دو تأیید **و** نسخهٔ امنِ تغییرهای نرفته (پشتیبانِ دیدنی + گاوصندوقِ رمزدارِ همان حساب).
   *  بی نسخهٔ امن هیچ خروجی نیست — حتی با هزار تأیید.
   */
  fun canForce(confirmations: Int, backupWritten: Boolean, keptForAccount: Boolean): Boolean =
    confirmations >= 2 && backupWritten && keptForAccount
}

/**
 *  کلیدِ Sync v1 ِ هر حساب **شناسهٔ همان حساب** است، هرگز نامِ نمایشی.
 *
 *  نامِ نمایشی بینِ دو حساب مشترک می‌شد («شاگرد») و خروج پاکش می‌کرد؛ پس کلید با هر خروج و ورود جابه‌جا می‌شد.
 *  حالِ کهنهٔ همان حساب (زیرِ نامِ نمایشیِ پیشین) یک بار به کلیدِ تازه می‌رود تا صفِ نرفته‌اش گم نشود.
 */
object SyncAccountKey {

  /**
   *  حالِ کدام کلیدِ کهنه به کلیدِ شناسهٔ حساب برود — یا هیچ.
   *
   *  @param target کلیدِ تازه (شناسهٔ حساب)
   *  @param targetHasState کلیدِ تازه از پیش حال دارد ⇒ دست نمی‌خورد
   *  @param sameDesk دفترِ روی میز از پیش مالِ همین حساب بود (`LedgerOwner`: SAME، یا سندِ همان حساب)
   *  @param previousCurrent شناسهٔ حسابی که کلیدِ v1 پیش از این ورود به آن بسته بود (خالی = هیچ)
   *  @param candidates کلیدهای کهنهٔ ممکن، به ترتیبِ ترجیح (نامِ نمایشیِ همین حساب، کلیدِ همین حالای موتور)
   *  @param deviceKey کلیدِ «بی حساب» (شناسهٔ دستگاه): هرگز منبع نیست — سایه‌اش پایهٔ بی‌op است
   */
  fun carryFrom(
    target: String,
    targetHasState: Boolean,
    sameDesk: Boolean,
    previousCurrent: String,
    candidates: List<String>,
    deviceKey: String,
    hasState: (String) -> Boolean,
  ): String? {
    if (target.isBlank() || targetHasState || !sameDesk) return null
    //  کلیدِ پیشین به حسابِ دیگری بسته بود ⇒ هیچ چیزی از آن به این حساب نمی‌رسد
    if (previousCurrent.isNotBlank() && previousCurrent != target) return null
    return candidates.firstOrNull { it.isNotBlank() && it != target && it != deviceKey && hasState(it) }
  }
}

/**
 *  دفترِ **کاملاً خالی** هرگز «همه حذف شد» نمی‌فرستد.
 *
 *  هیچ کارِ عادیِ دکان کلِ دفتر (کالا، فروش، مشتری، قرض…) را یک‌جا صفر نمی‌کند؛ دفترِ خالی روی میز یعنی دفترِ
 *  دیگری جای این نشسته (حسابِ دیگر، نصبِ تازه، فایلی که خوانده نشد). تفاضلش با سایهٔ پر برای هر ردیف DELETE
 *  می‌ساخت و با توکنِ صاحب روی سرور و همهٔ دستگاه‌ها می‌نشست.
 */
object EmptyLedgerGuard {
  fun blocks(localRows: Int, shadowRows: Int): Boolean = localRows <= 0 && shadowRows > 0
}
