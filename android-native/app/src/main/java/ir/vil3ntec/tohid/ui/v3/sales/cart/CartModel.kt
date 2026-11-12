package ir.vil3ntec.tohid.ui.v3.sales.cart

import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.BarcodeMatch
import ir.vil3ntec.tohid.data.PackScan
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ProductInfoMap
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.components.pos.CartSummary

/**
 *  کارهای کوچکِ سبد که صندوق، فروشِ سریع و صفحه‌کلید هر سه می‌کنند — خالص، روی همان
 *  موتورِ فروش (`SalesEngine.addToCartCapped`، `setCartQty`، `cartStep`، `shortageMessage`)
 *  و همان جمله‌های امروز. هیچ عددی این‌جا ساخته نمی‌شود؛ فقط «سبدِ تازه» و «چه بگوییم».
 */
object CartModel {

  /** نتیجهٔ یک افزودن: سبدِ تازه (null ⇒ دست نخورد) و پیامی که باید دیده شود */
  data class Step(val lines: List<SalesEngine.CartLine>?, val note: String?, val capped: Boolean)

  /** «فقط ۳ کیلوگرم موجود است» — مقدار با واحدِ کالا */
  fun stockLeft(p: Product, available: Double): String =
    qty(available) + if (p.unit.isNotBlank()) " ${p.unit}" else ""

  /**
   *  یک پله به سبد (زدنِ کاشی): پلهٔ واحدِ کالا (`cartStep`)، با نگهبانِ موجودی. تمام شده ⇒
   *  همان پیامِ موتور (`shortageMessage`) و سبد دست نمی‌خورد؛ بخشی ماند ⇒ تا موجودی و «فقط … مانده».
   */
  fun addOne(d: ShopData, lines: List<SalesEngine.CartLine>, productId: String): Step {
    val p = ShopStore.product(d, productId) ?: return Step(null, null, false)
    val step = SalesEngine.cartStep(p.unit)
    val added = SalesEngine.addToCartCapped(lines, p.id, step, ProductInfoMap.sellable(d, p.id))
    if (!added.capped) return Step(added.cart, null, false)
    val note = if (added.available <= 0) SalesEngine.shortageMessage(p, step, added.available)
    else StrSale.onlyLeftOf(p.name, stockLeft(p, added.available))
    return Step(added.cart.takeIf { it !== lines }, note, true)
  }

  /**
   *  POS-03 — «۳*» ِ کادرِ جست‌وجو و لمسِ کاشی: [count] تا (کالای وزنی همان مقدار، بی صفحه‌کلید) با همان نگهبانِ موجودیِ
   *  [addOne]. شمارِ نادرست (صفر، منفی، اعشار برای کالای شمردنی) ⇒ همان یک پلهٔ امروز.
   */
  fun addCount(d: ShopData, lines: List<SalesEngine.CartLine>, productId: String, count: Double): Step {
    val p = ShopStore.product(d, productId) ?: return Step(null, null, false)
    val weighed = SalesEngine.isFractionalUnit(p.unit)
    if (!count.isFinite() || count <= 0 || count == 1.0 || (!weighed && count != kotlin.math.floor(count))) return addOne(d, lines, productId)
    val added = SalesEngine.addToCartCapped(lines, p.id, count, ProductInfoMap.sellable(d, p.id))
    if (!added.capped) return Step(added.cart, null, false)
    val note = if (added.available <= 0) SalesEngine.shortageMessage(p, count, added.available)
    else StrSale.onlyLeftOf(p.name, stockLeft(p, added.available))
    return Step(added.cart.takeIf { it !== lines }, note, true)
  }

  /** یک پله کمتر («−» ِ کاشی)؛ پلهٔ آخر ردیف را برمی‌دارد */
  fun minusOne(d: ShopData, lines: List<SalesEngine.CartLine>, productId: String): List<SalesEngine.CartLine>? {
    val p = ShopStore.product(d, productId) ?: return null
    val now = lines.find { it.productId == p.id }?.quantity ?: return null
    return SalesEngine.setCartQty(lines, p.id, now - SalesEngine.cartStep(p.unit))
  }

  /**
   *  −/+ ِ یک ردیفِ سبدِ باز (r2): «+» ِ کالا از موجودی جلو نمی‌زند؛ ردیفِ آزاد موجودی ندارد؛
   *  «−» ِ پلهٔ آخر ردیف را برمی‌دارد — همان قاعدهٔ صندوقِ پیشین.
   */
  fun step(d: ShopData, lines: List<SalesEngine.CartLine>, row: CartSummary.Row, sign: Int): Step {
    if (sign > 0 && row.hasProduct) {
      val p = ShopStore.product(d, row.id) ?: return Step(null, null, false)
      val added = SalesEngine.addToCartCapped(lines, p.id, row.step, ProductInfoMap.sellable(d, p.id))
      val note = if (added.capped) StrSale.onlyLeftOf(p.name, stockLeft(p, added.available)) else null
      return Step(added.cart.takeIf { it !== lines }, note, added.capped)
    }
    return Step(SalesEngine.setCartQty(lines, row.id, row.quantity + sign * row.step), null, false)
  }

  /** شمارهٔ ردیفِ یک کالا (یا قلمِ آزاد) در سبد — برای `sale/line/{i}`؛ نبود ⇒ null */
  fun lineIndex(lines: List<SalesEngine.CartLine>, id: String): Int? = lines.indexOfFirst { it.productId == id }.takeIf { it >= 0 }

