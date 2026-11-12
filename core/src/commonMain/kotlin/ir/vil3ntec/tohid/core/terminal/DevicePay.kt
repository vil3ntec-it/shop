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
 *  می‌دهد؛ [VerifyingTerminal.lookup] پس از ازسرگیری می‌پرسد «با این کلید چه شد؟» و «ندیده» ([DeviceLookup.Unseen]) را از
 *  «پاسخ نداد» ([DeviceLookup.Unreachable]) جدا می‌گوید. پاسخِ دستگاه ([DeviceReceipt]) کلید، کدِ فاکتور، مبلغ و ارز را
 *  **بازمی‌گرداند** و شمارهٔ تأییدِ خودش را دارد.
 *
 *  ── کلیدِ تازه فقط پس از روشن شدنِ قبلی ([attempt]) ─────────────────
 *  همان فاکتور و مبلغ و پیش از مهلت ⇒ همان تلاش (ازسرگیری). تلاشِ بازی که مهلتش گذشته یا فاکتور/مبلغش عوض شده **هرگز** کلیدِ
 *  تازه نمی‌دهد: [Next.Resolve] ⇒ اول [resolve] (فقط `lookup`، هرگز `pay`) و ثبتِ حکمش، بعد [close]؛ وگرنه پولِ تلاشِ پیشین که
 *  در نبودنِ برنامه گرفته شد با کلیدِ تازه دوباره گرفته می‌شد. تلاشِ بسته ([DeviceAttempt.done]: تأیید، بررسی، رد یا لغو) هرگز
 *  دوباره به کار نمی‌رود ⇒ کلیدِ تازه (بی آن، «دوباره بکش» پس از رد تا پایانِ مهلت همان «رد» ِ ذخیره را می‌گرفت).
 *
 *  ── «موفق» فقط با تأییدِ قابلِ اتکا ([verdict]) ────────────────────────
 *   • تأییدِ دستگاه با همان کلید، همان فاکتور، همان مبلغ و AFN و شمارهٔ تأییدِ درست ⇒ ردیفِ `confirmed` با
 *     `verification = device` ([PaymentRow]) — تنها راهِ «موفق» در این سطح.
 *   • تأییدی که چیزی‌اش نمی‌خواند (فاکتورِ دیگر، مبلغ یا ارزِ دیگر، کلیدِ دیگر، بی شمارهٔ تأیید) یا **پس از** لغو/پایانِ
 *     مهلت رسید ⇒ ردیفِ `needs_review` (پول شاید گرفته شده؛ هرگز گم یا بی‌صدا نیست، هرگز «موفق» نیست).
 *   • پس از مهلت هنوز «در انتظار» یا دستگاه پاسخ نداد ⇒ همان «نیازمندِ بررسی» با مبلغِ تلاش ([Hold.UNKNOWN])، **نه** «لغو»:
 *     نمی‌دانیم پول گرفته شد یا نه.
 *   • رد/لغو، یا دستگاه پاسخ داد و این کلید را ندیده ⇒ هیچ ردیفی.
 *   • ⛔ کارت‌خوانِ دستی ([ManualCardTerminal]) هرگز این سطح نیست — همان «ثبتِ دستی» ِ PAY-06.
 *   • از کارت فقط چهار رقمِ آخر در مرجع می‌ماند، هرچه دستگاه فرستاد.
 *
 *  ── یک پرداخت، یک ردیف ([record]) ────────────────────────────────────
 *  پاسخِ تکراری (دو بار برگشتنِ همان نتیجه، ازسرگیری پس از ثبت) ردیفِ دوم نمی‌سازد: شمارهٔ تأییدِ دستگاهِ هر ردیفِ ثبت‌شده
 *  شمرده می‌شود؛ ردیفِ بررسیِ بی‌شمارهٔ همان تلاش (مرجعِ `?<کلید>`) با پاسخِ روشن‌ترِ همان کلید **جایگزین** می‌شود.
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
  /** حکمِ قطعی رسید (تأیید، بررسی، رد یا لغو؛ [DevicePay.close]) — دیگر نه `pay` می‌شود نه ازسر گرفته؛ بعدی کلیدِ تازه */
  val done: Boolean = false,
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
  /** کارتِ پوشیده، هرچه دستگاه فرستاد — در ردیف فقط چهار رقمِ آخرش می‌ماند */
  val maskedCard: String? = null,
) {
  enum class Status { APPROVED, DECLINED, CANCELLED, PENDING }
}

/** پاسخِ [VerifyingTerminal.lookup] */
sealed interface DeviceLookup {
  /** دستگاه این کلید را دیده — همان پاسخِ پیشین */
  data class Seen(val receipt: DeviceReceipt) : DeviceLookup
  /** دستگاه پاسخ داد و هیچ چیزی با این کلید ندیده ⇒ پولی با آن گرفته نشده */
  data object Unseen : DeviceLookup
  /** دستگاه پاسخ نداد (شبکه، خاموش) — «ندیده» نیست؛ شاید پول گرفته شده */
  data object Unreachable : DeviceLookup
}

