package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrGiftCard
import ir.vil3ntec.tohid.core.pay.GiftCard
import ir.vil3ntec.tohid.core.pay.Settlement
import ir.vil3ntec.tohid.core.pay.StoreCredit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PRM-15 — «اعتبارِ فروشگاه (از برگشت یا **هدیه**)»: اعطای دستیِ اعتبار بی کارت (`GiftCards.grantCredit`) روی دفترِ
 *  واقعیِ دکان. یک «رسید» ِ غیرنقد با `kind` = اعتبار (همان ردیفِ برگشت به اعتبار) ⇒ طلبِ مشتری بالا می‌رود و در
 *  فاکتورِ بعدی خرج می‌شود؛ نقدِ دخل نیست؛ با سابقهٔ نام‌دار (`by`، دلیل)؛ بی اجازهٔ «مجاز» ِ
 *  `sales.discount`، بی دلیل یا برای مشتریِ ادغام‌شده هیچ چیز نوشته نمی‌شود؛ همان کلید دو بار ⇒ یک ردیف.
 */
class StoreCreditGrantFlowTest {

  private var seq = 0
  private val newId: () -> String = { "id${++seq}" }
  private val today = "2026-11-03"

  private val base = ShopData(
    products = listOf(Product(id = "rice", name = "برنج", salePrice = 800.0, purchasePrice = 600.0, unit = "عدد")),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "rice", units = 20.0)),
    debtors = listOf(Debtor(id = "d1", name = "نوشا"), Debtor(id = "d2", name = "کهنه", mergedInto = "d1")),
    auditLog = listOf(AuditEntry(id = "open1", type = CashShift.OPEN, date = today, notes = "باز", createdAt = 50, amount = 500.0)),
  )

  private fun grant(d: ShopData, key: String = "g1", debtor: String = "d1", minor: Long = 50_000, reason: String? = "مشتریِ همیشگی", decision: String = "allow") =
    GiftCards.grantCredit(d, key, debtor, minor, reason, decision, today, 200, "صاحب")

  private fun drawer(d: ShopData): Double = CashShift.totals(d, 50, 700, 500.0, "open1").expected

  @Test fun `PRM-15 اعطای دستیِ اعتبار طلبِ مشتری می‌شود، نقدِ دخل نیست و در فاکتورِ بعدی خرج می‌شود`() {
    val r = grant(base) as GiftCards.Granted.Ok
    assertFalse(r.again)
    val d = r.data
    assertEquals(50_000L, GiftCards.creditOf(d, "d1"))
    val tx = d.transactions.single { it.id == "g1" }
    assertEquals("receive", tx.type)
    assertEquals(GiftCards.CREDIT, tx.kind)
    assertEquals(500.0, tx.amount, 0.0)
    assertNull("به فاکتوری بسته نیست", tx.refId)
    assertFalse(CashShift.isCashReceipt(tx))
    assertEquals("دخل دست نمی‌خورد", drawer(base), drawer(d), 0.0)
    val log = d.auditLog.single { it.type == GiftCards.CREDIT_GRANT }
    assertEquals("صاحب", log.by)
    assertEquals("مشتریِ همیشگی", log.reason)
    assertEquals("g1", log.refId)
    assertEquals(500.0, log.amount, 0.0)

    //  خرج در فاکتورِ بعدی (همان راهِ PAY-24)
    val st: Settlement = (PayFlow.checkout(PayFlow.Draft(800.0, "300", listOf(PayFlow.DraftRow("store_credit", "", "500")), "d1", stored = true))
      as PayFlow.Outcome.Ok).value
    val sold = (SalesEngine.record(d, listOf(SalesEngine.CartLine("rice", 1.0)), SalesEngine.Checkout(seller = "رضا", settlement = st, debtorId = "d1"),
      today, 300, newId, saleId = "s1") as SalesEngine.Result.Ok).data
    assertEquals(0L, GiftCards.creditOf(sold, "d1"))
    assertEquals("فقط ۳۰۰ ِ نقدِ فاکتور به دخل آمد", drawer(base) + 300.0, drawer(sold), 0.0)
  }

  @Test fun `PRM-15 بدهیِ مشتری اول کم می‌شود و پیش‌نمایش همان ماندهٔ پس از اعطاست`() {
    val owes = base.copy(transactions = listOf(DebtTransaction(id = "t0", debtorId = "d1", type = "give", amount = 700.0, date = today, createdAt = 10)))
    assertEquals(70_000L to 20_000L, GiftCards.grantPreview(owes, "d1", 50_000))
    val d = (grant(owes) as GiftCards.Granted.Ok).data
    assertEquals(20_000L, GiftCards.balanceMinor(d, "d1"))
    assertEquals(0L, GiftCards.creditOf(d, "d1"))
    assertEquals(0L to -50_000L, GiftCards.grantPreview(base, "d1", 50_000))
  }

  @Test fun `PRM-15 بی اجازه، بی دلیل، مبلغِ بد یا مشتریِ ادغام‌شده هیچ چیز نوشته نمی‌شود`() {
    for ((r, code) in listOf(
      grant(base, decision = "deny") to StoreCredit.GRANT_NOT_ALLOWED,
      grant(base, decision = "approve") to StoreCredit.GRANT_NOT_ALLOWED,
      grant(base, reason = " ") to GiftCard.Code.REASON_REQUIRED,
      grant(base, minor = 0) to StoreCredit.INVALID_AMOUNT,
      grant(base, debtor = "d2") to StoreCredit.NO_CUSTOMER,
      grant(base, debtor = "nobody") to StoreCredit.NO_CUSTOMER,
    )) {
      assertEquals(code, (r as GiftCards.Granted.Bad).code)
    }
    assertEquals(StrGiftCard.errGrantNotAllowed, GiftCards.message(StoreCredit.GRANT_NOT_ALLOWED))
  }

  @Test fun `PRM-15 همان کلید دو بار یک ردیف است؛ کلیدِ گرفته با محتوای دیگر رد`() {
    val once = (grant(base) as GiftCards.Granted.Ok).data
    val twice = grant(once) as GiftCards.Granted.Ok
    assertTrue(twice.again)
    assertSame(once, twice.data)
    assertEquals(1, twice.data.transactions.count { it.id == "g1" })
    assertEquals(GiftCard.Code.ID_CONFLICT, (grant(once, minor = 60_000) as GiftCards.Granted.Bad).code)
    assertTrue(GiftCards.hasGrant(once, "g1"))
    assertFalse(GiftCards.hasGrant(once, "g2"))
  }
}
