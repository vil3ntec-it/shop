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
}
