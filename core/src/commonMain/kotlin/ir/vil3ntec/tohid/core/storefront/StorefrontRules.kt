package ir.vil3ntec.tohid.core.storefront

import kotlin.math.floor

/*
 *  قاعده‌های ورودیِ صفحهٔ «ویترین و بازار» ِ برنامهٔ مدیریت — Kotlin ِ خالص (commonMain).
 *
 *  سرور (`server/src/lib/storefront.js`، `lib/market.js`) داورِ نهایی است؛ این‌جا فقط همان
 *  قاعده‌ها پیش از فرستادن (یا پیش از گذاشتن در صفِ آفلاین) سنجیده می‌شوند تا خطا کنارِ همان
 *  کادر دیده شود و چیزی که سرور حتماً رد می‌کند در صف نماند.
 *
 *  ⛔ بی java.* / android.* (§۴۰.۳ و نگهبانِ linuxX64): همین فایل در آیفون هم ساخته می‌شود.
 *  ⛔ پول به یک‌صدمِ افغانی (Long) — بی ممیزِ شناور در دادهٔ رفت‌وبرگشتی.
 *
 *  پیش‌تر در `android-native/.../core/storefront/StorefrontOwner.kt` و `MarketListing.kt` بود؛
 *  آن دو حالا همین را صدا می‌زنند (رفتار و پیام‌ها همان است).
 */
object StorefrontRules {

  /* ------------------------------ عدد و متن ------------------------------ */

  /** رقمِ فارسی/عربی ⇒ لاتین؛ «٫» ⇒ «.»؛ جداکنندهٔ هزارگان حذف */
  fun asciiDigits(raw: String): String = buildString {
    for (c in raw.trim()) when (c) {
      in '۰'..'۹' -> append('0' + (c - '۰'))
      in '٠'..'٩' -> append('0' + (c - '٠'))
      '٫' -> append('.')
      '٬', ',', '،', ' ', '‌' -> Unit
      else -> append(c)
    }
  }

  fun toFa(s: String): String = buildString { for (ch in s) append(if (ch in '0'..'9') '۰' + (ch - '0') else ch) }

  fun toFa(n: Int): String = toFa(n.toString())

  /** بی `String.format`: آن رقم را به زبانِ دستگاه (فارسی) می‌نوشت */
  internal fun pad(n: Long, width: Int): String = n.toString().padStart(width, '0')

  /* ------------------------------ نشانی ------------------------------ */

  /** همان الگوی سرور (`SLUG_RE`) */
  val SLUG_RE = Regex("^[a-z0-9][a-z0-9-]{1,30}[a-z0-9]$")

  /** همان فهرستِ رزروِ سرور */
  val RESERVED: Set<String> = setOf(
    "api", "www", "admin", "panel", "portal", "sdk", "shop", "store", "stores", "beta", "app", "apps",
    "pump", "mail", "smtp", "imap", "pop", "ftp", "ns1", "ns2", "cdn", "static", "assets", "help",
    "support", "status", "docs", "blog", "dev", "test", "staging", "login", "auth", "account",
    "billing", "pay", "payment", "download", "downloads", "home", "vill3n", "tohid", "official",
  )

  /** نشانیِ ویترین — پیامِ خطا یا null */
  fun slugError(raw: String): String? {
    val s = raw.trim().lowercase()
    return when {
      s.isEmpty() -> "نشانی لازم است"
      !SLUG_RE.matches(s) || s.contains("--") -> "۳ تا ۳۲ حرفِ کوچکِ انگلیسی، رقم یا خطِ تیره"
      s in RESERVED -> "این نشانی رزرو است"
      else -> null
    }
  }

  /* ------------------------------ پول و مقدار ------------------------------ */

  const val MAX_PRICE_MINOR = 100_000_000_000L
  const val MAX_FEE_MINOR = 100_000_000L
  const val MAX_QTY_MILLI = 10_000_000L

  /** «۱۲۰٫۵» ⇒ ۱۲۰۵۰ (یک‌صدم)؛ خالی ⇒ null؛ نادرست یا منفی ⇒ IllegalArgumentException */
  fun parseMinor(raw: String, max: Long = MAX_PRICE_MINOR): Long? {
    val s = asciiDigits(raw)
    if (s.isEmpty()) return null
    require(Regex("^\\d{1,12}(\\.\\d{1,2})?$").matches(s)) { "مبلغ درست نیست" }
    val parts = s.split('.')
    val minor = parts[0].toLong() * 100 + (parts.getOrNull(1)?.padEnd(2, '0')?.toLong() ?: 0L)
    require(minor <= max) { "مبلغ بیش از حد بزرگ است" }
    return minor
  }

  /** ۱۲۰۵۰ ⇒ «120.5»، ۳۰۰۰۰ ⇒ «300» — برای کادرِ ویرایش */
  fun formatMinor(minor: Long): String {
    val whole = minor / 100
    val frac = minor % 100
    return if (frac == 0L) "$whole" else "$whole." + pad(frac, 2).trimEnd('0')
  }

