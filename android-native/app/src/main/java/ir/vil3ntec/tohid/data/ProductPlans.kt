package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.product.PriceSchedule
import ir.vil3ntec.tohid.core.product.ProductKit
import kotlinx.serialization.Serializable

/** PRD-10 — یک جزءِ کیت: کالا و مقدار به `unit` ِ همان کالا (`Product.kit`، و رونوشتش روی `SaleItem.parts`) */
@Serializable
data class KitPart(val productId: String = "", val qty: Double = 0.0)

/** PRD-13 — یک بازهٔ قیمتِ زمان‌بندی‌شده: از و تا (هر دو شامل، `yyyy-MM-dd`) و قیمتِ فروش (`Product.priceWindows`) */
@Serializable
data class PriceWindow(val from: String = "", val to: String = "", val price: Double = 0.0)

/**
 *  کیت (PRD-10) و قیمتِ زمان‌بندی‌شده (PRD-13) در دفتر و فروش — منطقِ خالص در `core/product/ProductPlans`.
 *
 *  ⛔ هر دو از دادهٔ کالاست (فرم فقط با تنظیمِ `tohid-ui:product_info` می‌نویسد): کالای بی `kit` و بی
 *  `priceWindows` (همهٔ دفترهای پیشین) در هر تابعِ این‌جا دقیقاً همان مقدارِ پیشین را می‌گیرد.
 *  ⛔ موجودیِ کیت ساختگی است: «چند کیتِ کامل از اجزا» (`ShopStore.StockIndex`)؛ فروشِ کیت اجزا را کم می‌کند
 *  و اجزا روی قلمِ فاکتور رونوشت می‌شوند (`SaleItem.parts`) تا تغییرِ بعدیِ ترکیب فاکتورِ گذشته را عوض نکند؛
 *  ورود/اصلاحِ انبارِ خودِ کیت (مثلاً ضایعاتِ برگشتی) به اجزا پخش می‌شود.
 *  ⛔ با موتورِ تازهٔ موجودی (`StockLedger`، تنظیمِ دکان) کیت هنوز فروخته نمی‌شود (رد با دلیل، نه موجودیِ غلط).
 */
object ProductPlansMap {

  private fun milli(v: Double): Long = Math.round(v * 1000.0)
  private fun minor(v: Double): Long = Math.round(v * 100.0)

  fun isKit(p: Product?): Boolean = !p?.kit.isNullOrEmpty()

  fun kitOf(p: Product): List<ProductKit.Part> = p.kit.orEmpty().map { ProductKit.Part(it.productId, milli(it.qty)) }

  fun windowsOf(p: Product): List<PriceSchedule.Window> = p.priceWindows.orEmpty().map { PriceSchedule.Window(it.from, it.to, minor(it.price)) }

  /** شکلِ تمیزِ هسته ⇒ مدلِ دفتر؛ تهی ⇒ `null` تا نوشته نشود */
  fun kitModel(parts: List<ProductKit.Part>): List<KitPart>? = parts.map { KitPart(it.productId, it.qtyMilli / 1000.0) }.ifEmpty { null }

  fun windowsModel(w: List<PriceSchedule.Window>): List<PriceWindow>? = w.map { PriceWindow(it.from, it.to, it.priceMinor / 100.0) }.ifEmpty { null }

  /** قیمتِ فروشِ امروزِ کالا (PRD-13): بازهٔ فعال، وگرنه `salePrice` — کالای بی بازه بی خواندنِ ساعت */
  fun price(p: Product): Double =
    if (p.priceWindows.isNullOrEmpty()) p.salePrice
    else PriceSchedule.price(minor(p.salePrice), windowsOf(p), ir.vil3ntec.tohid.todayIso()) / 100.0

  /** بهای قلمِ فروش: کیت ⇒ جمعِ قیمتِ خریدِ اجزا × مقدار، وگرنه همان `purchasePrice` */
  fun cost(d: ShopData, p: Product): Double {
    val parts = p.kit.orEmpty().ifEmpty { return p.purchasePrice }
    val byId = d.products.associateBy { it.id }
    return parts.sumOf { (byId[it.productId]?.purchasePrice ?: 0.0) * it.qty }
  }

  /** رونوشتِ اجزا برای قلمِ فاکتور؛ کالای عادی ⇒ `null` (نوشته نمی‌شود) */
  fun parts(p: Product): List<KitPart>? = p.kit?.ifEmpty { null }

  /** حرکت‌های انبارِ یک قلمِ فروش: کیت ⇒ هر جزء، وگرنه همان کالا */
  fun moves(p: Product, qty: Double): List<Pair<String, Double>> =
    p.kit?.ifEmpty { null }?.map { it.productId to qty * it.qty } ?: listOf(p.id to qty)

  /**
   *  ایرادِ فروشِ کیتِ [p] در این سبد: موتورِ تازهٔ موجودی، یا جزئی که نیازِ همهٔ سبد (کیت‌ها و خودِ جزء)
   *  از موجودی‌اش بیشتر است. کالای عادی ⇒ `null` (سنجشِ همیشگیِ صندوق دست نمی‌خورد).
   */
  fun kitShortage(d: ShopData, cart: List<SalesEngine.CartLine>, p: Product): String? {
    if (!isKit(p)) return null
    if (StockLedger.isOn(d)) return "«${p.name}» کیت است و با موتورِ تازهٔ موجودی هنوز فروخته نمی‌شود"
    val byId = d.products.associateBy { it.id }
    val need = HashMap<String, Double>()
    for (line in cart) {
      if (line.free) continue
      val q = byId[line.productId] ?: continue
      for ((id, n) in moves(q, line.quantity)) need[id] = (need[id] ?: 0.0) + n
    }
    for (part in p.kit.orEmpty()) {
      val have = ShopStore.stock(d, part.productId)
      val want = need[part.productId] ?: 0.0
      if (want > have + 1e-9) {
        val name = byId[part.productId]?.name ?: part.productId
        return "«${p.name}»: از جزءِ «$name» ${ir.vil3ntec.tohid.qty(have.coerceAtLeast(0.0))} مانده، این سبد ${ir.vil3ntec.tohid.qty(want)} می‌خواهد"
      }
    }
    return null
  }

  /** موجودیِ کیت از موجودیِ خامِ اجزا (`ShopStore.StockIndex`) */
  fun kitStock(kit: List<KitPart>, raw: (String) -> Double): Double =
    ProductKit.available(kit.map { ProductKit.Part(it.productId, milli(it.qty)) }) { milli(raw(it)) }.toDouble()

  /** ردپای ویرایشِ همین دو بخش */
  fun changes(a: Product, b: Product): List<String> = buildList {
    if (a.kit.orEmpty() != b.kit.orEmpty()) add("اجزای کیت عوض شد")
    if (a.priceWindows.orEmpty() != b.priceWindows.orEmpty()) add("قیمتِ زمان‌بندی‌شده عوض شد")
  }
}
