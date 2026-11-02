package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import ir.vil3ntec.tohid.sync.v3.V3Convert
import ir.vil3ntec.tohid.sync.v3.V3Schema
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PUR-11 — خرید به ارزِ دیگر (`PurchaseFx`، `PurchaseBook.record(fx = …)`): تبدیل و گردکردنِ صریح، درستیِ نرخ، بهای
 *  افغانی در همان میانگینِ موزون، و ماندنِ اصلِ ارزیِ سند. تنظیمِ خاموش ⇒ همان امروز در `PurchaseFxOffTest`.
 */
class PurchaseFxTest {

  private var seq = 0
  private val newId: () -> String = { "id${++seq}" }
  private val today = "2026-11-02"
  private val usd = PurchaseFx.Rate("USD", 70_250_000) // ۱ دلار = ۷۰٫۲۵ افغانی

  private val base = ShopData(
    products = listOf(
      Product(id = "p1", name = "روغن", salePrice = 1200.0, purchasePrice = 800.0, unit = "عدد"),
      Product(id = "p2", name = "برنج", salePrice = 300.0, purchasePrice = 200.0, unit = "کیلو"),
    ),
    suppliers = listOf(Supplier(id = "s1", name = "واردکننده")),
  )

  private fun ok(r: PurchaseBook.Result): PurchaseBook.Result.Ok = r as PurchaseBook.Result.Ok

  /* ── تبدیل و گردکردن ─────────────────────────────────────────── */

  @Test fun `فیِ ارزی ضربِ نرخ به صدمِ افغانی گرد می‌شود و نیمه به بالا می‌رود`() {
    assertEquals(7025L, PurchaseFx.toAfnMinor(100, usd.rateMicros)) //  ۱ دلار دقیق
    assertEquals(87813L, PurchaseFx.toAfnMinor(1250, usd.rateMicros)) //  ۱۲٫۵۰ × ۷۰٫۲۵ = ۸۷۸٫۱۲۵ ⇒ ۸۷۸٫۱۳
    assertEquals(1L, PurchaseFx.toAfnMinor(1, 500_000)) //  نیمه ⇒ بالا
    assertEquals(0L, PurchaseFx.toAfnMinor(1, 499_999))
    assertEquals(2L, PurchaseFx.toAfnMinor(1, 1_500_000))
    assertEquals(0L, PurchaseFx.toAfnMinor(0, usd.rateMicros))
    //  ارزِ کم‌بها: ۱٬۰۰۰٬۰۰۰ ریال با ۰٫۰۰۱۷ ⇒ ۱۷۰۰ افغانی، بی هیچ Double
    assertEquals(170_000L, PurchaseFx.toAfnMinor(100_000_000, 1_700))
  }

  @Test fun `حاصلِ بیرون از سقفِ امن تبدیل نمی‌شود و فاکتور رد می‌شود`() {
    assertNull(PurchaseFx.toAfnMinor(PurchaseFx.MAX_PRICE_MINOR, PurchaseFx.MAX_RATE_MICROS))
    val big = PurchaseFx.Rate("USD", PurchaseFx.MAX_RATE_MICROS)
    val r = PurchaseBook.record(base, "s1", listOf(PurchaseBook.Line("p1", 1.0, 0.0, fxPriceMinor = PurchaseFx.MAX_PRICE_MINOR)), 0.0, today, "", today, 1, newId, fx = big)
    assertEquals(PurchaseBook.Result.Failed("ردیفِ 1: ${PurchaseFx.TOO_LARGE}"), r)
  }

  @Test fun `جمعِ ارزیِ ردیف مقدار ضربِ فی است با همان گردکردن`() {
    assertEquals(12_500L, PurchaseFx.lineTotalMinor(10.0, 1250))
    assertEquals(833L, PurchaseFx.lineTotalMinor(2.5, 333)) //  ۲٫۵ × ۳٫۳۳ = ۸٫۳۲۵ ⇒ ۸٫۳۳
    assertEquals(0L, PurchaseFx.lineTotalMinor(3.0, 0))
  }

  /* ── درستیِ نرخ و ارز ────────────────────────────────────────── */

  @Test fun `نرخ فقط عددِ مثبت تا شش رقمِ اعشار و تا سقف است و هیچ گردکردنِ پنهانی ندارد`() {
    assertEquals(70_250_000L, PurchaseFx.parseRate("70.25"))
    assertEquals(70_250_000L, PurchaseFx.parseRate(" 070.250000 "))
    assertEquals(1_123_456L, PurchaseFx.parseRate("1.1234560"))
    assertEquals(1L, PurchaseFx.parseRate("0.000001"))
    assertEquals(PurchaseFx.MAX_RATE_MICROS, PurchaseFx.parseRate("1000000"))
    for (bad in listOf("", "0", "0.0000001", "1.0000001", "-1", "1000000.000001", "1e3", "abc", "1,5", "12345678901234567890")) {
      assertNull("«$bad» نباید پذیرفته شود", PurchaseFx.parseRate(bad))
    }
    assertEquals(1250L, PurchaseFx.parseMinor("12.5"))
    assertEquals(0L, PurchaseFx.parseMinor("0"))
    assertNull(PurchaseFx.parseMinor("12.345"))
    assertNull(PurchaseFx.parseMinor("-3"))
  }