  /** مقدارِ هر کالا در سبد (برای نشانِ «۵ در سبد» روی کاشی) */
  fun inCart(lines: List<SalesEngine.CartLine>): Map<String, Double> =
    lines.groupBy { it.productId }.mapValues { (_, l) -> l.sumOf { it.quantity } }

  /** نتیجهٔ خواندنِ یک بارکد برای سبد */
  sealed interface Scan {
    /** کالا افزوده شد (یا تا موجودی)؛ `whole` = بارکدِ کامل خوانده شد، نه نیمه‌اش */
    data class Added(
      val lines: List<SalesEngine.CartLine>,
      val product: Product,
      val whole: Boolean,
      val capped: Boolean,
      val available: Double,
      /** برچسبِ ترازو: مقدارِ وزن‌شده (null ⇒ بارکدِ کالا) */
      val weighed: Double? = null,
      /** PRD-04: بارکدِ بسته — نامِ بسته («کارتن») و تعدادِ بسته‌ها؛ null ⇒ بارکدِ خودِ کالا */
      val pack: String? = null,
      val packCount: Double = 0.0,
    ) : Scan

    /** هیچ کالایی این بارکد را ندارد ⇒ «ثبتِ کالا» با همین کد */
    data class Unknown(val code: String) : Scan

    /** نیمهٔ بارکد چند کالا را می‌خواند ⇒ کد را کامل کنید */
    data class Several(val code: String, val count: Int) : Scan

    /** کالا هست ولی غیرفعال */
    data class Inactive(val product: Product) : Scan

    /** PRD-04: هم بارکدِ بسته و هم بارکدِ کالا (تعارضِ آفلاین) ⇒ هیچ‌کدام به سبد نمی‌رود */
    data class Ambiguous(val code: String) : Scan

    /** S05: کالا با قیمتِ دیگری در سبد است و قیمتِ لیستِ بسته با آن جمع نمی‌شود (`PackScan.Result.Clash`) */
    data class PackClash(val product: Product, val pack: String) : Scan

    /** «فقط بستهٔ کامل»: تعدادِ اعشاری از بسته */
    data class PackPart(val pack: String) : Scan
  }

  /**
   *  یک بارکد ⇒ سبد: اول بارکدِ بسته (PRD-04، `PackScan`: «کارتن» ⇒ اندازهٔ بسته × `multiplier`، و با [packPrice]
   *  قیمتِ لیستِ همان بسته — S05)، بعد شناختن با `BarcodeMatch` (کامل، و اگر نشد از نیمه، فقط وقتی یکتاست)
   *  و افزودنِ `multiplier` تا موجودی — همان قاعدهٔ صندوقِ امروز.
   *  @param packPrice «واحدِ بزرگ در صندوق» (`TillGoodsPref.units`) @param wholeOnly «فقط بستهٔ کامل»
   */
  fun scan(
    d: ShopData,
    index: Map<String, String>,
    lines: List<SalesEngine.CartLine>,
    raw: String,
    multiplier: Double = 1.0,
    packPrice: Boolean = false,
    wholeOnly: Boolean = false,
  ): Scan? {
    val code = raw.trim().takeIf { it.isNotEmpty() } ?: return null
    when (val k = PackScan.scan(d, lines, code, multiplier, index, packPrice, wholeOnly)) {
      null -> Unit
      is PackScan.Result.Added ->
        return Scan.Added(k.added.cart, k.product, whole = true, capped = k.added.capped, available = k.added.available, pack = k.pack.name, packCount = k.count)
      is PackScan.Result.Inactive -> return Scan.Inactive(k.product)
      is PackScan.Result.Ambiguous -> return Scan.Ambiguous(k.code)
      is PackScan.Result.Clash -> return Scan.PackClash(k.product, k.pack.name)
      is PackScan.Result.PartRejected -> return Scan.PackPart(k.pack.name)
    }
    return when (val hit = BarcodeMatch.find(code, index)) {
      is BarcodeMatch.Hit.Several -> Scan.Several(code, hit.count)
      is BarcodeMatch.Hit.Product -> {
        val p = ShopStore.product(d, hit.id) ?: return Scan.Unknown(code)
        if (!p.active) return Scan.Inactive(p)
        val added = SalesEngine.addToCartCapped(lines, p.id, multiplier, ProductInfoMap.sellable(d, p.id))
        Scan.Added(added.cart, p, hit.exact, added.capped, added.available)
      }
      BarcodeMatch.Hit.None -> Scan.Unknown(code)
    }
  }

  /**
   *  برچسبِ ترازو (`ScaleLabels.Match.Weighed`) ⇒ همان وزن به سبد، تا موجودی. شناختنِ برچسب و
   *  قاعده‌هایش با `ScaleLabels` است؛ این‌جا فقط افزودن.
   */
  fun weighed(d: ShopData, lines: List<SalesEngine.CartLine>, product: Product, quantity: Double): Scan.Added {
    val added = SalesEngine.addToCartCapped(lines, product.id, quantity, ProductInfoMap.sellable(d, product.id))
    return Scan.Added(added.cart, product, whole = true, capped = added.capped, available = added.available, weighed = quantity)
  }

  /** جملهٔ کوتاهِ همان خواندن — پس از «Added» */
  fun scanNote(s: Scan.Added): String = when {
    s.capped -> StrSale.onlyLeftOf(s.product.name, stockLeft(s.product, s.available))
    s.weighed != null -> StrSale.weighedAdded(s.product.name, stockLeft(s.product, s.weighed))
    s.pack != null -> StrSale.packAdded(qty(s.packCount), s.pack, s.product.name)
    s.whole -> StrSale.addedToCart(s.product.name)
    else -> StrSale.halfBarcode(s.product.name)
  }
}
