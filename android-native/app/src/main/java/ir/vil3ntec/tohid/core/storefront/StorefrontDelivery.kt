package ir.vil3ntec.tohid.core.storefront

/**
 *  «ارسال و زمان» ِ ویترین از سمتِ صاحب دکان (CHK-06/07/10/12) — قاعده‌های
 *  خالصِ کادرها، همان که سرور (`server/src/lib/storefront-delivery.js`) و نسخهٔ
 *  وب (`license/storefront-owner.js`) می‌سنجند. سرور دوباره همه را می‌سنجد؛ این
 *  فقط خطا را کنارِ همان کادر و پیش از رفتن نشان می‌دهد.
 *
 *  ناحیه ── چندضلعی (هر خط «عرض، طول»، ۳ تا ۱۰۰ نقطه) یا دایره (مرکز + شعاع
 *           به کیلومتر، ۰٫۰۵ تا ۵۰)، با هزینه، حداقل و «رایگان از…» ِ اختیاری.
 *  بازه ─── روش، روزها، «10:00-12:00»، ظرفیت (۱ تا ۱۰۰۰) و مهلت (دقیقه)؛ دو
 *           بازهٔ فعالِ یک روش در یک روز روی هم نمی‌افتند.
 */
object StorefrontDelivery {

  data class LatLng(val lat: Double, val lng: Double)

  /** کادرهای یک ناحیه، همان‌طور که کاربر نوشته */
  data class ZoneDraft(
    val name: String = "",
    val kind: String = "polygon",
    val fee: String = "0",
    val min: String = "0",
    val free: String = "",
    val center: String = "",
    val radiusKm: String = "2",
    val polygon: String = "",
    val active: Boolean = true,
  )

  /** کادرهای یک بازه */
  data class RuleDraft(
    val fulfillment: String = "delivery",
    val weekdays: Set<Int> = setOf(6, 0, 1, 2, 3, 4),
    val range: String = "10:00-12:00",
    val capacity: String = "5",
    val cutoff: String = "60",
    val active: Boolean = true,
  )

  data class ZoneOut(
    val name: String, val kind: String, val feeMinor: Long, val minOrderMinor: Long, val freeOverMinor: Long?,
    val center: LatLng?, val radiusM: Int?, val polygon: List<LatLng>, val active: Boolean,
  )

  data class RuleOut(
    val fulfillment: String, val weekdays: List<Int>, val startMin: Int, val endMin: Int,
    val capacity: Int, val cutoffMin: Int, val active: Boolean,
  )

  /** خطاها به کلیدِ کادر: «z0.name»، «r1.range»، «slotDays» … */
  data class Checked(val errors: Map<String, String>, val zones: List<ZoneOut>, val rules: List<RuleOut>, val slotDays: Int)

  /** رقمِ فارسی/عربی ⇒ لاتین و «٫» ⇒ «.»، جداکننده‌ها می‌مانند */
  private fun latin(raw: String): String = buildString {
    for (c in raw.trim()) when (c) {
      in '۰'..'۹' -> append('0' + (c - '۰'))
      in '٠'..'٩' -> append('0' + (c - '٠'))
      '٫' -> append('.')
      else -> append(c)
    }
  }

  private val LAT_LNG = Regex("^(-?\\d{1,2}(?:\\.\\d+)?)\\s*[,،\\s]\\s*(-?\\d{1,3}(?:\\.\\d+)?)$")

  /** «34.53, 69.17» یا «۳۴٫۵۳،۶۹٫۱۷» ⇒ نقطه؛ نادرست ⇒ null (شش رقمِ اعشار) */
  fun parseLatLng(raw: String): LatLng? {
    val m = LAT_LNG.matchEntire(latin(raw)) ?: return null
    val lat = m.groupValues[1].toDouble()
    val lng = m.groupValues[2].toDouble()
    if (lat < -90 || lat > 90 || lng < -180 || lng > 180) return null
    return LatLng(Math.round(lat * 1e6) / 1e6, Math.round(lng * 1e6) / 1e6)
  }

  fun formatLatLng(p: LatLng): String = "${p.lat}, ${p.lng}"