/** دستگاهی که خودش تأیید می‌کند (برنامه به برنامه) — امروز هیچ پیاده‌سازیِ واقعی ندارد */
interface VerifyingTerminal {
  val id: String

  /** درخواستِ پرداخت با [DeviceAttempt.key]؛ تکرارِ همان کلید ⇒ پاسخِ همان، نه پولِ دوم. `null` ⇒ در دسترس نیست */
  suspend fun pay(attempt: DeviceAttempt): DeviceReceipt?

  /** پس از ازسرگیری: با این کلید چه شد؟ نرسیدن به دستگاه [DeviceLookup.Unreachable] است، هرگز [DeviceLookup.Unseen] */
  suspend fun lookup(key: String): DeviceLookup
}

object DevicePay {

  /** سطحِ «تأییدِ دستگاه» ِ PAY-08 */
  const val DEVICE = "device"

  enum class Hold { OTHER_KEY, OTHER_SALE, AMOUNT, NO_APPROVAL, LATE, UNKNOWN }

  sealed interface Verdict {
    /** «موفق»: ردیفِ `confirmed` و `device` — با کلید، فاکتور و ارزی که دستگاه بازگرداند (`DevicePaySettle` می‌سنجد) */
    data class Confirmed(val row: PaymentRow, val key: String, val saleRef: String, val currency: String) : Verdict
    /** تأیید رسید ولی چیزی نمی‌خواند، یا پس از مهلت روشن نشد ⇒ ردیفِ «نیازمندِ بررسی»؛ [key] کلیدِ همین تلاش */
    data class Review(val row: PaymentRow, val hold: Hold, val key: String) : Verdict
    /** هنوز پاسخی نیست (`PENDING` یا دستگاه در دسترس نیست، پیش از مهلت) — همان کلید دوباره پرسیده می‌شود */
    data object Waiting : Verdict
    /** رد/لغو، یا دستگاه این کلید را ندیده ⇒ هیچ ردیفی */
    data class None(val status: DeviceReceipt.Status) : Verdict
  }

  /** نتیجهٔ [attempt] */
  sealed interface Next {
    /** این تلاش را بدوان ([run]): همان تلاشِ بازِ همین فاکتور و مبلغ پیش از مهلت (ازسرگیری)، یا تلاشِ تازه */
    data class Run(val attempt: DeviceAttempt) : Next
    /** تلاشِ بازِ پیشین روشن نیست: اول [resolve] و ثبتِ حکمش ([record])، بعد [close] — تا آن‌جا کلیدِ تازه نه */
    data class Resolve(val open: DeviceAttempt) : Next
  }

  /** کلیدِ یکتاییِ تازه — همان الفبا و اندازهٔ `HesabPayAttempts.newKey` */
  fun newKey(saleKey: String, random: Random = Random.Default): String {
    val safe = saleKey.filter { it.isLetterOrDigit() && it.code < 128 }.take(64)
    val tail = CharArray(12) { ALPHABET[random.nextInt(ALPHABET.length)] }.concatToString()
    return "dev-$safe-$tail"
  }

  /**
   *  تلاشِ بعدی برای این فاکتور و مبلغ. [open] تلاشِ نگه‌داشتهٔ پیشین (اگر هست):
   *  بسته ([DeviceAttempt.done]) یا نبود ⇒ کلیدِ تازه؛ همان فاکتور و مبلغ و پیش از مهلت ⇒ همان (ازسرگیری، نه پولِ دوم)؛
   *  هر چیزِ دیگر ⇒ [Next.Resolve] — کلیدِ تازه هرگز پیش از روشن شدنِ تلاشِ باز.
   */
  fun attempt(open: DeviceAttempt?, saleKey: String, amountMinor: Long, now: Long, ttlMs: Long, random: Random = Random.Default): Next = when {
    open == null || open.done -> Next.Run(DeviceAttempt(saleKey, amountMinor, newKey(saleKey, random), now + ttlMs))
    open.saleKey == saleKey && open.amountMinor == amountMinor && now < open.until -> Next.Run(open)
    else -> Next.Resolve(open)
  }

  /** حکم رسید ⇒ تلاشِ بسته (دیگر به کار نمی‌رود)؛ [Verdict.Waiting] ⇒ همان تلاشِ باز */
  fun close(a: DeviceAttempt, v: Verdict): DeviceAttempt = if (v is Verdict.Waiting) a else a.copy(done = true)

