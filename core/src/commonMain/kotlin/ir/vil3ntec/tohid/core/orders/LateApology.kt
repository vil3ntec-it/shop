package ir.vil3ntec.tohid.core.orders

import ir.vil3ntec.tohid.core.onboarding.OnboardingText
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 *  NTF-10 — سیاستِ «پوزش و کوپنِ خودکار برای تأخیر» (`GET/PUT /api/shop/orders/late-apology`، `lib/order-late-apology.js`).
 *  منطقِ خالصِ بخشِ تنظیمات، commonMain. ⛔ پیش‌فرض خاموش: سرور بی پرچمِ `shop.orders` و `shop.orders.late_apology`
 *  فقط `{enabled:false}` می‌دهد (یا ۴۰۳ برای نقشِ بی `orders.view`) ⇒ بخش دیده نمی‌شود. دیدن با `orders.view`؛
 *  نوشتن فقط صاحب و مدیر (`permissions.settings` = `settings.write`) — بقیه فقط می‌بینند.
 */
object LateApology {

  const val PATH = "/shop/orders/late-apology"
  const val OFF = "late_apology_disabled"

  /** همان بازه‌های `parsePolicy` ِ سرور */
  const val LATE_MIN = 5
  const val LATE_MAX = 1440
  const val AMOUNT_MAX_MINOR = 100_000_000L
  const val BP_MAX = 5000
  const val DAYS_MIN = 1
  const val DAYS_MAX = 90
  const val DEFAULT_DAYS = 14

  /** فقط در فرم: «بی کوپن» (سرور `coupon: null` می‌گیرد) */
  const val NONE = "none"
  const val AMOUNT = "amount"
  const val PERCENT = "percent"
  val KINDS: List<String> = listOf(NONE, AMOUNT, PERCENT)

  data class Coupon(val kind: String, val amountMinor: Long, val bp: Int, val days: Int)

  data class Policy(
    val enabled: Boolean = false,
    val lateMinutes: Int = 30,
    val coupon: Coupon? = null,
    val version: Long = 0,
    val updatedAt: Long? = null,
  )

  data class Recent(
    val orderNo: Long,
    /** `apology` (پیام و کوپن) یا `skipped_customer` (تأخیرِ کارِ خودِ مشتری) */
    val outcome: String,
    val lateMinutes: Int,
    val at: Long,
    val queued: Boolean,
    val couponLast4: String?,
    val redeemed: Boolean,
  )

  /** `enabled` = پرچمِ سرور (نه سیاستِ صاحب)؛ `canEdit` = `permissions.settings` */
  data class View(val enabled: Boolean, val policy: Policy, val recent: List<Recent>, val canEdit: Boolean)

