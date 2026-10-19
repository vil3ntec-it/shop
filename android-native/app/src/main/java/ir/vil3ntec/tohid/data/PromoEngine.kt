package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.promo.PromoCore
import kotlin.math.roundToLong

/**
 *  تخفیف‌های خودکار در صندوق (PRM-01..09، 12، 17، 18) — پلِ `PromoCore` و سبدِ
 *  `SalesEngine`.
 *
 *  ⛔ پیش‌فرض خاموش: کلیدِ دکان خاموش، بی قاعدهٔ فعال یا سبدِ خالی ⇒ `null`، و
 *  صندوق همان سبدِ دست‌نخورده را جمع و ثبت می‌کند — عدد به عدد همان امروز.
 *
 *  ⛔ تخفیفِ خودکار در `Sale.discount` نمی‌نشیند. قیمتِ ردیف را پایین می‌آورد،
 *  دقیقاً از همان راهِ «قیمتِ دستیِ ردیف» (`CartLine.unitPrice`): قیمتِ واحد =
 *  خالصِ ردیف ÷ مقدار، پس `lineTotal` همان خالص است. چرا:
 *    • Sync v1/v3 فاکتورِ شاگرد با `discount > 0` را رد می‌کند (`sales.discount`)،
 *      ولی تخفیفِ خودکار را صاحب/مدیر از پیش تعریف کرده و شاگرد هم باید بزند؛
 *    • جمعِ ردیف‌ها = جمعِ فاکتور، برگشت سهمش را از همان ردیف می‌گیرد و سود از
 *      همان قیمتِ واحد حساب می‌شود؛ قالبِ داده و همگام‌سازی دست نمی‌خورد.
 *  تخفیفِ دستیِ فاکتور (با سقفِ نقش) همان‌طور روی جمعِ پس از تخفیفِ خودکار می‌آید.
 *
 *  ردیفِ آزاد و ردیفِ با قیمتِ دستی تخفیفِ خودکار نمی‌گیرند (قیمتِ دستی نهایی است).
 */
object PromoEngine {

  /** آنچه از سرور رسیده (یا نسخهٔ همین دستگاه) — کلیدِ دکان، قاعده‌ها و مجوزها */
  data class Snapshot(
    val enabled: Boolean = false,
    val rules: List<PromoCore.Rule> = emptyList(),
    /** صاحب/مدیر (`settings.write`) */
    val manage: Boolean = false,
    /** فقط صاحبِ دکان «زیرِ بهای خرید» را روشن می‌کند */
    val belowCost: Boolean = false,
    /** زمانِ آخرین پاسخِ سرور (میلی‌ثانیه)؛ ۰ = هرگز */
    val at: Long = 0,
  ) {
    val active: Boolean get() = enabled && rules.any { it.active }
    val hasCodes: Boolean get() = rules.any { it.active && it.code.isNotBlank() }
  }

  data class Applied(val id: String, val name: String, val discount: Double)

  /**
   *  @param gross جمعِ سبد پیش از تخفیفِ خودکار (`SalesEngine.cartTotal`)
   *  @param cart همان سبد با قیمتِ پایین‌آمدهٔ ردیف‌هایی که تخفیف گرفته‌اند — جمع و ثبت از همین
   */
  data class Outcome(
    val gross: Double,
    val discount: Double,
    val applied: List<Applied>,
    val codes: List<PromoCore.CodeStatus>,
    val cart: List<SalesEngine.CartLine>,
  )

  /** ردیف‌های سبد ⇒ ورودیِ هسته. مبلغِ هر ردیف همان `SalesEngine.lineTotal`ِ امروز است. */
  fun lines(d: ShopData, cart: List<SalesEngine.CartLine>): List<PromoCore.Line> = cart.map { line ->
    val qtyMilli = (line.quantity * 1000).roundToLong()
    val p = if (line.free) null else d.products.find { it.id == line.productId }
    if (p == null) {
      val price = line.unitPrice ?: 0.0
      PromoCore.Line(
        key = line.productId, productId = "", qtyMilli = qtyMilli, unitMinor = (price * 100).roundToLong(),
        amountMinor = (SalesEngine.lineTotal(price, line.quantity) * 100).roundToLong(),
      )
    } else {
      val price = line.unitPrice ?: p.salePrice
      PromoCore.Line(
        key = line.productId,
        productId = p.id,
        qtyMilli = qtyMilli,
        unitMinor = (price * 100).roundToLong(),
        amountMinor = (SalesEngine.lineTotal(price, line.quantity) * 100).roundToLong(),
        costMinor = (p.purchasePrice * 100).roundToLong().coerceAtLeast(0),
        category = p.category,
        supplierId = p.supplierId,
        noPromo = line.unitPrice != null,
      )
    }
  }

  /**
   *  سبد + قاعده‌ها ⇒ نتیجه، یا `null` وقتی چیزی برای اعمال نیست (خاموش، بی
   *  قاعدهٔ فعال، سبدِ خالی، یا عددی بیرون از بازهٔ امنِ هسته).
   *
   *  @param date تاریخِ محلیِ دکان «YYYY-MM-DD»
   *  @param minute دقیقه از نیمه‌شب
   */
  fun evaluate(
    d: ShopData,
    cart: List<SalesEngine.CartLine>,
    snap: Snapshot,
    coupons: List<String>,
    date: String,
    minute: Int,
  ): Outcome? {
    if (!snap.active || cart.isEmpty()) return null
    val r = runCatching {
      PromoCore.evaluate(
        PromoCore.Cart(lines(d, cart), coupons, group = "", channel = "pos", at = PromoCore.At(date, minute.toLong()), stepMinor = 100),
        snap.rules,
      )
    }.getOrNull() ?: return null
    val net = r.lines.associate { it.key to it }
    val priced = cart.map { line ->
      val o = net[line.productId]
      if (o == null || o.discountMinor <= 0 || line.quantity <= 0) line
      else line.copy(unitPrice = (o.netMinor / 100.0) / line.quantity)
    }
    return Outcome(
      gross = SalesEngine.cartTotal(d, cart),
      discount = r.discountMinor / 100.0,
      applied = r.applied.map { Applied(it.id, it.name, it.discountMinor / 100.0) },
      codes = r.codes,
      cart = priced,
    )
  }

  /** سبدی که جمع و ثبت از آن می‌رود: با تخفیف اگر هست، وگرنه همان سبد (همان شیء) */
  fun priced(cart: List<SalesEngine.CartLine>, outcome: Outcome?): List<SalesEngine.CartLine> = outcome?.cart ?: cart

  /** پیامِ فارسیِ وضعیتِ یک کدِ واردشده */
  fun codeText(status: String): String = when (status) {
    "applied" -> "اعمال شد"
    "no_effect" -> "روی این سبد اثری ندارد"
    "used_up" -> "ظرفیتِ این کد پر شده است"
    "inactive" -> "این کد الآن فعال نیست"
    else -> "این کد پیدا نشد"
  }
}
