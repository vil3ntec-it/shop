package ir.vil3ntec.tohid

/**
 *  عددها.
 *
 *  رقم‌ها **لاتین** نوشته می‌شوند: 123 نه ۱۲۳. متنِ برنامه فارسی است ولی
 *  عدد لاتین؛ همان چیزی که در دکان روی ماشین‌حساب و روی خودِ اسکناس هم
 *  دیده می‌شود، و رقمی که کاربر روی صفحه‌کلید می‌زند هم همین است.
 *
 *  جداکنندهٔ هزارگان کاما است و اعشار نقطه — هر دو لاتین، تا عددِ روی
 *  فاکتورِ کاغذی و عددِ روی صفحه یکی باشند و کپی‌کردنش هم عدد بماند.
 *
 *  همه‌چیز از همین چند تابع می‌گذرد. هر جای برنامه که عدد نشان می‌دهد،
 *  یکی از این‌ها را صدا می‌زند؛ پس شکلِ عدد یک جا تعریف شده، نه صد جا.
 */

fun Int.fa(): String = toString()
fun Long.fa(): String = toString()

/** مبلغ — رُند شده، با جداکنندهٔ هزارگان. واحد (افغانی) را صدازننده می‌گذارد. */
fun money(value: Double): String {
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
fun qty(value: Double): String {
  val rounded = Math.round(value * 1000) / 1000.0
  return if (rounded == Math.floor(rounded)) rounded.toLong().toString()
  else rounded.toString().trimEnd('0').trimEnd('.')
}

/** عددِ ساده — سال و شمارهٔ فاکتور که پول نیستند و جداکننده نمی‌خواهند */
fun plain(value: Int): String = value.toString()

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
