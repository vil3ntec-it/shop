package ir.vil3ntec.tohid.desktop.perf

import ir.vil3ntec.tohid.core.sale.PriceList
import ir.vil3ntec.tohid.data.BarcodeMatch
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.PackScan
import ir.vil3ntec.tohid.data.ProductInfoMap
import ir.vil3ntec.tohid.data.PromoEngine
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ScaleLabels
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.desktop.perf.PosSpeed.Stats
import ir.vil3ntec.tohid.desktop.perf.PosSpeed.Till
import ir.vil3ntec.tohid.desktop.perf.PosSpeed.nanos
import ir.vil3ntec.tohid.ui.components.pos.CartSummary
import ir.vil3ntec.tohid.ui.components.pos.PosCatalog
import ir.vil3ntec.tohid.ui.components.pos.repriceCart
import ir.vil3ntec.tohid.ui.v3.sales.cart.CartModel
import ir.vil3ntec.tohid.ui.v3.sales.cart.CatalogModel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 *  POS-42 — زمانِ «اسکن تا ردیف» و «جست‌وجو تا نخستین نتیجه» با ۲۰ هزار کالا، روی JVM ِ همین ماشین
 *  (`docs/spec/bench/pos-speed-*.md`). داده و راه‌ها: [PosSpeed].
 *
 *  ── اجرا ──────────────────────────────────────────────────────────────
 *   • همیشه: ۲۰ هزار کالا با دفترِ کوچک و تکرارِ کم — سقفِ **پنج برابرِ بودجه** (۵۰۰ و ۷۵۰ میلی‌ثانیه برای p95) تا
 *     پس‌رفتِ بزرگ (مثلاً گشتنِ کلِ دفتر در هر اسکن) دیده شود، بی لرزشِ زمانی در CI.
 *   • سنجهٔ واقعی (دستی؛ در CI هرگز): ۲۰ هزار کالا، ۵۰ هزار فاکتور، صدها تکرار پس از گرم شدن —
 *       TOHID_POS_SPEED=1 gradle -p desktop test --tests '*PosSpeedBenchTest*' …   (یا ‎-Ptohid.posSpeed=1‎؛ heap ِ ۳ گیگ)
 *     خروجی: جدولِ متنی در stdout و `desktop/build/pos-speed-bench.json`.
 *   • عددِ این‌جا JVM ِ ماشینِ سنجه است، نه گوشی؛ گوشیِ ارزان چند برابر کُندتر است.
 */
class PosSpeedBenchTest {

  private val ctx = DesktopContext
  private val draft get() = File(ctx.filesDir, CartStore.FILE)

  @Before fun before() { draft.delete() }
  @After fun after() { draft.delete() }

  private fun wanted(): Boolean =
    System.getenv("TOHID_POS_SPEED")?.isNotBlank() == true || System.getProperty("tohid.posSpeed")?.isNotBlank() == true

  /* ------------------------------ اسکن ------------------------------ */

  private class ScanRun(val handler: Stats, val total: Stats, val rows: Int)

  /**
   *  [rounds] سبد، هر کدام [lines] اسکن (بیشترْ کالای تازه ⇒ سبد تا [lines] ردیف بزرگ می‌شود)، و پایانِ هر سبد خالی.
   *  هر نمونه = رسیدگیِ صفحه به همان کد + بازسازیِ آنچه صفحه با سبدِ تازه می‌خواند.
   */
  private fun scans(
    name: String, s: PosSpeed.Shop, t: Till, warm: Int, rounds: Int, lines: Int,
    handler: (Till, ShopData, String) -> Boolean, observed: (Till, ShopData) -> Unit,
  ): ScanRun {
    val r = BigLedger.Rng(7)
    val h = ArrayList<Long>()
    val all = ArrayList<Long>()
    var maxRows = 0
    repeat(warm + rounds) { round ->
      t.cart.clear()
      for (code in PosSpeed.basket(s, r, lines)) {
        var hn = 0L
        val tn = nanos { hn = nanos { handler(t, t.d, code) }; observed(t, t.d) }
        if (round >= warm) { h += hn; all += tn }
      }
      maxRows = maxOf(maxRows, t.cart.lines.value.size)
    }
    t.cart.clear()
    return ScanRun(Stats("$name — handler", h), Stats("$name — handler + UI state", all), maxRows)
  }