  /** «۲٫۵» ⇒ ۲۵۰۰ (هزارم)؛ خالی ⇒ null */
  fun parseQtyMilli(raw: String): Long? {
    val s = asciiDigits(raw)
    if (s.isEmpty()) return null
    require(Regex("^\\d{1,6}(\\.\\d{1,3})?$").matches(s)) { "مقدار درست نیست" }
    val parts = s.split('.')
    val milli = parts[0].toLong() * 1000 + (parts.getOrNull(1)?.padEnd(3, '0')?.toLong() ?: 0L)
    require(milli in 1..MAX_QTY_MILLI) { "مقدار باید بیش از صفر باشد" }
    return milli
  }

  fun formatQtyMilli(milli: Long): String {
    val whole = milli / 1000
    val frac = milli % 1000
    return if (frac == 0L) "$whole" else "$whole." + pad(frac, 3).trimEnd('0')
  }

  /* ------------------------------ ساعت و تعطیلی ------------------------------ */

  /** «8:30» ⇒ ۵۱۰؛ «24:00» ⇒ ۱۴۴۰؛ نادرست ⇒ null */
  fun parseHm(raw: String): Int? {
    val m = Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(asciiDigits(raw).replace('.', ':')) ?: return null
    val h = m.groupValues[1].toInt()
    val min = m.groupValues[2].toInt()
    if (min > 59 || h > 24 || (h == 24 && min != 0)) return null
    return h * 60 + min
  }

  fun formatHm(minutes: Int): String = pad(minutes / 60L, 2) + ":" + pad(minutes % 60L, 2)

  /** نتیجهٔ خواندنِ ساعت‌های یک روز */
  data class DayHours(val ranges: List<Pair<Int, Int>>, val error: String? = null)

