package ir.vil3ntec.tohid.desktop.perf

import android.content.Context
import ir.vil3ntec.tohid.core.promo.PromoCore
import ir.vil3ntec.tohid.core.sale.PriceList
import ir.vil3ntec.tohid.data.BarcodeMatch
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.DiscountGate
import ir.vil3ntec.tohid.data.PackScan
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ProductInfoMap
import ir.vil3ntec.tohid.data.ProductPack
import ir.vil3ntec.tohid.data.PromoEngine
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ScaleLabels
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.TillGoodsPref
import ir.vil3ntec.tohid.ui.components.pos.CartSummary
import ir.vil3ntec.tohid.ui.components.pos.OrderLineView
import ir.vil3ntec.tohid.ui.components.pos.PosCatalog
import ir.vil3ntec.tohid.ui.components.pos.PosHit
import ir.vil3ntec.tohid.ui.components.pos.PosSearch
import ir.vil3ntec.tohid.ui.components.pos.repriceCart
import ir.vil3ntec.tohid.ui.v3.sales.cart.CartModel
import ir.vil3ntec.tohid.ui.v3.sales.cart.CatalogModel
import java.util.Locale

/**
 *  POS-42 — «اسکن تا ردیف ≤ ۱۰۰ms و جست‌وجو ≤ ۱۵۰ms با ۲۰ هزار کالا»، روی JVM (`PosSpeedBenchTest`).
 *
 *  ── داده ──────────────────────────────────────────────────────────────
 *  دفترِ قطعیِ [BigLedger] (seed ثابت، همان فروش‌ها، حرکت‌ها و قرض‌ها) با کالاهایی که این‌جا «دکانی» می‌شوند:
 *  نامِ فارسی/دری/انگلیسی (با «ي/ك» ِ عربی و نیم‌فاصله در بخشی)، بارکدِ دوم، بارکدِ رقمِ فارسی، بستهٔ کارتن با
 *  بارکد و قیمتِ لیست، قیمتِ عمده، کالای غیرفعال، و محمولهٔ انقضادار. ⛔ هیچ دادهٔ واقعی‌ای نیست.
 *
 *  ── راه‌ها ────────────────────────────────────────────────────────────
 *  رسیدگیِ هر اسکن در صفحه درونِ یک `@Composable` است و از بیرون صدا زده نمی‌شود؛ پس هر راه **همان
 *  فراخوانی‌ها به همان ترتیب** را می‌زند (نامِ تابعِ صفحه کنارِ هر کدام):
 *   • کامپیوتر، بارکدخوان بیرونِ کادر — `DeskSaleScreen.fromScanner` ([deskScanner])
 *   • کامپیوتر، Enter در کادر — آخرین `matches` ِ همان کد + `DeskSaleScreen.submit` ([deskField])
 *   • گوشی v3 — `SaleRoute.onCode` ⇐ `CartFeedback.scan` ([phoneScan])
 *  و پس از `CartStore.set` آنچه صفحه با سبدِ تازه از نو می‌سازد ([deskObserved]، [phoneObserved]): تخفیفِ خودکار
 *  (`PromoLive.rememberOutcome` ⇐ `PromoEngine.evaluate`)، لیستِ قیمت (`PriceListAtPos` ⇐ `repriceCart`)، جمع، ردیف‌های
 *  سبد و — در گوشی — کاشی‌های کاتالوگ (`SaleScreen`: کلیدِ `inCart`).
 */
internal object PosSpeed {

  const val SCAN_BUDGET_MS = 100.0
  const val SEARCH_BUDGET_MS = 150.0

  /* ------------------------------ داده ------------------------------ */

