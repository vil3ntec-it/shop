package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.promo.PromoCore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  تخفیف‌های خودکار در صندوق — پلِ `PromoCore` و `SalesEngine`.
 *
 *  ۱. کلیدِ دکان خاموش ⇒ همان سبد، همان جمع، همان فاکتور (آزمونِ مرجع).
 *  ۲. روشن ⇒ قیمتِ ردیف پایین می‌آید، `Sale.discount` صفر می‌ماند (شاگرد هم
 *     ثبت می‌کند و Sync ردش نمی‌کند)، جمعِ ردیف‌ها = جمعِ فاکتور.
 *  ۳. قالبِ JSON رفت‌وبرگشت دارد.
 *  ۴. S2 — فیلدهای اختیاریِ فاکتور (`stamp`): قیمتِ فهرست، سهمِ هر قاعده، جمع و کدها؛
 *     خاموش ⇒ همان شیء. سهم‌ها روی همهٔ بردارهای هسته و سبدهای تصادفی جمع می‌خورند.
 *  ۵. S1 — حالِ حسابِ دیگر هرگز روی سبد نمی‌نشیند.
 */
class PromoEngineTest {

  private val tea = Product(id = "tea", name = "چای سبز", unit = "عدد", category = "نوشیدنی", purchasePrice = 100.0, salePrice = 300.0)
  private val rice = Product(id = "rice", name = "برنج", unit = "کیلوگرم", category = "خوراکی", purchasePrice = 0.0, salePrice = 115.0)
  private val d = ShopData(
    products = listOf(tea, rice),
    warehouseEntries = listOf(
      WarehouseEntry(id = "w1", productId = "tea", units = 50.0),
      WarehouseEntry(id = "w2", productId = "rice", units = 50.0),
    ),
  )
  private val cart = listOf(SalesEngine.CartLine("tea", 2.0), SalesEngine.CartLine("rice", 1.5))

  private val r1 = PromoCore.Rule(
    id = "r1", name = "ده درصدِ نوشیدنی", kind = "percent", bp = 1000,
    scope = PromoCore.Scope(categories = listOf("نوشیدنی")), channels = listOf("pos"),
  )
  private val r2 = PromoCore.Rule(id = "r2", name = "کدِ عید", kind = "percent", bp = 500, code = "EID5", stack = "combine")
  private val on = PromoEngine.Snapshot(enabled = true, rules = listOf(r1, r2))

  private var seq = 0
  private fun newId(): String = "t${seq++}"
  private fun record(c: List<SalesEngine.CartLine>, checkout: SalesEngine.Checkout = SalesEngine.Checkout()): SalesEngine.Result.Ok {
    seq = 0
    val r = SalesEngine.record(d, c, checkout, today = "2026-10-19", now = 1_760_000_000_000, newId = ::newId)
    assertTrue("$r", r is SalesEngine.Result.Ok)
    return r as SalesEngine.Result.Ok
  }
  private fun eval(s: PromoEngine.Snapshot, coupons: List<String> = emptyList(), c: List<SalesEngine.CartLine> = cart) =
    PromoEngine.evaluate(d, c, s, coupons, "2026-10-19", 600)

  @Test fun `خاموش با قاعده‌های فعال ⇒ همان سبد، همان جمع و همان فاکتورِ امروز`() {
    val off = on.copy(enabled = false)
    val o = eval(off, listOf("EID5"))
    assertNull(o)
    assertSame(cart, PromoEngine.priced(cart, o))
    val checkout = SalesEngine.Checkout(discountType = SalesEngine.DiscountType.PERCENT, discountValue = 10.0)
    assertEquals(SalesEngine.totals(d, cart, checkout), SalesEngine.totals(d, PromoEngine.priced(cart, o), checkout))
    val today = record(cart, checkout)
    val withOff = record(PromoEngine.priced(cart, o), checkout)
    assertEquals(today.data.sales, withOff.data.sales)
    assertEquals(today.data.saleItems, withOff.data.saleItems)
    assertEquals(773.0, today.data.sales.last().total, 0.0)
  }

  @Test fun `روشن ولی بی قاعدهٔ فعال یا سبدِ خالی ⇒ هیچ`() {
    assertNull(eval(on.copy(rules = listOf(r1.copy(active = false)))))
    assertNull(eval(on.copy(rules = emptyList())))
    assertNull(eval(on, c = emptyList()))
  }

