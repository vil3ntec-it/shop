package ir.vil3ntec.tohid.core.pos

import ir.vil3ntec.tohid.core.hw.ScaleReading
import ir.vil3ntec.tohid.core.hw.WeightBarcode
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 *  کالای وزنی در صندوق (POS-29، سناریوی S04) — از برچسبِ ترازو یا وزنِ خوانده از ترازو تا مقدارِ ردیف.
 *
 *  ── برچسبِ ترازو ([label]) ────────────────────────────────────────
 *  بی‌قاعده (`rules` خالی، پیش‌فرض) هیچ بارکدی برچسبِ ترازو نیست. بارکدی که خودش بارکدِ کالایی است
 *  برچسب حساب نمی‌شود (کالای ثبت‌شده جلوتر است). کدِ ترازو (PLU) یکی از بارکدهای کالاست: خودِ کد
 *  (`00150`) یا پیشوند و کد (`2100150`). رقمِ کنترلِ غلط ⇒ رد، نه «کالای ناشناس».
 *   • **وزنی**: مقدار همان وزن به واحدِ کالا (کیلو، یا گرم ⇐ ×۱۰۰۰ از عددِ خامِ برچسب).
 *   • **قیمتی**: مبلغِ برچسب به پول؛ مقدار = مبلغ ÷ قیمتِ کالا، گرد به گرم (کیلو سه رقم، گرم درست)،
 *     نیمه رو به بالا. فقط وقتی پذیرفته می‌شود که مبلغِ ردیفِ صندوق (قیمت × مقدار، گرد به افغانی)
 *     **همان** مبلغِ برچسب شود — اختلافِ اکیداً کمتر از نیم افغانی؛ وگرنه رد (مشتری همان را می‌پردازد
 *     که روی برچسب خوانده). مبلغِ کسری‌دارِ افغانی، کالای بی قیمت و برچسبِ صفر رد می‌شوند.
 *
 *  ── وزن از ترازو ([fromScale]) ─────────────────────────────────────
 *  فقط وزنِ ایستا، مثبت و بی‌خطا (`ScaleReading.usable`) و فقط برای کالای کیلویی یا گرمی؛ کیلو گرد به
 *  سه رقم، گرم گرد به گرمِ درست.
 *
 *  Kotlin خالص است — بی java.*، بی android.*؛ صفحه‌ها متنِ هر [Why] را خودشان دارند.
 */
object ScaleSale {

  /** واحدِ کالا از نگاهِ ترازو */
  enum class Weighs { KILO, GRAM, NONE }

  fun weighs(unit: String): Weighs = when (unit.trim()) {
    "کیلو", "کیلوگرم" -> Weighs.KILO
    "گرم" -> Weighs.GRAM
    else -> Weighs.NONE
  }

  /** همان چه از کالا لازم است؛ [priceMinor] قیمتِ هر واحد به پول */
  data class Item(val id: String, val name: String, val unit: String, val active: Boolean, val priceMinor: Long)

  enum class Why { BAD_CHECK, UNKNOWN_CODE, NOT_FOUND, INACTIVE, ZERO, NOT_WEIGHED, NO_PRICE, PRICE_MISMATCH }

  sealed interface Label {
    /** [quantity] به واحدِ کالا؛ [kg] همان به کیلو؛ [labelMinor] مبلغِ برچسبِ قیمتی (وزنی ⇒ `null`) */
    data class Weighed(val item: Item, val quantity: Double, val kg: Double, val labelMinor: Long? = null) : Label

    /** [arg] برای متن: رقمِ کنترل ⇒ کدِ خوانده، کدِ ناشناس ⇒ کدِ ترازو، بقیه ⇒ نامِ کالا */
    data class Rejected(val why: Why, val arg: String, val item: Item? = null) : Label
  }

