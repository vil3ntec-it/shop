package ir.vil3ntec.tohid.core.sale

import ir.vil3ntec.tohid.core.perm.PermCore
import ir.vil3ntec.tohid.core.perm.SaleCaps
import ir.vil3ntec.tohid.core.pos.LinePricing
import ir.vil3ntec.tohid.core.promo.PromoCore
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *  POS-18 «قیمتِ خاصِ مشتری» — `CustomerPrice` روی همهٔ `core/test-vectors/customer-price-v1.json`: ترتیبِ قیمت، بازهٔ
 *  اعتبار، عوض شدنِ مشتری، تخفیفِ خودکارِ روی آن (`PromoCore`)، سقفِ قیمت (`SaleCaps` + `PermCore`)، مقدارِ اعشاری،
 *  مجوز و درستیِ ردیف. core وابستگیِ JSON ندارد ⇒ هر ردیف یک خط، با الگو (همان روشِ `PriceListVectorsTest`).
 */
class CustomerPriceVectorsTest {

  private fun vectorText(): String {
    var dir: File? = File("").absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/customer-price-v1.json")
      if (f.isFile) return f.readText()
      dir = dir.parentFile
    }
    error("core/test-vectors/customer-price-v1.json پیدا نشد")
  }

  private fun str(line: String, key: String): String = Regex("\"$key\": \"([^\"]*)\"").find(line)?.groupValues?.get(1) ?: error("$key در $line نیست")
  private fun opt(line: String, key: String): String? = str(line, key).ifEmpty { null }
  private fun num(line: String, key: String): Long = Regex("\"$key\": (-?[0-9]+)").find(line)?.groupValues?.get(1)?.toLong() ?: error("$key در $line نیست")
  private fun bool(line: String, key: String): Boolean = Regex("\"$key\": (true|false)").find(line)?.groupValues?.get(1)?.toBooleanStrict() ?: error("$key در $line نیست")
  private fun raw(line: String, key: String): List<String> =
    (Regex("\"$key\": \\[([^\\]]*)\\]").find(line)?.groupValues?.get(1) ?: error("$key در $line نیست"))
      .split(',').map { it.trim() }.filter { it.isNotEmpty() }
  private fun longs(line: String, key: String) = raw(line, key).map { it.toLong() }
  private fun nullable(line: String, key: String): List<Long?> = longs(line, key).map { if (it < 0) null else it }

  private fun section(name: String): List<String> {
    val text = vectorText()
    val start = text.indexOf("\"$name\": [")
    require(start >= 0) { "$name نیست" }
    val end = text.indexOf("\n  ]", start)
    return text.substring(start, end).lines().filter { it.trimStart().startsWith("{ \"name\"") }
  }

  private fun customer(line: String, key: String): String? = str(line, key).takeIf { it != "-" }

  private fun lines(r: String, units: List<Long?>, specials: List<Long?>, fixed: List<Boolean>): List<CustomerPrice.Line> {
    val retail = longs(r, "retail"); val wholesale = longs(r, "wholesale")
    return units.indices.map { i -> CustomerPrice.Line("l$i", "p$i", PriceList.Prices(retail[i], wholesale[i]), units[i], specials[i], fixed[i]) }
  }

  private fun entries(r: String, products: String, prices: String, to: String? = null): List<CustomerPrice.Entry> {
    val ps = longs(r, products); val pr = longs(r, prices)
    return ps.indices.map { CustomerPrice.Entry("p${ps[it]}", pr[it], to = to) }
  }

  @Test fun validityRange() {
    val rows = section("active")
    assertTrue(rows.size >= 9)
    for (r in rows) {
      val e = CustomerPrice.Entry("p", num(r, "price"), opt(r, "from"), opt(r, "to"))
      assertEquals(bool(r, "want"), CustomerPrice.active(e, str(r, "date")), str(r, "name"))
    }
  }

  @Test fun precedence() {
    val rows = section("resolve")
    assertTrue(rows.size >= 13)
    for (r in rows) {
      val special = num(r, "special").takeIf { it >= 0 }?.let { CustomerPrice.Entry("p", it, opt(r, "from"), opt(r, "to")) }
      val got = CustomerPrice.resolve(
        PriceList.Prices(num(r, "retail"), num(r, "wholesale")),
        num(r, "manual").takeIf { it >= 0 }, bool(r, "allowed"), special,
        customer(r, "customer"), PriceList.State(str(r, "list"), bool(r, "byHand")), str(r, "date"),
      )
      assertEquals(CustomerPrice.Price(num(r, "want"), str(r, "source")), got, str(r, "name"))
    }
  }

  @Test fun repriceInvoice() {
    val rows = section("reprice")
    assertTrue(rows.size >= 6)
    for (r in rows) {
      val name = str(r, "name")
      val ls = lines(r, nullable(r, "units"), nullable(r, "specials"), raw(r, "fixed").map { it == "true" })
      val got = CustomerPrice.reprice(
        ls, entries(r, "eProducts", "ePrices", opt(r, "eTo")), customer(r, "customer"),
        PriceList.State(str(r, "list"), bool(r, "byHand")), str(r, "date"), bool(r, "allowed"),
      )
      assertEquals(nullable(r, "want"), got.lines.map { it.unitMinor }, name)
      assertEquals(nullable(r, "wantSpecials"), got.lines.map { it.specialMinor }, name)
      assertEquals(num(r, "changed").toInt(), got.changed, name)
      assertEquals(num(r, "kept").toInt(), got.keptManual, name)
      assertEquals(num(r, "special").toInt(), got.special, name)
    }
  }

  /** عوض شدنِ مشتری روی فاکتورِ باز: همان `PriceList.onCustomer` ِ صندوق، بعد همان [CustomerPrice.reprice] */
  @Test fun customerSwitchReprices() {
    val rows = section("switch")
    assertTrue(rows.size >= 3)
    for (r in rows) {
      val name = str(r, "name")
      val start = nullable(r, "units")
      val ls = lines(r, start, start.map { null }, start.map { false })
      val date = str(r, "date")
      val aList = customer(r, "aList")
      val sa = PriceList.onCustomer(PriceList.FRESH, aList)
      val a = CustomerPrice.reprice(ls, entries(r, "aProducts", "aPrices"), aList, sa, date, true)
      assertEquals(nullable(r, "wantA"), a.lines.map { it.unitMinor }, "$name / A")
      val bList = customer(r, "bList")
      val sb = PriceList.onCustomer(sa, bList)
      val b = CustomerPrice.reprice(a.lines, entries(r, "bProducts", "bPrices"), bList, sb, date, true)
      assertEquals(nullable(r, "wantB"), b.lines.map { it.unitMinor }, "$name / B")
      assertEquals(num(r, "changedB").toInt(), b.changed, "$name / B")
      assertEquals(num(r, "keptB").toInt(), b.keptManual, "$name / B")
      //  هیچ ردیفی قیمتِ خاصِ مشتریِ پیشین را نگه نمی‌دارد
      val bSpecial = entries(r, "bProducts", "bPrices").associate { it.productId to it.priceMinor }
      b.lines.forEach { l -> l.specialMinor?.let { assertEquals(bSpecial[l.productId], it, "$name / B ${l.key}") } }
    }
  }

  /** تخفیفِ خودکار روی قیمتِ خاص همان‌طور می‌نشیند که روی قیمتِ لیست */
  @Test fun promoOnTop() {
    val rows = section("promo")
    assertTrue(rows.size >= 3)
    for (r in rows) {
      val name = str(r, "name")
      val unit = CustomerPrice.resolve(PriceList.Prices(10_000, 0), null, true, CustomerPrice.Entry("p1", num(r, "special")), "", PriceList.FRESH, "2026-11-08")
      assertEquals(CustomerPrice.SPECIAL, unit.source, name)
      val rule = when (str(r, "kind")) {
        "percent" -> PromoCore.Rule(id = "r1", name = "r1", kind = "percent", bp = num(r, "bp"), scope = PromoCore.Scope(products = listOf("p1")))
        else -> PromoCore.Rule(id = "r1", name = "r1", kind = "price", prices = listOf(PromoCore.Price("p1", num(r, "promoUnit"))), scope = PromoCore.Scope(products = listOf("p1")))
      }
      val out = PromoCore.evaluate(PromoCore.Cart(listOf(PromoCore.Line("k", "p1", num(r, "qty"), unit.unitMinor))), listOf(rule))
      assertEquals(num(r, "amount"), out.subtotalMinor, name)
      assertEquals(num(r, "discount"), out.discountMinor, name)
      assertEquals(num(r, "net"), out.totalMinor, name)
    }
  }

  /** قیمتِ خاص قیمتِ فهرست نیست: `SaleCaps` فاصله‌اش را از نزدیک‌ترین خرده/عمده می‌سنجد و `PermCore` تصمیم می‌گیرد */
  @Test fun priceCapStillApplies() {
    val rows = section("cap")
    assertTrue(rows.size >= 7)
    for (r in rows) {
      val name = str(r, "name")
      val c = SaleCaps.line(
        SaleCaps.Line("p1", num(r, "special"), num(r, "qty")),
        SaleCaps.Ref(num(r, "retail"), num(r, "wholesale")), "2026-11-08",
      )
      assertEquals(num(r, "bp"), c.deviationBp, name)
      assertEquals(num(r, "reduction"), c.reductionMinor, name)
      val layer = buildMap<String, Any?> {
        opt(r, "mode")?.let { put("mode", it) }
        num(r, "maxPct").takeIf { it >= 0 }?.let { put("caps", mapOf("maxPct" to it.toDouble())) }
      }
      val layers = if (layer.isEmpty()) null else listOf(mapOf("sales.price" to layer))
      val d = PermCore.evaluate(str(r, "role"), layers, "sales.price", mapOf("pct" to c.deviationBp / 100.0))
      assertEquals(str(r, "decision"), d.decision, name)
    }
  }

  @Test fun unitPriceTimesQuantity() {
    val rows = section("amounts")
    assertTrue(rows.size >= 3)
    for (r in rows) assertEquals(num(r, "want"), LinePricing.grossMinor(num(r, "special"), num(r, "qty")), str(r, "name"))
  }

  @Test fun whoMaySetSpecialPrices() {
    val rows = section("edit")
    assertTrue(rows.size >= 11)
    for (r in rows) {
      val layers = opt(r, "mode")?.let { listOf(mapOf("sales.price" to mapOf("mode" to it))) }
      assertEquals(bool(r, "want"), CustomerPrice.canEdit(str(r, "role"), layers), str(r, "name"))
    }
  }

  @Test fun entryValidation() {
    val rows = section("check")
    assertTrue(rows.size >= 9)
    for (r in rows) {
      val e = CustomerPrice.Entry(str(r, "product"), num(r, "price"), opt(r, "from"), opt(r, "to"))
      val n = num(r, "others").toInt()
      val others = (0 until n).map { i -> CustomerPrice.Entry(if (bool(r, "dup") && i == 0) e.productId else "o$i", 100) }
      assertEquals(opt(r, "want"), CustomerPrice.check(e, others), str(r, "name"))
    }
  }

  @Test fun receiptSource() {
    val rows = section("stamp")
    assertTrue(rows.size >= 3)
    for (r in rows) {
      assertEquals(opt(r, "want"), CustomerPrice.stamp(num(r, "special").takeIf { it >= 0 }, num(r, "sold")), str(r, "name"))
    }
  }
}