  /**
   *  نخستین اسکن پس از ثبتِ یک فروش: دفترِ تازه ⇒ جدولِ موجودی (`ShopStore.index`) و — در گوشی — نمایهٔ کاتالوگ
   *  (کلیدِ `d.sales`) از نو. [samples] بار، هر بار با فروشِ تازه.
   */
  private fun afterSale(s: PosSpeed.Shop, base: Till, phone: Boolean, samples: Int): Stats {
    val out = ArrayList<Long>()
    var d = s.d
    val cart = base.cart
    repeat(samples + 3) { i ->
      d = BigLedger.sell(d, 5_000 + i)
      val code = s.itemCodes[(i * 131) % s.itemCodes.size]
      cart.clear()
      //  کالاها همان فهرست‌اند ⇒ نمایهٔ بارکد و بسته همان (`remember(d.products)`)؛ نمایهٔ کاتالوگِ گوشی و جدولِ موجودی از نو
      val t = Till(ctx, d, base.snap, cart, base.index, base.packIndex)
      val ns = nanos {
        if (phone) { PosSpeed.phoneScan(t, d, code); PosSpeed.phoneObserved(t, d) } else { PosSpeed.deskScanner(t, d, code); PosSpeed.deskObserved(t, d) }
      }
      if (i >= 3) out += ns
    }
    cart.clear()
    return Stats(if (phone) "phone v3 — first scan after a sale (cold indexes)" else "desktop — first scan after a sale (cold indexes)", out)
  }

  /* ---------------------------- جست‌وجو ---------------------------- */

  /** هر پرس‌وجو حرف‌به‌حرف؛ هر حرف = یک نمونه («تا نخستین نتیجه»: فهرستِ آمادهٔ نمایش) */
  private fun searches(s: PosSpeed.Shop, warm: Int, rounds: Int): List<Stats> {
    val d = s.d
    val smartIndex = PosCatalog.searchIndex(d.products, false)
    val byId = d.products.associateBy { it.id }
    val catalog = CatalogModel.index(d)
    val engines = linkedMapOf<String, (String) -> Int>(
      "desktop default (PosCatalog.matches)" to { q ->
        val term = ir.vil3ntec.tohid.data.CartQuery.parse(q).term.trim()
        PosCatalog.matches(d.products, term, false).size
      },
      "desktop smart POS-04 (CatalogMatch)" to { q ->
        val term = ir.vil3ntec.tohid.data.CartQuery.parse(q).term.trim()
        PosCatalog.smartMatches(smartIndex, byId, term).size
      },
      "phone v3 (CatalogModel.items)" to { q -> CatalogModel.items(d, catalog, q, null, emptyMap(), emptyMap(), emptyList()).size },
    )
    val out = ArrayList<Stats>()
    for ((engine, run) in engines) {
      for ((group, qs) in PosSpeed.queries(s)) {
        val ns = ArrayList<Long>()
        repeat(warm + rounds) { round ->
          for (q in qs) for (p in PosSpeed.prefixes(q)) {
            var n = 0
            val t = nanos { n = run(p) }
            PosSpeed.sink = n
            if (round >= warm) ns += t
          }
        }
        out += Stats("search · $engine · $group", ns)
      }
    }
    return out
  }

  /* --------------------------- شکستِ گام‌ها --------------------------- */

