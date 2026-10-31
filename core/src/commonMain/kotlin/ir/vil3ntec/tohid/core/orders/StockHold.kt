package ir.vil3ntec.tohid.core.orders

/**
 *  M5 — رزروِ نرمِ موجودی برای سفارشِ آنلاین، از چشمِ برنامهٔ مدیریت (سرور: `server/src/lib/stock-reserve.js`).
 *
 *  ⛔ منبعِ حقیقت سرور است: رزرو، انقضا و «در بررسی» را فقط سرور می‌سازد و می‌بندد. این‌جا فقط
 *  ۱) سنجشِ مهلتِ نگه‌داشتن همان‌طور که سرور می‌سنجد (`stock.saveSettings`: عددِ صحیح ۶۰ تا ۸۶۴۰۰ ثانیه)،
 *  ۲) نگاشتِ `stockHold` ِ جزئیاتِ سفارش به متن و کارِ پیشنهادی، و
 *  ۳) «کدام سفارش تازه به بررسی رفت» برای اعلانِ یک‌باره.
 *
 *  ⛔ رزرو هرگز جلوی فروشِ حضوری را نمی‌گیرد (INV-19): اگر فروشِ حضوری کالای نگه‌داشته را برد، **سفارش**
 *  به بررسی می‌رود و دکان تصمیم می‌گیرد — ردِ «ناموجود»، لغو، یا صبر تا ورودِ کالا (بررسی خودش برداشته می‌شود).
 *
 *  خالص است — نه شبکه، نه زمانِ سیستم، نه android.* / java.* — تا اندروید، کامپیوتر و آیفون همین را بگیرند.
 */
object StockHold {

  /** همان بازهٔ `intIn(b.reserveTtlSec, …, 60, 86400)` ِ سرور */
  const val TTL_MIN_SEC = 60
  const val TTL_MAX_SEC = 86_400

  /** پیش‌فرضِ سرور (`stock-reserve.DEFAULT_TTL_SEC`) وقتی دکان هنوز چیزی ننوشته */
  const val TTL_DEFAULT_SEC = 1_800

  const val TTL_MIN_MINUTES = TTL_MIN_SEC / 60
  const val TTL_MAX_MINUTES = TTL_MAX_SEC / 60

  /** دقیقاً شرطِ سرور: عددِ صحیح در بازه */
  fun ttlSecValid(sec: Long): Boolean = sec in TTL_MIN_SEC.toLong()..TTL_MAX_SEC.toLong()

  sealed interface Ttl {
    data class Ok(val sec: Int) : Ttl
    data class Error(val message: String) : Ttl
  }

  /**
   *  ورودیِ فرم به **دقیقه** (رقمِ فارسی، عربی یا لاتین) ⇒ ثانیه برای `PUT /shop/catalog/settings`.
   *  دقیقهٔ درست ۱ تا ۱۴۴۰ دقیقاً همان ۶۰ تا ۸۶۴۰۰ ثانیهٔ سرور است، پس هر «درست» این‌جا را سرور هم می‌پذیرد.
   */
  fun parseTtlMinutes(text: String): Ttl {
    val t = latinDigits(text.trim())
    if (t.isEmpty()) return Ttl.Error("مهلت را به دقیقه بنویسید ($TTL_MIN_MINUTES تا $TTL_MAX_MINUTES)")
    if (t.length > 7 || !t.all { it in '0'..'9' }) return Ttl.Error("فقط عددِ صحیح، به دقیقه ($TTL_MIN_MINUTES تا $TTL_MAX_MINUTES)")
    val sec = t.toLong() * 60
    if (!ttlSecValid(sec)) return Ttl.Error("مهلت باید بینِ $TTL_MIN_MINUTES دقیقه و ۲۴ ساعت ($TTL_MAX_MINUTES دقیقه) باشد")
    return Ttl.Ok(sec.toInt())
  }

  /** مقدارِ فعلی برای پر کردنِ فرم (دقیقه). مقدارِ سرور که مضربِ دقیقه نیست ⇒ رو به بالا (هرگز کمتر از سرور). */
  fun minutesField(sec: Int): String = ((sec + 59) / 60).toString()

  /** «۳۰ دقیقه»، «۲ ساعت»، «۱ ساعت و ۳۰ دقیقه»، «۹۰ ثانیه» — بی گرد کردنِ پنهان */
  fun ttlLabel(sec: Int): String {
    if (sec % 60 != 0) return "$sec ثانیه"
    val m = sec / 60
    val h = m / 60
    val r = m % 60
    return when {
      h == 0 -> "$m دقیقه"
      r == 0 -> "$h ساعت"
      else -> "$h ساعت و $r دقیقه"
    }
  }

