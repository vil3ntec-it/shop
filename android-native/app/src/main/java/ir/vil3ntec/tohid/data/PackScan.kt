package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.product.PackBarcodes
import ir.vil3ntec.tohid.core.product.UnitSale
import kotlin.math.abs

/**
 *  PRD-04 و S05 — بارکدِ بسته (کارتن، شل…) در سبدِ صندوقِ گوشیِ نسخهٔ ۳: اسکنِ بارکدِ کارتنِ ۱۲ دانه‌ای ⇒ ۱۲ دانه
 *  (با «۲×» ⇒ ۲۴)، همان قاعدهٔ `PosSearch.resolve` و `DeskSale.fromScanner` ِ کامپیوتر.
 *
 *  ── قیمت ──────────────────────────────────────────────────────────────
 *  با «واحدِ بزرگ در صندوق» (`TillGoodsPref.units`، پیش‌فرض خاموش) و قیمتِ بسته در لیستِ قیمتِ کالا
 *  (`ProductPack.priceMinor`) ردیف همان قیمتِ لیستِ بسته را می‌گیرد — همان قیمتی که برگهٔ ردیف با «کارتن» می‌دهد
 *  (`UnitSale.lineUnitPrice`، `UnitLine`): ۲ کارتن به ۱٬۷۰۰ = ۳٬۴۰۰، نه ۱۲ × ۱۵۰ × ۲. بی قیمتِ لیست یا تنظیمِ خاموش ⇒
 *  اندازه × قیمتِ ردیف (رفتارِ پیشین).
 *
 *  ⛔ هر کالا یک ردیف و هر ردیف یک قیمت دارد (`SalesEngine.addToCart`). ردیفی که با قیمتِ دیگری (دانه، قیمتِ دستی یا
 *  بستهٔ دیگر) در سبد است با قیمتِ لیستِ بسته جمع نمی‌شود: نه افزوده می‌شود و نه قیمتِ ردیف بی‌صدا عوض ([Result.Clash]) —
 *  صندوقدار ردیف را باز می‌کند و واحد را عوض می‌کند. همان بسته با همان قیمت ⇒ جمع.
 *  ⛔ کدی که هم بارکدِ بسته است و هم کاملاً بارکدِ کالا (تعارضِ آفلاین) ⇒ [Result.Ambiguous]، هیچ‌کدام به سبد نمی‌رود.
 *  ⛔ «فقط بستهٔ کامل» (`TillGoodsPref.wholePacks`): تعدادِ اعشاری از بسته رد ([Result.PartRejected]).
 *  منطقِ خالص (بی Android)؛ آزمون: `PackScanTest`، `ScenarioS05Test`.
 */
object PackScan {

  sealed interface Result {
    /** افزوده شد (یا تا موجودی)؛ [priced] = قیمتِ لیستِ بسته روی ردیف نشست */
    data class Added(val added: SalesEngine.Added, val product: Product, val pack: PackBarcodes.Pack, val count: Double, val priced: Boolean) : Result
    data class Inactive(val product: Product) : Result
    data class Ambiguous(val code: String) : Result
    data class PartRejected(val product: Product, val pack: PackBarcodes.Pack) : Result
    data class Clash(val product: Product, val pack: PackBarcodes.Pack) : Result
  }

  /** همان مقایسهٔ قیمتِ ردیف با قیمتِ واحد (`UnitLine.plan`) */
  private fun same(a: Double?, b: Double?): Boolean = a != null && b != null && abs(a - b) < 1e-6

  /** قیمتِ هر واحدِ پایهٔ ردیف از لیستِ قیمتِ [pack] (افغانی)؛ `null` ⇒ بسته قیمتِ لیست ندارد */
  fun linePrice(pack: PackBarcodes.Pack): Double? = UnitSale.lineUnitPrice(UnitSale.Unit(pack.name, pack.sizeMilli, pack.priceMinor))

  /**
   *  [code] بارکدِ بسته‌ای است؟ `null` ⇒ نه (راهِ همیشگیِ بارکدِ کالا). [count] تعدادِ بسته («۲×»).
   *  @param index همان `ShopStore.barcodeIndex` (برای «کاملاً بارکدِ کالا»)
   *  @param listPrice «واحدِ بزرگ در صندوق» ِ همین دستگاه
   */
  fun scan(
    d: ShopData,
    lines: List<SalesEngine.CartLine>,
    code: String,
    count: Double,
    index: Map<String, String>,
    listPrice: Boolean,
    wholeOnly: Boolean,
    packs: PackBarcodes.Index = ShopStore.packIndex(d),
  ): Result? {
    val c = code.trim()
    if (c.isEmpty()) return null
    //  «کاملاً بارکدِ کالا» همان `PosSearch.isItemCode` (نه نیمه)
    val hit = when (val l = packs.lookup(c) { (BarcodeMatch.find(it, index) as? BarcodeMatch.Hit.Product)?.exact == true }) {
      PackBarcodes.Lookup.Ambiguous -> return Result.Ambiguous(c)
      PackBarcodes.Lookup.NotPack -> return null
      is PackBarcodes.Lookup.Pack -> l.hit
    }
    val p = d.products.firstOrNull { it.id == hit.productId } ?: return null
    if (!p.active) return Result.Inactive(p)
    val unit = UnitSale.Unit(hit.pack.name, hit.pack.sizeMilli, hit.pack.priceMinor)
    if (UnitSale.partRejected(count, unit, wholeOnly)) return Result.PartRejected(p, hit.pack)
    val qty = UnitSale.toBase(count, unit, false) ?: return null
    val price = if (listPrice) linePrice(hit.pack) else null
    val existing = lines.firstOrNull { it.productId == p.id && !it.free }
    if (price != null && existing != null && !same(existing.unitPrice, price)) return Result.Clash(p, hit.pack)
    val added = SalesEngine.addToCartCapped(lines, p.id, qty, ProductInfoMap.sellable(d, p.id))
    if (price == null || added.cart === lines) return Result.Added(added, p, hit.pack, count, priced = false)
    return Result.Added(added.copy(cart = SalesEngine.setLinePrice(added.cart, p.id, price)), p, hit.pack, count, priced = true)
  }

  /**
   *  POS-21 (`DiscountGate.reductionMinor`): ردیفی که دقیقاً به قیمتِ لیستِ یکی از بسته‌های کالاست و مقدارش مضربِ درستِ
   *  همان بسته است کاهشِ دلخواه نیست — همان قیمتِ لیست است (۲۴ دانه به قیمتِ ۲ کارتن). ۱۳ دانه به نرخِ کارتن هنوز کاهش است.
   */
  fun packListed(p: Product, unitPrice: Double, quantity: Double): Boolean {
    val milli = kotlin.math.round(quantity * 1000.0).toLong()
    if (milli <= 0) return false
    return p.packs.orEmpty().any { pk ->
      pk.priceMinor != null && pk.sizeMilli > 0 && milli % pk.sizeMilli == 0L && same(linePrice(ShopStore.coreOf(pk)), unitPrice)
    }
  }
}