  private val FA = listOf(
    "برنج باسمتی", "روغن آفتاب‌گردان", "شکر سفید", "چای سیاه", "چای سبز", "صابون", "شامپو", "آرد گندم", "نخود",
    "لوبیا چیتی", "رب گوجه", "ماکارونی", "نمک یددار", "عدس", "پنیر", "شیر", "کیک", "بیسکویت", "نوشابه", "آب معدنی",
    "دستمال کاغذی", "خرما", "کشمش", "زعفران", "زردچوبه", "قند", "خمیر دندان", "پودر لباس‌شویی", "مایع ظرف‌شویی",
    "روغن زیتون", "عسل", "مربا", "سرکه", "ماست", "تخم مرغ",
  )
  //  واژه‌های دری (کچالو، مرچ، قروت، مسکه، بادنجان رومی…)
  private val DARI = listOf("کچالو", "مرچ سرخ", "قروت", "مسکه", "بادنجان رومی", "نسک", "پلو", "شیرپیره", "کلچه", "جلغوزه")
  private val EN = listOf(
    "Coca Cola", "Pepsi", "Fanta Orange", "Nescafe Classic", "Lipton Yellow Label", "Head & Shoulders", "Colgate Total",
    "Dove Soap", "Ariel Powder", "Tide", "Nutella", "Pringles", "KitKat", "Snickers", "Red Bull", "Nido Milk",
    "Corn Flakes", "Sunsilk", "Lux Soap", "Pampers",
  )
  private val BRANDS = listOf("ممتاز", "درجه‌یک", "خانواده", "اقتصادی", "طلایی", "سبز", "کابل", "هرات", "بلخ", "آریانا", "پامیر", "هندوکش")
  private val SIZES = listOf("۲۵۰ گرم", "۵۰۰ گرم", "۱ کیلو", "۵ کیلو", "۱ لیتر", "۲ لیتر", "بستهٔ ۱۲", "۲۰ عدد")
  private val EN_SIZES = listOf("330ml", "1.5L", "250g", "500g", "1kg", "x12", "200ml", "Family")
  private val CATS = listOf("خوراکی", "نوشیدنی", "بهداشتی", "شوینده", "لبنیات", "تنقلات", "حبوبات", "کنسرو", "ادویه", "میوه خشک", "نان", "خانه")

  private fun fa(s: String): String = buildString(s.length) { for (c in s) append(if (c in '0'..'9') '۰' + (c - '0') else c) }

  /** ۵٪ ِ نام‌ها با «ي/ك» ِ عربی — همان‌طور که از فایلِ کهنه یا صفحه‌کلیدِ عربی می‌آید */
  private fun arabic(s: String): String = s.replace('ی', 'ي').replace('ک', 'ك')

  data class Shop(val d: ShopData, val packCodes: List<String>, val itemCodes: List<String>, val faCodes: List<String>)

  /**
   *  [products] کالا و [sales] فاکتور (همان دفترِ [BigLedger]؛ کالاها هم‌شناسه، فقط «دکانی‌تر»). قطعی.
   */
  fun shop(products: Int = 20_000, sales: Int = 50_000, seed: Int = 1405): Shop {
    val base = BigLedger.make(
      BigLedger.Size(products = products, sales = sales, warehouseEntries = maxOf(2_000, products), stockMoves = sales * 2),
      seed,
    )
    val r = BigLedger.Rng(seed + 42)
    val packCodes = ArrayList<String>()
    val itemCodes = ArrayList<String>()
    val faCodes = ArrayList<String>()
    val ps = base.products.mapIndexed { i, p ->
      val kind = r.int(100)
      val name = when {
        kind < 55 -> "${r.pick(FA)} ${r.pick(BRANDS)} ${r.pick(SIZES)}"
        kind < 70 -> "${r.pick(DARI)} ${r.pick(BRANDS)} ${r.pick(SIZES)}"
        else -> "${r.pick(EN)} ${r.pick(EN_SIZES)}"
      }.let { if (r.int(100) < 5) arabic(it) else it }
      val code = p.barcodes.first()
      //  ۲٪ بارکدِ ثبت‌شده با رقمِ فارسی (صفحه‌کلیدِ فارسی هنگامِ ثبت)
      val main = if (r.int(100) < 2) fa(code).also { faCodes += code } else code.also { itemCodes += it }
      val second = if (r.int(100) < 15) listOf("590" + (200_000_000L + i) + (i % 7)) else emptyList()
      val packs = if (r.int(100) < 25) {
        val size = if (r.int(2) == 0) 12 else 24
        val pc = "1$code"
        packCodes += pc
        listOf(ProductPack(name = "کارتن", sizeMilli = size * 1000L, barcodes = listOf(pc), priceMinor = Math.round(p.salePrice * size * 95.0)))
      } else null
      p.copy(
        name = name,
        category = r.pick(CATS),
        barcodes = listOf(main) + second,
        wholesalePrice = Math.round(p.salePrice * 0.9).toDouble(),
        active = r.int(100) >= 2,
        nameEn = if (kind < 70 && r.int(100) < 20) "item ${r.pick(EN)}" else null,
        namePs = if (kind < 70 && r.int(100) < 10) "توکی ${r.pick(BRANDS)}" else null,
        brand = r.pick(BRANDS),
        packs = packs,
      )
    }
    //  ورودِ آغازینِ هر کالا (هر کالا روزی وارد انبار شده؛ بی آن بیشترِ اسکن‌ها به سقفِ موجودی می‌خورد و سبد پر نمی‌شد)
    val opening = ps.mapIndexed { i, p ->
      ir.vil3ntec.tohid.data.WarehouseEntry(
        id = BigLedger.ulid(BigLedger.NOW - 4 * 365 * 86_400_000L, 11, i.toLong()), productId = p.id, cartons = 20.0, perCarton = 25.0,
        units = 500.0, unit = p.unit, price = p.purchasePrice, date = BigLedger.dateOf(BigLedger.NOW - 4 * 365 * 86_400_000L), createdAt = i.toLong(),
      )
    }
    //  ۳۰٪ ِ ورودها انقضادار (PRM-10، INV-21)
    val entries = (opening + base.warehouseEntries).mapIndexed { i, e ->
      if (r.int(100) < 30) e.copy(expiryDate = BigLedger.dateOf(BigLedger.NOW + (r.int(400) - 30) * 86_400_000L), batch = "B${i % 97}") else e
    }
    val d = base.copy(products = ps, warehouseEntries = entries, productCategories = CATS)
    return Shop(d, packCodes, itemCodes, faCodes)
  }

