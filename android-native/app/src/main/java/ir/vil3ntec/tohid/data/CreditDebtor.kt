package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrTillPay
import ir.vil3ntec.tohid.core.pay.Settlement
import ir.vil3ntec.tohid.core.pay.StoredPay

/**
 *  پولِ نسیه همیشه صاحب دارد (صاحب‌کار ۱۴۰۵/۰۸/۱۳: «فروشِ نسیه بی انتخابِ قرض‌دار ثبت می‌شود و بدهی به هیچ‌کس نمی‌رود»).
 *
 *  یک قاعده برای همهٔ صندوق‌ها — گوشیِ نسخهٔ ۳ و فروشِ سریع (`CheckoutModel`)، صندوقِ کامپیوتر (`DeskSale`)، آیفون (همان
 *  کدِ مشترک) — و برای خودِ ثبت (`SalesEngine.record`)، تا راهی که صفحه را دور بزند (فاکتورِ نگه‌داشته، پیش‌نویسِ پس از
 *  قطعِ برق، تعویض) هم بسته باشد:
 *  ⛔ هر سهمِ «به حساب» بزرگ‌تر از صفر — نسیهٔ کامل، پرداختِ ناقص، باقی‌ماندهٔ پرداختِ ترکیبی — یک مشتریِ **انتخاب‌شده،
 *     موجود و فعال** می‌خواهد. مشتریِ ادغام‌شده (CRM-10) فعال نیست: بدهیِ او در هیچ فهرستی دیده نمی‌شد (برگهٔ «انتخابِ
 *     مشتری» ِ گوشی همه را نشان می‌داد)؛ پیام نامِ مشتریِ ماندگار را می‌گوید.
 *  فروشِ نقدیِ کامل همان امروز است: مشتری اختیاری است و هیچ چیزی سنجیده نمی‌شود. دفتر و دادهٔ کهنه دست نمی‌خورند.
 *  سرور ردیفِ نسیهٔ بی‌مشتریِ برنامه‌های کهنه را رد نمی‌کند (فروشِ واقعی است) ولی نشان می‌گذارد:
 *  `server/src/lib/credit-debtor.js`.
 */
object CreditDebtor {

  enum class Issue(val code: String) {
    /** مشتری انتخاب نشده */
    MISSING("credit_no_debtor"),
    /** شناسه در دفتر نیست (حذف‌شده، یا هنوز به این دستگاه نرسیده) */
    UNKNOWN("debtor_unknown"),
    /** در مشتریِ دیگری ادغام شده (`Debtor.mergedInto`) */
    MERGED("debtor_merged"),
  }

  /** مشتریِ «به حساب» درست است؟ `null` ⇒ آری */
  fun issue(d: ShopData, debtorId: String?): Issue? {
    val id = debtorId?.trim()?.takeIf { it.isNotEmpty() } ?: return Issue.MISSING
    val x = d.debtors.find { it.id == id } ?: return Issue.UNKNOWN
    return if (CustomerCrm.isActive(x)) null else Issue.MERGED
  }

  /** سهمِ به‌حساب هست ([owed]) ⇒ مسئلهٔ مشتری؛ نیست ⇒ `null` (نقدِ کامل آزاد است) */
  fun check(d: ShopData, debtorId: String?, owed: Boolean): Issue? = if (owed) issue(d, debtorId) else null

  /** مشتریِ ماندگارِ یک ادغام‌شده (زنجیرهٔ `mergedInto`) */
  fun keeperOf(d: ShopData, debtorId: String?): String? = debtorId?.let { CustomerCrm.resolve(d, it) }

  private fun nameOf(d: ShopData, id: String?): String = d.debtors.firstOrNull { it.id == id }?.name?.trim().orEmpty().ifEmpty { "—" }

  /** متنِ ردِ موتور — فارسی مثلِ بقیهٔ ردهای `SalesEngine`؛ «بی مشتری» و «پیدا نشد» همان متنِ همیشه */
  fun engineText(d: ShopData, issue: Issue, debtorId: String?): String = when (issue) {
    Issue.MISSING -> "برای فروش نسیه، قرض‌دار را انتخاب کنید"
    Issue.UNKNOWN -> "قرض‌دار پیدا نشد"
    Issue.MERGED -> nameOf(d, keeperOf(d, debtorId)).let { keep ->
      "«${nameOf(d, debtorId)}» با «$keep» یکی شده است؛ برای نسیه «$keep» را انتخاب کنید"
    }
  }

  /** سهمِ به‌حسابِ پنلِ ترکیبی (پول): باقی‌ماندهٔ پیش‌نمایش (بی مشتری «کم»، با مشتری «به حساب») */
  fun splitOwed(s: Settlement?): Long = s?.remaining ?: 0L

  /** سهمِ به‌حسابِ پرداختِ ساده (پول): فقط «نسیه» و فقط باقی‌مانده */
  fun plainOwed(credit: Boolean, remaining: Double): Long = if (credit) PayFlow.minor(remaining).coerceAtLeast(0L) else 0L

  /**
   *  «ثبت» ِ صندوق: سهمِ به‌حسابِ [owedMinor] پول که صاحبِ درست ندارد ⇒ متنِ کنارِ «ثبت» ِ خاموش (با «انتخابِ مشتری»)؛
   *  `null` ⇒ آزاد. [split] = پنلِ ترکیبی (پیام «کامل کنید یا مشتری» می‌گوید، نه فقط «نسیه»).
   */
  fun tillBlock(d: ShopData, debtorId: String?, owedMinor: Long, split: Boolean): String? {
    if (owedMinor <= 0) return null
    return when (issue(d, debtorId) ?: return null) {
      Issue.MISSING -> if (split) StrTillPay.unpaidNeedsCustomer(ir.vil3ntec.tohid.money(PayFlow.major(owedMinor))) else StrTillPay.creditNeedsCustomer
      Issue.UNKNOWN -> StrTillPay.customerGone
      Issue.MERGED -> StrTillPay.customerMerged(nameOf(d, debtorId), nameOf(d, keeperOf(d, debtorId)))
    }
  }

  /** خرجِ اعتبارِ فروشگاه (PAY-24) هم «به حسابِ» مشتری است — همان مشتریِ فعال (`GiftCards` موجودی را می‌سنجد) */
  fun storedOwed(s: Settlement?): Long = s?.let { StoredPay.creditMinor(it) } ?: 0L
}