  @Test fun `ارز سه حرفِ لاتین و نه افغانی، و نرخِ بیرون از بازه رد می‌شود`() {
    assertEquals("USD", PurchaseFx.code(" usd "))
    assertNull(PurchaseFx.code("AFN"))
    assertNull(PurchaseFx.code("US"))
    assertNull(PurchaseFx.code("دلار"))
    assertNull(PurchaseFx.check(usd))
    assertEquals(PurchaseFx.BAD_CURRENCY, PurchaseFx.check(PurchaseFx.Rate("usd", 1)))
    assertEquals(PurchaseFx.BAD_CURRENCY, PurchaseFx.check(PurchaseFx.Rate("AFN", 1)))
    assertEquals(PurchaseFx.BAD_RATE, PurchaseFx.check(PurchaseFx.Rate("USD", 0)))
    assertEquals(PurchaseFx.BAD_RATE, PurchaseFx.check(PurchaseFx.Rate("USD", -5)))
    assertEquals(PurchaseFx.BAD_RATE, PurchaseFx.check(PurchaseFx.Rate("USD", PurchaseFx.MAX_RATE_MICROS + 1)))
    //  نرخِ بد یا فیِ ارزیِ نبود ⇒ کلِ فاکتور رد، دفتر دست‌نخورده
    val line = PurchaseBook.Line("p1", 1.0, 0.0, fxPriceMinor = 100)
    assertEquals(PurchaseBook.Result.Failed(PurchaseFx.BAD_RATE), PurchaseBook.record(base, "s1", listOf(line), 0.0, today, "", today, 1, newId, fx = PurchaseFx.Rate("USD", 0)))
    val missing = PurchaseBook.record(base, "s1", listOf(line, PurchaseBook.Line("p2", 1.0, 500.0)), 0.0, today, "", today, 1, newId, fx = usd)
    assertEquals(PurchaseBook.Result.Failed("ردیفِ 2: ${PurchaseFx.BAD_PRICE}"), missing)
    assertEquals(0, seq)
  }

  /* ── بها در همان میانگینِ موزون ──────────────────────────────── */

  @Test fun `بهای افغانیِ تبدیل‌شده همان راهِ خریدِ افغانی را می‌رود و میانگینِ موزون همان است`() {
    //  پیشینه: ۱۰ عدد به ۸۰۰ افغانی (افغانی)، سپس ۱۰ عدد به ۱۲٫۵۰ دلار با نرخِ ۷۰٫۲۵
    val first = ok(PurchaseBook.record(base, "s1", listOf(PurchaseBook.Line("p1", 10.0, 800.0)), 0.0, today, "", today, 1, newId)).data
    val seqAt = seq
    val fx = ok(PurchaseBook.record(first, "s1", listOf(PurchaseBook.Line("p1", 10.0, 0.0, fxPriceMinor = 1250)), 1000.0, today, "ارزی", today, 2, newId, fx = usd)).data
    seq = seqAt
    val afn = ok(PurchaseBook.record(first, "s1", listOf(PurchaseBook.Line("p1", 10.0, 878.13)), 1000.0, today, "ارزی", today, 2, newId)).data

    //  انبار، تاریخچهٔ قیمت، حرکت و سابقه یکی‌اند؛ فقط ردیفِ خرید اصلِ ارزی را هم دارد
    assertEquals(afn.warehouseEntries, fx.warehouseEntries)
    assertEquals(afn.priceHistory, fx.priceHistory)
    assertEquals(afn.stockMovements, fx.stockMovements)
    assertEquals(afn.auditLog, fx.auditLog)
    assertEquals(afn.products, fx.products)
    assertEquals(878.13, fx.products.first { it.id == "p1" }.purchasePrice, 0.0)
    assertEquals(afn.purchases.map { it.copy(fxCurrency = null, fxRateMicros = null, fxPriceMinor = null, fxTotalMinor = null) }, fx.purchases.map { it.copy(fxCurrency = null, fxRateMicros = null, fxPriceMinor = null, fxTotalMinor = null) })

    val viewFx = StockLedger.productView(StockLedger.replay(fx), "p1")
    assertEquals(StockLedger.productView(StockLedger.replay(afn), "p1"), viewFx)
    assertEquals(20_000L, viewFx.qtyMilli)
    assertEquals(1_678_130L, viewFx.valueMinor) //  ۸۰۰۰ + ۸۷۸۱٫۳۰
    assertEquals(83_907L, viewFx.unitCostMinor) //  ۸۳۹٫۰۶۵ ⇒ ۸۳۹٫۰۷

    //  بدهیِ تأمین‌کننده افغانیِ روزِ ثبت: ۸۰۰۰ + round(۱۰ × ۸۷۸٫۱۳) − ۱۰۰۰
    assertEquals(8000.0 + 8781.0 - 1000.0, ShopStore.supplierDebt(fx, "s1"), 0.0)
  }

