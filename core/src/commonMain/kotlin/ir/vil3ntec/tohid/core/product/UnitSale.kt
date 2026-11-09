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
 *  • PRD-07 — ضریبِ واحدِ فروشِ دوم (`Product.unitFactor`، Double روی خط) به هزارمِ واحدِ پایه: [factorMilli] = همان
 *    `unit_factor_milli` ِ سرور (`catalog.js`، `toStockLines`) و همان اندازهٔ [units]. ضریبِ تازه باید دقیقاً به هزارم
 *    نوشته شود ([exactMilli]) و تبدیلش با عددِ صحیح است ([toBaseQty]، همان `mulDivRound` ِ سرور؛ ضربِ ممیزِ شناور
 *    ۱٫۵ × ۰٫۰۰۹ را ۰٫۰۱۳ می‌کرد، نه ۰٫۰۱۴). «۱ دانه = ۱/۲۴ کارتن» در دفترِ هزارمی دقیق نمی‌ماند ⇒ ضریبِ تازهٔ ناجور رد؛
 *    ضریبِ کهنهٔ ناجور همان ضربِ پیشین را دارد (نزدیک‌ترین هزارم به همان فروش: ۶ بطری از کارتنِ ۱۲ = ۰٫۵).
 *  منطقِ خالص؛ آزمون: `ScenarioS05Test`، `UnitFactorTest`.
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
    val size = if (saleUnit.isBlank()) null else factorMilli(unitFactor)
    if (size != null && size != 1000L) out += Unit(saleUnit.trim(), size, byName[key(saleUnit)]?.takeIf { it.sizeMilli == size }?.priceMinor)
    for (p in packs) {
      if (p.sizeMilli > 0 && out.none { key(it.name) == key(p.name) }) out += Unit(p.name, p.sizeMilli, p.priceMinor)
    }
    return out
  }

  /** ضریبِ واحدِ فروشِ دوم ⇒ هزارمِ واحدِ پایه (گردِ نیمه‌به‌بالا، همان `toMilli` ِ سرور)؛ نامثبت، NaN یا بیرون از سقف ⇒ null */
  fun factorMilli(unitFactor: Double): Long? {
    if (!unitFactor.isFinite() || unitFactor <= 0) return null
    return (unitFactor * 1000.0).roundToLong().takeIf { it in 1..PackBarcodes.MAX_SIZE_MILLI }
  }

  /** ضریبی که دقیقاً به هزارم نوشته می‌شود (۰٫۰۰۱، ۰٫۵، ۲۴، ۱۰۰۰)؛ ۱/۲۴ یا ۰٫۰۰۰۵ ⇒ null — شرطِ ضریبِ تازه */
  fun exactMilli(unitFactor: Double): Long? =
    factorMilli(unitFactor)?.takeIf { abs(unitFactor * 1000.0 - it) <= 1e-9 * it }

  /**
   *  مقدار به واحدِ فروشِ دوم ⇒ به واحدِ پایه (سه رقمِ اعشار): هزارمِ مقدار × [factorMilli] ÷ ۱۰۰۰ با عددِ صحیح
   *  (نیمه به +∞، همان `PurchaseUnits.baseQtyMilli` و `mulDivRound` ِ سرور). ضریبِ نادرست ⇒ null. ضریبِ کهنه‌ای که
   *  به هزارم دقیق نیست (۱/۱۲) ⇒ همان ضربِ پیشین، گرد به هزارم: نزدیک‌ترین عدد به مقدارِ واقعیِ همین فروش.
   */
  fun toBaseQty(quantity: Double, unitFactor: Double): Double? {
    val f = factorMilli(unitFactor) ?: return null
    if (!quantity.isFinite()) return null
    if (exactMilli(unitFactor) == null) return (quantity * unitFactor * 1000.0).roundToLong() / 1000.0
    val q = (quantity * 1000.0).roundToLong()
    //  بیرون از بازهٔ دقیق (۲^۵۳) فقط عددِ بی‌معنا می‌رسد؛ همان حسابِ پیشین، بی پرتابِ خطا در صندوق
    return runCatching { PurchaseUnits.baseQtyMilli(q, f) / 1000.0 }.getOrElse { (quantity * f).roundToLong() / 1000.0 }
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