  /**
   *  پاسخِ دستگاه ⇒ آنچه ثبت می‌شود. @param closed تلاش پیش از رسیدنِ این پاسخ لغو شد یا مهلتش گذشت
   */
  fun verdict(a: DeviceAttempt, r: DeviceReceipt, closed: Boolean = false): Verdict {
    if (r.status == DeviceReceipt.Status.PENDING) return if (closed) unknown(a) else Verdict.Waiting
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
    //  مرجع = شمارهٔ تأییدِ دستگاه (یکتا در دستگاه) و فقط چهار رقمِ آخرِ کارت؛ بی آن، کلیدِ همین تلاش تا ردیفِ بررسی یک بار ثبت شود
    val ref = approval?.let { it + (lastFour(r.maskedCard)?.let { m -> " (•••• $m)" } ?: "") } ?: "$NO_REF${a.key}"
    //  مبلغِ ردیف همان که دستگاه گفت گرفت (برای بررسی)؛ «موفق» فقط وقتی برابرِ تلاش است
    val row = PaymentRow(a.method, "card", r.amountMinor, ref, if (hold == null) PayStatus.CONFIRMED else PayStatus.NEEDS_REVIEW, DEVICE)
    return if (hold == null) Verdict.Confirmed(row, r.key, r.saleRef, r.currency) else Verdict.Review(row, hold, a.key)
  }

  /** پس از مهلت روشن نشد (هنوز «در انتظار» یا دستگاه پاسخ نداد) ⇒ بررسی با مبلغِ تلاش؛ نه «لغو» */
  private fun unknown(a: DeviceAttempt): Verdict =
    Verdict.Review(PaymentRow(a.method, "card", a.amountMinor, "$NO_REF${a.key}", PayStatus.NEEDS_REVIEW, DEVICE), Hold.UNKNOWN, a.key)

  /** نتیجهٔ [record] */
  sealed interface Recorded {
    data class Added(val rows: List<PaymentRow>) : Recorded
    /** ردیفِ بررسیِ بی‌شمارهٔ همین تلاش (`?<کلید>`) با پاسخِ روشن‌ترِ همان کلید جایگزین شد — باز هم یک ردیف */
    data class Replaced(val rows: List<PaymentRow>) : Recorded
    /** همین تلاش (یا همین شمارهٔ تأییدِ دستگاه) پیش‌تر ثبت شده ⇒ ردیفِ دوم نه */
    data object Already : Recorded
  }

  /** ردیفِ تازه به ردیف‌های فاکتور: یک پرداخت یک ردیف، با شمارهٔ تأیید یا کلیدِ تلاش */
  fun record(rows: List<PaymentRow>, v: Verdict): Recorded {
    val (row, key) = when (v) {
      is Verdict.Confirmed -> v.row to v.key
      is Verdict.Review -> v.row to v.key
      else -> return Recorded.Added(rows)
    }
    val id = idOf(row.ref)
    if (rows.any { it.verification == DEVICE && idOf(it.ref) == id }) return Recorded.Already
    val pending = rows.indexOfFirst { it.verification == DEVICE && it.ref == "$NO_REF$key" }
    return if (pending < 0) Recorded.Added(rows + row) else Recorded.Replaced(rows.mapIndexed { i, r -> if (i == pending) row else r })
  }

  /**
   *  یک دورِ کامل با دستگاه: اول [VerifyingTerminal.lookup] (ازسرگیری — شاید در نبودنِ برنامه پرداخت شده)، وگرنه
   *  [VerifyingTerminal.pay] با همان کلید. دستگاهِ در دسترس نبود ⇒ [Verdict.Waiting]. پس از مهلت، لغو، یا تلاشِ بسته
   *  هرگز `pay` نمی‌شود: ندیده ⇒ هیچ؛ نرسیدن به دستگاه یا هنوز «در انتظار» ⇒ بررسی.
   */
  suspend fun run(t: VerifyingTerminal, a: DeviceAttempt, now: Long, closed: Boolean = false): Verdict {
    val late = closed || a.done || now >= a.until
    when (val seen = t.lookup(a.key)) {
      is DeviceLookup.Seen -> if (seen.receipt.status != DeviceReceipt.Status.PENDING || late) return verdict(a, seen.receipt, late)
      DeviceLookup.Unseen -> if (late) return Verdict.None(DeviceReceipt.Status.CANCELLED)
      DeviceLookup.Unreachable -> if (late) return unknown(a)
    }
    val r = t.pay(a) ?: return Verdict.Waiting
    return verdict(a, r)
  }

  /** روشن کردنِ تلاشِ باز ([Next.Resolve]) — فقط `lookup`، هرگز پولِ تازه */
  suspend fun resolve(t: VerifyingTerminal, open: DeviceAttempt, now: Long): Verdict = run(t, open, now, closed = true)

  /** شناسهٔ یکتای ردیفِ دستگاه: شمارهٔ تأیید (بی چهار رقمِ کارت)، یا کلیدِ تلاشِ بی‌تأیید */
  private fun idOf(ref: String?): String = ref.orEmpty().substringBefore(" (")

  /** فقط چهار رقمِ آخرِ کارت (رقمِ فارسی هم)؛ کمتر از چهار رقم ⇒ هیچ */
  private fun lastFour(masked: String?): String? =
    masked?.let { ManualCardTerminal.digits(it).filter { c -> c in '0'..'9' }.takeLast(4) }?.takeIf { it.length == 4 }

  private const val NO_REF = "?"
  private const val ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789"
}
