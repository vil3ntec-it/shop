package ir.vil3ntec.tohid.core.crm

import ir.vil3ntec.tohid.core.onboarding.OnboardingText
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 *  SEC-10 — «حذفِ دادهٔ این مشتری» در پروندهٔ مشتری (`POST /api/shop/customer-erasure`، `lib/customer-erasure.js`).
 *  منطقِ خالص، commonMain. ⛔ فقط صاحبِ دکان (`owner_only`)؛ پرچمِ خاموشِ `shop.customer_erasure` ⇒ سرور همان ۴۰۴ ِ
 *  درِ ناشناخته را می‌دهد ⇒ دکمه پیدا نیست. پرسشِ بی‌اثر ([probe]: `dryRun` بی شماره) روشن و صاحب ⇒ ۴۰۰ [BAD_TARGET].
 *
 *  ترتیب، همیشه: پیش‌نمایش (`dryRun`، هیچ نوشتنی) ⇒ تأییدِ سخت (چهار رقمِ آخرِ شماره) ⇒ تأییدِ دوبارهٔ هویت
 *  (همان `ReauthGate` ِ برنامه) ⇒ اجرای واقعی ⇒ شمارِ پاک‌شده‌ها. سفارشِ باز ⇒ اجرا نیست (سرور هم ۴۰۹ `open_orders`).
 *  ⛔ اسنادِ مالی ناشناس می‌مانند و دفترِ همین دستگاه (قرض‌دار و فروش‌ها) پاک نمی‌شود — سازوکارِ فرمانِ حذف به دستگاه نیست.
 */
object CustomerErasure {

  const val PATH = "/shop/customer-erasure"
  const val BAD_TARGET = "bad_target"
  const val OWNER_ONLY = "owner_only"
  const val OPEN_ORDERS = "open_orders"
  const val REAUTH = "reauth_required"

  /** همان `COUNT_KEYS` ِ سرور (`lib/customer-erasure.js`)، به همان ترتیب — آزمونِ `ServerFeatureScreensTest` هر دو را می‌سنجد */
  val COUNT_KEYS: List<String> = listOf(
    "orders", "orderLines", "orderEvents", "deliveryEvents", "deliveryPhotos", "pickPhotos", "deliveryLocations",
    "notifyOutbox", "conversations", "messages", "chatMedia", "chatPins", "chatReports", "carts", "favorites", "lists", "areaRequests",
    "alerts", "issues", "issuePhotos", "reviews", "pushSubs", "alertPushSubs", "orderDevices", "addressMedia", "addressMediaLinks",
  )

  /** شمارِ رقم‌هایی که برای تأیید باید نوشته شود */
  const val CONFIRM_DIGITS = 4

  /** بدنهٔ پرسشِ بی‌اثر: بی هدف ⇒ هیچ خواندن و نوشتنی؛ فقط پرچم و نقش سنجیده می‌شوند */
  fun probe(): JsonObject = buildJsonObject { put("dryRun", true) }

  /** true = دکمه پیدا؛ false = پنهان (خاموش، غیرِ صاحب، سرورِ کهنه)؛ null = خطای گذرا ⇒ همان حالِ پیشین */
  fun probeSays(code: String, refused: Boolean, invalid: Boolean): Boolean? = when {
    invalid && code == BAD_TARGET -> true
    refused || invalid -> false
    else -> null
  }

  fun digits(raw: String): String = OnboardingText.latinDigits(raw).filter { it in '0'..'9' }

  /** شمارهٔ قابلِ حذف: دست‌کم هفت رقم (همان کفِ `validate.phone` نیست؛ سرور خودش می‌سنجد) */
  fun usable(phone: String?): Boolean = phone != null && digits(phone).length >= 7

  fun body(phone: String, dryRun: Boolean): JsonObject = buildJsonObject {
    put("phone", phone.trim())
    put("dryRun", dryRun)
  }

