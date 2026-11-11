package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.keymap.KeyMap
import ir.vil3ntec.tohid.core.keymap.KeyName
import ir.vil3ntec.tohid.core.money.Currency
import ir.vil3ntec.tohid.core.staff.Commission
import ir.vil3ntec.tohid.ui.components.pos.PosAction
import ir.vil3ntec.tohid.ui.components.pos.PosKeys
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  POS-23 / STF-08 — فروشندهٔ هر ردیف در صندوق، ثبت، «فروشِ هر کاربر»، پورسانت و برگهٔ CSV؛ و با تنظیمِ خاموش همه چیز
 *  بیت‌به‌بیت همان امروز (`SaleSellerTest` و `StaffReportTest` دست نخوردند).
 */
class LineSellerFlowTest {

  private var seq = 0
  private fun newId(): String = "i${seq++}"

  private val rice = Product(id = "rice", name = "برنج", category = "خوراکی", unit = "کیلوگرم", salePrice = 100.0)
  private val oil = Product(id = "oil", name = "روغن", category = "روغن", unit = "عدد", salePrice = 200.0)
  private val shop = ShopData(
    products = listOf(rice, oil),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "rice", units = 50.0), WarehouseEntry(id = "w2", productId = "oil", units = 50.0)),
  )
  private val json = Json { encodeDefaults = true }

  private fun record(cart: List<SalesEngine.CartLine>, on: Boolean, seller: String = "احمد", discount: Double = 0.0): ShopData =
    (SalesEngine.record(
      shop, cart, SalesEngine.Checkout(seller = seller, lineSellers = on, discountValue = discount), "2026-11-01", 1_700_000_000_000, ::newId,
    ) as SalesEngine.Result.Ok).data

  private val cart = listOf(SalesEngine.CartLine("rice", 1.0), SalesEngine.CartLine("oil", 1.0, seller = " زهرا "))

  @Test
  fun `ثبت فروشندهٔ ردیف را فقط با تنظیمِ روشن و فقط وقتی با فاکتور فرق دارد می‌نویسد`() {
    val on = record(cart, on = true).saleItems
    assertEquals(listOf(null, "زهرا"), on.map { it.seller })
    //  همان فروشندهٔ فاکتور ⇒ نوشته نمی‌شود
    val same = record(listOf(SalesEngine.CartLine("rice", 1.0, seller = "احمد")), on = true).saleItems
    assertNull(same.single().seller)
    //  خاموش ⇒ هیچ قلمی فروشنده ندارد و JSON ِ قلم کلیدِ «seller» ندارد (بیت‌به‌بیت پیشین)
    val off = record(cart, on = false).saleItems
    assertTrue(off.all { it.seller == null })
    off.forEach { assertFalse(json.encodeToString(SaleItem.serializer(), it).contains("\"seller\"")) }
    assertEquals(
      json.encodeToString(SaleItem.serializer(), off[1]),
      json.encodeToString(SaleItem.serializer(), record(cart.map { it.copy(seller = null) }, on = false).saleItems[1].copy(id = off[1].id, saleId = off[1].saleId)),
    )
    //  دفترِ همگام‌شده (JSON) فروشندهٔ ردیف را نگه می‌دارد و دوباره می‌خواند
    val item = record(cart, on = true).saleItems[1]
    assertEquals("زهرا", json.decodeFromString(SaleItem.serializer(), json.encodeToString(SaleItem.serializer(), item)).seller)
  }

  @Test
  fun `پیش‌نویس و فاکتورِ نگه‌داشته فروشندهٔ ردیف را برمی‌گردانند؛ پیش‌نویسِ بی آن همان پیشین است`() {
    val draft = SaleDraft.of("k", cart, null, 1L)
    assertEquals(listOf(null, " زهرا "), SaleDraftCodec.decode(SaleDraftCodec.encode(draft))!!.cart().map { it.seller })
    val plain = SaleDraftCodec.encode(SaleDraft.of("k", cart.map { it.copy(seller = null) }, null, 1L))
    assertFalse(plain.contains("\"seller\""))
  }

  @Test
  fun `مجوز — نامِ دیگری فقط صاحب و مدیر؛ خودم همیشه`() {
    val c = listOf(SalesEngine.CartLine("rice", 1.0), SalesEngine.CartLine("oil", 1.0))
    //  شاگرد نامِ زهرا را نمی‌گذارد: سبد همان شیء می‌ماند
    assertSame(c, CartSellers.assign(c, setOf("oil"), "زهرا", invoice = "احمد", me = "احمد", othersAllowed = false))
    //  صاحب/مدیر می‌گذارد؛ ردیف‌های دیگر دست نمی‌خورند
    val set = CartSellers.assign(c, setOf("oil"), "زهرا", invoice = "احمد", me = "احمد", othersAllowed = true)
    assertEquals(listOf(null, "زهرا"), set.map { it.seller })
    //  برگشت به خودم (= فروشندهٔ فاکتور) همیشه آزاد است و فیلد پاک می‌شود
    assertEquals(listOf(null, null), CartSellers.assign(set, setOf("oil"), "احمد", invoice = "احمد", me = "احمد", othersAllowed = false).map { it.seller })
    assertEquals(listOf(null, null), CartSellers.assign(set, setOf("oil"), null, invoice = "احمد", me = "احمد", othersAllowed = false).map { it.seller })
    assertTrue(CartSellers.any(set, "احمد"))
    assertFalse(CartSellers.any(c, "احمد"))
  }

  private fun sale(id: String, seller: String, total: Double, discount: Double = 0.0, status: String = "completed", date: String = "2026-11-02") =
    Sale(id = id, seller = seller, total = total + discount, discount = discount, finalTotal = total, date = date, status = status)

  private fun item(id: String, saleId: String, product: String, total: Double, seller: String? = null) =
    SaleItem(id = id, saleId = saleId, productId = product, quantity = 1.0, totalPrice = total, seller = seller)

  /** فاکتورِ ۲۷۰ ِ احمد (برنجِ ۱۰۰ ِ خودش و روغنِ ۲۰۰ ِ زهرا، تخفیفِ ۳۰)، فاکتورِ ۱۰۰ ِ زهرا (برنجِ احمد) و لغوشده */
  private val ledger = shop.copy(
    sales = listOf(sale("a", "احمد", 270.0, discount = 30.0), sale("b", "زهرا", 100.0), sale("c", "احمد", 999.0, status = "cancelled")),
    saleItems = listOf(
      item("a1", "a", "rice", 100.0), item("a2", "a", "oil", 200.0, seller = "زهرا"),
      item("b1", "b", "rice", 100.0, seller = "احمد"),
      item("c1", "c", "oil", 999.0, seller = "زهرا"),
    ),
    saleReturns = listOf(
      SaleReturn(id = "r1", saleId = "a", saleItemId = "a2", productId = "oil", amount = 90.0, date = "2026-11-03"),
      SaleReturn(id = "r2", saleId = "c", saleItemId = "c1", productId = "oil", amount = 999.0, date = "2026-11-03"),
    ),
  )

  @Test
  fun `فروشِ هر کاربر — ردیف به نامِ فروشندهٔ خودش، برگشت از فروشندهٔ ردیفِ اصل، لغوشده بیرون`() {
    val rows = ReportEngine.bySeller(ledger, "2026-11-01", "2026-11-30", lineSeller = true)
    val z = rows.first { it.seller == "زهرا" }
    val a = rows.first { it.seller == "احمد" }
    //  زهرا: روغن ۲۰۰ − سهمِ تخفیف ۲۰ = ۱۸۰ + کلِ فاکتورِ خودش جز برنجِ احمد (۰) ⇒ ۱۸۰؛ احمد: ۹۰ + ۱۰۰ = ۱۹۰
    assertEquals(180.0, z.net, 1e-9)
    assertEquals(190.0, a.net, 1e-9)
    assertEquals(370.0, rows.sumOf { it.net }, 1e-9)
    assertEquals(30.0, rows.sumOf { it.discount }, 1e-9)
    assertEquals(listOf(1, 2), listOf(z.count, a.count))
    //  برگشتِ روغن از زهرا، نه از احمد (فروشندهٔ فاکتور)؛ برگشتِ فاکتورِ لغوشده بیرون
    assertEquals(90.0, z.returns, 1e-9)
    assertEquals(0.0, a.returns, 1e-9)
    //  خاموش: همان پیشین — به نامِ فروشندهٔ فاکتور و بی برگشت
    val off = ReportEngine.bySeller(ledger, "2026-11-01", "2026-11-30")
    assertEquals(listOf(ReportEngine.SellerStat("احمد", 1, 270.0, 30.0), ReportEngine.SellerStat("زهرا", 1, 100.0, 0.0)), off)
  }

  @Test
  fun `برگهٔ CSV ِ sellers — خاموش همان برگه، روشن با ستونِ برگشتی`() {
    val off = ReportCsv.of("sellers", ledger, "2026-11-01", "2026-11-30", "2026-11-30").rows
    assertEquals(listOf("فروشنده", "تعدادِ فاکتور", "فروشِ خالص", "تخفیف"), off.first())
    assertEquals(listOf("احمد", "1", "270", "30"), off[1])
    val on = ReportCsv.of("sellers", ledger, "2026-11-01", "2026-11-30", "2026-11-30", lineSeller = true).rows
    assertEquals(listOf("فروشنده", "تعدادِ فاکتور", "فروشِ خالص", "تخفیف", "برگشتی"), on.first())
    assertEquals(listOf("احمد", "2", "190", "10", "0"), on[1])
    assertEquals(listOf("زهرا", "1", "180", "20", "90"), on[2])
    //  صفحه برگهٔ امروز را می‌گیرد و فقط با تنظیمِ روشن برگهٔ `sellers` را عوض می‌کند
    val base = ReportCsv.of("sellers", ledger, "2026-11-01", "2026-11-30", "2026-11-30")
    assertEquals(on, ReportCsv.lineSellers(base, "sellers", ledger, "2026-11-01", "2026-11-30", true).rows)
    assertSame(base, ReportCsv.lineSellers(base, "sellers", ledger, "2026-11-01", "2026-11-30", false))
    val sales = ReportCsv.of("sales", ledger, "2026-11-01", "2026-11-30", "2026-11-30")
    assertSame(sales, ReportCsv.lineSellers(sales, "sales", ledger, "2026-11-01", "2026-11-30", true))
  }

  @Test
  fun `پورسانت — هر ردیف به نامِ فروشندهٔ خودش و برگشت از همان؛ خاموش همان پیشین`() {
    val on = StaffReport.commissionLines(ledger, "2026-11-01", "2026-11-30", lineSeller = true)
    val r = Commission.compute(on, Commission.Plan(1_000), Currency.AFN)
    val base = r.sellers.associate { it.sellerId to it.base.minor }
    assertEquals(19_000L, base.getValue("احمد"))           // ۹۰ + ۱۰۰
    assertEquals(9_000L, base.getValue("زهرا"))            // ۱۸۰ − ۹۰
    assertEquals(listOf(1_900L, 900L), r.sellers.map { it.commission.minor })
    //  خاموش: همان ردیف‌های پیشین (فروشندهٔ فاکتور برای همه) حتی با `SaleItem.seller` در دفتر
    val off = StaffReport.commissionLines(ledger, "2026-11-01", "2026-11-30")
    assertEquals(StaffReport.commissionLines(ledger.copy(saleItems = ledger.saleItems.map { it.copy(seller = null) }), "2026-11-01", "2026-11-30"), off)
    assertEquals(setOf("احمد", "زهرا"), off.map { it.sellerId }.toSet())
    val offBase = Commission.compute(off, Commission.Plan(1_000), Currency.AFN).sellers.associate { it.sellerId to it.base.minor }
    assertEquals(18_000L, offBase.getValue("احمد"))        // ۲۷۰ − ۹۰
    assertEquals(10_000L, offBase.getValue("زهرا"))
  }

  @Test
  fun `کلید — Shift+F2 فقط با تنظیمِ روشن فروشندهٔ ردیف است؛ خاموش همان «مشتری»`() {
    assertEquals("Shift+F2", KeyMap.SALE_LINE_SELLER.hint())
    assertTrue(PosKeys.lineSeller(KeyName.F2, primary = false, shift = true, alt = false, enabled = true))
    assertFalse(PosKeys.lineSeller(KeyName.F2, primary = false, shift = true, alt = false, enabled = false))
    assertFalse(PosKeys.lineSeller(KeyName.F2, primary = false, shift = false, alt = false, enabled = true))
    assertFalse(PosKeys.lineSeller(KeyName.F2, primary = true, shift = true, alt = false, enabled = true))
    assertFalse(PosKeys.lineSeller(KeyName.F2, primary = false, shift = true, alt = true, enabled = true))
    //  خاموش ⇒ همان نگاشتِ امروز: F2 (و Shift+F2) ⇒ «مشتری»
    assertEquals(PosAction.CUSTOMER, PosKeys.screen(KeyName.F2, primary = false))
  }

  @Test
  fun `ریزِ ردیفِ «کاربر» در گزارش‌ها همان سهم‌های جدول است — روشن به فروشندهٔ ردیف، خاموش همان پیشین`() {
    val raw = Json.parseToJsonElement(ir.vil3ntec.tohid.core.report.ReportCoreVectorsTest.vectorFile().readText())
      .jsonObject.getValue("ledgers").jsonObject.getValue("lineSeller").jsonObject
    val l = ir.vil3ntec.tohid.core.report.ReportLedger.parse(raw)
    val core = ir.vil3ntec.tohid.core.report.ReportCore
    for (on in listOf(true, false)) {
      val users = core.sales(l, "2026-10-01", "2026-10-14", lineSeller = on).user
      users.forEach { row ->
        val list = core.invoicesOf(l, "2026-10-01", "2026-10-14", "user", row.key, lineSeller = on)
        assertEquals("${row.key} on=$on", row.sales, list.sumOf { it.sales })
        assertEquals("${row.key} on=$on", row.invoices, list.size)
      }
    }
    //  روشن: زهرا ۲۰۰ − ۲۰ (روغنِ فاکتورِ احمد) + ۵۰٫۰۱؛ خاموش: فقط فاکتورِ خودش
    assertEquals(23_001L, core.sales(l, "2026-10-01", "2026-10-14", lineSeller = true).user.first { it.key == "زهرا" }.sales)
    assertEquals(10_001L, core.sales(l, "2026-10-01", "2026-10-14").user.first { it.key == "زهرا" }.sales)
  }

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/$rel") }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  @Test
  fun `صندوقِ کامپیوتر و گوشی همان برگه را پشتِ همان تنظیم دارند`() {
    val desk = src("ui/screens/DeskSale.kt")
    assertTrue(desk.contains("val lineSellerOn = remember { ir.vil3ntec.tohid.data.LineSellerPref.on(context) }"))
    assertTrue(desk.contains("lineSellers = lineSellerOn,"))
    val key = desk.indexOf("if (PosKeys.lineSeller(keyName(e.key), e.isPrimaryModifier, e.isShiftPressed, e.isAltPressed, lineSellerOn)) {")
    assertTrue(key >= 0)
    assertTrue("Shift+F2 پیش از F2 ِ «مشتری»", key < desk.indexOf("when (PosKeys.screen(keyName(e.key), e.isPrimaryModifier)) {"))
    assertTrue(desk.contains("LineSellerSheet(d, cart, seller, lineSellerFor"))
    assertTrue(desk.contains("LineSellerButton(cart.isNotEmpty())"))
    val phone = src("ui/v3/sales/cart/PhoneTillTools.kt")
    assertTrue(phone.contains("LineSellerButton(lines.isNotEmpty()) { sellerOpen = true }"))
    assertTrue(phone.contains("LineSellerSheet(data, lines, me, preselect = null"))
    //  POS-37 همان `copy` مالیاتِ صندوق را هم می‌برد (`tax = taxConfig`)؛ برگهٔ فروشنده همچنان به پرداخت می‌رسد
    assertTrue(src("ui/v3/sales/checkout/CheckoutRoute.kt").contains(".copy(lineSellers = lineSellers, tax = taxConfig)"))
    //  برگه فقط با تنظیمِ روشن کشیده می‌شود و نوشتن فقط از `CartSellers.assign` (مجوز همان‌جا)
    val sheet = src("ui/components/pos/LineSellerAndroid.kt")
    assertTrue(sheet.contains("if (!show || !LineSellerTill.on()) return"))
    assertTrue(sheet.contains("CartSellers.assign(cart, picked, who, invoice, invoice, others)"))
    assertTrue(src("data/LineSellerPrefAndroid.kt").contains("getBoolean(KEY, false)"))
  }
}