  @Test fun `روشن — ده درصد روی نوشیدنی، کدِ ترکیبی؛ فاکتور با قیمتِ خالصِ ردیف و discount صفر`() {
    val auto = eval(on)!!
    assertEquals(773.0, auto.gross, 0.0)
    assertEquals(60.0, auto.discount, 0.0)
    assertEquals(listOf(PromoEngine.Applied("r1", "ده درصدِ نوشیدنی", 60.0)), auto.applied)

    val o = eval(on, listOf("eid-5"))!!
    assertEquals(99.0, o.discount, 0.0)
    assertEquals(listOf(PromoCore.CodeStatus("EID5", "applied")), o.codes)
    val priced = PromoEngine.priced(cart, o)
    assertEquals(674.0, SalesEngine.cartTotal(d, priced), 0.0)

    val sale = record(priced)
    val s = sale.data.sales.last()
    assertEquals(674.0, s.total, 0.0)
    assertEquals(0.0, s.discount, 0.0)
    assertEquals(674.0, s.finalTotal, 0.0)
    val items = sale.data.saleItems.associateBy { it.productId }
    assertEquals(510.0, items.getValue("tea").totalPrice, 0.0)
    assertEquals(255.0, items.getValue("tea").unitPrice, 1e-9)
    assertEquals(164.0, items.getValue("rice").totalPrice, 0.0)
    assertEquals(s.total, sale.data.saleItems.sumOf { it.totalPrice }, 0.0)
    //  مقدارها همان سبد است — فقط قیمت پایین آمده
    assertEquals(2.0, items.getValue("tea").quantity, 0.0)
    assertEquals(1.5, items.getValue("rice").quantity, 0.0)
  }

  @Test fun `تخفیفِ دستیِ فاکتور روی جمعِ پس از تخفیفِ خودکار`() {
    val priced = PromoEngine.priced(cart, eval(on)!!)
    val t = SalesEngine.totals(d, priced, SalesEngine.Checkout(discountType = SalesEngine.DiscountType.PERCENT, discountValue = 10.0))
    assertEquals(713.0, t.subtotal, 0.0)
    assertEquals(71.0, t.discount, 0.0)
    assertEquals(642.0, t.finalTotal, 0.0)
  }

  @Test fun `ردیفِ با قیمتِ دستی و قلمِ آزاد تخفیفِ خودکار نمی‌گیرند`() {
    val c = listOf(
      SalesEngine.CartLine("tea", 2.0, unitPrice = 280.0),
      SalesEngine.CartLine("free-1", 1.0, unitPrice = 50.0, label = "کیسه"),
    )
    val all = on.copy(rules = listOf(r1.copy(scope = PromoCore.Scope())))
    val o = eval(all, c = c)!!
    assertEquals(0.0, o.discount, 0.0)
    assertEquals(c, o.cart)
    assertEquals(610.0, o.gross, 0.0)
  }

  @Test fun `کفِ بها — ۹۰٪ روی چای فقط تا بهای خرید`() {
    val o = eval(on.copy(rules = listOf(r1.copy(bp = 9000))))!!
    //  ۲ × ۳۰۰ = ۶۰۰، بها ۲ × ۱۰۰ = ۲۰۰ ⇒ حداکثر ۴۰۰
    assertEquals(400.0, o.discount, 0.0)
    val below = eval(on.copy(rules = listOf(r1.copy(bp = 9000, allowBelowCost = true))))!!
    assertEquals(540.0, below.discount, 0.0)
  }

  @Test fun `عددِ بیرون از بازهٔ امنِ هسته ⇒ بی تخفیف، نه خطا`() {
    val huge = d.copy(products = listOf(tea.copy(salePrice = 1e14)))
    assertNull(PromoEngine.evaluate(huge, cart, on, emptyList(), "2026-10-19", 600))
  }

  @Test fun `JSON — قاعده و عکسِ حال رفت‌وبرگشت دارند`() {
    val full = r1.copy(
      window = PromoCore.Window(from = "2026-10-01", to = "2026-10-31", days = listOf(5L, 6L), start = 1320, end = 120, except = listOf("2026-10-30")),
      tiers = listOf(PromoCore.Tier(min = 3000, bp = 500)), prices = listOf(PromoCore.Price("tea", 25000)),
      code = "SALE10", maxUses = 3, allowBelowCost = true, priority = 7, stack = "exclusive",
    )
    assertEquals(full, PromoCore.ruleOf(PermPolicy.tree(PromoJson.toJson(full))))
    val snap = PromoEngine.Snapshot(enabled = true, rules = listOf(full, r2), manage = true, belowCost = false, at = 42, stamp = true)
    assertEquals(snap, PromoJson.snapshotOf(PromoJson.toJson(snap), 42))
    assertEquals(PromoEngine.Snapshot(at = 7), PromoJson.snapshotOf(kotlinx.serialization.json.JsonObject(emptyMap()), 7))
  }

