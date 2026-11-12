package ir.vil3ntec.tohid.core.terminal

import ir.vil3ntec.tohid.core.pay.PaymentRow
import ir.vil3ntec.tohid.core.pay.PayStatus
import kotlin.random.Random

/*
 *  PAY-08 — سطحِ دومِ تأیید: «تأییدِ دستگاه» (کارت‌خوان یا دستگاهِ پرداختِ یکپارچه که خودش پاسخ می‌دهد)، کنارِ «ثبتِ دستی»
 *  (`verification = manual`/`cashier`، صندوقدار پولِ رسیده را زد) و «تأییدِ ارائه‌دهنده» (`gateway`، قصدِ سرور با وب‌هوکِ
 *  امضاشده و تأییدِ دوم — `core/gateway/HesabTill`). کاتلینِ خالص، بی ساعت و بی شبکه (`DevicePayTest` با دستگاهِ ساختگی).
 *
 *  ── قرارداد با دستگاه ([VerifyingTerminal]) ─────────────────────────────
 *  هر درخواست یک **کلیدِ یکتایی** ([DeviceAttempt.key]) دارد که پیش از نخستین درخواست ساخته و نگه داشته می‌شود (همان قاعدهٔ
 *  `HesabPayAttempts`): تکرارِ همان کلید (قطعِ شبکه، بسته شدنِ برنامه وسطِ پرداخت) هرگز پولِ دوم نمی‌گیرد و پاسخِ همان را
 *  می‌دهد؛ [VerifyingTerminal.lookup] پس از ازسرگیری می‌پرسد «با این کلید چه شد؟». پاسخِ دستگاه ([DeviceReceipt]) کلید، کدِ
 *  فاکتور، مبلغ و ارز را **بازمی‌گرداند** و شمارهٔ تأییدِ خودش را دارد.
 *
 *  ── «موفق» فقط با تأییدِ قابلِ اتکا ([verdict]) ────────────────────────
 *   • تأییدِ دستگاه با همان کلید، همان فاکتور، همان مبلغ و AFN و شمارهٔ تأییدِ درست ⇒ ردیفِ `confirmed` با
 *     `verification = device` ([PaymentRow]) — تنها راهِ «موفق» در این سطح.
 *   • تأییدی که چیزی‌اش نمی‌خواند (فاکتورِ دیگر، مبلغ یا ارزِ دیگر، کلیدِ دیگر، بی شمارهٔ تأیید) یا **پس از** لغو/پایانِ
 *     مهلت رسید ⇒ ردیفِ `needs_review` (پول شاید گرفته شده؛ هرگز گم یا بی‌صدا نیست، هرگز «موفق» نیست).
 *   • رد/لغو/در دسترس نبودن ⇒ هیچ ردیفی.
 *   • ⛔ کارت‌خوانِ دستی ([ManualCardTerminal]) هرگز این سطح نیست — همان «ثبتِ دستی» ِ PAY-06.
 *
 *  ── یک بار ([record]) ───────────────────────────────────────────────
 *  پاسخِ تکراری (دو بار برگشتنِ همان نتیجه، ازسرگیری پس از ثبت) ردیفِ دوم نمی‌سازد: شمارهٔ تأییدِ دستگاهِ هر ردیفِ ثبت‌شده
 *  (و برای تأییدِ بی‌شماره، کلیدِ همان تلاش) شمرده می‌شود.
 *
 *  ⛔ هیچ دستگاهِ واقعی‌ای این رابط را ندارد تا سندِ یکپارچه‌سازیِ کارت‌خوان/بانک یا حساب‌پی برسد (D-08؛ `VendorAppTerminal`).
 */

/** یک تلاشِ پرداخت با دستگاه برای یک فاکتور — کلید پیش از نخستین درخواست ساخته و نگه داشته می‌شود */
data class DeviceAttempt(
  val saleKey: String,
  val amountMinor: Long,
  /** کلیدِ یکتایی: `dev-<کلیدِ فاکتور>-<۱۲ نویسه>` */
  val key: String,
  /** مهلتِ پاسخ (ساعتِ همین دستگاه) */
  val until: Long,
  val currency: String = "AFN",
  /** روشِ جدولِ PAY-06 (`afpay_card`) */
  val method: String = "afpay_card",
)

/** پاسخِ دستگاه — هرچه دستگاه برگرداند، نه آنچه ما فرستادیم */
data class DeviceReceipt(
  val key: String,
  val saleRef: String,
  val amountMinor: Long,
  val currency: String,
  val status: Status,
  /** شمارهٔ تأیید/مرجعِ دستگاه (فقط با [Status.APPROVED]) */
  val approval: String = "",
  /** فقط چهار رقمِ آخر، اگر داده شد */
  val maskedCard: String? = null,
) {
  enum class Status { APPROVED, DECLINED, CANCELLED, PENDING }
}

/** دستگاهی که خودش تأیید می‌کند (برنامه به برنامه) — امروز هیچ پیاده‌سازیِ واقعی ندارد */
interface VerifyingTerminal {
  val id: String

  /** درخواستِ پرداخت با [DeviceAttempt.key]؛ تکرارِ همان کلید ⇒ پاسخِ همان، نه پولِ دوم. `null` ⇒ در دسترس نیست */
  suspend fun pay(attempt: DeviceAttempt): DeviceReceipt?

  /** پس از ازسرگیری: با این کلید چه شد؟ `null` ⇒ دستگاه چیزی با این کلید ندیده */
  suspend fun lookup(key: String): DeviceReceipt?
}

object DevicePay {

  /** سطحِ «تأییدِ دستگاه» ِ PAY-08 */
  const val DEVICE = "device"

