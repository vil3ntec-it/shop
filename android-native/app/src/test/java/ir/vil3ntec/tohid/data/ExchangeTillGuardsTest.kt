package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrLedger
import ir.vil3ntec.tohid.core.sale.CheckoutRules
import ir.vil3ntec.tohid.core.sale.SaleTax
import ir.vil3ntec.tohid.core.stock.ExpiryGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  فروشِ «تعویض» در برگشت (RET-05) همان سدهای صندوقِ همین دستگاه را دارد (`TillGuards`): «فروخته نشود»، S22 «منقضی فقط با
 *  تأییدِ مدیر» و POS-24 قاعده‌های پیش از پایانِ فاکتور — هم گوشیِ نسخهٔ ۳ (`ReturnFlowSheet`) و هم کامپیوتر (`ReturnsV2`).
 *  ⛔ روی `claude/batch-56` (0d06aa8d5) هر دو صفحه `SalesEngine.Checkout(seller = me)` ِ خام به `ReturnFlow.exchange` می‌دادند ⇒
 *  کالای منقضی بی هیچ تأییدی و فاکتورِ صفر یا بی مشتری از راهِ «تعویض» ثبت می‌شد (موتور با `expiryGate = false` نمی‌سنجد).
 *  پنجرهٔ واقعیِ Compose: `ExchangeGuardsDeskUiTest` و `ExchangeGuardsPhoneUiTest` (desktop).
 */
class ExchangeTillGuardsTest {

  private var seq = 0
  private fun newId() = "ex-${seq++}"
  private val today = "2026-11-02"

  private val shoe = Product(id = "shoe", name = "کفش", unit = "جفت", salePrice = 1000.0)
  private val milk = Product(id = "milk", name = "شیر", unit = "عدد", purchasePrice = 50.0, salePrice = 100.0)
  private val gift = Product(id = "gift", name = "هدیه", unit = "عدد", salePrice = 0.0)
  private val d0 = ShopData(
    products = listOf(shoe, milk, gift),
    warehouseEntries = listOf(
      WarehouseEntry(id = "w-shoe", productId = "shoe", units = 5.0),
      WarehouseEntry(id = "w-gift", productId = "gift", units = 5.0),
      //  ۲ منقضی و ۱۰ سالم (همان ریختِ `ScenarioS22Test`)
      WarehouseEntry(id = "old", productId = "milk", units = 2.0, expiryDate = "2026-10-30"),
      WarehouseEntry(id = "far", productId = "milk", units = 10.0, expiryDate = "2026-12-12"),
    ),
    debtors = listOf(Debtor(id = "d1", name = "احمد"), Debtor(id = "d2", name = "کریم", mergedInto = "d1")),
  )

  /** فاکتورِ اصل: یک کفش، با مشتریِ [debtor] یا بی مشتری */
  private fun sold(debtor: String?): Pair<ShopData, Sale> {
    val r = SalesEngine.record(d0, listOf(SalesEngine.CartLine("shoe", 1.0)), SalesEngine.Checkout(seller = "صندوقدار", debtorId = debtor), "2026-11-01", 1L, ::newId)
    val d = (r as SalesEngine.Result.Ok).data
    return d to d.sales.single()
  }

  private fun req(d: ShopData, sale: Sale) = ReturnFlow.Request(
    docId = newId(), saleId = sale.id, picks = mapOf(d.saleItems.single { it.saleId == sale.id }.id to ReturnFlow.Pick(1.0)),
    reason = "اندازه", mode = ReturnFlow.ORIGINAL, by = "صندوقدار",
  )

  private fun exchange(d: ShopData, sale: Sale, cart: List<SalesEngine.CartLine>, c: SalesEngine.Checkout) =
    ReturnFlow.exchange(d, req(d, sale), cart, c, newId(), today, 2L, ::newId)

  private fun manager(need: Map<String, Long>) = ExpiryGate.Approval("مدیر", "manager", need, "مشتری خواست")

  private val eleven = listOf(SalesEngine.CartLine("milk", 11.0))

