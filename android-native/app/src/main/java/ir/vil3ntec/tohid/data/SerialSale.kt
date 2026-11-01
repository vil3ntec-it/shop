package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrProduct
import ir.vil3ntec.tohid.core.pos.SerialNumbers

/**
 *  POS-28 — کالای سریال‌دار در سبد و ثبتِ فروش (منطقِ خالص: `core/pos/SerialNumbers`).
 *
 *  ⛔ از دادهٔ کالاست (`Product.serialTracked`، فرم فقط با `tohid-ui:product_info`): کالای بی پرچم هیچ سنجشِ
 *  تازه‌ای ندارد و قلمش `serials` نمی‌گیرد. «فروخته‌شده» = سریالِ قلم‌های فاکتورِ لغونشده که هیچ دانه‌اش برنگشته
 *  (برگشت نمی‌گوید کدام سریال برگشت، پس قلمِ برگشت‌خورده سریال‌هایش را آزاد می‌کند — سخت‌گیری بی‌جا فروشِ درست را
 *  نمی‌بندد).
 */
object SerialSale {

  fun tracked(p: Product?): Boolean = p?.serialTracked == true

  /** سریال‌های فروخته‌شده و برنگشتهٔ کلِ دفتر */
  fun sold(d: ShopData): Set<String> {
    if (d.saleItems.none { !it.serials.isNullOrEmpty() }) return emptySet()
    val cancelled = d.sales.filter { it.status == "cancelled" }.mapTo(HashSet()) { it.id }
    return d.saleItems.asSequence().filter { it.saleId !in cancelled && it.returnedQty <= 0.0 }.flatMap { it.serials.orEmpty() }.toHashSet()
  }

  /** ایرادِ سریال‌های ردیفِ [line] از کالای [p] برای ثبت؛ کالای بی پرچم ⇒ `null` */
  fun problem(d: ShopData, line: SalesEngine.CartLine, p: Product): String? {
    if (!tracked(p) || line.free) return null
    return when (val x = SerialNumbers.problem(line.serials.orEmpty(), Math.round(line.quantity * 1000.0), sold(d))) {
      null -> null
      is SerialNumbers.Problem.Missing -> StrProduct.serialMissing(p.name, ir.vil3ntec.tohid.plain(x.have), ir.vil3ntec.tohid.plain(x.need))
      is SerialNumbers.Problem.TooMany -> StrProduct.serialTooMany(p.name, ir.vil3ntec.tohid.plain(x.have), ir.vil3ntec.tohid.plain(x.need))
      is SerialNumbers.Problem.Duplicate -> StrProduct.serialDuplicate(x.serial)
      is SerialNumbers.Problem.AlreadySold -> StrProduct.serialSold(x.serial)
      SerialNumbers.Problem.Fractional -> StrProduct.serialWhole(p.name)
    }
  }

  /** سریال‌های قلمِ فاکتور؛ کالای بی پرچم ⇒ `null` (نوشته نمی‌شود) */
  fun forItem(line: SalesEngine.CartLine, p: Product): List<String>? = if (tracked(p)) line.serials?.ifEmpty { null } else null
}

/** POS-28 — سریال‌های یک ردیفِ سبد؛ ردیف‌های دیگر دست نمی‌خورند و سبدِ بی تغییر همان شیء برمی‌گردد */
object CartSerials {
  fun set(cart: List<SalesEngine.CartLine>, productId: String, serials: List<String>): List<SalesEngine.CartLine> {
    val clean = serials.ifEmpty { null }
    if (cart.none { it.productId == productId && it.serials != clean }) return cart
    return cart.map { if (it.productId == productId) it.copy(serials = clean) else it }
  }
}
