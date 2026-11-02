package ir.vil3ntec.tohid.core.storefront

import ir.vil3ntec.tohid.core.calendar.CivilDays

/*
 *  WEB-24 — فرمِ «بنرهای ویترین» در برنامهٔ مدیریت (`PUT /api/shop/storefront/banners`، همان
 *  `server/src/lib/storefront-extras.js`). سرور داورِ نهایی است؛ این‌جا همان قاعده‌ها پیش از فرستادن
 *  سنجیده می‌شوند تا خطا کنارِ همان کادر دیده شود:
 *
 *   • حداکثر [MAX] بنر؛ عنوان لازم و ≤ [TITLE_MAX]، متن ≤ [BODY_MAX].
 *   • بازه به **روزِ دکان**: از آغازِ روزِ «از» تا پایانِ روزِ «تا» (`dayStart` ِ همان منطقهٔ زمانیِ ویترین،
 *     از بیرون داده می‌شود)؛ «تا» پیش از «از» نه؛ حداکثر [MAX_DAYS] روز (همان ۹۲ روزِ سرور).
 *     بنرِ خوانده‌شده که روزهایش دست نخورده همان `startAt`/`endAt` ِ سرور را نگه می‌دارد.
 *   • پیوند: هیچ | کالا (شناسهٔ همان الگوی سرور) | دسته (لازم و ≤ [CATEGORY_MAX]).
 *   • ناحیه: خالی = همه‌جا. ⛔ بنری که فقط ناحیه‌های برداشته‌شده را دارد هرگز دیده نمی‌شود ⇒ خطا،
 *     نه «همه‌جا» ِ بی‌صدا (پخشِ ناخواسته).
 *   • اولویت عددِ صحیح ۰ تا [SORT_MAX] (بیشتر = جلوتر).
 *
 *  Kotlin ِ خالص (commonMain): بی java.* / android.*؛ پیام‌ها کد اند ([Err]) و متنشان در برنامه.
 */
object StorefrontBanners {
  const val MAX = 20
  const val TITLE_MAX = 80
  const val BODY_MAX = 200
  const val CATEGORY_MAX = 80
  const val MAX_DAYS = 92
  const val SORT_MAX = 100
  const val NONE = "none"
  const val PRODUCT = "product"
  const val CATEGORY = "category"
  val LINKS: List<String> = listOf(NONE, PRODUCT, CATEGORY)

  /** همان `sf.PRODUCT_ID_RE` ِ سرور */
  val PRODUCT_ID_RE = Regex("^[A-Za-z0-9_.:-]{1,80}$")

  /** نوشته‌های یک کارتِ بنر، همان‌طور که کاربر نوشته */
  data class Draft(
    val title: String = "",
    val body: String = "",
    val link: String = NONE,
    val productId: String = "",
    val category: String = "",
    /** روزِ آغاز و پایان (`yyyy-MM-dd`، هر دو شامل) */
    val startDay: String = "",
    val endDay: String = "",
    /** خالی = همه‌جا */
    val zoneIds: Set<String> = emptySet(),
    val sort: String = "0",
    /** زمان‌های سرورِ بنرِ خوانده‌شده و روزهایی که از آن‌ها ساخته شد — روزِ دست‌نخورده همان زمان را نگه می‌دارد */
    val startAt: Long? = null,
    val endAt: Long? = null,
    val loadedStartDay: String = "",
    val loadedEndDay: String = "",
  )

  /** بنرِ آمادهٔ فرستادن (همان کلیدهای بدنهٔ سرور) */
  data class Banner(
    val title: String,
    val body: String,
    val linkType: String,
    val linkValue: String,
    val zoneIds: List<String>,
    val startAt: Long,
    val endAt: Long,
    val sort: Int,
  )

  enum class Err {
    TOO_MANY, TITLE_REQUIRED, TITLE_LONG, BODY_LONG, START_BAD, END_BAD, END_BEFORE_START, SPAN_TOO_LONG,
    PRODUCT_REQUIRED, CATEGORY_REQUIRED, CATEGORY_LONG, SORT_BAD, ZONES_GONE,
  }

  /** نتیجهٔ سنجش: خطاها با کلیدِ `b<i>.<کادر>` (و `list` برای سقفِ فهرست)؛ بی خطا ⇒ [banners] */
  data class Checked(val banners: List<Banner>?, val errors: Map<String, Err>)

  private fun clean(s: String): String = s.trim()

  /** روزِ پس از [iso] (پایانِ بازه = آغازِ روزِ بعد) */
  fun nextDay(iso: String): String? = CivilDays.addDays(iso, 1)