  @Test
  fun `تعویض با «منقضی فقط با مجوز» بی تأییدِ مدیر ثبت نمی‌شود و با تأیید ثبت و در سابقه می‌ماند`() {
    val (d, sale) = sold(null)
    //  همان چیزی که صفحه‌ها پیش از این می‌دادند: تعویضِ کالای منقضی بی هیچ پرسشی ثبت می‌شد (دورزدنِ S22)
    assertTrue("مستند: `Checkout` ِ خام", exchange(d, sale, eleven, SalesEngine.Checkout(seller = "صندوقدار")) is ReturnFlow.Outcome.Ok)

    val gate = TillGuards(expiryGate = true)
    val c = gate.exchange(d, "صندوقدار", sale.debtorId, null)
    assertTrue(c.expiryGate && !c.blockExpired)
    val need = ReturnFlow.exchangeExpiry(d, req(d, sale), eleven, c, today, 2L, ::newId)
    assertEquals(mapOf("milk" to 1_000L), need)
    val refused = exchange(d, sale, eleven, c)
    assertTrue(refused is ReturnFlow.Outcome.Bad)
    assertEquals("همان پیامِ موتور", TillGuards.expiredRefusal(d, need!!), (refused as ReturnFlow.Outcome.Bad).message)

    //  تأییدِ مدیر (برگهٔ PIN) ⇒ همان کالا و تا همان مقدار؛ ثبت با نام و دلیل در سابقه
    val approved = gate.exchange(d, "صندوقدار", sale.debtorId, manager(need))
    assertNull(ReturnFlow.exchangeExpiry(d, req(d, sale), eleven, approved, today, 2L, ::newId))
    val ok = exchange(d, sale, eleven, approved) as ReturnFlow.Outcome.Ok
    assertEquals("مدیر", ok.value.data.auditLog.single { it.type == "expired_sale" }.approvedBy)
    //  سبدِ بزرگ‌تر یا تأییدِ شاگرد نمی‌پوشاند
    val twelve = listOf(SalesEngine.CartLine("milk", 12.0))
    assertEquals(mapOf("milk" to 2_000L), ReturnFlow.exchangeExpiry(d, req(d, sale), twelve, approved, today, 2L, ::newId))
    assertTrue(exchange(d, sale, twelve, approved) is ReturnFlow.Outcome.Bad)
    val staff = gate.exchange(d, "صندوقدار", null, ExpiryGate.Approval("رحیم", "staff", need, "خواست"))
    assertTrue(exchange(d, sale, eleven, staff) is ReturnFlow.Outcome.Bad)
    //  بی منقضی ⇒ بی پرسش
    assertNull(ReturnFlow.exchangeExpiry(d, req(d, sale), listOf(SalesEngine.CartLine("milk", 10.0)), c, today, 2L, ::newId))
  }

  @Test
  fun `پیامِ ردِ تعویض همان متنِ صندوق است و «فروخته نشود» همیشه می‌بندد`() {
    val (d, sale) = sold(null)
    val till = SalesEngine.record(d, eleven, SalesEngine.Checkout(expiryGate = true), today, 2L, ::newId)
    assertEquals(TillGuards.expiredRefusal(d, mapOf("milk" to 1_000L)), (till as SalesEngine.Result.Failed).message)
    //  `ExpiryPref.gate` با «فروخته نشود» خاموش است؛ اگر هم هر دو بیاید، بستن بر تأیید پیش است
    val block = TillGuards(blockExpired = true, expiryGate = true).exchange(d, "", null, manager(mapOf("milk" to 1_000L)))
    assertNull(ReturnFlow.exchangeExpiry(d, req(d, sale), eleven, block, today, 2L, ::newId))
    assertTrue(exchange(d, sale, eleven, block) is ReturnFlow.Outcome.Bad)
    assertTrue("سالم آزاد", exchange(d, sale, listOf(SalesEngine.CartLine("milk", 10.0)), block) is ReturnFlow.Outcome.Ok)
  }

  @Test
  fun `قاعدهٔ «فاکتورِ صفر» تعویض را هم می‌بندد ولی تعویضِ هم‌بها را نه`() {
    val (d, sale) = sold(null)
    val zero = TillGuards(rules = CheckoutRules.of(null, true))
    val free = exchange(d, sale, listOf(SalesEngine.CartLine("gift", 1.0)), zero.exchange(d, "", null, null))
    assertEquals(StrLedger.zeroTotalRule, (free as ReturnFlow.Outcome.Bad).message)
    assertTrue("خاموش ⇒ همان امروز", exchange(d, sale, listOf(SalesEngine.CartLine("gift", 1.0)), TillGuards.OFF.exchange(d, "", null, null)) is ReturnFlow.Outcome.Ok)
    //  برگشت کلِ بها را می‌پوشاند (دریافتی ۰) ولی خودِ فاکتورِ تعویض ۱۰۰۰ است ⇒ قاعده بی‌جا نمی‌گیرد
    val even = exchange(d, sale, listOf(SalesEngine.CartLine("shoe", 1.0)), zero.exchange(d, "", null, null)) as ReturnFlow.Outcome.Ok
    assertEquals(0.0, even.value.due, 0.0)
  }

