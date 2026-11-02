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
 *  زمانِ تقریبیِ تحویلِ ویترین (WEB-04) — درِ سرورِ صاحب (`/api/shop/storefront/eta`،
 *  `server/src/routes/storefront-eta.js` و `lib/storefront-eta.js`).
 *
 *  ⛔ پرچمِ `shop.storefront.eta` (پیش‌فرض خاموش): `GET` فقط `{enabled:false}` و `PUT` ۴۰۳ ⇒ بخش پنهان است.
 *  دیدن `data.read`، تنظیم `settings.write` (صاحب و مدیر) ⇒ بخش فقط با `permissions.manage`.
 *  مرزهای کادر همان `saveSettings` ِ سرور است تا ۴۰۰ ِ `bad_number` پیش از فرستادن گرفته شود.
 */
object StorefrontEtaApi {
  const val ROOT = "/shop/storefront/eta"
  const val FLAG = "shop.storefront.eta"

  const val PREP_MIN = 1
  const val PREP_MAX = 240
  const val TRAVEL_MIN = 0
  const val TRAVEL_MAX = 240
  const val PARALLEL_MIN = 1
  const val PARALLEL_MAX = 20
  const val ZONE_MIN = 0
  const val ZONE_MAX = 240
  /** `sfd.MAX_ZONES` ِ سرور */
  const val MAX_ZONES = 20

  fun parse(body: JsonObject): EtaViewDto = ApiJson.decode<EtaViewDto>(body)

  /** پاسخِ `PUT` ⇒ `{ settings }` */
  fun parseSettings(body: JsonObject): EtaSettingsDto = ApiJson.decode<EtaSettingsDto>(body["settings"] ?: JsonNull)

  /** پرچمِ خاموش، نقشِ بی `settings.write` یا هنوز بی پاسخ ⇒ هیچ ردیف و بخشی */
  fun visible(v: EtaViewDto?): Boolean = v != null && v.enabled && v.permissions.manage

  /** کادرهای بخش (متن، تا رقمِ فارسی هم پذیرفته شود) */
  data class Form(
    val show: Boolean = true,
    val prep: String = "",
    val travel: String = "",
    val parallel: String = "",
    /** شناسهٔ ناحیه ⇒ دقیقه؛ خالی = همان دقیقهٔ راهِ کلی */
    val zones: Map<String, String> = emptyMap(),
  )

  fun formOf(v: EtaViewDto): Form = Form(
    show = v.settings.show,
    prep = v.settings.prepMinutes.toString(),
    travel = v.settings.travelMinutes.toString(),
    parallel = v.settings.parallel.toString(),
    zones = v.zones.associate { z -> z.id to (v.settings.zoneTravel[z.id]?.toString() ?: "") },
  )

  data class Checked(val errors: Map<String, String>, val body: JsonObject?)

  private fun intIn(raw: String, lo: Int, hi: Int): Int? {
    val s = StorefrontRules.asciiDigits(raw)
    if (!Regex("^\\d{1,4}$").matches(s)) return null
    return s.toInt().takeIf { it in lo..hi }
  }

  private fun range(lo: Int, hi: Int) = "عددِ صحیح از ${StorefrontRules.toFa(lo)} تا ${StorefrontRules.toFa(hi)}"

  /**
   *  کادرها ⇒ بدنهٔ `PUT` یا خطای هر کادر (کلید: `prep`، `travel`، `parallel`، `zone:<شناسه>`).
   *  ناحیه فقط از فهرستِ خودِ سرور ([zoneIds])؛ فهرستِ خالی ⇒ `zoneTravel` فرستاده نمی‌شود تا ذخیرهٔ پیشین بماند.
   */
  fun check(form: Form, zoneIds: List<String>, expectedVersion: Long): Checked {
    val errors = LinkedHashMap<String, String>()
    val prep = intIn(form.prep, PREP_MIN, PREP_MAX)
    if (prep == null) errors["prep"] = range(PREP_MIN, PREP_MAX)
    val travel = intIn(form.travel, TRAVEL_MIN, TRAVEL_MAX)
    if (travel == null) errors["travel"] = range(TRAVEL_MIN, TRAVEL_MAX)
    val parallel = intIn(form.parallel, PARALLEL_MIN, PARALLEL_MAX)
    if (parallel == null) errors["parallel"] = range(PARALLEL_MIN, PARALLEL_MAX)
    val zoneTravel = LinkedHashMap<String, Int>()
    if (zoneIds.size > MAX_ZONES) errors["zones"] = "بیش از ${StorefrontRules.toFa(MAX_ZONES)} ناحیه"
    for (id in zoneIds) {
      val raw = form.zones[id].orEmpty()
      if (StorefrontRules.asciiDigits(raw).isEmpty()) continue
      val m = intIn(raw, ZONE_MIN, ZONE_MAX)
      if (m == null) errors["zone:$id"] = range(ZONE_MIN, ZONE_MAX) else zoneTravel[id] = m
    }
    if (errors.isNotEmpty()) return Checked(errors, null)
    return Checked(emptyMap(), body(form.show, prep!!, travel!!, parallel!!, if (zoneIds.isEmpty()) null else zoneTravel, expectedVersion))
  }

  fun body(show: Boolean, prep: Int, travel: Int, parallel: Int, zoneTravel: Map<String, Int>?, expectedVersion: Long): JsonObject =
    buildJsonObject {
      put("show", show)
      put("prepMinutes", prep)
      put("travelMinutes", travel)
      put("parallel", parallel)
      if (zoneTravel != null) putJsonObject("zoneTravel") { for ((k, v) in zoneTravel) put(k, v) }
      put("expectedVersion", expectedVersion)
    }