  /* ---------------- S2: فیلدهای تازهٔ فاکتور ---------------- */

  private val revs = mapOf("r1" to 3L, "r2" to 7L)
  /** کلیدِ دکان روشن و پرچمِ `shop.promo_stamp` روشن */
  private val stamping = on.copy(stamp = true)

  @Test fun `خاموش ⇒ stamp همان شیء را برمی‌گرداند و هیچ فیلدِ تازه‌ای نمی‌نشیند`() {
    val r = record(cart)
    assertSame(r, PromoEngine.stamp(r, null))
    assertSame(r, PromoEngine.stamp(r, eval(stamping.copy(enabled = false))))
    val failed = SalesEngine.Result.Failed("x")
    assertSame(failed, PromoEngine.stamp(failed, eval(stamping)))
    assertNull(r.data.sales.last().promoDiscountMinor)
    assertTrue(r.data.saleItems.all { it.listPriceMinor == null && it.promo.isEmpty() })
  }

  @Test fun `پرچمِ shop_promo_stamp خاموش ⇒ تخفیف می‌خورد ولی فاکتور و قلم‌ها بایت‌به‌بایت همان پیشین`() {
    val o = eval(on, listOf("eid-5"))!!
    assertTrue(o.discount > 0)
    val r = record(PromoEngine.priced(cart, o))
    assertSame(r, PromoEngine.stamp(r, o))
    val json = kotlinx.serialization.json.Json { encodeDefaults = true; ignoreUnknownKeys = true }
    val text = json.encodeToString(ShopData.serializer(), (PromoEngine.stamp(r, o) as SalesEngine.Result.Ok).data)
    for (k in listOf("promoDiscountMinor", "coupons", "listPriceMinor", "\"promo\"")) assertTrue(k, !text.contains(k))
  }

  @Test fun `روشن ⇒ قیمتِ فهرست، سهمِ هر قاعده با rev و نام، جمعِ تخفیفِ خودکار و فقط کدهای اعمال‌شده`() {
    val o = eval(stamping.copy(revs = revs), listOf("eid-5", "NOPE123"))!!
    val before = record(PromoEngine.priced(cart, o))
    val r = PromoEngine.stamp(before, o) as SalesEngine.Result.Ok
    val sale = r.data.sales.last()
    val items = r.data.saleItems.associateBy { it.productId }
    //  هیچ عددی جابه‌جا نشد — فقط نشان
    assertEquals(before.data.sales.last().copy(promoDiscountMinor = sale.promoDiscountMinor, coupons = sale.coupons), sale)
    assertEquals(before.data.saleItems.map { it.totalPrice }, r.data.saleItems.map { it.totalPrice })
    assertEquals(9900L, sale.promoDiscountMinor)
    assertEquals(listOf("EID5"), sale.coupons)
    assertEquals(30000L, items.getValue("tea").listPriceMinor)
    assertEquals(11500L, items.getValue("rice").listPriceMinor)
    //  چای: ده درصدِ نوشیدنی، بعد کدِ عید روی مانده؛ برنج فقط کدِ عید
    assertEquals(listOf("r1", "r2"), items.getValue("tea").promo.map { it.id })
    assertEquals(listOf(3L, 7L), items.getValue("tea").promo.map { it.rev })
    //  نامِ همان لحظه — فاکتورِ دوباره‌چاپ‌شده از حالِ امروزِ قاعده‌ها نمی‌خواند
    assertEquals(listOf("ده درصدِ نوشیدنی", "کدِ عید"), items.getValue("tea").promo.map { it.name })
    assertEquals(listOf("r2"), items.getValue("rice").promo.map { it.id })
    for (it in r.data.saleItems) {
      val list = it.listPriceMinor!!
      //  جمعِ ردیف = فهرست × مقدار − سهم‌های تخفیف (همه به واحدِ کوچک، صحیح)
      assertEquals((SalesEngine.lineTotal(list / 100.0, it.quantity) * 100).toLong() - it.promo.sumOf { p -> p.discountMinor }, (it.totalPrice * 100).toLong())
    }
    assertEquals(sale.promoDiscountMinor, r.data.saleItems.sumOf { it.promo.sumOf { p -> p.discountMinor } })
    assertEquals(o.applied.sumOf { (it.discount * 100).toLong() }, sale.promoDiscountMinor)
  }