  enum class Hold { OTHER_KEY, OTHER_SALE, AMOUNT, NO_APPROVAL, LATE }

  sealed interface Verdict {
    /** «موفق»: ردیفِ `confirmed` و `device` */
    data class Confirmed(val row: PaymentRow) : Verdict
    /** تأیید رسید ولی چیزی نمی‌خواند ⇒ ردیفِ «نیازمندِ بررسی» */
    data class Review(val row: PaymentRow, val hold: Hold) : Verdict
    /** هنوز پاسخی نیست (`PENDING`) — همان کلید دوباره پرسیده می‌شود */
    data object Waiting : Verdict
    /** رد/لغو ⇒ هیچ ردیفی */
    data class None(val status: DeviceReceipt.Status) : Verdict
  }

  /** کلیدِ یکتاییِ تازه — همان الفبا و اندازهٔ `HesabPayAttempts.newKey` */
  fun newKey(saleKey: String, random: Random = Random.Default): String {
    val safe = saleKey.filter { it.isLetterOrDigit() && it.code < 128 }.take(64)
    val tail = CharArray(12) { ALPHABET[random.nextInt(ALPHABET.length)] }.concatToString()
    return "dev-$safe-$tail"
  }

  /** تلاشِ تازه، یا همان تلاشِ بازِ همین فاکتور و مبلغ (ازسرگیری، نه پولِ دوم) */
  fun attempt(open: DeviceAttempt?, saleKey: String, amountMinor: Long, now: Long, ttlMs: Long, random: Random = Random.Default): DeviceAttempt =
    open?.takeIf { it.saleKey == saleKey && it.amountMinor == amountMinor && now < it.until }
      ?: DeviceAttempt(saleKey, amountMinor, newKey(saleKey, random), now + ttlMs)

  /**
   *  پاسخِ دستگاه ⇒ آنچه ثبت می‌شود. @param closed تلاش پیش از رسیدنِ این پاسخ لغو شد یا مهلتش گذشت
   */
  fun verdict(a: DeviceAttempt, r: DeviceReceipt, closed: Boolean = false): Verdict {
    if (r.status == DeviceReceipt.Status.PENDING) return if (closed) Verdict.None(DeviceReceipt.Status.CANCELLED) else Verdict.Waiting
    if (r.status != DeviceReceipt.Status.APPROVED) return Verdict.None(r.status)
    val approval = ManualCardTerminal.reference(r.approval)
    val hold = when {
      r.key != a.key -> Hold.OTHER_KEY
      r.saleRef != a.saleKey -> Hold.OTHER_SALE
      r.amountMinor != a.amountMinor || r.currency != a.currency -> Hold.AMOUNT
      approval == null -> Hold.NO_APPROVAL
      closed -> Hold.LATE
      else -> null
    }
    //  مرجع = شمارهٔ تأییدِ دستگاه (یکتا در دستگاه)؛ بی آن، کلیدِ همین تلاش تا ردیفِ بررسی هم یک بار ثبت شود
    val ref = approval?.let { it + (r.maskedCard?.let { m -> " ($m)" } ?: "") } ?: "$NO_REF${a.key}"
    //  مبلغِ ردیف همان که دستگاه گفت گرفت (برای بررسی)؛ «موفق» فقط وقتی برابرِ تلاش است
    val row = PaymentRow(a.method, "card", r.amountMinor, ref, if (hold == null) PayStatus.CONFIRMED else PayStatus.NEEDS_REVIEW, DEVICE)
    return if (hold == null) Verdict.Confirmed(row) else Verdict.Review(row, hold)
  }

  /** نتیجهٔ [record] */
  sealed interface Recorded {
    data class Added(val rows: List<PaymentRow>) : Recorded
    /** همین تلاش (یا همین شمارهٔ تأییدِ دستگاه) پیش‌تر ثبت شده ⇒ ردیفِ دوم نه */
    data object Already : Recorded
  }

  /** ردیفِ تازه به ردیف‌های فاکتور، فقط یک بار برای هر کلید یا شمارهٔ تأیید */
  fun record(rows: List<PaymentRow>, v: Verdict): Recorded {
    val row = when (v) {
      is Verdict.Confirmed -> v.row
      is Verdict.Review -> v.row
      else -> return Recorded.Added(rows)
    }
    val id = idOf(row.ref)
    val seen = rows.any { it.verification == DEVICE && idOf(it.ref) == id }
    return if (seen) Recorded.Already else Recorded.Added(rows + row)
  }

  /**
   *  یک دورِ کامل با دستگاه: اول [VerifyingTerminal.lookup] (ازسرگیری — شاید در نبودنِ برنامه پرداخت شده)، وگرنه
   *  [VerifyingTerminal.pay] با همان کلید. دستگاهِ در دسترس نبود ⇒ [Verdict.Waiting].
   */
  suspend fun run(t: VerifyingTerminal, a: DeviceAttempt, now: Long, closed: Boolean = false): Verdict {
    val late = closed || now >= a.until
    t.lookup(a.key)?.let { seen -> if (seen.status != DeviceReceipt.Status.PENDING || late) return verdict(a, seen, late) }
    if (late) return Verdict.None(DeviceReceipt.Status.CANCELLED)
    val r = t.pay(a) ?: return Verdict.Waiting
    return verdict(a, r)
  }

  /** شناسهٔ یکتای ردیفِ دستگاه: شمارهٔ تأیید (بی چهار رقمِ کارت)، یا کلیدِ تلاشِ بی‌تأیید */
  private fun idOf(ref: String?): String = ref.orEmpty().substringBefore(" (")

  private const val NO_REF = "?"
  private const val ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789"
}