  /** هر خط (یا «؛») یک نقطه ⇒ چندضلعی؛ خطا ⇒ پیام */
  fun parsePolygon(raw: String): Pair<List<LatLng>?, String?> {
    val lines = raw.split('\n', ';', '؛').map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.size < 3) return null to "دست‌کم ۳ نقطه، هر خط «عرض، طول»"
    if (lines.size > 100) return null to "حداکثر ۱۰۰ نقطه"
    val out = ArrayList<LatLng>(lines.size)
    for (l in lines) out += parseLatLng(l) ?: return null to "نقطهٔ «$l» درست نیست"
    return out to null
  }

  fun formatPolygon(points: List<LatLng>): String = points.joinToString("\n") { formatLatLng(it) }

  /** «10:00-12:00» ⇒ (۶۰۰، ۷۲۰) */
  fun parseRange(raw: String): Pair<Pair<Int, Int>?, String?> {
    val d = StorefrontOwner.parseDay(raw)
    if (d.error != null) return null to d.error
    if (d.ranges.size != 1) return null to "یک بازه مثلِ 10:00-12:00"
    return d.ranges[0] to null
  }

  /** نخستین بازه‌ای که با بازهٔ فعالِ دیگری از همان روش در همان روز روی هم افتاده — یا ‎-1‎ */
  fun overlapIndex(rules: List<RuleOut>): Int {
    val act = rules.withIndex().filter { it.value.active }
    for (a in act.indices) for (b in a + 1 until act.size) {
      val x = act[a].value
      val y = act[b].value
      if (x.fulfillment == y.fulfillment && x.weekdays.any { it in y.weekdays } && x.startMin < y.endMin && y.startMin < x.endMin) {
        return act[b].index
      }
    }
    return -1
  }

  /** پیش‌نویس ⇒ آن‌چه سرور می‌گیرد، یا خطا کنارِ هر کادر */
  fun check(zones: List<ZoneDraft>, rules: List<RuleDraft>, slotDays: String): Checked {
    val e = LinkedHashMap<String, String>()
    fun money(key: String, raw: String, max: Long = StorefrontOwner.MAX_PRICE_MINOR): Long? =
      try { StorefrontOwner.parseMinor(raw, max) } catch (x: IllegalArgumentException) { e[key] = x.message ?: "مبلغ درست نیست"; null }

    val zs = zones.mapIndexed { i, z ->
      val k = "z$i"
      if (z.name.isBlank()) e["$k.name"] = "نام لازم است"
      val fee = money("$k.fee", z.fee, StorefrontOwner.MAX_FEE_MINOR) ?: 0L
      val min = money("$k.min", z.min) ?: 0L
      val free = money("$k.free", z.free)
      var center: LatLng? = null
      var radius: Int? = null
      var poly: List<LatLng> = emptyList()
      if (z.kind == "radius") {
        center = parseLatLng(z.center)
        if (center == null) e["$k.center"] = "مثلِ 34.53, 69.17"
        val km = StorefrontOwner.asciiDigits(z.radiusKm).toDoubleOrNull()
        if (km == null || km < 0.05 || km > 50) e["$k.radius"] = "بین ۰٫۰۵ و ۵۰ کیلومتر" else radius = Math.round(km * 1000).toInt()
      } else {
        val (pts, err) = parsePolygon(z.polygon)
        if (err != null) e["$k.polygon"] = err else poly = pts!!
      }
      ZoneOut(z.name.trim().take(60), if (z.kind == "radius") "radius" else "polygon", fee, min, free, center, radius, poly, z.active)
    }

    val rs = rules.mapIndexed { i, r ->
      val k = "r$i"
      if (r.weekdays.isEmpty()) e["$k.days"] = "دست‌کم یک روز"
      val (range, rErr) = parseRange(r.range)
      if (rErr != null) e["$k.range"] = rErr
      val cap = StorefrontOwner.asciiDigits(r.capacity).toIntOrNull()
      if (cap == null || cap < 1 || cap > 1000) e["$k.capacity"] = "۱ تا ۱۰۰۰"
      val cut = StorefrontOwner.asciiDigits(r.cutoff.ifBlank { "0" }).toIntOrNull()
      if (cut == null || cut < 0 || cut > 10080) e["$k.cutoff"] = "۰ تا ۱۰۰۸۰ دقیقه"
      RuleOut(if (r.fulfillment == "pickup") "pickup" else "delivery", r.weekdays.sorted(),
        range?.first ?: 0, range?.second ?: 0, cap ?: 0, cut ?: 0, r.active)
    }

    val days = StorefrontOwner.asciiDigits(slotDays).toIntOrNull()
    if (days == null || days < 1 || days > 14) e["slotDays"] = "۱ تا ۱۴"
    if (e.isEmpty()) {
      val k = overlapIndex(rs)
      if (k >= 0) e["r$k.range"] = "با بازهٔ دیگرِ همین روش در همان روز روی هم افتاده است"
    }
    return Checked(e, zs, rs, days ?: 3)
  }

  /** برچسبِ کوتاهِ یک ناحیه در فهرست: «کارته‌سه · ۳۰ افغانی · حداقل ۰» */
  fun zoneSummary(z: ZoneOut): String = buildString {
    append(z.name)
    append(" · ارسال ").append(StorefrontOwner.toFa(StorefrontOwner.formatMinor(z.feeMinor))).append(" افغانی")
    if (z.minOrderMinor > 0) append(" · حداقل ").append(StorefrontOwner.toFa(StorefrontOwner.formatMinor(z.minOrderMinor)))
    z.freeOverMinor?.let { append(" · رایگان از ").append(StorefrontOwner.toFa(StorefrontOwner.formatMinor(it))) }
  }
}