  @Test fun `قیمتِ دستی و قلمِ آزاد — فهرست هست، سهم نه؛ قلمِ آزاد هیچ`() {
    val c = listOf(SalesEngine.CartLine("tea", 2.0, unitPrice = 280.0), SalesEngine.CartLine("free-1", 1.0, unitPrice = 50.0, label = "کیسه"))
    val o = eval(stamping.copy(rules = listOf(r1.copy(scope = PromoCore.Scope()))), c = c)!!
    val r = PromoEngine.stamp(record(PromoEngine.priced(c, o)), o) as SalesEngine.Result.Ok
    val tea = r.data.saleItems.single { it.productId == "tea" }
    val free = r.data.saleItems.single { it.productId.isEmpty() }
    assertEquals(30000L, tea.listPriceMinor)
    assertEquals(28000L, (tea.unitPrice * 100).toLong())
    assertTrue(tea.promo.isEmpty())
    assertNull(free.listPriceMinor)
    assertTrue(free.promo.isEmpty())
    assertEquals(0L, r.data.sales.last().promoDiscountMinor)
  }

  @Test fun `rev از پاسخِ سرور و نسخهٔ دستگاه رفت‌وبرگشت دارد`() {
    val raw = kotlinx.serialization.json.Json.parseToJsonElement(
      """{"enabled":true,"rules":[{"id":"r1","name":"n","kind":"percent","bp":1000,"rev":4},{"id":"r2","name":"m","kind":"percent","bp":500}]}""",
    ) as kotlinx.serialization.json.JsonObject
    val s = PromoJson.snapshotOf(raw, 9)
    assertEquals(mapOf("r1" to 4L), s.revs)
    assertEquals(s, PromoJson.snapshotOf(PromoJson.toJson(s), 9))
    //  بدنهٔ PUT ِ قاعده rev ندارد
    assertNull(PromoJson.toJson(s.rules[0])["rev"])
  }

  /* ---------------- سهمِ هر قاعده: روی همهٔ بردارهای هسته و سبدهای تصادفی ---------------- */

  private fun checkSplit(name: String, cart: PromoCore.Cart, rules: List<PromoCore.Rule>) {
    val r = PromoCore.evaluate(cart, rules)
    val parts = PromoEngine.split(cart, rules, r)
    assertTrue("$name: تقسیم نشد", parts != null)
    for (l in r.lines) assertEquals(name, l.discountMinor, parts!![l.key]?.sumOf { it.second } ?: 0L)
    for (a in r.applied) assertEquals(name, a.discountMinor, parts!!.values.sumOf { xs -> xs.filter { it.first == a.id }.sumOf { it.second } })
    assertTrue(name, parts!!.values.all { xs -> xs.all { it.second > 0 && r.applied.any { a -> a.id == it.first } } })
  }

  @Test fun `سهمِ هر قاعده — همهٔ بردارهای promo-v1`() {
    var dir: java.io.File? = java.io.File(System.getProperty("user.dir")).absoluteFile
    var f: java.io.File? = null
    while (dir != null && f == null) { f = java.io.File(dir, "core/test-vectors/promo-v1.json").takeIf { it.isFile }; dir = dir.parentFile }
    val v = kotlinx.serialization.json.Json.parseToJsonElement(f!!.readText()) as kotlinx.serialization.json.JsonObject
    var n = 0
    for (e in v.getValue("evaluate") as kotlinx.serialization.json.JsonArray) {
      val c = PermPolicy.tree(e) as Map<*, *>
      if (c["error"] == true) continue
      checkSplit(c["name"].toString(), PromoCore.cartOf(c["cart"]), (c["rules"] as List<*>).map { PromoCore.ruleOf(it) })
      n++
    }
    assertTrue(n > 40)
  }

