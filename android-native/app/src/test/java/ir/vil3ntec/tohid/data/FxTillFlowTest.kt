package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.hw.DrawerRule
import ir.vil3ntec.tohid.core.i18n.StrFx
import ir.vil3ntec.tohid.core.pay.FxRate
import ir.vil3ntec.tohid.core.pay.Pay
import ir.vil3ntec.tohid.core.pay.PayResult
import ir.vil3ntec.tohid.core.pay.Settlement
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PAY-10 در برنامه — `FxTillFlow` و سیم‌کشیِ `PayFlow`/`SalesEngine`/`CashShift`/`ReturnFlow`: فروش به افغانی، ارز یک
 *  ردیفِ `fx` با رکوردِ ثابت (ارز، مبلغِ اصلی، نرخ، منبع، زمان، گذارنده)، پولِ پس افغانی و همان «مورد انتظار» ِ افغانی در
 *  هستهٔ شیفت و صندوقِ ساده. بی کادرِ ارز همه‌چیز همان امروز است. حسابِ خودِ هسته: `FxTillVectorsTest` (core).
 */
class FxTillFlowTest {

  private val t0 = 1_760_000_000_000L
  private val usd = FxRate("USD", 7025, 1, "سرای شهزاده", t0, "مدیر")
  private val pkr = FxRate("PKR", 24600, 1000, "سرای شهزاده", t0, "مدیر")
  private val rates = listOf(usd, pkr)
  private fun tender(amount: String, currency: String = "USD", now: Long = t0 + 60_000) = FxTillFlow.Tender(rates, currency, amount, now)

  private var seq = 0
  private val newId: () -> String = { "id${++seq}" }
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

  private fun <T> ok(o: PayFlow.Outcome<T>): T = when (o) {
    is PayFlow.Outcome.Ok -> o.value
    is PayFlow.Outcome.Bad -> throw AssertionError("انتظارِ موفقیت: ${o.problem}")
  }
  private fun <T> bad(o: PayFlow.Outcome<T>): PayFlow.Problem = when (o) {
    is PayFlow.Outcome.Bad -> o.problem
    is PayFlow.Outcome.Ok -> throw AssertionError("انتظارِ خطا: ${o.value}")
  }

  @Test fun `جدولِ نرخ - رفت‌وبرگشتِ JSON؛ خراب یا یک ردیفِ نادرست ⇒ هیچ نرخ`() {
    assertEquals(rates, FxTillFlow.decode(FxTillFlow.encode(rates)))
    assertEquals(emptyList<FxRate>(), FxTillFlow.decode(null))
    assertEquals(emptyList<FxRate>(), FxTillFlow.decode("{"))
    assertEquals(emptyList<FxRate>(), FxTillFlow.decode("""{"currency":"USD"}"""))
    assertEquals(emptyList<FxRate>(), FxTillFlow.decode("""[{"currency":"USD","rate":7025,"per":1,"source":"x","at":1,"by":"u"},{"currency":"AFN","rate":1,"per":1,"source":"x","at":1,"by":"u"}]"""))
    //  نرخِ رشته‌ای عدد نیست (نوعِ دیگر ⇒ رد، نه حدس)
    assertEquals(emptyList<FxRate>(), FxTillFlow.decode("""[{"currency":"USD","rate":"7025","per":1,"source":"x","at":1,"by":"u"}]"""))
    assertEquals(listOf(FxRate("IRR", 6600, 1, "x", 1, "u")), FxTillFlow.decode("""[{"currency":"IRR","rate":6600,"source":"x","at":1,"by":"u"}]"""))
  }

  @Test fun `ذخیرهٔ نرخ - واحدِ پیش‌فرضِ هر ارز، زمان و گذارنده؛ نادرست با نامِ کادر`() {
    val a = (FxTillFlow.withRate(emptyList(), "PKR", "246", " بانک ", t0, "مدیر") as PayResult.Ok).value
    assertEquals(listOf(FxRate("PKR", 24600, 1000, "بانک", t0, "مدیر")), a)
    val b = (FxTillFlow.withRate(a, "USD", "۷۰٫۲۵", "سرای شهزاده", t0 + 1, "مدیر") as PayResult.Ok).value
    assertEquals(listOf("USD", "PKR"), b.map { it.currency })
    assertEquals(7025L, b.first().rate)
    val c = (FxTillFlow.withRate(b, "USD", "71", "سرای شهزاده", t0 + 2, "علی") as PayResult.Ok).value
    assertEquals(listOf(7100L, 24600L), c.map { it.rate })
    assertEquals("علی", c.first().by)
    assertEquals("rate", (FxTillFlow.withRate(b, "USD", "abc", "x", t0, "u") as PayResult.Err).field)
    assertEquals("rate", (FxTillFlow.withRate(b, "USD", "0", "x", t0, "u") as PayResult.Err).field)
    assertEquals("source", (FxTillFlow.withRate(b, "USD", "70", "  ", t0, "u") as PayResult.Err).field)
    assertEquals(listOf("PKR"), FxTillFlow.remove(b, "USD").map { it.currency })
    assertTrue(FxTillFlow.fresh(usd, t0 + 86_400_000))
    assertTrue(!FxTillFlow.fresh(usd, t0 + 86_400_001))
  }