  /**
   *  «08:00-12:00، 14:00-20:00» ⇒ بازه‌ها (دقیقه، `[آغاز، پایان)`). خالی = آن روز
   *  تعطیل. همان قاعدهٔ سرور: حداکثر ۴ بازه، پایان پس از آغاز، بی هم‌پوشانی.
   *  شبِ پس از نیمه‌شب در روزِ بعد نوشته می‌شود (۰۰:۰۰ تا …).
   */
  fun parseDay(raw: String): DayHours {
    val s = raw.trim()
    if (s.isEmpty()) return DayHours(emptyList())
    val parts = s.split(',', '،', ';', '؛').map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size > 4) return DayHours(emptyList(), "حداکثر ۴ بازه در روز")
    val ranges = mutableListOf<Pair<Int, Int>>()
    for (p in parts) {
      val ends = p.split('-', '–', '—').map { it.trim() }
      if (ends.size != 2) return DayHours(emptyList(), "هر بازه مثلِ 08:00-12:00 نوشته شود")
      val a = parseHm(ends[0]) ?: return DayHours(emptyList(), "ساعتِ «${ends[0]}» درست نیست")
      val b = parseHm(ends[1]) ?: return DayHours(emptyList(), "ساعتِ «${ends[1]}» درست نیست")
      if (a >= 1440 || b <= a) return DayHours(emptyList(), "پایانِ بازه باید پس از آغاز باشد (شبِ پس از ۲۴ را در روزِ بعد بنویسید)")
      ranges += a to b
    }
    ranges.sortBy { it.first }
    for (i in 1 until ranges.size) {
      if (ranges[i].first < ranges[i - 1].second) return DayHours(emptyList(), "بازه‌ها روی هم افتاده‌اند")
    }
    return DayHours(ranges)
  }

  fun formatDay(ranges: List<Pair<Int, Int>>): String =
    ranges.joinToString("، ") { "${formatHm(it.first)}-${formatHm(it.second)}" }

  /** «2026-10-20، 2026-11-01» ⇒ فهرست؛ خطا با متن */
  fun parseClosedDates(raw: String): Pair<List<String>, String?> {
    val items = raw.split(',', '،', ' ', '\n', '\t').map { asciiDigits(it) }.filter { it.isNotEmpty() }
    if (items.size > 60) return emptyList<String>() to "حداکثر ۶۰ روزِ تعطیل"
    for (d in items) if (!validDate(d)) return emptyList<String>() to "تاریخِ «$d» باید مثلِ 2026-10-20 باشد"
    return items.distinct().sorted() to null
  }

  /** تاریخِ میلادیِ `YYYY-MM-DD` که واقعاً هست (۲۹ فوریه فقط در سالِ کبیسه) */
  fun validDate(s: String): Boolean {
    val m = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").matchEntire(s) ?: return false
    val (y, mo, d) = m.destructured
    val month = mo.toInt()
    val day = d.toInt()
    if (month !in 1..12 || day < 1) return false
    return day <= daysInMonth(y.toInt(), month)
  }

  internal fun daysInMonth(year: Int, month: Int): Int = when (month) {
    2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
  }

  /* ------------------------------ فرمِ تنظیمات (PUT /api/shop/storefront) ------------------------------ */

  /** همان `text(b.title, 60)`، `text(b.tagline, 120)`، `text(b.notice, 120)` ِ سرور — سرور می‌بُرد؛ ما می‌گوییم */
  const val TITLE_MAX = 60
  const val TAGLINE_MAX = 120
  const val NOTICE_MAX = 120

  val ACCENT_RE = Regex("^#[0-9a-fA-F]{6}$")

  /** کادرهای خامِ فرمِ «تنظیمات» — همان که کاربر نوشته */
  data class SettingsDraft(
    val slug: String,
    val title: String,
    val tagline: String = "",
    val phone: String = "",
    val accent: String = "",
    val notice: String = "",
    val enabled: Boolean = false,
    val delivery: Boolean = true,
    val pickup: Boolean = true,
    val fee: String = "",
    val minOrder: String = "",
    val timezone: String = "Asia/Kabul",
    /** روز (۰ = یکشنبه) ⇒ متنِ بازه‌ها */
    val days: Map<Int, String> = emptyMap(),
    val closed: String = "",
  )

  /** نتیجهٔ سنجش: خطا با کلیدِ کادر؛ خالی ⇒ فرستادنی و مقدارهای خوانده‌شده آماده */
  data class SettingsCheck(
    val errors: Map<String, String>,
    val hours: Map<Int, List<Pair<Int, Int>>> = emptyMap(),
    val closedDates: List<String> = emptyList(),
    val feeMinor: Long = 0,
    val minOrderMinor: Long = 0,
  ) {
    val ok: Boolean get() = errors.isEmpty()
  }

  fun checkSettings(d: SettingsDraft): SettingsCheck {
    val errors = linkedMapOf<String, String>()
    slugError(d.slug)?.let { errors["slug"] = it }
    val title = d.title.trim()
    if (d.enabled && title.isEmpty()) errors["title"] = "برای روشن کردن، نامِ ویترین لازم است"
    else if (title.length > TITLE_MAX) errors["title"] = "نام حداکثر ${toFa(TITLE_MAX)} نویسه است (الان ${toFa(title.length)})"
    if (d.tagline.trim().length > TAGLINE_MAX) errors["tagline"] = "حداکثر ${toFa(TAGLINE_MAX)} نویسه"
    if (d.notice.trim().length > NOTICE_MAX) errors["notice"] = "حداکثر ${toFa(NOTICE_MAX)} نویسه"
    if (d.accent.isNotBlank() && !ACCENT_RE.matches(d.accent.trim())) errors["accent"] = "مثلِ #2c5ce6"
    if (!d.delivery && !d.pickup) errors["fulfillment"] = "دست‌کم یکی از ارسال یا دریافت از دکان"
    if (d.timezone.isBlank()) errors["timezone"] = "منطقهٔ زمانی لازم است (مثلِ Asia/Kabul)"
    val fee = runCatching { parseMinor(d.fee, MAX_FEE_MINOR) ?: 0L }
      .getOrElse { errors["fee"] = it.message ?: "مبلغ درست نیست"; 0L }
    val min = runCatching { parseMinor(d.minOrder) ?: 0L }
      .getOrElse { errors["min"] = it.message ?: "مبلغ درست نیست"; 0L }
    val hours = linkedMapOf<Int, List<Pair<Int, Int>>>()
    for (day in 0..6) {
      val r = parseDay(d.days[day].orEmpty())
      if (r.error != null) errors["day$day"] = r.error else hours[day] = r.ranges
    }
    val (closedList, closedErr) = parseClosedDates(d.closed)
    closedErr?.let { errors["closed"] = it }
    return if (errors.isNotEmpty()) SettingsCheck(errors)
    else SettingsCheck(emptyMap(), hours, closedList, fee, min)
  }

  /* ------------------------------ بازار (PUT /api/shop/market) ------------------------------ */

  /** همان `text(b.area, 60)` ِ سرور */
  const val AREA_MAX = 60

  /**
   *  همان `stripControl(str(v))` ِ سرور: اول trim، بعد بی نویسهٔ کنترلی (≤۳۱ و ۱۲۷).
   *  نویسه‌های کنترلی همه تک‌واحدی‌اند و نیم‌جفت‌ها (surrogate) بالای ۳۱، پس پیمودنِ
   *  نویسه‌به‌نویسه همان نتیجهٔ پیمودنِ code point را دارد.
   */
  fun cleanArea(s: String): String = buildString {
    for (c in s.trim()) if (c.code > 31 && c.code != 127) append(c)
  }

  fun areaError(area: String): String? {
    val n = cleanArea(area).length
    return if (n > AREA_MAX) "ناحیه حداکثر ${toFa(AREA_MAX)} نویسه است (الان ${toFa(n)})" else null
  }

  /** `keys` = کلیدهای دسته که سرور می‌شناسد */
  fun categoryError(key: String, listed: Boolean, keys: Collection<String>): String? = when {
    key.isBlank() -> if (listed) "برای نمایش در بازارچه، دستهٔ دکان را انتخاب کنید" else null
    key !in keys -> "این دسته در فهرستِ بازارچه نیست"
    else -> null
  }

  /** همان `Math.round(v * 100)` ِ سرور (نیمه رو به بالا، نه رو به زوج) */
  fun coarse(v: Double): Double = floor(v * 100 + 0.5) / 100
}