  data class Result(
    val dryRun: Boolean,
    val mode: String,
    val openOrders: Int,
    /** فقط شمارهای ناصفر، به ترتیبِ [COUNT_KEYS] */
    val counts: List<Pair<String, Int>>,
    val total: Int,
    val kept: List<String>,
    val erasureId: String?,
  )

  private fun long(o: JsonObject?, k: String): Long? = (o?.get(k) as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
  private fun str(o: JsonObject?, k: String): String? = (o?.get(k) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

  /** پاسخِ سرور؛ بی `counts` ⇒ null */
  fun result(o: JsonObject): Result? {
    val c = o["counts"] as? JsonObject ?: return null
    val counts = COUNT_KEYS.mapNotNull { k -> (long(c, k) ?: 0).toInt().takeIf { it > 0 }?.let { k to it } }
    return Result(
      dryRun = (o["dryRun"] as? JsonPrimitive)?.booleanOrNull == true,
      mode = str(o, "mode").orEmpty(),
      openOrders = (long(o, "openOrders") ?: 0).toInt(),
      counts = counts,
      total = (long(o, "total") ?: counts.sumOf { it.second }.toLong()).toInt(),
      kept = (o["kept"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
      erasureId = str(o, "erasureId"),
    )
  }

  /** تأییدِ سخت: چهار رقمِ آخرِ همین شماره (رقمِ فارسی هم) */
  fun confirmOk(typed: String, phone: String): Boolean {
    val want = digits(phone).takeLast(CONFIRM_DIGITS)
    return want.length == CONFIRM_DIGITS && digits(typed) == want
  }

  // ── گام‌ها ──

  sealed interface Step {
    /** هنوز چیزی نپرسیده */
    data object Idle : Step
    data object Previewing : Step
    /** پیش‌نمایش آمد؛ [note] پیامِ آخرین کارِ ناموفق (مثلاً انصرافِ تأییدِ هویت) */
    data class Preview(val result: Result, val note: String? = null) : Step
    data class Confirm(val preview: Result, val typed: String = "", val note: String? = null) : Step
    data class Reauth(val preview: Result) : Step
    data class Erasing(val preview: Result) : Step
    data class Done(val result: Result) : Step
    data class Failed(val message: String) : Step
  }

  /** پیش‌نمایش اجازهٔ رفتن به تأیید می‌دهد؟ سفارشِ باز یا چیزی برای پاک کردن نیست ⇒ نه */
  fun canProceed(r: Result): Boolean = r.openOrders == 0 && r.total > 0

  fun start(s: Step): Step? = when (s) {
    is Step.Idle, is Step.Preview, is Step.Done, is Step.Failed -> Step.Previewing
    else -> null
  }

  fun previewed(r: Result): Step = Step.Preview(r)

  fun toConfirm(s: Step): Step? = if (s is Step.Preview && canProceed(s.result)) Step.Confirm(s.result) else null

  fun typed(s: Step, text: String): Step = if (s is Step.Confirm) s.copy(typed = text.take(16), note = null) else s

  /** «حذف کن» زده شد: فقط از تأییدِ درست ⇒ پرسشِ هویت */
  fun askReauth(s: Step, phone: String): Step? =
    if (s is Step.Confirm && confirmOk(s.typed, phone) && canProceed(s.preview)) Step.Reauth(s.preview) else null

  /** جوابِ برگهٔ هویت: تأیید ⇒ اجرا؛ انصراف ⇒ همان تأیید با پیام (هیچ درخواستی نرفت) */
  fun reauthed(s: Step, ok: Boolean, cancelled: String): Step = when {
    s !is Step.Reauth -> s
    ok -> Step.Erasing(s.preview)
    else -> Step.Confirm(s.preview, note = cancelled)
  }

  fun erased(s: Step, r: Result): Step = if (s is Step.Erasing) Step.Done(r) else s

  /** اجرای ناموفق: سفارشِ تازهٔ باز یا نشستِ کهنه ⇒ برگشت به پیش‌نمایش با پیام (باید از نو پرسید) */
  fun eraseFailed(s: Step, message: String): Step = if (s is Step.Erasing) Step.Preview(s.preview, note = message) else s
}