  private fun str(o: JsonObject?, k: String): String? = (o?.get(k) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
  private fun long(o: JsonObject?, k: String): Long? = (o?.get(k) as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
  private fun bool(o: JsonObject?, k: String): Boolean? = (o?.get(k) as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

  fun policy(o: JsonObject?): Policy {
    o ?: return Policy()
    val c = o["coupon"] as? JsonObject
    val kind = str(c, "kind")?.takeIf { it == AMOUNT || it == PERCENT }
    return Policy(
      enabled = bool(o, "enabled") == true,
      lateMinutes = (long(o, "lateMinutes") ?: 30).toInt(),
      coupon = kind?.let { Coupon(it, long(c, "amountMinor") ?: 0, (long(c, "bp") ?: 0).toInt(), (long(c, "days") ?: DEFAULT_DAYS.toLong()).toInt()) },
      version = long(o, "version") ?: 0,
      updatedAt = long(o, "updatedAt"),
    )
  }

  /** پاسخِ `GET`؛ `enabled` ِ نادرست یا نبود ⇒ خاموش */
  fun view(o: JsonObject): View {
    val on = bool(o, "enabled") == true
    if (!on) return View(false, Policy(), emptyList(), false)
    val recent = (o["recent"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map { r ->
      val c = r["coupon"] as? JsonObject
      Recent(
        orderNo = long(r, "orderNo") ?: 0, outcome = str(r, "outcome").orEmpty(), lateMinutes = (long(r, "lateMinutes") ?: 0).toInt(),
        at = long(r, "at") ?: 0, queued = bool(r, "queued") == true, couponLast4 = str(c, "last4"),
        redeemed = c != null && long(c, "redeemedAt") != null,
      )
    }
    val perms = o["permissions"] as? JsonObject
    return View(true, policy(o["policy"] as? JsonObject), recent, bool(perms, "settings") == true)
  }

  /** پاسخِ `PUT`: `{policy}` */
  fun saved(o: JsonObject): Policy? = (o["policy"] as? JsonObject)?.let(::policy)

  // ── فرم ──

  /** فرمِ ویرایش: عددها همان که کاربر نوشته (رقمِ فارسی هم)؛ مبلغ به افغانی، درصد با یک یا دو رقمِ اعشار */
  data class Form(
    val enabled: Boolean = false,
    val minutes: String = "30",
    val kind: String = NONE,
    val amount: String = "",
    val percent: String = "",
    val days: String = DEFAULT_DAYS.toString(),
  )

  fun formOf(p: Policy): Form = Form(
    enabled = p.enabled,
    minutes = p.lateMinutes.toString(),
    kind = p.coupon?.kind ?: NONE,
    amount = p.coupon?.takeIf { it.kind == AMOUNT }?.let { majorText(it.amountMinor) }.orEmpty(),
    percent = p.coupon?.takeIf { it.kind == PERCENT }?.let { bpText(it.bp) }.orEmpty(),
    days = (p.coupon?.days ?: DEFAULT_DAYS).toString(),
  )

  /** کلیدِ کادرِ نادرست ⇒ نوعِ خطا (متنش با صفحه) */
  enum class Bad { MINUTES, AMOUNT, PERCENT, DAYS }

  data class Check(val errors: Set<Bad>, val body: JsonObject?)

  private fun digits(raw: String): String = OnboardingText.latinDigits(raw.trim())

  private fun int(raw: String): Long? = digits(raw).takeIf { it.isNotEmpty() && it.all(Char::isDigit) && it.length <= 12 }?.toLong()

  /** «12.5» ⇒ ۱۲۵۰ (صدمِ واحد)؛ بیش از دو رقمِ اعشار یا نویسهٔ دیگر ⇒ null */
  fun hundredths(raw: String): Long? {
    val s = digits(raw)
    val m = Regex("^([0-9]{1,10})(?:\\.([0-9]{1,2}))?$").matchEntire(s) ?: return null
    val frac = m.groupValues[2].padEnd(2, '0')
    return m.groupValues[1].toLong() * 100 + frac.toLong()
  }

  /** ۵۰۰۰ ⇒ «50»، ۱۲۵۰ ⇒ «12.5»، ۱۲۰۵ ⇒ «12.05» */
  fun majorText(minor: Long): String {
    val whole = minor / 100
    val frac = minor % 100
    return when {
      frac == 0L -> whole.toString()
      frac % 10 == 0L -> "$whole.${frac / 10}"
      else -> "$whole.${frac.toString().padStart(2, '0')}"
    }
  }
  fun bpText(bp: Int): String = majorText(bp.toLong())

  /** سنجشِ فرم با همان بازه‌های سرور؛ همه درست ⇒ بدنهٔ `PUT` با `expectedVersion` */
  fun check(f: Form, version: Long): Check {
    val bad = LinkedHashSet<Bad>()
    val minutes = int(f.minutes)?.takeIf { it in LATE_MIN..LATE_MAX }
    if (minutes == null) bad += Bad.MINUTES
    var coupon: Coupon? = null
    if (f.kind == AMOUNT || f.kind == PERCENT) {
      val days = int(f.days)?.takeIf { it in DAYS_MIN..DAYS_MAX }
      if (days == null) bad += Bad.DAYS
      if (f.kind == AMOUNT) {
        val minor = hundredths(f.amount)?.takeIf { it in 1..AMOUNT_MAX_MINOR }
        if (minor == null) bad += Bad.AMOUNT
        if (minor != null && days != null) coupon = Coupon(AMOUNT, minor, 0, days.toInt())
      } else {
        val bp = hundredths(f.percent)?.takeIf { it in 1..BP_MAX.toLong() }
        if (bp == null) bad += Bad.PERCENT
        if (bp != null && days != null) coupon = Coupon(PERCENT, 0, bp.toInt(), days.toInt())
      }
    }
    if (bad.isNotEmpty() || minutes == null) return Check(bad, null)
    val c = coupon
    return Check(emptySet(), buildJsonObject {
      put("enabled", f.enabled)
      put("lateMinutes", minutes.toInt())
      if (c == null) put("coupon", JsonNull)
      else put("coupon", buildJsonObject {
        put("kind", c.kind)
        if (c.kind == AMOUNT) put("amountMinor", c.amountMinor) else put("bp", c.bp)
        put("days", c.days)
      })
      put("expectedVersion", version)
    })
  }

  /** همان فرمِ ذخیره‌شده؟ (دکمهٔ «ذخیره» فقط با تغییر) */
  fun changed(f: Form, p: Policy): Boolean = f != formOf(p)
}
