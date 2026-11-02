package ir.vil3ntec.tohid.core.storefront

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.core.net.ApiResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 *  تخفیفِ اولین خرید (MKT-02) و سدِ سوءاستفاده از تخفیفِ ویترین (RSK-09) — درِ سرورِ صاحب
 *  (`/api/shop/storefront/promo-guard`، `server/src/routes/storefront-promo-guard.js` و `lib/storefront-promo-guard.js`).
 *
 *  ⛔ پرچمِ `shop.storefront.promo_guard` (پیش‌فرض خاموش): `GET` ۴۰۳ ِ `promo_guard_disabled` ⇒ بخش پنهان.
 *  دیدن و نوشتن هر دو `settings.write` (صاحب و مدیر)؛ نقشِ دیگر ۴۰۳ ِ `permission_denied` ⇒ باز هم پنهان.
 *  مرزهای کادر همان `parseSettings` ِ سرور است (مبلغ‌ها گام ِ ۱ افغانی، سقفِ یک میلیون؛ درصد تا ۱۰۰ با دو رقمِ اعشار).
 */
object PromoGuardApi {
  const val ROOT = "/shop/storefront/promo-guard"
  const val FLAG = "shop.storefront.promo_guard"

  /** `STEP_MINOR` ِ سرور: مبلغ‌ها افغانیِ درست */
  const val STEP_MINOR = 100L
  /** `MAX_MINOR` ِ سرور (یک میلیون افغانی) */
  const val MAX_MINOR = 100_000_000L
  /** `MAX_PER` ِ سرور: سقفِ کد برای هر شماره/دستگاه */
  const val MAX_PER = 1000
  /** ۱۰۰٪ = ۱۰۰۰۰ واحدِ پایه */
  const val MAX_BP = 10_000

  const val PERCENT = "percent"
  const val AMOUNT = "amount"
  const val COMBINE = "combine"
  const val EXCLUSIVE = "exclusive"

  enum class Gate { READY, OFF, ERROR }

  /**
   *  خطای `GET` ⇒ حالتِ بخش. ۴۰۴ = سرورِ قدیمی‌تر؛ ۴۰۳ (`promo_guard_disabled`، `permission_denied`، `no_shop`)
   *  = پرچمِ خاموش یا نقشِ بی دسترسی ⇒ پنهان. خطای شبکه/سرور ⇒ ERROR (باز هم بی بخش، تا عددِ ساختگی نشان ندهد).
   */
  fun gateOf(status: Int): Gate = when (status) {
    404, 403 -> Gate.OFF
    else -> Gate.ERROR
  }

  fun parse(body: JsonObject): PromoGuardViewDto = ApiJson.decode<PromoGuardViewDto>(body)

  /** پاسخِ `PUT` ⇒ `{ settings, updatedAt }` */
  fun parseSettings(body: JsonObject): PromoGuardSettingsDto = ApiJson.decode<PromoGuardSettingsDto>(body["settings"] ?: JsonNull)

  data class Form(
    val enabled: Boolean = false,
    val kind: String = PERCENT,
    /** درصد، مثلِ «۱۰» یا «۷٫۵» */
    val percent: String = "",
    /** افغانی */
    val amount: String = "",
    val max: String = "",
    val minSubtotal: String = "",
    val stack: String = COMBINE,
    val codePerPhone: String = "",
    val codePerDevice: String = "",
    val maxDiscount: String = "",
  )

  private fun afs(minor: Long): String = if (minor <= 0) "" else (minor / 100).toString()

  /** ۱۲۵۰ ⇒ «12.5» */
  fun percentText(bp: Int): String {
    if (bp <= 0) return ""
    val whole = bp / 100
    val frac = bp % 100
    return if (frac == 0) "$whole" else "$whole." + frac.toString().padStart(2, '0').trimEnd('0')
  }

  fun formOf(s: PromoGuardSettingsDto): Form = Form(
    enabled = s.firstOrder.enabled,
    kind = if (s.firstOrder.kind == AMOUNT) AMOUNT else PERCENT,
    percent = percentText(s.firstOrder.bp),
    amount = afs(s.firstOrder.amountMinor),
    max = afs(s.firstOrder.maxMinor),
    minSubtotal = afs(s.firstOrder.minSubtotalMinor),
    stack = if (s.firstOrder.stack == EXCLUSIVE) EXCLUSIVE else COMBINE,
    codePerPhone = if (s.limits.codePerPhone > 0) s.limits.codePerPhone.toString() else "",
    codePerDevice = if (s.limits.codePerDevice > 0) s.limits.codePerDevice.toString() else "",
    maxDiscount = afs(s.limits.maxDiscountMinor),
  )