  @Test fun `سهمِ هر قاعده — سبدهای تصادفی با چند قاعدهٔ ترکیبی`() {
    val rnd = java.util.Random(307)
    val kinds = listOf("percent", "amount", "tiers", "bxgy", "bundle", "threshold")
    repeat(400) { round ->
      val lines = (0 until 1 + rnd.nextInt(5)).map { i ->
        PromoCore.Line(
          key = "k$i", productId = "P$i", qtyMilli = (1 + rnd.nextInt(6)) * 1000L, unitMinor = (1 + rnd.nextInt(50)) * 1000L,
          costMinor = if (rnd.nextBoolean()) rnd.nextInt(20) * 500L else 0, category = if (i % 2 == 0) "x" else "y",
        )
      }
      val rules = (0 until 1 + rnd.nextInt(4)).map { j ->
        val kind = kinds[rnd.nextInt(kinds.size)]
        val base = PromoCore.Rule(
          id = "r$j", name = "r$j", kind = kind, stack = listOf("best", "combine", "combine")[rnd.nextInt(3)],
          priority = rnd.nextInt(3).toLong(), allowBelowCost = rnd.nextBoolean(),
          scope = if (rnd.nextBoolean()) PromoCore.Scope(categories = listOf("x")) else PromoCore.Scope(),
        )
        when (kind) {
          "percent" -> base.copy(bp = (1 + rnd.nextInt(50)) * 100L)
          "amount" -> base.copy(amountMinor = (1 + rnd.nextInt(30)) * 100L)
          "tiers" -> base.copy(tiers = listOf(PromoCore.Tier(min = 2000, bp = 500), PromoCore.Tier(min = 4000, bp = 1500)))
          "bxgy" -> base.copy(buy = 1L + rnd.nextInt(2), get = 1, bp = 5000)
          "bundle" -> base.copy(qty = 2, priceMinor = (1 + rnd.nextInt(40)) * 1000L, scope = PromoCore.Scope(products = lines.map { it.productId }))
          else -> base.copy(tiers = listOf(PromoCore.Tier(min = 10_000, amountMinor = 1_000), PromoCore.Tier(min = 50_000, amountMinor = 4_000)))
        }
      }.filter { PromoCore.validate(it).isEmpty() }
      checkSplit("تصادفیِ $round", PromoCore.Cart(lines), rules)
    }
  }

  @Test fun `کدها — فقط اعمال‌شده، بی تکرار، با سقفِ شمار و طول`() {
    val many = (1..15).map { PromoCore.CodeStatus("C${it}OK", "applied") } + PromoCore.CodeStatus("NOPE", "unknown") +
      PromoCore.CodeStatus("X".repeat(60), "applied") + PromoCore.CodeStatus("C1OK", "applied")
    val o = eval(stamping)!!.copy(codes = many)
    val sale = (PromoEngine.stamp(record(PromoEngine.priced(cart, o)), o) as SalesEngine.Result.Ok).data.sales.last()
    assertEquals(PromoEngine.MAX_COUPONS, sale.coupons.size)
    assertTrue(sale.coupons.none { it == "NOPE" })
    assertTrue(sale.coupons.all { it.length <= PromoEngine.MAX_COUPON_LEN })
    assertEquals(sale.coupons.distinct(), sale.coupons)
  }

  /* ---------------- S1: حالِ حسابِ دیگر ---------------- */

  @Test fun `S1 — عکسِ حسابِ قبلی روی سبدِ حسابِ تازه نمی‌نشیند`() {
    val shopA = on.copy(account = "u1@shopA")
    assertTrue(eval(PromoEngine.forAccount(shopA, "u1@shopA")) != null)
    //  همان حساب، دکانِ دیگر؛ یا حسابِ دیگر ⇒ خاموش و همان سبد
    for (other in listOf("u1@shopB", "u2@shopA", "u1@")) {
      val s = PromoEngine.forAccount(shopA, other)
      assertEquals(PromoEngine.Snapshot(account = other), s)
      assertNull(eval(s, listOf("EID5")))
    }
  }

  @Test fun `S1 — پاسخِ سرور فقط وقتی می‌نشیند که حافظه و حسابِ همین لحظه هر دو مالِ همان حساب‌اند`() {
    assertTrue(PromoEngine.accepts("u1@A", "u1@A", "u1@A"))
    //  پاسخِ حسابِ قبلی پس از جابه‌جایی (حافظه یا حسابِ واردشده عوض شده)
    assertTrue(!PromoEngine.accepts("u2@B", "u1@A", "u2@B"))
    assertTrue(!PromoEngine.accepts(null, "u1@A", "u1@A"))
    //  توکنِ تازه پیش از `align`: حافظه هنوز «u1@A» است ولی حسابِ همین لحظه نه ⇒ نه در حافظه، نه در نسخهٔ دستگاه
    assertTrue(!PromoEngine.accepts("u1@A", "u1@A", "u2@B"))
  }

  @Test fun `S1 — کلیدِ تازه «خوانده شد» و «آخرین تلاش» را صفر می‌کند`() {
    val b = PromoEngine.Binding()
    assertTrue(b.bind("u1@shopA"))
    b.loaded = true
    b.triedAt = 42
    assertTrue(!b.bind("u1@shopA"))
    assertTrue(b.loaded)
    assertEquals(42L, b.triedAt)
    assertTrue(b.bind("u1@shopB"))
    assertEquals("u1@shopB", b.key)
    assertTrue(!b.loaded)
    assertEquals(0L, b.triedAt)
    //  ورود/خروج: مالِ هیچ‌کس؛ همان کلید دوباره «تازه» است
    b.loaded = true
    b.reset()
    assertNull(b.key)
    assertTrue(!b.loaded)
    assertTrue(b.bind("u1@shopB"))
  }
}