  /* ── اصلِ سند می‌ماند ────────────────────────────────────────── */

  @Test fun `ارز و نرخ و فی و جمعِ ارزی روی سند می‌مانند و در دفتر و همگام‌سازی گم نمی‌شوند`() {
    val r = ok(PurchaseBook.record(
      base, "s1",
      listOf(PurchaseBook.Line("p1", 10.0, 0.0, fxPriceMinor = 1250), PurchaseBook.Line("", 0.0, 0.0), PurchaseBook.Line("p2", 2.5, 0.0, fxPriceMinor = 333)),
      0.0, today, "", today, 1, newId, fx = usd,
    ))
    val rows = r.data.purchases.filter { it.invoiceNo == r.invoiceNo }
    assertEquals(listOf("p1", "p2"), rows.map { it.productId })
    assertTrue(rows.all { it.fxCurrency == "USD" && it.fxRateMicros == 70_250_000L })
    assertEquals(listOf(1250L, 333L), rows.map { it.fxPriceMinor })
    assertEquals(listOf(12_500L, 833L), rows.map { it.fxTotalMinor })
    //  افغانی: ۳٫۳۳ × ۷۰٫۲۵ = ۲۳۳٫۹۳۲۵ ⇒ ۲۳۳٫۹۳؛ جمع = round(۲٫۵ × ۲۳۳٫۹۳)
    assertEquals(listOf(878.13, 233.93), rows.map { it.purchasePrice })
    assertEquals(listOf(8781.0, 585.0), rows.map { it.totalAmount })

    val inv = PurchaseBook.invoices(r.data).single()
    assertEquals(PurchaseFx.Summary("USD", 70_250_000L, 13_333L), inv.fx)
    assertEquals(listOf("", "ارز", "USD", "نرخ", "70.25", "133.33"), PurchaseBook.invoiceSheet(r.data, inv).rows.last())

    //  دیسک/Sync v1: رفت و برگشت بی کم و زیاد
    assertEquals(r.data.purchases, LedgerDiff.fromJson(LedgerDiff.toJson(r.data)).purchases)
    //  Sync v3: فیلدهای `fx…` همان عددِ صحیح می‌روند و برمی‌گردند (نه تقسیم بر ۱۰۰)
    val op = LedgerDiff.diff(LedgerDiff.toJson(base), LedgerDiff.toJson(r.data), now = 1).first { it.table == "purchases" }
    val v3 = V3Convert.fromLegacyOp(op, "hlc").ops.toString()
    assertTrue(v3, v3.contains("\"fxPriceMinor\":1250") && v3.contains("\"fxRateMicros\":70250000") && v3.contains("\"fxCurrency\":\"USD\""))
    val back = V3Convert.dataToLegacy(V3Schema.specOf("purchases")!!, buildJsonObject {
      put("fxPriceMinor", 1250); put("fxTotalMinor", 12_500); put("fxRateMicros", 70_250_000); put("purchasePriceMinor", 87_813)
    })
    assertEquals(JsonPrimitive(1250), back["fxPriceMinor"])
    assertEquals(JsonPrimitive(70_250_000), back["fxRateMicros"])
    assertEquals(JsonPrimitive(878.13), back["purchasePrice"])
  }

  @Test fun `فاکتورِ افغانی هیچ فیلدِ ارزی نمی‌گیرد و خلاصهٔ ارزی ندارد`() {
    val r = ok(PurchaseBook.record(base, "s1", listOf(PurchaseBook.Line("p1", 2.0, 800.0, fxPriceMinor = 999)), 0.0, today, "", today, 1, newId))
    val p = r.data.purchases.single()
    assertNull(p.fxCurrency); assertNull(p.fxRateMicros); assertNull(p.fxPriceMinor); assertNull(p.fxTotalMinor)
    assertEquals(800.0, p.purchasePrice, 0.0)
    assertNull(PurchaseBook.invoices(r.data).single().fx)
    assertTrue((LedgerDiff.toJson(r.data)["purchases"] as kotlinx.serialization.json.JsonArray).toString().let { !it.contains("fx") })
    //  دو نرخ در یک فهرست ⇒ خلاصهٔ یکتا نیست
    val a = PurchaseFx.stamp(p, usd, 100)
    assertNull(PurchaseFx.summary(listOf(a, PurchaseFx.stamp(p.copy(id = "x"), PurchaseFx.Rate("USD", 70_000_000), 100))))
    assertEquals(PurchaseFx.Summary("USD", 70_250_000L, 200L), PurchaseFx.summary(listOf(a)))
  }

  @Test fun `نرخ و مبلغ برای نمایش بی صفرِ اضافه نوشته می‌شوند`() {
    assertEquals("70.25", PurchaseFx.rateText(70_250_000))
    assertEquals("0.0017", PurchaseFx.rateText(1_700))
    assertEquals("12.5", PurchaseFx.minorText(1250))
    assertEquals("125", PurchaseFx.minorText(12_500))
  }
}