  @Test fun `صندوق - ۱۰ دالر برای ۵۰۰ افغانی ⇒ ردیفِ ارزِ ۵۰۰ با رکورد، پولِ پسِ ۲۰۳ افغانی`() {
    val s = ok(PayFlow.checkout(PayFlow.Draft(500.0, fx = tender("10"))))
    assertEquals(listOf(Pay.FX to 50000L), s.payments.map { it.method to it.amount })
    assertEquals(70300L, s.tendered)
    assertEquals(20300L, s.change)
    val f = PayFlow.saleFields(s)
    assertEquals("cash", f.paymentMethod)
    assertEquals(500.0, f.paidAmount, 0.0)
    assertEquals(SaleFx("USD", 1000, 7025, 1, 70300, "سرای شهزاده", t0, "مدیر"), f.payments.single().fx)
    //  نقدِ دقیق پس از ارز: فقط باقیِ افغانی
    assertEquals(297.0, ok(PayFlow.exactCash(PayFlow.Draft(1000.0, fx = tender("10")))), 0.0)
  }

  @Test fun `بی مبلغِ ارز یا بی کادر همان تسویهٔ امروز`() {
    val today = ok(PayFlow.checkout(PayFlow.Draft(850.0, "1000")))
    assertEquals(today, ok(PayFlow.checkout(PayFlow.Draft(850.0, "1000", fx = tender("")))))
    assertEquals(today, ok(PayFlow.checkout(PayFlow.Draft(850.0, "1000", fx = tender("  ")))))
    assertNull(today.payments.single().fx)
    assertNull(PayFlow.saleFields(today).payments.single().fx)
  }

  @Test fun `نرخِ کهنه، ارزِ بی نرخ، ارزِ نالازم و مبلغِ نادرست با پیامِ خودش رد می‌شوند`() {
    val stale = bad(PayFlow.preview(PayFlow.Draft(500.0, fx = tender("10", now = t0 + 86_400_001))))
    assertEquals("FX_STALE" to Pay.FX, stale.code to stale.method)
    assertEquals(StrFx.stale, PayFlow.message(stale))
    assertEquals("FX_NO_RATE", bad(PayFlow.preview(PayFlow.Draft(500.0, fx = tender("10", currency = "IRR")))).code)
    val excess = bad(PayFlow.preview(PayFlow.Draft(0.0, fx = tender("10"))))
    assertEquals("FX_EXCESS" to Pay.FX, excess.code to excess.method)
    assertEquals(StrFx.excess, PayFlow.message(excess))
    val amount = bad(PayFlow.preview(PayFlow.Draft(500.0, fx = tender("10.555"))))
    assertEquals("INVALID_AMOUNT" to Pay.FX, amount.code to amount.method)
    assertEquals(StrFx.amountBad, PayFlow.message(amount))
    //  ارز و نقدِ افغانی: ارز اول، باقی نقد؛ پولِ پس از نقد
    val mixed = ok(PayFlow.checkout(PayFlow.Draft(800.0, "100", fx = tender("10"))))
    assertEquals(listOf("cash" to 9700L, Pay.FX to 70300L), mixed.payments.map { it.method to it.amount })
    assertEquals(300L, mixed.change)
  }

  // ── ثبت، رسید، شیفت، برگشت ─────────────────────────────
  private val shop = ShopData(
    products = listOf(Product(id = "p2", name = "روغن", salePrice = 2450.0, purchasePrice = 2000.0, unit = "عدد")),
    warehouseEntries = listOf(WarehouseEntry(id = "w", productId = "p2", units = 10.0)),
  )
  private val cart = listOf(SalesEngine.CartLine("p2", 1.0))

  private fun recorded(st: Settlement): Sale =
    (SalesEngine.record(shop, cart, SalesEngine.Checkout(settlement = st), "2026-10-19", t0 + 120_000, newId) as SalesEngine.Result.Ok).data.sales.single()

