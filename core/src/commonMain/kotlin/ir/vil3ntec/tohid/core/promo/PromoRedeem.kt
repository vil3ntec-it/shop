package ir.vil3ntec.tohid.core.promo

/**
 *  PRM-02 — «سقفِ مصرفِ کد» پیش از ثبتِ فاکتور از سرور گرفته می‌شود
 *  (`POST /api/shop/promos/redeem`، پشتِ پرچمِ `shop.promo_uses` ِ سرور).
 *
 *  ⛔ فقط کدی که **اعمال شده** و قاعده‌اش سقف دارد (`maxUses > 0`) به سرور می‌رود؛ کدِ بی سقف و
 *     تخفیفِ خودکار همان رفتارِ آفلاینِ امروز را دارند (D-05).
 *  ⛔ سقف را فقط سرور می‌شمارد: بی پاسخِ سرور (آفلاین، قطع، ۵xx) کدِ سقف‌دار پذیرفته **نمی‌شود** — وگرنه
 *     دو صندوقِ آفلاین آخرین ظرفیت را دو بار می‌دادند. صندوقدار کد را برمی‌دارد یا وصل می‌شود.
 *  ⛔ همان فاکتور (کلیدِ پیش‌نویس) دوباره ⇒ سرور «already» می‌گوید و مصرفِ دوم نمی‌سازد؛ پس زدنِ
 *     دوباره پس از قطعِ شبکه بی‌خطر است.
 *
 *  منطقِ خالص (بی `java.*`/`android.*`، §۴۰.۳): برنامه شبکه را می‌زند و نتیجه را این‌جا می‌دهد.
 */
object PromoRedeem {

  /**
   *  کدهای سبد که باید پیش از ثبت گرفته شوند: وضعیتِ `applied` و قاعدهٔ زنده با همان کد و سقف.
   *  نرمال، بی تکرار و به ترتیبِ زده‌شدن.
   */
  fun capped(rules: List<PromoCore.Rule>, codes: List<PromoCore.CodeStatus>): List<String> {
    val out = ArrayList<String>()
    for (c in codes) {
      if (c.status != "applied") continue
      val code = PromoCore.normalizeCode(c.code)
      if (code.isEmpty() || code in out) continue
      if (rules.any { it.active && it.maxUses > 0 && it.code.isNotEmpty() && PromoCore.normalizeCode(it.code) == code }) out += code
    }
    return out
  }

  enum class Decision {
    /** سرور گرفت (یا همین فاکتور پیش‌تر گرفته بود) ⇒ ثبت */
    GO,
    /** سرور رد کرد (ظرفیتِ پر، کدِ ناشناخته یا خاموش) ⇒ ثبت نه؛ پیامِ خودِ سرور */
    REJECTED,
    /** پاسخی نیامد یا سرور نتوانست ⇒ ثبت نه؛ «فقط با اینترنت» */
    UNREACHABLE,
  }

  /**
   *  @param ok پاسخِ ۲xx
   *  @param status کدِ HTTP؛ ۰ = پاسخی نرسید
   *  @param code کدِ خطای سرور (`error.code`)
   */
  fun decide(ok: Boolean, status: Int, code: String): Decision = when {
    ok -> Decision.GO
    //  پرچمِ شمارش همین حالا در سرور خاموش شد ⇒ سقف شمرده نمی‌شود؛ همان رفتارِ پیش از PRM-02
    status == 403 && code == "promo_uses_disabled" -> Decision.GO
    status == 409 && code == "coupon_rejected" -> Decision.REJECTED
    //  ورودیِ نادرست هم یعنی این کد پذیرفته نمی‌شود — نه «دوباره بزن»
    status == 400 -> Decision.REJECTED
    else -> Decision.UNREACHABLE
  }

  // ── آزادِ خودکار با لغوِ فاکتور (PRM-02) ─────────────────────────────

  /**
   *  پس از لغوِ فاکتور باید از سرور آزاد کردنِ کدها خواسته شود؟ شمارشِ سرور در آخرین حالِ دکان روشن است
   *  (`redeem`) یا خودِ فاکتور کدِ اعمال‌شده دارد (`Sale.coupons`، پرچمِ `shop.promo_stamp`). نه ⇒ هیچ پرسشی:
   *  دکانی که سقفِ کد ندارد با هر لغو به سرور نمی‌رود.
   */
  fun releaseNeeded(redeemOn: Boolean, saleCoupons: List<String>): Boolean =
    redeemOn || saleCoupons.any { it.isNotBlank() }

  enum class Release {
    /** آزاد شد، چیزی برای آزاد کردن نبود، یا شمارش در سرور خاموش است ⇒ از صف بیرون */
    DONE,
    /** هرگز با تلاشِ دوباره درست نمی‌شود (اجازه نیست، ورودیِ نادرست) ⇒ از صف بیرون، بی تکرارِ بی‌پایان */
    DROP,
    /** پاسخی نیامد، نشست تمام شد یا سرور نتوانست ⇒ در صف می‌ماند تا بارِ بعد */
    RETRY,
  }

  /** پاسخِ `POST /redeem/release` ⇒ سرنوشتِ ردیفِ صف (`status` ۰ = پاسخی نرسید) */
  fun releaseDecision(ok: Boolean, status: Int, code: String): Release = when {
    ok -> Release.DONE
    //  شمارش خاموش ⇒ سقفی شمرده نمی‌شود؛ روشن شدنِ دوباره ردیف‌های آزادنشدهٔ کهنه را می‌شمارد، ولی صف همیشگی نمی‌ماند
    status == 403 && code == "promo_uses_disabled" -> Release.DONE
    //  `release_not_allowed`/`permission_denied`: لغوکننده اجازهٔ آزاد کردن ندارد ⇒ کارِ صاحب یا مدیر، نه تکرارِ همین دستگاه
    status == 403 || status == 400 || status == 404 -> Release.DROP
    else -> Release.RETRY
  }

  /** سقفِ صفِ آزادنشده‌ها روی دستگاه — صفِ بی‌اینترنتِ طولانی هرگز بی‌اندازه بزرگ نمی‌شود */
  const val MAX_PENDING = 200

  /** افزودنِ یک ردیف به صف: بی تکرار (همان ردیف ته می‌رود)، سقفِ [MAX_PENDING] با بیرون رفتنِ کهنه‌ترین */
  fun enqueue(queue: List<String>, entry: String): List<String> {
    if (entry.isBlank()) return queue
    val next = queue.filter { it != entry } + entry
    return if (next.size > MAX_PENDING) next.takeLast(MAX_PENDING) else next
  }
}
