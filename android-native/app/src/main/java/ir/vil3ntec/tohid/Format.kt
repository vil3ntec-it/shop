package ir.vil3ntec.tohid

import ir.vil3ntec.tohid.util.faDigits

/**
 *  عددها.
 *
 *  رقم‌ها **فارسی** نوشته می‌شوند: ۱۲۳ نه 123. متنِ برنامه فارسی است و
 *  عددِ لاتین وسطِ آن، مثلِ چیزی از یک برنامه‌ی دیگر دیده می‌شود.
 *
 *  جداکنندهٔ هزارگان کاما می‌ماند و اعشار نقطه.
 *
 *  این فقط **نمایش** است: آنچه کاربر می‌نویسد با `latinDigits()` خوانده
 *  می‌شود و خروجیِ CSV هم از این راه نمی‌گذرد.
 *
 *  همه‌چیز از همین چند تابع می‌گذرد. هر جای برنامه که عدد نشان می‌دهد،
 *  یکی از این‌ها را صدا می‌زند؛ پس شکلِ عدد یک جا تعریف شده، نه صد جا.
 */

fun Int.fa(): String = toString().faDigits()
fun Long.fa(): String = toString().faDigits()

/** مبلغ — رُند شده، با جداکنندهٔ هزارگان. واحد (افغانی) را صدازننده می‌گذارد. */
fun money(value: Double): String = moneyPlain(value).faDigits()

/**
 *  همان مبلغ، ولی با رقمِ لاتین.
 *
 *  برای متنی که **ذخیره** می‌شود، نه متنی که نشان داده می‌شود: یادداشتِ
 *  دفترچهٔ ثبت، شرحِ تراکنش، پیامِ خطای موتورها. آن رشته‌ها روی گوشی
 *  می‌مانند و به سرور می‌روند؛ عوض‌کردنِ رقمشان یعنی دفترِ قدیمی و تازه
 *  دو شکل داشته باشند. شکلِ نمایش کارِ لایهٔ رابط کاربری است.
 */
fun moneyPlain(value: Double): String {
  val n = if (value.isNaN() || value.isInfinite()) 0L else Math.round(value)
  val negative = n < 0
  val digits = kotlin.math.abs(n).toString()
  val out = StringBuilder()
  digits.forEachIndexed { i, c ->
    if (i > 0 && (digits.length - i) % 3 == 0) out.append(',')
    out.append(c)
  }
  return if (negative) "-$out" else out.toString()
}

/**
 * مقدار — کیلو و لیتر و گرم اعشار دارند، دانه ندارد. تا سه رقمِ اعشار،
 * بدونِ صفرهای اضافی؛ همان `formatCartQty` نسخهٔ وب.
 */
fun qty(value: Double): String = qtyPlain(value).faDigits()

/** همان مقدار با رقمِ لاتین — برای متنِ ذخیره‌شونده. شرحش سرِ `moneyPlain` */
fun qtyPlain(value: Double): String {
  val rounded = Math.round(value * 1000) / 1000.0
  return if (rounded == Math.floor(rounded)) rounded.toLong().toString()
  else rounded.toString().trimEnd('0').trimEnd('.')
}

/** عددِ ساده — سال و شمارهٔ فاکتور که پول نیستند و جداکننده نمی‌خواهند */
fun plain(value: Int): String = value.toString().faDigits()

/**
 *  یک شمارِ روز به متن — «۱۲ روز»، «۳ ماه»، «۲ سال و ۴ ماه».
 *
 *  ماه ۳۰ روز و سال ۳۶۵ روز حساب می‌شود: اینجا حرفِ «چقدر شده» است، نه
 *  تاریخِ دقیق — و تاریخِ دقیق را همان کنارش می‌نویسیم.
 */
fun daysText(days: Long): String {
  val d = days.coerceAtLeast(0)
  return when {
    d < 1 -> "امروز"
    d < 31 -> "${d.fa()} روز"
    d < 365 -> "${(d / 30).fa()} ماه"
    else -> {
      val years = d / 365
      val months = (d % 365) / 30
      if (months == 0L) "${years.fa()} سال" else "${years.fa()} سال و ${months.fa()} ماه"
    }
  }
}

/**
 *  «چقدر گذشته» — برای مدتِ عضویت و مدتِ قرض.
 *
 *  یک جا نوشته شده چون چند جای برنامه همین را می‌خواهند و اگر هر کدام
 *  خودش حساب می‌کرد، یکی «۲ ماه» می‌گفت و دیگری «۶۵ روز».
 */
fun spanText(from: Long, now: Long = System.currentTimeMillis()): String {
  if (from <= 0) return "—"
  return daysText((now - from) / 86_400_000L)
}

/**
 *  رقمِ فارسی و عربی به لاتین.
 *
 *  بعضی صفحه‌کلیدها رقمِ فارسی می‌فرستند و `toDouble` آن را نمی‌شناسد —
 *  عددی که کاربر دیده و نوشته نباید بی‌صدا صفر حساب شود. جداکنندهٔ
 *  اعشارِ عربی (`٫`) هم به نقطه تبدیل می‌شود.
 *
 *  یک جا نوشته شده تا همه‌جای برنامه یک‌شکل رفتار کند: کادرِ عدد، کادرِ
 *  تاریخ، جستجوی شمارهٔ فاکتور، و بارکدِ دستی.
 */
fun latinDigits(text: String): String = text.map { c ->
  when (c) {
    in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')   // ۰..۹
    in '\u0660'..'\u0669' -> '0' + (c - '\u0660')   // ٠..٩
    '\u066B', '\u066C' -> '.'                       // ٫ و ٬
    else -> c
  }
}.joinToString("")