  /* ─────────────────────────── وضعیتِ رزروِ یک سفارش ─────────────────────────── */

  /** حالِ نمایشیِ `stockHold` (سرور: `orderView`) */
  enum class Phase { REVIEW, HELD, EXPIRED, COMMITTED, RELEASED }

  data class Line(val lineNo: Int, val status: String, val short: Boolean)

  /** `review` بر همه جلو می‌افتد؛ وضعیتِ ناشناس (سرورِ تازه‌تر) ⇒ null ⇒ هیچ نشانی، نه حدس */
  fun phase(status: String, review: Boolean): Phase? = when {
    review -> Phase.REVIEW
    status == "held" -> Phase.HELD
    status == "expired" -> Phase.EXPIRED
    status == "committed" -> Phase.COMMITTED
    status == "released" -> Phase.RELEASED
    else -> null
  }

  fun label(p: Phase): String = when (p) {
    Phase.REVIEW -> "سفارش در بررسی"
    Phase.HELD -> "کالا برای این سفارش نگه داشته شده"
    Phase.EXPIRED -> "مهلتِ نگه‌داشتن گذشت"
    Phase.COMMITTED -> "فروشِ تحویل ثبت شد"
    Phase.RELEASED -> "کالا آزاد شد"
  }

  /** فقط «در بررسی» هشدار است؛ بقیه آگاهی‌اند */
  fun isWarning(p: Phase): Boolean = p == Phase.REVIEW || p == Phase.EXPIRED

  /** شمارهٔ قلم‌هایی که دیگر پوشش ندارند (یکتا، به ترتیب) */
  fun shortLineNos(lines: List<Line>): List<Int> = lines.filter { it.short }.map { it.lineNo }.distinct().sorted()

  /** نامِ قلم‌های کم (از اقلامِ همین سفارش با `lineNo`)؛ قلمِ بی‌نام ⇒ «قلمِ n» */
  fun shortNames(lines: List<Line>, nameOf: Map<Int, String>): List<String> =
    shortLineNos(lines).map { n -> nameOf[n]?.takeIf { it.isNotBlank() } ?: "قلمِ $n" }

  /** متنِ راهنمای «در بررسی» در جزئیاتِ سفارش */
  fun reviewBody(names: List<String>): String {
    val what = when {
      names.isEmpty() -> "بخشی از کالای این سفارش"
      names.size <= 3 -> names.joinToString("، ") { "«$it»" }
      else -> names.take(3).joinToString("، ") { "«$it»" } + " و ${names.size - 3} قلمِ دیگر"
    }
    return "فروشِ حضوری $what را برد که برای این سفارش نگه داشته شده بود؛ فروش درست است و برنمی‌گردد. " +
      "سفارش را با دلیلِ «ناموجود» رد یا لغو کنید، یا با مشتری هماهنگ کنید و منتظرِ ورودِ کالا بمانید — " +
      "با رسیدنِ موجودی بررسی خودش برداشته می‌شود."
  }

  /** کارِ پیشنهادی برای سفارشِ در بررسی: گذار + دلیلِ «ناموجود»، فقط اگر همین کاربر همین گذار را دارد */
  data class Suggestion(val to: String, val reason: String)

  const val OUT_OF_STOCK = "out_of_stock"

  /**
   *  [allowed]: دکمه‌های مجازِ همین کاربر (`OrderStates.actions`). تازه ⇒ «رد»؛ قبول‌شده تا پیش از تحویل ⇒
   *  «لغو». هر دو دلیلِ «ناموجود» را در فهرستِ سرور دارند (`OrderStates.REASONS`). وگرنه null.
   */
  fun suggestion(orderStatus: String, allowed: List<String>): Suggestion? {
    val to = when {
      orderStatus == "new" -> "rejected"
      "cancelled" in (OrderStates.TRANSITIONS[orderStatus] ?: emptyList()) -> "cancelled"
      else -> return null
    }
    if (to !in allowed) return null
    if (OUT_OF_STOCK !in (OrderStates.REASONS[to] ?: emptyList())) return null
    return Suggestion(to, OUT_OF_STOCK)
  }

  /* ─────────────────────────── اعلانِ «سفارش به بررسی رفت» ─────────────────────────── */

  /** یک سفارشِ در بررسی از `GET /shop/orders/summary` (`review`) */
  data class Ref(val id: String, val orderNo: Long)

