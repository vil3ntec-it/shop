package ir.vil3ntec.tohid.core.perm

import kotlin.math.roundToLong

/**
 *  CRM-03 / §۱۰.۱ — «تغییرِ سقفِ اعتبار» (`customers.credit_limit`) **پیش از ذخیره**، روی دستگاه.
 *
 *  سرور با پرچمِ خاموشِ `shop.credit_limit_perm` هر تغییرِ سقفِ مشتریِ موجود را در v1، v3 و REST می‌سنجد و ردِ آن
 *  (`credit_limit_not_allowed`) به صندوقِ تعارض می‌رود (`server/src/lib/credit-limit-perm.js`). این همان قاعده است تا
 *  برگهٔ مشتری چیزی را ذخیره نکند که سرور بی خبرِ کارمند رد می‌کند:
 *   • فقط **تغییر**: سقفِ پیشینِ همین مشتری (null = بی سقف) ≠ سقفِ تازه.
 *   • **مشتریِ تازه** (تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۲۳ #۲۵، بستنِ دورزدن): «پیشین» = سقفِ پیش‌فرضِ دکان ([NEW_DEFAULT_MINOR]؛
 *     دکان تنظیمی برایش ندارد ⇒ صفر)؛ پس کارمندِ «ممنوع» مشتریِ تازه را فقط با صفر می‌سازد و با سقفِ نقش تا همان سقف.
 *     سقفِ دیگر را مدیر/صاحب می‌گذارد (ویرایشِ مشتری، یا «دوباره ثبت با تأییدِ من» در صندوقِ تعارضِ سرور).
 *   • مبلغ = سقفِ تازه به Minor (`round(افغانی × ۱۰۰)`)؛ «بی سقف»، منفی یا عددِ نادرست ⇒ [INF] (بیش از هر `maxAmount`).
 *   • تصمیم همان `PermCore.evaluate(role, layers, PERM, {amount})`؛ این کلید «نیازمند تأیید» ندارد، پس هر چه «مجاز»
 *     نیست ممنوع است و راهِ PIN ِ مدیر یا «پرسیدن از مدیر» برایش نیست (سرور برگهٔ تأیید را برای این کلید نمی‌خواند).
 *  ⛔ فقط وقتی سرور همین کلید را برای همین دکان اجرا می‌کند ([Rules.enforced] ⇐ `policy.editable`)؛ پرچمِ خاموش، صاحبِ دکان
 *  یا نقشی که این نسخه نمی‌شناسد ⇒ [Rules.active] نادرست و همان رفتارِ امروز (سرور خودش می‌سنجد).
 *
 *  Kotlin خالص — بی java.*، بی android.*.
 */
object CreditLimitPerm {

  const val PERM = "customers.credit_limit"
  const val FLAG = "shop.credit_limit_perm"

  /** سقفِ پیش‌فرضِ مشتریِ تازه (Minor) — همان `DEFAULT_MINOR` ِ سرور */
  const val NEW_DEFAULT_MINOR: Long = 0L

  /**
   *  CRM-03 #۲۵ — سقفِ نخستِ مشتریِ تازه برای این کاربر: اگر «بی سقف» (پیش‌فرضِ امروز) برایش مجاز نیست ⇒ سقفِ پیش‌فرضِ دکان
   *  ([NEW_DEFAULT_MINOR] به افغانی)، وگرنه null (همان امروز). راه‌های «مشتریِ تازه با یک نام» (صندوق، فهرستِ مشتریان) همین را
   *  می‌گذارند تا سرور ردشان نکند.
   */
  fun newLimit(rules: Rules): Double? =
    if (check(rules, null, null, existing = false) != null) NEW_DEFAULT_MINOR / 100.0 else null

  /** «بی سقف» یا عددِ نادرست — همان `Number.MAX_SAFE_INTEGER` ِ سرور */
  const val INF: Long = 9_007_199_254_740_991L

