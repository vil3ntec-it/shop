package ir.vil3ntec.tohid.core.product

import kotlin.math.abs
import kotlin.math.roundToLong

/*
 *  فروش با واحدِ بزرگ در ردیفِ صندوق — POS-09، PRD-07 (سناریوی S05): «روغن» با واحدِ پایهٔ «دانه» و
 *  «کارتن = ۱۲ دانه». صندوق (Ctrl+U در کامپیوتر، برگهٔ ردیف در گوشی) واحدِ ردیف را عوض می‌کند و تعداد به
 *  همان واحد تایپ می‌شود؛ موجودی همیشه به واحدِ پایه کم می‌شود.
 *
 *  • واحدها: پایه، واحدِ فروشِ دومِ کالا (`saleUnit` × `unitFactor`) و بسته‌ها (`PackBarcodes.Pack`)؛ نامِ تکراری یک بار.
 *  • قیمتِ یک واحد: قیمتِ همان بسته در لیستِ قیمتِ کالا (`Pack.priceMinor`) اگر تعریف شده، وگرنه اندازه × قیمتِ پایه.
 *  • «فروشِ بخشی از بسته مجاز نیست» (تنظیمِ دکان): تعدادِ اعشاری از واحدِ بزرگ‌تر از پایه رد می‌شود.
 *  منطقِ خالص؛ آزمون: `ScenarioS05Test`.
 */
object UnitSale {

  /** یک واحدِ ردیف: نام، اندازه به هزارمِ واحدِ پایه (کارتنِ ۱۲ دانه‌ای ⇒ 12000) و قیمتِ یک واحد از لیستِ قیمت */
  data class Unit(val name: String, val sizeMilli: Long, val priceMinor: Long? = null)

  /** پیامِ ردِ «بخشی از بسته» */
  fun partMessage(unit: String): String = "فروشِ بخشی از «$unit» مجاز نیست — تعدادِ درست بنویسید"

  private fun key(s: String) = SimilarNames.exactKey(s)

  /** واحدهای ردیف؛ نخستین همیشه پایه است */
  fun units(base: String, saleUnit: String, unitFactor: Double, packs: List<PackBarcodes.Pack>): List<Unit> {
    val out = ArrayList<Unit>()
    out += Unit(base.trim(), 1000L)
    val byName = packs.associateBy { key(it.name) }
    if (saleUnit.isNotBlank() && !unitFactor.isNaN() && unitFactor > 0) {
      val size = (unitFactor * 1000.0).roundToLong()
      if (size > 0 && size != 1000L) out += Unit(saleUnit.trim(), size, byName[key(saleUnit)]?.takeIf { it.sizeMilli == size }?.priceMinor)
    }
    for (p in packs) {
      if (p.sizeMilli > 0 && out.none { key(it.name) == key(p.name) }) out += Unit(p.name, p.sizeMilli, p.priceMinor)
    }
    return out
  }

  /** قیمتِ یک [u] (Minor): قیمتِ بسته از لیستِ قیمت، وگرنه اندازه × قیمتِ واحدِ پایه */
  fun priceMinor(u: Unit, basePriceMinor: Long): Long = u.priceMinor ?: (basePriceMinor.toDouble() * u.sizeMilli / 1000.0).roundToLong()

  /** تعدادِ اعشاری از واحدی بزرگ‌تر از پایه، وقتی دکان فقط بستهٔ کامل می‌فروشد */
  fun partRejected(count: Double, u: Unit, wholeOnly: Boolean): Boolean =
    wholeOnly && u.sizeMilli > 1000L && abs(count - count.roundToLong().toDouble()) > 1e-9

  /** تعداد به [u] ⇒ مقدار به واحدِ پایه (سه رقمِ اعشار)؛ `null` ⇒ نادرست یا بخشی از بستهٔ نامجاز */
  fun toBase(count: Double?, u: Unit, wholeOnly: Boolean): Double? {
    if (count == null || count.isNaN() || count <= 0 || partRejected(count, u, wholeOnly)) return null
    return (count * u.sizeMilli).roundToLong() / 1000.0
  }

  /** مقدار به واحدِ پایه ⇒ تعداد به [u] */
  fun fromBase(qty: Double, u: Unit): Double = qty * 1000.0 / u.sizeMilli

  /** قیمتِ هر واحدِ پایه (افغانی) برای ردیفِ سبد — فقط وقتی [u] قیمتِ خودش را دارد؛ `null` ⇒ قیمتِ خودِ کالا */
  fun lineUnitPrice(u: Unit): Double? = u.priceMinor?.let { it * 1000.0 / u.sizeMilli / 100.0 }

  /** Ctrl+U: واحدِ بعدی؛ پس از آخری دوباره پایه */
  fun next(units: List<Unit>, current: Int): Int = if (units.size < 2) 0 else (current + 1).mod(units.size)

  /** واحدی که مقدارِ ردیف ([qty] به پایه) را بی اعشار می‌گیرد — بزرگ‌ترین؛ وگرنه پایه (۰) */
  fun fitting(units: List<Unit>, qty: Double): Int {
    val milli = (qty * 1000.0).roundToLong()
    return units.indices.filter { it > 0 && milli > 0 && milli % units[it].sizeMilli == 0L }.maxByOrNull { units[it].sizeMilli } ?: 0
  }
}