  /**
   *  تخفیفِ خودکار روشن، چهار قاعده (درصدِ دسته، «دو بخر یکی نیم‌بها»، پلهٔ مبلغ، درصدِ برند)؛ [expiry] ⇒ قاعدهٔ
   *  «نزدیکِ انقضا» (PRM-10) هم، که محموله‌ها را برای هر سبد می‌سنجد.
   */
  fun promos(d: ShopData, expiry: Boolean = false): PromoEngine.Snapshot {
    val some = d.products.filterIndexed { i, _ -> i % 400 == 0 }.map { it.id }
    val rules = listOf(
      PromoCore.Rule(id = "r1", name = "ده درصدِ بهداشتی", kind = "percent", bp = 1000, scope = PromoCore.Scope(categories = listOf("بهداشتی")), channels = listOf("pos")),
      PromoCore.Rule(id = "r2", name = "دو بخر یکی نیم‌بها", kind = "bxgy", buy = 2, get = 1, bp = 5000, stack = "combine", scope = PromoCore.Scope(products = some), channels = listOf("pos")),
      PromoCore.Rule(id = "r3", name = "خریدِ بالای ۱۰۰۰", kind = "threshold", stack = "combine", tiers = listOf(PromoCore.Tier(min = 100_000, amountMinor = 5_000)), channels = listOf("pos")),
      PromoCore.Rule(id = "r4", name = "پنج درصدِ هرات", kind = "percent", bp = 500, scope = PromoCore.Scope(brands = listOf("هرات")), channels = listOf("pos")),
    ) + if (expiry) listOf(PromoCore.Rule(id = "r5", name = "نزدیکِ انقضا", kind = "expiry", bp = 2000, nearDays = 30, stack = "combine", channels = listOf("pos"))) else emptyList()
    return PromoEngine.Snapshot(enabled = true, rules = rules, manage = true, at = BigLedger.NOW)
  }

  /* ------------------------------ سنجه ------------------------------ */

  class Stats(val name: String, nanos: List<Long>) {
    private val ms = nanos.map { it / 1e6 }.sorted()
    val n: Int = ms.size
    val p50: Double = pct(0.50)
    val p95: Double = pct(0.95)
    val max: Double = ms.lastOrNull() ?: 0.0
    private fun pct(q: Double): Double = if (ms.isEmpty()) 0.0 else ms[((ms.size - 1) * q).toInt()]
    override fun toString(): String = "%-58s n=%5d  p50 %8.3f  p95 %8.3f  max %8.3f ms".format(Locale.ROOT, name, n, p50, p95, max)
  }

  inline fun nanos(block: () -> Unit): Long {
    val t0 = System.nanoTime()
    block()
    return System.nanoTime() - t0
  }

  /* ------------------------------ اسکن ------------------------------ */