  /**
   *  `null` ⇒ برچسبِ ترازو نیست؛ راهِ معمولیِ بارکد.
   *  @param code بارکدِ خوانده با رقمِ لاتین
   *  @param idOf بارکد ⇒ شناسهٔ کالا (همان نمایهٔ بارکد)
   */
  fun label(rules: List<WeightBarcode.Rule>, code: String, idOf: (String) -> String?, itemOf: (String) -> Item?): Label? {
    if (rules.isEmpty()) return null
    val clean = code.trim()
    if (idOf(clean) != null) return null
    return when (val r = WeightBarcode.parse(clean, rules)) {
      WeightBarcode.Result.NotScale -> null
      is WeightBarcode.Result.BadCheck -> Label.Rejected(Why.BAD_CHECK, clean)
      is WeightBarcode.Result.Hit -> hit(r, idOf, itemOf)
    }
  }

  private fun hit(r: WeightBarcode.Result.Hit, idOf: (String) -> String?, itemOf: (String) -> Item?): Label {
    val id = idOf(r.itemCode) ?: idOf(r.rule.prefix + r.itemCode)
      ?: return Label.Rejected(Why.UNKNOWN_CODE, r.itemCode)
    val p = itemOf(id) ?: return Label.Rejected(Why.NOT_FOUND, r.itemCode)
    if (!p.active) return Label.Rejected(Why.INACTIVE, p.name, p)
    return if (r.rule.kind == WeightBarcode.Kind.PRICE) priced(r, p) else weighed(r, p)
  }

  private fun weighed(r: WeightBarcode.Result.Hit, p: Item): Label {
    if (r.raw <= 0L) return Label.Rejected(Why.ZERO, p.name, p)
    val kg = r.value
    return when (weighs(p.unit)) {
      Weighs.KILO -> Label.Weighed(p, kg, kg)
      //  از عددِ خامِ برچسب، تا ۲۵ گرم ۲۵ بماند نه ۲۵٫۰۰۰۰۰۰۰۰۰۰۰۰۰۴
      Weighs.GRAM -> Label.Weighed(p, r.raw * 1000.0 / pow10(r.rule.decimals), kg)
      Weighs.NONE -> Label.Rejected(Why.NOT_WEIGHED, p.name, p)
    }
  }

  private fun priced(r: WeightBarcode.Result.Hit, p: Item): Label {
    val labelMinor = toMinor(r.raw, r.rule.decimals) ?: return Label.Rejected(Why.PRICE_MISMATCH, p.name, p)
    if (labelMinor <= 0L) return Label.Rejected(Why.ZERO, p.name, p)
    val step = when (weighs(p.unit)) {
      Weighs.KILO -> 1L
      Weighs.GRAM -> 1000L
      Weighs.NONE -> return Label.Rejected(Why.NOT_WEIGHED, p.name, p)
    }
    if (p.priceMinor <= 0L) return Label.Rejected(Why.NO_PRICE, p.name, p)
    //  صندوق مبلغِ ردیف را به افغانیِ درست گرد می‌کند؛ مبلغِ کسری‌دار هرگز همان نمی‌شود
    if (labelMinor % 100L != 0L) return Label.Rejected(Why.PRICE_MISMATCH, p.name, p)
    //  هزارمِ واحد، گرد به [step] نیمه رو به بالا
    val den = p.priceMinor * step
    val milli = (labelMinor * 1000L * 2L + den) / (den * 2L) * step
    if (milli <= 0L) return Label.Rejected(Why.PRICE_MISMATCH, p.name, p)
    //  پول × هزارم: نیم افغانی = ۵۰ پول × ۱۰۰۰
    if (abs(p.priceMinor * milli - labelMinor * 1000L) >= 50_000L) return Label.Rejected(Why.PRICE_MISMATCH, p.name, p)
    val quantity = milli / 1000.0
    val kg = if (step == 1L) quantity else milli / 1_000_000.0
    return Label.Weighed(p, quantity, kg, labelMinor)
  }

  /* ── قاعده‌های «دستگاه‌ها ⇐ ترازو»: طولِ کد و مقدار و رقمِ اعشار ── */

  /** رقم‌های کدِ کالا (PLU) روی برچسب؛ مقدار بقیهٔ ده رقمِ پس از پیشوندِ دورقمی است */
  val ITEM_DIGITS: List<Int> = listOf(4, 5, 6)