  /** کاربرِ همین دستگاه و این‌که سرور این کلید را برای همین دکان اجرا می‌کند */
  data class Rules(val role: String, val layers: List<Any?>, val enforced: Boolean) {
    val active: Boolean get() = enforced && role != "owner" && role in PermCore.ROLES

    companion object {
      val OFF = Rules("owner", emptyList(), enforced = false)
    }
  }

  /** یک تغییرِ سقف: پیشین و تازه به Minor (null = بی سقف) و مبلغِ سنجش */
  data class Need(val fromMinor: Long?, val toMinor: Long?, val amount: Long)

  /** کادرِ سقف در برگهٔ مشتری: [locked] = هیچ تغییری پذیرفته نیست؛ [capMinor] = بیشترین سقفی که این کاربر می‌گذارد */
  data class Field(val locked: Boolean, val capMinor: Long?) {
    companion object {
      val OPEN = Field(locked = false, capMinor = null)
    }
  }

  /**
   *  @param from سقفِ پیشینِ همین مشتری به افغانی (null = بی سقف)
   *  @param to سقفِ تازه به افغانی (null = بی سقف)
   *  @param existing مشتریِ موجود است؛ نادرست ⇒ مشتریِ تازه، [from] نادیده و «پیشین» = [NEW_DEFAULT_MINOR]
   *  ⇒ null اگر سقف عوض نمی‌شود
   */
  fun need(from: Double?, to: Double?, existing: Boolean): Need? {
    //  #۲۵: مشتریِ تازه از سقفِ پیش‌فرضِ دکان سنجیده می‌شود (همان `needNew` ِ سرور)
    if (!existing) return need(NEW_DEFAULT_MINOR / 100.0, to, existing = true)
    //  همان `minorLimit` ِ سرور: افغانی ⇒ Minor؛ null یا عددِ نادرست ⇒ null
    val a = from?.takeIf { it.isFinite() }?.let { (it * 100).roundToLong() }
    val b = to?.takeIf { it.isFinite() }?.let { (it * 100).roundToLong() }
    if ((from == null && to == null) || (a != null && b != null && a == b)) return null
    val amount = if (b == null || b < 0 || b > INF) INF else b
    return Need(a, b?.takeIf { it in 0..INF }, amount)
  }

  /** درخواستِ عددی برای `PermCore.evaluate` — همان `req` ِ سرور */
  fun req(n: Need): Map<String, Double> = mapOf("amount" to n.amount.toDouble())

  /**
   *  ذخیرهٔ این تغییر بسته است؟ ⇒ null (آزاد، یا قاعده خاموش، یا سقف عوض نشده) یا تصمیمِ ردِ همین کاربر
   *  (`reason` = `denied` یا `over_cap` با `limit` = سقفِ Minor ِ نقش).
   */
  fun check(rules: Rules, from: Double?, to: Double?, existing: Boolean): PermCore.Decision? {
    if (!rules.active) return null
    val n = need(from, to, existing) ?: return null
    return PermCore.evaluate(rules.role, rules.layers, PERM, req(n)).takeIf { it.decision != "allow" }
  }

  /**
   *  حالِ کادرِ سقف پیش از هر نوشتن: بسته (حالتِ «ممنوع») یا باز تا سقفِ نقش؛ قاعدهٔ خاموش ⇒ [Field.OPEN]. مشتریِ تازه (#۲۵)
   *  همان حال را دارد و کادرش با [newLimit] پر می‌شود. [existing] برای خواناییِ صدازننده مانده است.
   */
  @Suppress("UNUSED_PARAMETER")
  fun field(rules: Rules, existing: Boolean): Field {
    if (!rules.active) return Field.OPEN
    val r = PermCore.resolve(rules.role, rules.layers, PERM)
    if (r.mode != "allow") return Field(locked = true, capMinor = null)
    val cap = r.caps["maxAmount"]?.takeIf { it.isFinite() && it >= 0 }
    return Field(locked = false, capMinor = cap?.let { kotlin.math.floor(it).toLong() })
  }
}