  /** آنچه صفحه با هر اسکن از نو می‌خواند: ثابت‌های همین دستگاه (تنظیم‌ها از prefs، مثلِ صفحه) */
  class Till(
    val ctx: Context, val d: ShopData, val snap: PromoEngine.Snapshot, val cart: CartStore,
    /** `remember(d.products)` ِ صفحه — پس از فروش کالاها همان‌اند و صفحه از نو نمی‌سازد */
    index: Map<String, String>? = null,
    packIndex: ir.vil3ntec.tohid.core.product.PackBarcodes.Index? = null,
  ) {
    val index: Map<String, String> = index ?: ShopStore.barcodeIndex(d)
    val packIndex = packIndex ?: ShopStore.packIndex(d)
    /** `SaleScreen`: `remember(d.products, d.sales, d.saleItems)` — فقط گوشی می‌خواند */
    val catalog by lazy { CatalogModel.index(d) }
    val today = BigLedger.dateOf(BigLedger.NOW)
    val group = PromoEngine.groupOf(d, null, snap.rules)
  }

  /** `DeskSaleScreen.fromScanner` (ScanRouter / بارکدخوانی که کادر را فوکوس نداشت) — همان ترتیب */
  fun deskScanner(t: Till, d: ShopData, code: String): Boolean {
    val ctx = t.ctx
    //  scaleLabel(code, dd)
    ScaleLabels.match(ScaleLabels.rules(ctx), code, ShopStore.barcodeIndex(d), d.products)?.let { return true }
    val items = ShopStore.barcodeIndex(d)
    //  addPack(code, 1.0, dd, items)
    PackScan.scan(d, t.cart.lines.value, code, 1.0, items, TillGoodsPref.units(ctx), TillGoodsPref.wholePacks(ctx))?.let { r ->
      if (r is PackScan.Result.Added) return t.cart.set(r.added.cart)
      return false
    }
    return when (val hit = BarcodeMatch.find(code, items)) {
      is BarcodeMatch.Hit.Product -> ShopStore.product(d, hit.id)?.let {
        if (!it.active) false
        else t.cart.set(SalesEngine.addToCartCapped(t.cart.lines.value, it.id, 1.0, ProductInfoMap.sellable(d, it.id)).cart)
      } ?: false
      else -> false
    }
  }

  /**
   *  Enter در کادرِ صندوقِ کامپیوتر: آخرین ترکیبِ پیش از Enter (`matches` ِ همان کد، جست‌وجوی پیش‌فرض) و
   *  `DeskSaleScreen.submit` — همان ترتیب (ثبتِ سریع و پنلِ پرداخت خاموش، پیش‌فرض).
   */
  fun deskField(t: Till, d: ShopData, query: String): Boolean {
    val ctx = t.ctx
    val parsed = PosSearch.parse(query, t.index, t.packIndex).let { it.count to it.term }
    val term = parsed.second.trim()
    val matches = PosCatalog.matches(d.products, term, false)
    //  submit()
    ScaleLabels.match(ScaleLabels.rules(ctx), query, ShopStore.barcodeIndex(d), d.products)?.let { return true }
    PackScan.scan(d, t.cart.lines.value, parsed.second, parsed.first, t.index, TillGoodsPref.units(ctx), TillGoodsPref.wholePacks(ctx))?.let { r ->
      if (r is PackScan.Result.Added) return t.cart.set(r.added.cart)
      return false
    }
    return when (val hit = PosSearch.resolve(query, t.index, d.products, matches, 0, t.packIndex)) {
      is PosHit.Add -> t.cart.set(SalesEngine.addToCartCapped(t.cart.lines.value, hit.product.id, hit.count, ProductInfoMap.sellable(d, hit.product.id)).cart)
      else -> false
    }
  }

  /** گوشی v3: `SaleRoute.onCode` ⇐ `CartFeedback.scan` ⇐ `CartModel.scan` — همان ترتیب */
  fun phoneScan(t: Till, d: ShopData, code: String): Boolean {
    val ctx = t.ctx
    ScaleLabels.match(ScaleLabels.rules(ctx), code.trim(), t.index, d.products)?.let { return true }
    val packPrice = TillGoodsPref.units(ctx)
    val wholeOnly = TillGoodsPref.wholePacks(ctx)
    return when (val s = CartModel.scan(d, t.index, t.cart.lines.value, code, 1.0, packPrice, wholeOnly)) {
      is CartModel.Scan.Added -> t.cart.set(s.lines)
      else -> false
    }
  }

