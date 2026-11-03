package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrGiftCard
import ir.vil3ntec.tohid.core.pay.Settlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PRM-15 — «اعتبارِ فروشگاه (از برگشت یا هدیه)»، از سر تا ته روی دفترِ واقعیِ دکان: فروشِ نقد ⇒ برگشت به اعتبارِ
 *  فروشگاه (`ReturnFlow.post`، مقصدِ `store_credit`) ⇒ طلبِ مشتری (`GiftCards.creditOf`) ⇒ خرج در فاکتورِ بعدی
 *  (`PayFlow` با «اعتبار» ⇐ `SalesEngine.record`) ⇒ صفر؛ خرجِ دوم رد؛ برگشتِ فاکتورِ خرج‌شده دوباره به اعتبار؛ و هیچ‌کدام
 *  نقدِ دخل نیست. «از هدیه» همان کارتِ هدیه است (PRM-14، `GiftCardFlowTest`).
 */
class StoreCreditLifecycleTest {

  private var seq = 0
  private val newId: () -> String = { "id${++seq}" }
  private val today = "2026-11-03"

  private val base = ShopData(
    products = listOf(
      Product(id = "oil", name = "روغن", salePrice = 2450.0, purchasePrice = 2000.0, unit = "عدد"),
      Product(id = "rice", name = "برنج", salePrice = 800.0, purchasePrice = 600.0, unit = "عدد"),
    ),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "oil", units = 20.0), WarehouseEntry(id = "w2", productId = "rice", units = 20.0)),
    debtors = listOf(Debtor(id = "d1", name = "نوشا")),
    auditLog = listOf(AuditEntry(id = "open1", type = CashShift.OPEN, date = today, notes = "باز", createdAt = 50, amount = 500.0)),
  )

  private fun settle(total: Double, cash: String, rows: List<PayFlow.DraftRow> = emptyList(), customer: String? = null): Settlement =
    (PayFlow.checkout(PayFlow.Draft(total, cash, rows, customer, stored = true)) as PayFlow.Outcome.Ok).value

  private fun credit(amount: String) = PayFlow.DraftRow("store_credit", "", amount)

  private fun sell(d: ShopData, id: String, cart: List<SalesEngine.CartLine>, st: Settlement, debtor: String?): ShopData {
    val r = SalesEngine.record(d, cart, SalesEngine.Checkout(seller = "رضا", settlement = st, debtorId = debtor), today, 100, newId, saleId = id)
    return (r as SalesEngine.Result.Ok).data
  }

  /** نقدِ موردِ انتظارِ دخلِ همین شیفت (بازِ ۵۰۰) */
  private fun drawer(d: ShopData): Double = CashShift.totals(d, 50, 700, 500.0, "open1").expected

  private fun giveBack(d: ShopData, doc: String, sale: String, mode: String, customer: String? = null) =
    ReturnFlow.post(
      d,
      ReturnFlow.Request(docId = doc, saleId = sale, picks = mapOf(d.saleItems.first { it.saleId == sale }.id to ReturnFlow.Pick(1.0)), reason = "دیگر", mode = mode, customerId = customer),
      today, 300, newId,
    )

  @Test fun `PRM-15 برگشتِ نقد به اعتبارِ فروشگاه طلبِ مشتری می‌شود و در فاکتورِ بعدی تا صفر خرج می‌شود`() {
    //  ۱) فروشِ نقدِ برنج (۸۰۰) به نوشا
    val sold = sell(base, "s1", listOf(SalesEngine.CartLine("rice", 1.0)), settle(800.0, "800", customer = "d1"), "d1")
    assertEquals(0L, GiftCards.creditOf(sold, "d1"))

    assertEquals(1300.0, drawer(sold), 0.0)
    //  ۲) برگشت به اعتبارِ فروشگاه: نقدی از دخل بیرون نمی‌رود (رسیدِ برگشت کمِ دخل را خنثی می‌کند)، طلبِ ۸۰۰ ِ مشتری
    val back = (giveBack(sold, "r1", "s1", ReturnFlow.STORE_CREDIT) as ReturnFlow.Outcome.Ok).value
    assertEquals(800.0, back.split.credit, 0.0)
    assertEquals(0.0, back.split.cash, 0.0)
    assertEquals(1300.0, drawer(back.data), 0.0)
    assertEquals(80_000L, GiftCards.creditOf(back.data, "d1"))

    //  ۳) فاکتورِ بعدی: روغنِ ۲٬۴۵۰ = ۸۰۰ اعتبار + ۱٬۶۵۰ نقد ⇒ طلب صفر، و هیچ بدهی
    val spent = sell(back.data, "s2", listOf(SalesEngine.CartLine("oil", 1.0)), settle(2450.0, "1650", listOf(credit("800")), customer = "d1"), "d1")
    assertEquals(0L, GiftCards.creditOf(spent, "d1"))
    assertEquals(0.0, spent.sales.single { it.id == "s2" }.debtGiven, 0.0)
    //  دخل فقط سهمِ نقدِ همان فاکتور را می‌گیرد
    assertEquals(1300.0 + 1650.0, drawer(spent), 0.0)
    //  خرجِ دوم از همان طلب رد می‌شود و دفتر دست نمی‌خورد
    val again = SalesEngine.record(
      spent, listOf(SalesEngine.CartLine("rice", 1.0)),
      SalesEngine.Checkout(seller = "رضا", settlement = settle(800.0, "0", listOf(credit("800")), customer = "d1"), debtorId = "d1"),
      today, 100, newId, saleId = "s3",
    )
    assertEquals(StrGiftCard.errCredit, (again as SalesEngine.Result.Failed).message)

    //  ۴) برگشتِ فاکتورِ خرج‌شده با «روشِ اصلی»: سهمِ اعتبار به اعتبار برمی‌گردد (نه نقد)، سهمِ نقد از دخل
    val back2 = (giveBack(spent, "r2", "s2", ReturnFlow.ORIGINAL) as ReturnFlow.Outcome.Ok).value
    assertEquals(800.0, back2.split.methods["store_credit"] ?: 0.0, 0.0)
    assertEquals(1650.0, back2.split.cash, 0.0)
    assertEquals(80_000L, GiftCards.creditOf(back2.data, "d1"))
    assertEquals(1300.0, drawer(back2.data), 0.0)
  }

  @Test fun `PRM-15 اعتبارِ فروشگاه بی مشتری ثبت نمی‌شود و دفتر دست نمی‌خورد`() {
    val sold = sell(base, "s1", listOf(SalesEngine.CartLine("rice", 1.0)), settle(800.0, "800"), null)
    val r = giveBack(sold, "r1", "s1", ReturnFlow.STORE_CREDIT)
    assertTrue(r is ReturnFlow.Outcome.Bad)
    assertEquals(ReturnFlow.Code.CUSTOMER_REQUIRED, (r as ReturnFlow.Outcome.Bad).code)
    //  با مشتریِ انتخاب‌شده سرِ برگشت (فاکتورِ بی مشتری) ⇒ طلبِ همان مشتری
    val ok = (giveBack(sold, "r1", "s1", ReturnFlow.STORE_CREDIT, customer = "d1") as ReturnFlow.Outcome.Ok).value
    assertEquals(80_000L, GiftCards.creditOf(ok.data, "d1"))
  }
}