  /** هر گام جدا، میانه و بیشینه روی [n] بار، با سبدِ [lines] ردیفی — برای پیدا کردنِ گامِ گران */
  private fun stages(s: PosSpeed.Shop, n: Int, lines: Int, expiry: Boolean): List<Stats> {
    val d = s.d
    val cart = CartStore(ctx)
    val t = Till(ctx, d, PosSpeed.promos(d), cart)
    val r = BigLedger.Rng(9)
    cart.clear()
    for (code in PosSpeed.basket(s, r, lines)) PosSpeed.deskScanner(t, d, code)
    val c = cart.lines.value
    val code = s.itemCodes[s.itemCodes.size / 2]
    val pid = t.index.getValue(code)
    val expirySnap = PosSpeed.promos(d, expiry = true)
    //  کالاهای عوض‌شده (یک نام) — برای سنجیدنِ ساختنِ کاملِ نمایهٔ کاتالوگ
    val edited = d.copy(products = d.products.mapIndexed { i, p -> if (i == 0) p.copy(name = p.name + " نو") else p })
    var flip = false
    val steps = linkedMapOf<String, () -> Any?>(
      "ShopStore.barcodeIndex(d)" to { ShopStore.barcodeIndex(d) },
      "ShopStore.packIndex(d)" to { ShopStore.packIndex(d) },
      "ScaleLabels.match (no rules)" to { ScaleLabels.match(ScaleLabels.rules(ctx), code, t.index, d.products) },
      "PackScan.scan (item code)" to { PackScan.scan(d, c, code, 1.0, t.index, false, false) },
      "BarcodeMatch.find (exact)" to { BarcodeMatch.find(code, t.index) },
      "BarcodeMatch.find (unknown code)" to { BarcodeMatch.find("4000000000001", t.index) },
      "products.firstOrNull { id }" to { d.products.firstOrNull { it.id == pid } },
      "ProductInfoMap.sellable (warm stock index)" to { ProductInfoMap.sellable(d, pid) },
      "ShopStore.index(d) rebuild (full ledger)" to { ShopStore.Companion.StockIndex(d) },
      "SalesEngine.addToCartCapped" to { SalesEngine.addToCartCapped(c, pid, 1.0, 100.0) },
      "PromoEngine.evaluate ($lines lines)" to { PromoEngine.evaluate(d, c, t.snap, emptyList(), t.today, 600, t.group) },
      "PromoEngine.evaluate + near-expiry rule ($lines lines)" to { if (expiry) PromoEngine.evaluate(d, c, expirySnap, emptyList(), t.today, 600, t.group) else null },
      "SalesEngine.totals ($lines lines)" to { SalesEngine.totals(d, c, SalesEngine.Checkout()) },
      "repriceCart (price list, retail)" to { repriceCart(d, c, PriceList.RETAIL) },
      "CartSummary.rows" to { CartSummary.rows(c, d.products) },
      "CartModel.inCart" to { CartModel.inCart(c) },
      "phone CatalogModel.items (no search, all tiles)" to { CatalogModel.items(d, t.catalog, "", null, CartModel.inCart(c), emptyMap(), emptyList()) },
      "phone CatalogModel.bestsellers" to { CatalogModel.bestsellers(d, t.catalog, CartModel.inCart(c), emptyMap()) },
      "phone CatalogModel.index(d) after a sale (same products)" to { CatalogModel.index(d.copy(sales = d.sales.toList())) },
      "phone CatalogModel.index(d) rebuild (products changed)" to { flip = !flip; CatalogModel.index(if (flip) d else edited) },
      "phone CatalogSearch.popularity (all sales)" to { ir.vil3ntec.tohid.ui.components.pos.CatalogSearch.popularity(d.sales, d.saleItems) },
      "ExpiryEngine.lots(d)" to { if (expiry) ir.vil3ntec.tohid.data.ExpiryEngine.lots(d, t.today) else null },
      "desktop PosCatalog.searchIndex rebuild" to { PosCatalog.searchIndex(d.products, false) },
    )
    val out = ArrayList<Stats>()
    for ((name, step) in steps) {
      val reps = if (name.contains("near-expiry") || name.contains("rebuild") || name.contains("after a sale")) maxOf(3, n / 20) else n
      repeat(maxOf(2, reps / 5)) { PosSpeed.sink = step() }
      val ns = ArrayList<Long>(reps)
      repeat(reps) { ns += nanos { PosSpeed.sink = step() } }
      out += Stats("stage · $name", ns)
    }
    cart.clear()
    return out
  }

  /* ------------------------------ گزارش ------------------------------ */

  private fun machine(): String {
    val rt = Runtime.getRuntime()
    val cpu = runCatching { File("/proc/cpuinfo").readLines().firstOrNull { it.startsWith("model name") }?.substringAfter(':')?.trim() }.getOrNull()
    return "${System.getProperty("os.name")} ${System.getProperty("os.arch")} · ${cpu ?: "?"} · ${rt.availableProcessors()} cores · " +
      "JVM ${System.getProperty("java.vm.name")} ${System.getProperty("java.version")} · max heap ${rt.maxMemory() / 1_000_000} MB"
  }

  private fun report(all: List<Stats>, meta: Map<String, Any>) {
    println("\n[pos-speed] ${machine()}")
    meta.forEach { (k, v) -> println("[pos-speed] $k: $v") }
    for (x in all) println("[pos-speed] $x")
    val f = File(System.getProperty("user.dir"), "build/pos-speed-bench.json")
    fun q(x: String) = "\"" + x.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    val rows = all.joinToString(",\n") {
      "  {\"name\":${q(it.name)},\"n\":${it.n},\"p50\":%.4f,\"p95\":%.4f,\"max\":%.4f}".format(Locale.ROOT, it.p50, it.p95, it.max)
    }
    val head = meta.entries.joinToString(",") { "${q(it.key)}:${q(it.value.toString())}" }
    runCatching { f.parentFile.mkdirs(); f.writeText("{\"machine\":${q(machine())},$head,\"rows\":[\n$rows\n]}\n") }
    println("[pos-speed] → ${f.absolutePath}")
  }