  /** نتیجه‌ای که در آزمون‌ها سنجیده می‌شود تا JIT کار را دور نریزد */
  @Volatile var sink: Any? = null

  /**
   *  کامپیوتر — آنچه `DeskSaleScreen` با سبدِ تازه از نو می‌سازد: تخفیف، جمع، ردیف‌های سبد، پایهٔ تخفیف و لیستِ قیمت
   *  (`PriceListAtPos`، خرده ⇒ همان سبد).
   */
  fun deskObserved(t: Till, d: ShopData) {
    val cart = t.cart.lines.value
    val promo = PromoEngine.evaluate(d, cart, t.snap, emptyList(), t.today, 600, t.group)
    val priced = PromoEngine.priced(cart, promo)
    val totals = SalesEngine.totals(d, priced, SalesEngine.Checkout())
    val basis = DiscountGate.basis(false, d, priced, promo, totals)
    val lineViews = cart.map { line ->
      val p = ShopStore.product(d, line.productId)
      OrderLineView(
        id = line.productId, name = if (line.free) line.label else p?.name.orEmpty(), unit = p?.unit.orEmpty(), quantity = line.quantity,
        price = line.unitPrice ?: p?.salePrice ?: 0.0, customPrice = line.unitPrice != null, category = p?.category.orEmpty(),
      )
    }
    val repriced = repriceCart(d, cart, PriceList.RETAIL)
    sink = listOf(totals.finalTotal, basis, lineViews.size, repriced.size)
  }

  /**
   *  گوشی v3 — آنچه `SaleRoute` و `SaleScreen` با سبدِ تازه از نو می‌سازند: تخفیف و جمع (`SaleRoute`)، `inCart`، کاشی‌های
   *  کاتالوگ بی جست‌وجو (`items`) و «پرفروش‌ها» (هر دو با کلیدِ `inCart`)، ردیف‌های سبد و لیستِ قیمت (`PhoneTillTools`).
   */
  fun phoneObserved(t: Till, d: ShopData, search: String = "") {
    val cart = t.cart.lines.value
    val promo = PromoEngine.evaluate(d, cart, t.snap, emptyList(), t.today, 600, t.group)
    val total = SalesEngine.cartTotal(d, PromoEngine.priced(cart, promo))
    val inCart = CartModel.inCart(cart)
    val items = CatalogModel.items(d, t.catalog, search, null, inCart, emptyMap(), emptyList())
    val best = CatalogModel.bestsellers(d, t.catalog, inCart, emptyMap())
    val rows = CartSummary.rows(cart, d.products)
    val repriced = repriceCart(d, cart, PriceList.RETAIL)
    sink = listOf(total, items.size, best.size, rows.size, repriced.size)
  }

  /** کدهای یک سبدِ نمونه: بیشترْ بارکدِ کالا، گاه بستهٔ کارتن، گاه بارکدِ رقمِ فارسی، گاه دوباره همان کالا */
  fun basket(s: Shop, r: BigLedger.Rng, lines: Int): List<String> {
    val out = ArrayList<String>(lines)
    while (out.size < lines) {
      val k = r.int(100)
      out += when {
        k < 10 && s.packCodes.isNotEmpty() -> r.pick(s.packCodes)
        k < 13 && s.faCodes.isNotEmpty() -> r.pick(s.faCodes)
        k < 30 && out.isNotEmpty() -> out.last()
        else -> r.pick(s.itemCodes)
      }
    }
    return out
  }

  /* ---------------------------- جست‌وجو ---------------------------- */

  /** پرس‌وجوهای نمونه به گروه — هر پرس‌وجو حرف‌به‌حرف تایپ می‌شود و هر حرف یک نمونه است */
  fun queries(s: Shop): Map<String, List<String>> {
    val code = s.itemCodes[s.itemCodes.size / 3]
    return linkedMapOf(
      "1-3 letters" to listOf("ب", "رو", "شام", "c", "co", "nes", "ك"),
      "full word" to listOf("برنج", "شامپو", "کچالو", "Nutella", "روغن زیتون"),
      "typo" to listOf("برمج", "شامپوو", "کچلو", "Nutela"),
      "persian digits" to listOf("۵ کیلو", "برنج ۲۵۰", "۶۲۶۱۰۰۰۱"),
      "barcode prefix" to listOf(code.take(6), code.take(9), code),
    )
  }

  fun prefixes(q: String): List<String> = (1..q.length).map { q.take(it) }
}