  @Test
  fun `قاعدهٔ «مشتری از مبلغِ X» با مشتریِ برگشت سنجیده می‌شود`() {
    val everyone = TillGuards(rules = CheckoutRules.of(0L, false))
    val cart = listOf(SalesEngine.CartLine("shoe", 1.0))
    //  فاکتورِ اصل با مشتری ⇒ فاکتورِ تعویض همان مشتری را می‌گیرد و ثبت می‌شود
    val (d, sale) = sold("d1")
    val ok = exchange(d, sale, cart, everyone.exchange(d, "", sale.debtorId, null)) as ReturnFlow.Outcome.Ok
    assertEquals("d1", ok.value.data.sales.single { it.id == ok.value.saleId }.debtorId)
    //  بی مشتری ⇒ همان پیامِ صندوق و هیچ ثبت
    val (d2, anon) = sold(null)
    val prefix = StrLedger.customerFromRule("\u0000").substringBefore("\u0000")
    val no = exchange(d2, anon, cart, everyone.exchange(d2, "", anon.debtorId, null))
    assertTrue(no is ReturnFlow.Outcome.Bad && no.message.startsWith(prefix))
    //  مشتریِ ادغام‌شده (CRM-10) یا ناپیدا روی فاکتور نمی‌نشیند
    assertNull(everyone.exchange(d2, "", "d2", null).debtorId)
    assertNull(everyone.exchange(d2, "", "gone", null).debtorId)
    assertEquals("d1", TillGuards.OFF.exchange(d2, "", "d1", null).debtorId)
  }

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/$rel") }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  @Test
  fun `هر دو صفحهٔ برگشت فروشِ تعویض را با سدهای صندوق می‌سازند، نه Checkout ِ خام`() {
    val phone = src("ui/v3/sales/returns/ReturnFlowSheet.kt")
    val desk = src("ui/screens/ReturnsV2.kt")
    for ((name, s) in listOf("ReturnFlowSheet" to phone, "ReturnsV2" to desk)) {
      assertFalse("$name: `Checkout` ِ خام", s.contains("SalesEngine.Checkout("))
      assertTrue(name, s.contains("val guards = remember { TillGuardsPref.get(context) }"))
      assertTrue(name, s.contains("val checkout = guards.exchange(d, me, sale?.debtorId ?: creditTo, expiryApproval)"))
      assertTrue("$name: سنجش پیش از ثبت", Regex("""ReturnFlow\.exchangeExpiry\(d, req, cart, checkout, todayIso\(\), now, [:\w]+\)\?\.let \{ need -> expiryAsk = need; return \}""").containsMatchIn(s))
      assertTrue("$name: همان سبد و همان checkout به ثبت", s.contains("ReturnFlow.exchange(") && s.indexOf("ReturnFlow.exchangeExpiry(") < s.indexOf("ReturnFlow.exchange("))
      assertTrue("$name: عنوانِ برگهٔ منقضی", s.contains("title = StrPos.expiredSaleTitle"))
      assertTrue("$name: بستن بی تأیید ⇒ پیام", s.contains("error = TillGuards.expiredRefusal(d, need)"))
    }
    assertTrue("گوشی: همان برگهٔ PIN ِ صندوق", phone.contains("CreditApprovalSheet(") && phone.contains("CheckoutExpiry.approval(ok, need)"))
    assertTrue("گوشی: تأیید ⇒ همان ثبت", phone.contains("submit(closer, ok)"))
    assertTrue("کامپیوتر: همان پنجرهٔ `DeskSale`", desk.contains("ir.vil3ntec.tohid.ui.components.staff.CreditApprovalDialog("))
    assertTrue("کامپیوتر: تأیید فقط همان یک تلاش", desk.contains("submit(ExpiryGate.Approval(ok.by, ok.role, need, ok.reason))"))
    //  صندوق‌ها هم سدها را از همان یک خواننده می‌گیرند (کپیِ دومِ خواندن نه)
    for (till in listOf("ui/screens/DeskSale.kt", "ui/v3/sales/checkout/CheckoutRoute.kt")) {
      assertTrue(till, src(till).contains("val tillGuards = remember { ir.vil3ntec.tohid.data.TillGuardsPref.get(context) }"))
    }
  }

  /* ------------- POS-37 مالیات و PAY-09 گردِ نقدی در تعویض (`claude/e-rows-37e`؛ رفتارِ دستگاه: `ExchangeTillPricingTest`) ------------- */

  private val tenPercent = SaleTax.Config(SaleTax.Mode.INVOICE, 1_000)
  private val bag = Product(id = "bag", name = "کیف", unit = "عدد", salePrice = 1023.0)

  @Test
  fun `فروشِ تعویض مالیات و پلهٔ گردِ همین صندوق را می‌گیرد و خاموش همان امروز است`() {
    val c = TillGuards(tax = tenPercent, cashRounding = 500L).exchange(d0, "صندوقدار", null, null)
    assertEquals(tenPercent, c.tax)
    assertEquals(500L, c.cashRounding)
    assertEquals("گردِ نقد فقط پس از برگشت معلوم است", 0L, c.cashCovered)
    val off = TillGuards.OFF.exchange(d0, "صندوقدار", null, null)
    assertEquals(SalesEngine.Checkout(seller = "صندوقدار"), off)
  }