  /** «۱۲٫۵» ⇒ ۱۲۵۰؛ خالی ⇒ ۰؛ نادرست یا بیش از ۱۰۰ ⇒ null */
  fun parseBp(raw: String): Int? {
    val s = StorefrontRules.asciiDigits(raw)
    if (s.isEmpty()) return 0
    if (!Regex("^\\d{1,3}(\\.\\d{1,2})?$").matches(s)) return null
    val parts = s.split('.')
    val bp = parts[0].toInt() * 100 + (parts.getOrNull(1)?.padEnd(2, '0')?.toInt() ?: 0)
    return bp.takeIf { it <= MAX_BP }
  }

  /** افغانیِ درست ⇒ واحدِ خُرد؛ خالی ⇒ ۰ (بی سقف/بی کف)؛ اعشار، منفی یا بیش از سقف ⇒ null */
  fun parseAfghani(raw: String): Long? {
    val s = StorefrontRules.asciiDigits(raw)
    if (s.isEmpty()) return 0
    if (!Regex("^\\d{1,7}$").matches(s)) return null
    val minor = s.toLong() * STEP_MINOR
    return minor.takeIf { it <= MAX_MINOR }
  }

  fun parseCount(raw: String): Int? {
    val s = StorefrontRules.asciiDigits(raw)
    if (s.isEmpty()) return 0
    if (!Regex("^\\d{1,4}$").matches(s)) return null
    return s.toInt().takeIf { it <= MAX_PER }
  }

  data class Checked(val errors: Map<String, String>, val settings: PromoGuardSettingsDto?)

  private const val AFS_RANGE = "افغانیِ درست، بی اعشار، تا ۱٬۰۰۰٬۰۰۰ (خالی = بی حد)"

  /** کادرها ⇒ تنظیمِ کامل (همان بدنه‌ای که سرور جایگزین می‌کند) یا خطای هر کادر */
  fun check(f: Form): Checked {
    val e = LinkedHashMap<String, String>()
    val percent = f.kind == PERCENT
    val bp = if (percent) parseBp(f.percent) else 0
    if (bp == null) e["percent"] = "درصد از ۰ تا ۱۰۰، با حداکثر دو رقمِ اعشار"
    val amount = if (!percent) parseAfghani(f.amount) else 0L
    if (amount == null) e["amount"] = AFS_RANGE
    val max = parseAfghani(f.max)
    if (max == null) e["max"] = AFS_RANGE
    val minSub = parseAfghani(f.minSubtotal)
    if (minSub == null) e["minSubtotal"] = AFS_RANGE
    val perPhone = parseCount(f.codePerPhone)
    if (perPhone == null) e["codePerPhone"] = "عددِ صحیح از ۰ تا ۱٬۰۰۰ (خالی = بی سقف)"
    val perDevice = parseCount(f.codePerDevice)
    if (perDevice == null) e["codePerDevice"] = "عددِ صحیح از ۰ تا ۱٬۰۰۰ (خالی = بی سقف)"
    val maxDiscount = parseAfghani(f.maxDiscount)
    if (maxDiscount == null) e["maxDiscount"] = AFS_RANGE
    //  همان قاعدهٔ سرور: اولین خریدِ روشن بی مقدار ⇒ ۴۰۰
    if (f.enabled && percent && bp == 0) e["percent"] = "برای اولین خریدِ روشن، درصد بیش از صفر باشد"
    if (f.enabled && !percent && amount == 0L) e["amount"] = "برای اولین خریدِ روشن، مبلغ بیش از صفر باشد"
    if (e.isNotEmpty()) return Checked(e, null)
    return Checked(
      emptyMap(),
      PromoGuardSettingsDto(
        firstOrder = FirstOrderDto(
          enabled = f.enabled, kind = if (percent) PERCENT else AMOUNT, bp = bp!!, amountMinor = amount!!,
          maxMinor = max!!, minSubtotalMinor = minSub!!, stack = if (f.stack == EXCLUSIVE) EXCLUSIVE else COMBINE,
        ),
        limits = PromoLimitsDto(codePerPhone = perPhone!!, codePerDevice = perDevice!!, maxDiscountMinor = maxDiscount!!),
      ),
    )
  }