  /**
   *  کدام‌ها **تازه** به بررسی رفته‌اند ([seen]: شناسه‌هایی که پیش‌تر خبر داده شد). [keep]: آن‌چه باید
   *  ذخیره شود — فقط همین‌هایی که هنوز در بررسی‌اند، تا سفارشی که بیرون آمد و دوباره رفت، دوباره خبر بدهد.
   */
  data class Fresh(val fresh: List<Ref>, val keep: Set<String>)

  /**
   *  [claimed]: سفارش‌هایی که اعلانِ M6 (`order_review` ِ `/order-notify/pending`) در [CLAIM_MS] ِ گذشته
   *  خبرشان را داده ([claims]). خبرِ دوباره نمی‌گیرند و در [Fresh.keep] می‌مانند، حتی اگر این `summary`
   *  کهنه‌تر از همان `pending` باشد و هنوز آن‌ها را نداشته باشد — وگرنه دورِ بعد همان سفارش دوباره خبر می‌داد.
   */
  fun fresh(current: List<Ref>, seen: Set<String>, claimed: Set<String> = emptySet()): Fresh {
    val ids = current.map { it.id }.filter { it.isNotBlank() }.toSet()
    val skip = seen + claimed
    return Fresh(current.filter { it.id.isNotBlank() && it.id !in skip }.distinctBy { it.id }, ids + claimed)
  }

  /*
   *  ── یک سفارش، یک خبر (#429 × M6) ──────────────────────────────────
   *  «به بررسی رفت» از دو راه می‌رسد: نگهبان از `summary.review` (برنامهٔ بسته هم) و M6 از ردیفِ
   *  `order_review` ِ outbox (فقط برنامهٔ باز، با رسیدِ «باز شد»). هر کدام زودتر رسید خبر می‌دهد:
   *  M6 پیش از نشان دادن [claim] می‌پرسد و اگر بله، سفارش در «خبرداده‌شده»های نگهبان ([seen]) و
   *  ادعاها می‌نشیند؛ نگهبان سفارشِ ادعاشده را خبر نمی‌دهد ([fresh]).
   *  ──────────────────────────────────────────────────────────────────
   */

  /** ادعای M6 تا این مدت نگه داشته می‌شود — بسیار بیشتر از کهنگیِ هر پاسخِ `summary` */
  const val CLAIM_MS = 30 * 60_000L

  /** M6 این `order_review` را نشان بدهد؟ فقط اگر نگهبان یا خودِ M6 همین سفارش را پیش‌تر خبر نداده باشد. */
  fun claim(orderId: String, seen: Set<String>, claims: Map<String, Long>): Boolean =
    orderId.isNotBlank() && orderId !in seen && orderId !in claims

  /** ادعاهای ذخیره‌شده («شناسه|زمان») ⇒ ادعاهای زنده؛ کهنه، آینده‌دار یا بدشکل بیرون. */
  fun claims(raw: Set<String>, now: Long): Map<String, Long> {
    val out = HashMap<String, Long>()
    for (e in raw) {
      val i = e.lastIndexOf('|')
      if (i <= 0) continue
      val at = e.substring(i + 1).toLongOrNull() ?: continue
      if (at > now || now - at > CLAIM_MS) continue
      out[e.substring(0, i)] = maxOf(at, out[e.substring(0, i)] ?: 0L)
    }
    return out
  }

  fun encodeClaims(claims: Map<String, Long>): Set<String> = claims.map { (id, at) -> "$id|$at" }.toSet()

  /** متنِ اعلان — مثلِ اعلانِ سرور بی دادهٔ مشتری، فقط شمارهٔ سفارش */
  fun notice(fresh: List<Ref>): Pair<String, String>? {
    if (fresh.isEmpty()) return null
    val title = if (fresh.size == 1) "سفارش به بررسی رفت — #${fresh[0].orderNo}" else "${fresh.size} سفارش به بررسی رفت"
    val nos = fresh.take(5).joinToString("، ") { "#${it.orderNo}" } + if (fresh.size > 5) " …" else ""
    val body = "فروشِ حضوری کالای نگه‌داشتهٔ $nos را برد؛ در «سفارش‌ها» تصمیم بگیرید."
    return title to body
  }

  private fun latinDigits(s: String): String = buildString(s.length) {
    for (ch in s) append(
      when (ch) {
        in '۰'..'۹' -> '0' + (ch - '۰')
        in '٠'..'٩' -> '0' + (ch - '٠')
        else -> ch
      }
    )
  }
}