  /** شمارِ روزهای بازه، هر دو سر شامل؛ تاریخِ ناخوانا ⇒ null */
  fun days(startDay: String, endDay: String): Int? {
    val a = CivilDays.jdnOfIso(startDay) ?: return null
    val b = CivilDays.jdnOfIso(endDay) ?: return null
    return b - a + 1
  }

  /**
   *  سنجشِ کلِ فهرست. [knownZones] ناحیه‌های امروزِ «ارسال و زمان»؛ [dayStart] آغازِ روزِ `yyyy-MM-dd`
   *  به وقتِ دکان (ms) یا null برای تاریخِ ناخوانا.
   */
  fun check(drafts: List<Draft>, knownZones: Set<String>, dayStart: (String) -> Long?): Checked {
    val errors = LinkedHashMap<String, Err>()
    if (drafts.size > MAX) errors["list"] = Err.TOO_MANY
    val out = ArrayList<Banner>()
    drafts.forEachIndexed { i, d ->
      val k = "b$i"
      val title = clean(d.title)
      val body = clean(d.body)
      when {
        title.isEmpty() -> errors["$k.title"] = Err.TITLE_REQUIRED
        title.length > TITLE_MAX -> errors["$k.title"] = Err.TITLE_LONG
      }
      if (body.length > BODY_MAX) errors["$k.body"] = Err.BODY_LONG

      val startKept = d.startAt != null && d.startDay == d.loadedStartDay
      val endKept = d.endAt != null && d.endDay == d.loadedEndDay
      val start = if (startKept) d.startAt else CivilDays.jdnOfIso(d.startDay)?.let { dayStart(d.startDay) }
      val end = if (endKept) d.endAt else CivilDays.jdnOfIso(d.endDay)?.let { nextDay(d.endDay)?.let(dayStart) }
      if (start == null) errors["$k.start"] = Err.START_BAD
      if (end == null) errors["$k.end"] = Err.END_BAD
      if (start != null && end != null) {
        if (end <= start) errors["$k.end"] = Err.END_BEFORE_START
        else if (end - start > MAX_DAYS * DAY_MS) errors["$k.end"] = Err.SPAN_TOO_LONG
      }

      val linkType = if (d.link in LINKS) d.link else NONE
      val linkValue = when (linkType) {
        PRODUCT -> d.productId.trim().also { if (!PRODUCT_ID_RE.matches(it)) errors["$k.link"] = Err.PRODUCT_REQUIRED }
        CATEGORY -> clean(d.category).also {
          if (it.isEmpty()) errors["$k.link"] = Err.CATEGORY_REQUIRED else if (it.length > CATEGORY_MAX) errors["$k.link"] = Err.CATEGORY_LONG
        }
        else -> ""
      }

      val sort = clean(d.sort).ifEmpty { "0" }.let(StorefrontRules::asciiDigits).toIntOrNull()
      if (sort == null || sort !in 0..SORT_MAX) errors["$k.sort"] = Err.SORT_BAD

      if (d.zoneIds.isNotEmpty() && d.zoneIds.none { it in knownZones }) errors["$k.zones"] = Err.ZONES_GONE

      if (start != null && end != null && sort != null) {
        out += Banner(title, body, linkType, linkValue, d.zoneIds.sorted(), start, end, sort)
      }
    }
    return Checked(if (errors.isEmpty()) out else null, errors)
  }

  /** بنرِ سرور ⇒ کارت: روزِ آغاز از `startAt` و روزِ پایان از آخرین لحظهٔ بازه (`endAt − 1`) */
  fun draftOf(b: Banner, dayOf: (Long) -> String): Draft {
    val startDay = dayOf(b.startAt)
    val endDay = dayOf(b.endAt - 1)
    return Draft(
      title = b.title, body = b.body, link = if (b.linkType in LINKS) b.linkType else NONE,
      productId = if (b.linkType == PRODUCT) b.linkValue else "",
      category = if (b.linkType == CATEGORY) b.linkValue else "",
      startDay = startDay, endDay = endDay, zoneIds = b.zoneIds.toSet(), sort = b.sort.toString(),
      startAt = b.startAt, endAt = b.endAt, loadedStartDay = startDay, loadedEndDay = endDay,
    )
  }

  /** کارتِ تازه: از امروز تا یک هفته، همه‌جا */
  fun fresh(today: String): Draft = Draft(startDay = today, endDay = CivilDays.addDays(today, 6) ?: today)

  /** «اکنون نشان داده می‌شود / آینده / گذشته» برای نشانِ هر کارت */
  enum class When { LIVE, LATER, ENDED }

  fun whenOf(b: Banner, now: Long): When = when {
    now < b.startAt -> When.LATER
    now >= b.endAt -> When.ENDED
    else -> When.LIVE
  }

  private const val DAY_MS = 24L * 60 * 60 * 1000
}
