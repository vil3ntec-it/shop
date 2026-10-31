package ir.vil3ntec.tohid

import ir.vil3ntec.tohid.core.calendar.ShopDay

/**
 *  جانشینِ JVMِ `ShopDayChoice`ِ برنامه (LOC-03)، فقط برای ابزارِ انتقال.
 *
 *  نسخهٔ برنامه حالتِ Compose و `Context` دارد و این‌جا کامپایل نمی‌شود؛ `Jalali.kt`
 *  فقط برای «پایانِ روزِ کاری» ِ `todayIso` به آن نگاه می‌کند. ابزار انتخابِ دکان را
 *  نمی‌داند، پس همان پیش‌فرضِ برنامه را برمی‌گرداند: نیمه‌شب، یعنی تاریخِ تقویمی.
 */
object ShopDayChoice {
  val hours: Int = ShopDay.OFF
}