  private fun run(products: Int, sales: Int, warm: Int, rounds: Int, lines: Int, searchWarm: Int, searchRounds: Int, afterSales: Int, stageN: Int, expiryRounds: Int): List<Stats> {
    val (s, genNs) = run { var x: PosSpeed.Shop? = null; val ns = nanos { x = PosSpeed.shop(products, sales) }; x!! to ns }
    val d = s.d
    val out = ArrayList<Stats>()
    val cart = CartStore(ctx)
    val till = Till(ctx, d, PosSpeed.promos(d), cart)
    //  جدولِ موجودی یک بار گرم (همان که صفحه پس از بارِ دفتر می‌سازد)
    ShopStore.index(d)
    val desk = scans("desktop scanner (fromScanner)", s, till, warm, rounds, lines, PosSpeed::deskScanner, PosSpeed::deskObserved)
    val field = scans("desktop field + Enter (submit)", s, till, warm, rounds, lines, PosSpeed::deskField, PosSpeed::deskObserved)
    val phone = scans("phone v3 scan (CartFeedback.scan)", s, till, warm, rounds, lines, PosSpeed::phoneScan) { t, dd -> PosSpeed.phoneObserved(t, dd) }
    val runs = mutableListOf(desk, field, phone)
    //  PRM-10: قاعدهٔ «نزدیکِ انقضا» محموله‌ها را برای هر سبد می‌سنجد — جدا، با تکرارِ کمتر
    if (expiryRounds > 0) {
      val expiryTill = Till(ctx, d, PosSpeed.promos(d, expiry = true), cart, till.index, till.packIndex)
      runs += scans("phone v3 scan + near-expiry promo", s, expiryTill, 1, expiryRounds, lines, PosSpeed::phoneScan) { t, dd -> PosSpeed.phoneObserved(t, dd) }
    }
    for (x in runs) { out += x.handler; out += x.total }
    out += afterSale(s, till, phone = false, samples = afterSales)
    out += afterSale(s, till, phone = true, samples = afterSales)
    out += searches(s, searchWarm, searchRounds)
    out += stages(s, stageN, lines, expiry = expiryRounds > 0)
    report(out, linkedMapOf(
      "products" to d.products.size, "active" to d.products.count { it.active }, "sales" to d.sales.size, "saleItems" to d.saleItems.size,
      "warehouseEntries" to d.warehouseEntries.size, "packs" to s.packCodes.size, "persianDigitBarcodes" to s.faCodes.size,
      "cartRowsMax" to desk.rows, "generateMs" to "%.0f".format(Locale.ROOT, genNs / 1e6),
    ))
    return out
  }

  /** همیشه: ۲۰ هزار کالا، دفترِ کوچک، تکرارِ کم — فقط سقفِ پنج برابرِ بودجه (p95) */
  @Test(timeout = 300_000) fun `اسکن و جست‌وجو با ۲۰ هزار کالا — سقفِ پنج برابرِ بودجه`() {
    assumeTrue("سنجهٔ کامل جدا اجرا می‌شود", !wanted())
    val all = run(products = 20_000, sales = 3_000, warm = 2, rounds = 4, lines = 25, searchWarm = 1, searchRounds = 1, afterSales = 3, stageN = 10, expiryRounds = 0)
    val scanCap = PosSpeed.SCAN_BUDGET_MS * 5
    val searchCap = PosSpeed.SEARCH_BUDGET_MS * 5
    for (x in all) {
      when {
        x.name.startsWith("search") -> assertTrue("$x ⇒ بیش از ${searchCap}ms", x.p95 <= searchCap)
        x.name.contains("near-expiry") || x.name.startsWith("stage") -> Unit
        else -> assertTrue("$x ⇒ بیش از ${scanCap}ms", x.p95 <= scanCap)
      }
    }
  }

  /** دستی: `TOHID_POS_SPEED=1` — ۲۰ هزار کالا، ۵۰ هزار فاکتور؛ S125: `TOHID_POS_SPEED_PRODUCTS=50000` ⇒ ۵۰ هزار کالا در یک دکان */
  @Test fun `سنجهٔ POS-42 — اندازهٔ واقعی`() {
    assumeTrue("فقط با TOHID_POS_SPEED یا -Ptohid.posSpeed", wanted())
    val products = System.getenv("TOHID_POS_SPEED_PRODUCTS")?.trim()?.toIntOrNull()?.coerceIn(1_000, 200_000) ?: 20_000
    run(products = products, sales = 50_000, warm = 20, rounds = 80, lines = 25, searchWarm = 3, searchRounds = 15, afterSales = 30, stageN = 200, expiryRounds = 2)
  }
}