  @Test
  fun `پیش‌نمایشِ تعویض همان اعدادی است که ثبت می‌شود`() {
    val (d1, sale) = sold(null)
    val d = d1.copy(products = d1.products + bag, warehouseEntries = d1.warehouseEntries + WarehouseEntry(id = "w-bag", productId = "bag", units = 5.0))
    val guards = listOf(TillGuards.OFF, TillGuards(tax = tenPercent), TillGuards(cashRounding = 500L), TillGuards(tax = tenPercent, cashRounding = 500L))
    val carts = listOf(listOf(SalesEngine.CartLine("shoe", 1.0)), listOf(SalesEngine.CartLine("bag", 1.0)), listOf(SalesEngine.CartLine("bag", 2.0)))
    for (g in guards) for (cart in carts) {
      val c = g.exchange(d, "صندوقدار", null, null)
      val ex = (exchange(d, sale, cart, c) as ReturnFlow.Outcome.Ok).value
      val s = ex.data.sales.single { it.id == ex.saleId }
      val p = ExchangeTill.preview(d, cart, c, ex.split.cash)
      val tag = "$g $cart"
      assertEquals(tag, s.finalTotal, p.total, 0.0)
      assertEquals(tag, PayFlow.major(s.taxMinor ?: 0L), p.tax, 0.0)
      assertEquals(tag, PayFlow.major(s.roundingMinor ?: 0L), p.rounding, 0.0)
      assertEquals(tag, ex.due, p.due, 0.0)
    }
    //  هر دو روشن، دو کیف: ۲۰۴۶ + ۲۰۵ مالیات = ۲۲۵۱؛ برگشتِ نقدِ ۱۰۰۰ ⇒ ۱۲۵۱ ⇐ گرد به ۱۲۵۰ (ردیفِ −۱ روی فاکتورِ تعویض)
    val both = ExchangeTill.preview(d, listOf(SalesEngine.CartLine("bag", 2.0)), TillGuards(tax = tenPercent, cashRounding = 500L).exchange(d, "", null, null), 1_000.0)
    assertEquals(ExchangeTill.Preview(goods = 2_046.0, tax = 205.0, rounding = -1.0, total = 2_250.0, due = 1_250.0), both)
    //  پس‌دادنی گرد نمی‌شود: یک کفش با برگشتِ نقدِ ۱۰۲۳ ⇒ دقیقاً ۲۳ به مشتری
    val back = ExchangeTill.preview(d, listOf(SalesEngine.CartLine("shoe", 1.0)), TillGuards(cashRounding = 500L).exchange(d, "", null, null), 1_023.0)
    assertEquals(0.0, back.rounding, 0.0)
    assertEquals(-23.0, back.due, 0.0)
  }

  @Test
  fun `صندوق‌ها و هر دو برگهٔ تعویض مالیات و پلهٔ گرد را از همان یک خواننده می‌گیرند`() {
    val pref = src("data/TillGuardsAndroid.kt")
    assertTrue(pref.contains("tax = SaleTaxPref.active(context)") && pref.contains("cashRounding = CashRoundPref.step(context)"))
    val route = src("ui/v3/sales/checkout/CheckoutRoute.kt")
    assertTrue(route.contains("val cashStep = tillGuards.cashRounding") && route.contains("val taxConfig = tillGuards.tax"))
    val desk = src("ui/screens/DeskSale.kt")
    assertTrue(desk.contains("val cashStep = tillGuards.cashRounding") && desk.contains("tax = tillGuards.tax,"))
    for ((name, s) in listOf("CheckoutRoute" to route, "DeskSale" to desk)) {
      assertFalse("$name: خواندنِ دوم", s.contains("SaleTaxPref.active(context)") || s.contains("CashRoundPref.step(context)"))
    }
    for (rel in listOf("ui/v3/sales/returns/ReturnFlowSheet.kt", "ui/screens/ReturnsV2.kt")) {
      val s = src(rel)
      assertTrue(rel, s.contains("ExchangeTill.preview(d, cart, guards.exchange(d, me, null, null), split?.cash ?: 0.0)"))
      assertTrue("$rel: تفاوت از همان پیش‌نمایش", s.contains("kotlin.math.abs(bill.due)"))
      assertFalse("$rel: جمعِ خامِ سبد", s.contains("SalesEngine.cartTotal("))
      assertTrue("$rel: فروشنده کاربرِ صندوق (S46)", s.contains("val me = remember { TillUser.me(context).trim() }"))
    }
    assertTrue("ثبت: نقدِ برگشتِ همان سند پیش از گرد", src("data/ReturnFlow.kt").contains("SalesEngine.record(posted.data, cart, ExchangeTill.sale(checkout, posted.split),"))
  }
}