  /** بازه ⇒ «۲۰ تا ۳۰ دقیقه» */
  fun rangeText(r: EtaRangeDto?): String? =
    r?.let { "${StorefrontRules.toFa(it.minMinutes)} تا ${StorefrontRules.toFa(it.maxMinutes)} دقیقه" }

  fun reasonLabel(reason: String): String = when (reason) {
    "hidden" -> "نمایش خاموش است"
    "paused" -> "پذیرشِ سفارش موقتاً متوقف است"
    "closed" -> "دکان الان بسته است"
    "unconfirmed" -> "اتصالِ دکان هنوز تأیید نشده"
    else -> "الان عددی نشان داده نمی‌شود"
  }

  /** خطِ پیش‌نمایشِ مشتری — همان‌چه ویترین الان نشان می‌دهد */
  fun previewLines(p: EtaPreviewDto?, zones: List<EtaZoneDto>): List<String> {
    if (p == null) return listOf("ویترین هنوز ساخته نشده؛ پیش‌نمایشی نیست")
    if (!p.available) return listOf("مشتری الان عددی نمی‌بیند: ${reasonLabel(p.reason)}")
    val names = zones.associate { it.id to it.name }
    return buildList {
      rangeText(p.delivery)?.let { add("ارسال: $it") }
      rangeText(p.pickup)?.let { add("دریافت از دکان: $it") }
      for (z in p.zones) add("${names[z.id] ?: "ناحیه"}: ${rangeText(EtaRangeDto(z.minMinutes, z.maxMinutes))}")
    }
  }

  /** نشانه‌های همین لحظه (فقط برای خودِ دکان) */
  fun signalLine(s: EtaSignalsDto): String = buildString {
    append("صف: ${StorefrontRules.toFa(s.queue)} سفارش")
    append(" · آماده‌سازی: ${StorefrontRules.toFa(s.prepMinutes)} دقیقه")
    append(if (s.prepFromHistory) " (از سفارش‌های اخیر)" else " (از تنظیم؛ هنوز سفارشِ کافی نیست)")
    if (s.busyExtraMinutes > 0) append(" · شلوغ: +${StorefrontRules.toFa(s.busyExtraMinutes)} دقیقه")
    if (s.couriers.couriers > 0) append(" · پیک: ${StorefrontRules.toFa(s.couriers.live)} در راه از ${StorefrontRules.toFa(s.couriers.cap)}")
  }

  /** خطای سرور ⇒ متنِ فارسی؛ ناسازگاریِ نسخه پیامِ خودش را دارد، بقیه همان پیامِ فارسیِ سرور */
  fun errorText(code: String, serverMessage: String): String = when (code) {
    "version_conflict" -> "تنظیم در این میان جای دیگری عوض شد؛ دوباره باز کنید"
    "storefront_eta_disabled", "storefront_disabled" -> "زمانِ تقریبیِ تحویل برای این دکان روشن نیست"
    else -> serverMessage.ifBlank { "ذخیره نشد؛ دوباره تلاش کنید" }
  }

  suspend fun load(api: ApiClient): ApiResult<EtaViewDto> = ApiResult.of { parse(api.get(ROOT)) }

  suspend fun save(api: ApiClient, body: JsonObject): ApiResult<EtaSettingsDto> = ApiResult.of { parseSettings(api.put(ROOT, body)) }
}

@Serializable
data class EtaPermissionsDto(val manage: Boolean = false)

@Serializable
data class EtaSettingsDto(
  val show: Boolean = true,
  val prepMinutes: Int = 15,
  val travelMinutes: Int = 20,
  val parallel: Int = 2,
  val zoneTravel: Map<String, Int> = emptyMap(),
  val version: Long = 0,
  val updatedAt: Long? = null,
)

@Serializable
data class EtaZoneDto(val id: String = "", val name: String = "", val active: Boolean = true)

@Serializable
data class EtaCouriersDto(val couriers: Int = 0, val cap: Int = 0, val live: Int = 0, val waiting: Int = 0)

@Serializable
data class EtaSignalsDto(
  val queue: Int = 0,
  val prepMinutes: Int = 0,
  val samples: Int = 0,
  val prepFromHistory: Boolean = false,
  val busyExtraMinutes: Int = 0,
  val couriers: EtaCouriersDto = EtaCouriersDto(),
)

@Serializable
data class EtaRangeDto(val minMinutes: Int = 0, val maxMinutes: Int = 0)

@Serializable
data class EtaZoneRangeDto(val id: String = "", val minMinutes: Int = 0, val maxMinutes: Int = 0)

@Serializable
data class EtaPreviewDto(
  val available: Boolean = false,
  val reason: String = "",
  val delivery: EtaRangeDto? = null,
  val pickup: EtaRangeDto? = null,
  val zones: List<EtaZoneRangeDto> = emptyList(),
)

/** `GET /api/shop/storefront/eta` — پرچمِ خاموش ⇒ فقط `enabled = false` */
@Serializable
data class EtaViewDto(
  val enabled: Boolean = false,
  val settings: EtaSettingsDto = EtaSettingsDto(),
  val zones: List<EtaZoneDto> = emptyList(),
  val signals: EtaSignalsDto = EtaSignalsDto(),
  val preview: EtaPreviewDto? = null,
  val permissions: EtaPermissionsDto = EtaPermissionsDto(),
)
