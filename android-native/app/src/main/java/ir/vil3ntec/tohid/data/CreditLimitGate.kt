package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrSaleCaps
import ir.vil3ntec.tohid.core.perm.CreditLimitPerm

/**
 *  CRM-03 / §۱۰.۱ — برگهٔ مشتری پیش از ذخیره می‌پرسد «این کاربر سقفِ اعتبارِ همین مشتری را عوض می‌کند؟» (`customers.credit_limit`).
 *  قاعده **همان** `core/perm/CreditLimitPerm` است که سرور (`server/src/lib/credit-limit-perm.js`) با آن رد می‌کند، پس برگه
 *  تغییری را نمی‌نشاند که سرور بی‌صدا به صندوقِ تعارض بفرستد:
 *   • حالتِ «ممنوع» ⇒ کادرِ سقف بسته با دلیل ([helper])؛ • سقفِ نقش (`maxAmount`) ⇒ کادر باز با «تا X» و بیشتر یا «بی سقف»
 *     ⇒ خطای درجا و هیچ ذخیره ([error])؛ • مجاز ⇒ همان امروز. این کلید «نیازمند تأیید» ندارد (سرور برگهٔ تأیید را برایش
 *     نمی‌خواند)، پس PIN ِ مدیر یا «پرسیدن از مدیر» راهِ آن نیست؛ صاحبِ دکان خودش عوض می‌کند.
 *  ⛔ فقط با پرچمِ روشنِ `shop.credit_limit_perm` ِ همین دکان (`rememberCreditLimitRules` ⇐ `policy.editable`)؛ خاموش، صاحب
 *  یا مشتریِ تازه ⇒ هیچ تغییری.
 *
 *  Kotlinِ خالص (بی android.*): گوشیِ نسخهٔ ۳ (`DebtorFormSheet`/`DebtorFormsModel.save`) و کامپیوتر (`DeskCustomersScreen`).
 */
object CreditLimitGate {

  /** راهنمای کادرِ سقف: بسته ⇒ دلیل؛ سقفِ نقش ⇒ «تا X»؛ null ⇒ راهنمای امروز */
  fun helper(f: CreditLimitPerm.Field): String? {
    if (f.locked) return StrSaleCaps.limitLocked
    //  ⛔ بی smart cast: `capMinor` در پیمانهٔ دیگر (`core`) است
    val cap = f.capMinor ?: return null
    return StrSaleCaps.limitUpTo(ir.vil3ntec.tohid.money(cap / 100.0))
  }

  /**
   *  @param debtor مشتریِ موجود (null = مشتریِ تازه، سنجیده نمی‌شود)
   *  @param limit سقفِ تازه به افغانی (null = بی سقف)
   *  ⇒ null (ذخیره آزاد) یا پیامِ ردِ همین تغییر
   */
  fun error(rules: CreditLimitPerm.Rules, debtor: Debtor?, limit: Double?): String? {
    val d = CreditLimitPerm.check(rules, debtor?.creditLimit, limit, existing = debtor != null) ?: return null
    if (d.reason != "over_cap") return StrSaleCaps.limitLocked
    val cap = ir.vil3ntec.tohid.money((d.limit ?: 0.0) / 100.0)
    return if (limit == null) StrSaleCaps.limitNoRemove(cap) else StrSaleCaps.limitOverCap(cap)
  }
}