  /** رقمِ اعشارِ مقدار: وزن ⇐ گرم (۳) یا ده‌گرم (۲)؛ قیمت ⇐ افغانی (۰) یا با دو رقمِ پول (۲). نخستین پیش‌فرض است */
  fun decimalsFor(kind: WeightBarcode.Kind): List<Int> = if (kind == WeightBarcode.Kind.PRICE) listOf(0, 2) else listOf(3, 2)

  /**
   *  قاعده‌های ۲۰ تا ۲۹ با همین طول‌ها — همیشه EAN-13 ِ درست (۲ + کد + مقدار + رقمِ کنترل). طول یا اعشارِ
   *  بیرون از فهرست ⇒ پیش‌فرض (کدِ ۵ رقمی، نخستین اعشار)، نه قاعدهٔ نادرست.
   */
  fun rules(kind: WeightBarcode.Kind, itemDigits: Int, decimals: Int): List<WeightBarcode.Rule> {
    val item = itemDigits.takeIf { it in ITEM_DIGITS } ?: 5
    val dec = decimals.takeIf { it in decimalsFor(kind) } ?: decimalsFor(kind).first()
    return (20..29).map { WeightBarcode.Rule(prefix = it.toString(), itemDigits = item, valueDigits = 10 - item, kind = kind, decimals = dec) }
  }

  /**
   *  برچسبِ نمونه برای صفحه: کدِ ۱۵۰ با ۱٫۲۵ کیلو یا ۱۵۰ افغانی (اگر در رقم‌های مقدار نگنجد ۱۵ افغانی)، به
   *  شکلِ همین قاعده‌ها (پیشوندِ ۲۱). نگنجد ⇒ `null`.
   */
  fun sample(rules: List<WeightBarcode.Rule>): String? {
    val r = rules.firstOrNull { it.prefix == "21" && it.valid } ?: return null
    var scale = 1L
    repeat(r.decimals) { scale *= 10 }
    val tries = if (r.kind == WeightBarcode.Kind.PRICE) listOf(150L * scale, 15L * scale) else listOf(125L * scale / 100)
    val raw = tries.firstOrNull { it.toString().length <= r.valueDigits } ?: return null
    return WeightBarcode.build(r, "150", raw)
  }

  enum class ReadWhy { NOT_WEIGHED, ERROR, OVER, UNDER, MOTION, NO_WEIGHT }

  sealed interface Weigh {
    data class Ok(val quantity: Double, val kg: Double) : Weigh
    data class Rejected(val why: ReadWhy) : Weigh
  }

  /** وزنِ خوانده از ترازو ⇒ مقدارِ ردیف به واحدِ کالا ([unit]) */
  fun fromScale(reading: ScaleReading, unit: String): Weigh {
    val w = weighs(unit)
    if (w == Weighs.NONE) return Weigh.Rejected(ReadWhy.NOT_WEIGHED)
    if (reading.error) return Weigh.Rejected(ReadWhy.ERROR)
    if (reading.over) return Weigh.Rejected(ReadWhy.OVER)
    if (reading.under) return Weigh.Rejected(ReadWhy.UNDER)
    if (reading.motion) return Weigh.Rejected(ReadWhy.MOTION)
    val kg = reading.kg
    if (kg == null || !kg.isFinite() || kg <= 0.0) return Weigh.Rejected(ReadWhy.NO_WEIGHT)
    val grams = (kg * 1000.0).roundToLong()
    if (grams <= 0L) return Weigh.Rejected(ReadWhy.NO_WEIGHT)
    return when (w) {
      Weighs.KILO -> Weigh.Ok(grams / 1000.0, grams / 1000.0)
      else -> Weigh.Ok(grams.toDouble(), grams / 1000.0)
    }
  }

  /** عددِ خامِ برچسب با [decimals] رقمِ اعشار ⇒ پول؛ بیش از دو رقمِ اعشارِ ناصفر ⇒ `null` */
  private fun toMinor(raw: Long, decimals: Int): Long? =
    if (decimals <= 2) raw * pow10(2 - decimals).toLong()
    else {
      val div = pow10(decimals - 2).toLong()
      if (raw % div != 0L) null else raw / div
    }

  private fun pow10(n: Int): Double {
    var x = 1.0
    repeat(n) { x *= 10.0 }
    return x
  }
}