  fun body(s: PromoGuardSettingsDto): JsonObject = buildJsonObject {
    putJsonObject("firstOrder") {
      put("enabled", s.firstOrder.enabled)
      put("kind", s.firstOrder.kind)
      if (s.firstOrder.kind == PERCENT) put("bp", s.firstOrder.bp) else put("amountMinor", s.firstOrder.amountMinor)
      put("maxMinor", s.firstOrder.maxMinor)
      put("minSubtotalMinor", s.firstOrder.minSubtotalMinor)
      put("stack", s.firstOrder.stack)
    }
    putJsonObject("limits") {
      put("codePerPhone", s.limits.codePerPhone)
      put("codePerDevice", s.limits.codePerDevice)
      put("maxDiscountMinor", s.limits.maxDiscountMinor)
    }
  }

  /** یک ردیفِ فهرستِ «مشکوک»: سرخط و جزئیات */
  data class SuspectRow(val title: String, val detail: String)

  /** فهرستِ «مشکوک» ⇒ ردیف‌ها (اول دستگاه‌ها، بعد شماره‌ها)؛ هیچ ⇒ فهرستِ خالی */
  fun suspectRows(s: SuspiciousDto): List<SuspectRow> =
    s.devices.map { d ->
      SuspectRow("یک دستگاه (${d.device.ifBlank { "نامعلوم" }}) با ${StorefrontRules.toFa(d.count)} شماره", d.phones.joinToString("، "))
    } + s.phones.map { p ->
      SuspectRow("شمارهٔ ${p.phone} روی ${StorefrontRules.toFa(p.count)} دستگاه", p.devices.joinToString("، "))
    }

  /** سرخطِ فهرستِ «مشکوک» — آستانه از خودِ سرور */
  fun suspectSummary(s: SuspiciousDto): String {
    val n = s.devices.size + s.phones.size
    val rule = "${StorefrontRules.toFa(s.threshold)} شماره یا بیشتر از یک دستگاه، یا ${StorefrontRules.toFa(s.threshold)} دستگاه یا بیشتر برای یک شماره"
    return if (n == 0) "موردِ مشکوکی نیست ($rule، ۹۰ روزِ اخیر)" else "${StorefrontRules.toFa(n)} موردِ مشکوک ($rule)"
  }

  fun kindLabel(kind: String): String = when (kind) {
    "first_order" -> "اولین خرید"
    "code" -> "کد"
    "auto" -> "خودکار"
    else -> kind
  }

  fun errorText(code: String, serverMessage: String): String = when (code) {
    "promo_guard_disabled" -> "سدِ تخفیفِ ویترین برای این دکان روشن نیست"
    else -> serverMessage.ifBlank { "ذخیره نشد؛ دوباره تلاش کنید" }
  }

  suspend fun load(api: ApiClient): ApiResult<PromoGuardViewDto> = ApiResult.of { parse(api.get(ROOT)) }

  suspend fun save(api: ApiClient, s: PromoGuardSettingsDto): ApiResult<PromoGuardSettingsDto> =
    ApiResult.of { parseSettings(api.put(ROOT, body(s))) }
}

@Serializable
data class FirstOrderDto(
  val enabled: Boolean = false,
  val kind: String = PromoGuardApi.PERCENT,
  val bp: Int = 0,
  val amountMinor: Long = 0,
  val maxMinor: Long = 0,
  val minSubtotalMinor: Long = 0,
  val stack: String = PromoGuardApi.COMBINE,
)

@Serializable
data class PromoLimitsDto(val codePerPhone: Int = 0, val codePerDevice: Int = 0, val maxDiscountMinor: Long = 0)

@Serializable
data class PromoGuardSettingsDto(val firstOrder: FirstOrderDto = FirstOrderDto(), val limits: PromoLimitsDto = PromoLimitsDto())

@Serializable
data class PromoClaimDto(
  val kind: String = "",
  val promoId: String = "",
  val code: String = "",
  val phone: String = "",
  val device: String = "",
  val discountMinor: Long = 0,
  val at: Long = 0,
  val orderNo: Long? = null,
)

@Serializable
data class SuspectDeviceDto(val device: String = "", val phones: List<String> = emptyList(), val count: Int = 0, val lastAt: Long = 0)

@Serializable
data class SuspectPhoneDto(val phone: String = "", val devices: List<String> = emptyList(), val count: Int = 0, val lastAt: Long = 0)

@Serializable
data class SuspiciousDto(
  val threshold: Int = 3,
  val devices: List<SuspectDeviceDto> = emptyList(),
  val phones: List<SuspectPhoneDto> = emptyList(),
)

/** `GET /api/shop/storefront/promo-guard` (فقط با پرچمِ روشن؛ وگرنه ۴۰۳) */
@Serializable
data class PromoGuardViewDto(
  val settings: PromoGuardSettingsDto = PromoGuardSettingsDto(),
  val recent: List<PromoClaimDto> = emptyList(),
  val suspicious: SuspiciousDto = SuspiciousDto(),
)
