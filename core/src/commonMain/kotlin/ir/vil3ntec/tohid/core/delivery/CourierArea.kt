package ir.vil3ntec.tohid.core.delivery

import ir.vil3ntec.tohid.core.text.TextNormalizer

/**
 *  محدودهٔ خدمتِ هر پیک (S83، §۱۹.۱۲) — دوقلوی `server/src/lib/delivery-courier-area.js` با بردارِ مشترکِ
 *  `core/test-vectors/courier-area-v1.json` (`CourierAreaVectorsTest`).
 *
 *  • محدودهٔ خالی = همه‌جا (رفتارِ پیش از S83).
 *  • ناحیهٔ سفارش = نامِ ناحیهٔ ارسالِ ویترین، وگرنه «ناحیه» ِ نشانی؛ مقایسه با `TextNormalizer.compareKey`
 *    (شناسهٔ ناحیهٔ ویترین با هر ذخیره از نو ساخته می‌شود، پس نام).
 *  • سفارشِ بی ناحیه بیرون از محدودهٔ هیچ پیکی نیست.
 *  • این‌جا فقط پیشنهاد و نمایش است؛ سرور هر سپردن را زیرِ قفلِ پیک دوباره می‌سنجد (۴۰۹ `courier_out_of_area`
 *    مگر `overrideArea`).
 */
object CourierArea {

  const val MAX_AREAS = 30
  const val MAX_LEN = 80

  fun key(s: String): String = TextNormalizer.compareKey(s)

  /** همان `tidy` ِ سرور: بی نویسهٔ کنترلی، فاصله‌ها یکی، دو سر بی فاصله */
  fun tidy(s: String): String {
    val out = StringBuilder(s.length)
    var space = false
    for (c in s) {
      if (c.code <= 31 || c.code == 127) continue
      if (TextNormalizer.isSpace(c)) { space = out.isNotEmpty(); continue }
      if (space) { out.append(' '); space = false }
      out.append(c)
    }
    return out.toString()
  }

  /** ناحیهٔ سفارش: نامِ ناحیهٔ ویترین (`address.zone.name`)، وگرنه `address.area` */
  fun orderArea(zoneName: String, area: String): String = tidy(zoneName).ifEmpty { tidy(area) }

  fun covers(areas: List<String>, orderArea: String): Boolean {
    if (areas.isEmpty()) return true
    val k = key(orderArea)
    if (k.isEmpty()) return true
    return areas.any { key(it) == k }
  }

  fun covers(c: CourierRow, orderArea: String): Boolean = covers(c.areas, orderArea)

  /** ورودیِ فرم ⇒ همان فهرستی که سرور نگه می‌دارد: پاک، بی تکرار (نخستین می‌ماند)، بی نامِ خالی یا بلند، حداکثر ۳۰ */
  fun clean(names: List<String>): List<String> {
    val seen = HashSet<String>()
    val out = ArrayList<String>()
    for (raw in names) {
      val name = tidy(raw)
      val k = key(name)
      if (k.isEmpty() || name.length > MAX_LEN || !seen.add(k)) continue
      out += name
      if (out.size == MAX_AREAS) break
    }
    return out
  }

  /** این نام همین حالا در فهرست هست؟ (با همان کلید) */
  fun has(areas: List<String>, name: String): Boolean = key(name).let { k -> k.isNotEmpty() && areas.any { key(it) == k } }

  /** افزودن یا برداشتنِ یک نام (زدنِ تراشه در فرمِ پیک) */
  fun toggle(areas: List<String>, name: String): List<String> =
    if (has(areas, name)) areas.filter { key(it) != key(name) } else clean(areas + name)
}