  @Test fun `ثبتِ فروش با ۴۰ دالر - جمع افغانی، ردیفِ ارز با رکورد، رسید هر دو ارز را دارد، JSON رفت‌وبرگشت`() {
    val s = recorded(ok(PayFlow.checkout(PayFlow.Draft(2450.0, fx = tender("40")))))
    assertEquals(2450.0, s.finalTotal, 0.0)
    assertEquals(2450.0, s.paidAmount, 0.0)
    assertEquals("cash", s.paymentMethod)
    assertEquals(281000L, s.tenderedMinor)
    assertEquals(36000L, s.changeMinor)
    val row = s.payments!!.single()
    assertEquals(Pay.FX to 245000L, row.method to row.amountMinor)
    assertEquals(281000L, row.fx!!.afnMinor)
    assertEquals(36000L, FxTillFlow.changeOut(s))
    val back = json.decodeFromString<Sale>(json.encodeToString(s))
    assertEquals(s.payments, back.payments)
    val wire = json.parseToJsonElement(json.encodeToString(s)).jsonObject["payments"]!!.jsonArray[0].jsonObject
    assertEquals("USD", wire["fx"]!!.jsonObject["currency"]!!.jsonPrimitive.content)
    val lines = PayFlow.receiptLines(s)
    assertTrue(lines.toString(), lines.first().first.contains("40.00 USD · 1 USD = 70.25 AFN"))
    assertEquals(2450.0, lines.first().second, 0.0)
    assertEquals("ارز", PayFlow.payLabel(s))
    assertEquals(PayFlow.CASH, PayFlow.payGroup(s))
    //  کشو باز می‌شود: اسکناسِ ارز به دخل می‌رود و پولِ پسِ افغانی از آن
    assertTrue(DrawerRule.opensAfter(DrawerRule.Sale(s.paymentMethod, payments = s.payments!!.map { DrawerRule.Payment(it.kind, it.amountMinor, it.status, it.custody) })))
  }

  @Test fun `شیفت - ارز در روش‌ها هست، «مورد انتظار» ِ افغانی همان دخل؛ هسته و صندوقِ ساده یک عدد`() {
    val sale = recorded(ok(PayFlow.checkout(PayFlow.Draft(2450.0, fx = tender("40")))))
    val open = AuditEntry(id = "sh", type = CashShift.OPEN, createdAt = t0, amount = 1000.0)
    val d = shop.copy(sales = listOf(sale), auditLog = listOf(open))
    val x = ok(PayFlow.report(d, open, null, t0 + 600_000))
    assertTrue(x.skipped.toString(), x.skipped.isEmpty())
    assertEquals(245000L, x.report.methods.getValue(Pay.FX).sales)
    assertEquals(36000L, x.report.moves.cashOut)
    //  ۱۰۰۰ افغانیِ آغاز − ۳۶۰ پولِ پس؛ ارز در «مورد انتظار» ِ افغانی نیست
    assertEquals(mapOf("cash" to 64000L), x.report.expected)
    assertEquals(640.0, CashShift.totalsOf(d, CashShift.Shift(open, null), t0 + 600_000).expected, 1e-9)
    assertEquals(listOf(FxTillFlow.Total("USD", 4000, 281000, 1)), FxTillFlow.totals(d, t0, t0 + 600_000))
    assertEquals("ارز", PayFlow.methodName(Pay.FX))
  }

  @Test fun `RPT-04 - خطِ ارزِ X و Z ِ کشوی نام‌دار فقط فروشِ مهرِ خودش را می‌شمارد و کشوی اصلی بقیه را`() {
    val sale = recorded(ok(PayFlow.checkout(PayFlow.Draft(2450.0, fx = tender("40")))))
    val main = AuditEntry(id = "sh", type = CashShift.OPEN, createdAt = t0, amount = 1000.0)
    val named = AuditEntry(id = "nd", type = CashShift.OPEN, createdAt = t0, amount = 1000.0, drawer = "کشوی دوم")
    val d = shop.copy(sales = listOf(sale.copy(id = "s-main"), sale.copy(id = "s-named", shiftId = "nd")), auditLog = listOf(main, named))
    val to = t0 + 600_000
    val one = listOf(FxTillFlow.Total("USD", 4000, 281000, 1))
    assertEquals(one, FxTillFlow.totals(d, t0, to, CashShift.ownSale(d, "sh", t0, to)))
    assertEquals(one, FxTillFlow.totals(d, t0, to, CashShift.ownSale(d, "nd", t0, to)))
    //  بی مهر (پیش‌فرض، شیفتِ ناشناخته) همان پنجرهٔ پیشین: هر دو فروش
    val both = listOf(FxTillFlow.Total("USD", 8000, 562000, 2))
    assertEquals(both, FxTillFlow.totals(d, t0, to))
    assertEquals(both, FxTillFlow.totals(d, t0, to, CashShift.ownSale(d, "gone", t0, to)))
    //  همان شمارِ هستهٔ شیفت برای همان کشو
    for (sh in listOf(named, main)) {
      val x = ok(PayFlow.report(d, sh, null, to))
      assertTrue(x.skipped.toString(), x.skipped.isEmpty())
      assertEquals(245000L, x.report.methods.getValue(Pay.FX).sales)
    }
  }

  @Test fun `برگشت - سهمِ ارز به افغانیِ نقد پس داده می‌شود، نه به ارز`() {
    val sale = recorded(ok(PayFlow.checkout(PayFlow.Draft(2450.0, fx = tender("40")))))
    val split = (ReturnFlow.split(sale, 2450.0, ReturnFlow.ORIGINAL, "مدیر", null) as ReturnFlow.Outcome.Ok).value
    assertEquals(2450.0, split.cash, 0.0)
    assertEquals(emptyMap<String, Double>(), split.methods)
  }
}
