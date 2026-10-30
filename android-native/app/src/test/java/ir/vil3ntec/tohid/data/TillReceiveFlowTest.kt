package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  POS-39: دریافتِ قسط از صندوق روی همان دفترِ قرض‌داران — یک ردیفِ `receive` با کلیدِ یکتا،
 *  تکرار بی اثرِ دوم، پیش‌پرداخت فقط با تأیید.
 */
class TillReceiveFlowTest {

  private val ahmad = Debtor(id = "d1", name = "احمد")
  private val ledger = ShopData(
    debtors = listOf(ahmad),
    transactions = listOf(DebtTransaction(id = "g1", debtorId = "d1", type = "give", amount = 1200.5, date = "2026-10-01", createdAt = 1)),
  )
  private var seq = 0
  private fun newId() = "n${seq++}"

  private fun apply(d: ShopData, amountMinor: Long?, key: String = "k1", confirmed: Boolean = false) =
    TillReceiveFlow.apply(d, "d1", amountMinor, "", key, confirmed, "2026-10-30", 1_761_800_000_000, ::newId)

  @Test
  fun `قسطِ جزئی یک ردیفِ receive با همان کلید و سابقهٔ customer_payment می‌سازد`() {
    val r = apply(ledger, 50_000) as TillReceiveFlow.Outcome.Ok
    val tx = r.data.transactions.single { it.type == "receive" }
    assertEquals("k1", tx.id)
    assertEquals("k1", r.txId)
    assertEquals(500.0, tx.amount, 0.0)
    assertEquals("دریافت از صندوق", tx.notes)
    assertEquals(70_050L, TillReceiveFlow.balanceMinor(r.data, "d1"))
    assertTrue(r.data.auditLog.any { it.type == "customer_payment" && it.refId == "k1" })
  }

  @Test
  fun `همان کلید دو بار ثبت نمی‌شود`() {
    val first = apply(ledger, 50_000) as TillReceiveFlow.Outcome.Ok
    assertEquals(TillReceiveFlow.Outcome.Already("k1"), apply(first.data, 50_000))
    assertEquals(1, first.data.transactions.count { it.type == "receive" })
  }

  @Test
  fun `همهٔ بدهی تا پول، بی گردِ افغانی`() {
    val r = apply(ledger, 120_050) as TillReceiveFlow.Outcome.Ok
    assertEquals(0L, TillReceiveFlow.balanceMinor(r.data, "d1"))
    assertEquals(0L, r.verdict.prepayMinor)
  }

  @Test
  fun `بیش از بدهی فقط با تأییدِ پیش‌پرداخت`() {
    val ask = apply(ledger, 1_200_500)
    assertTrue(ask is TillReceiveFlow.Outcome.NeedsConfirm)
    assertEquals(1_080_450L, (ask as TillReceiveFlow.Outcome.NeedsConfirm).verdict.prepayMinor)
    val ok = apply(ledger, 1_200_500, confirmed = true) as TillReceiveFlow.Outcome.Ok
    assertEquals(-1_080_450L, TillReceiveFlow.balanceMinor(ok.data, "d1"))
  }

  @Test
  fun `مبلغِ نادرست یا مشتریِ ناموجود رد می‌شود و دفتر دست نمی‌خورد`() {
    assertEquals(TillReceiveFlow.Outcome.Failed("مبلغ را درست بنویسید"), apply(ledger, null))
    assertEquals(TillReceiveFlow.Outcome.Failed("مبلغ باید بیشتر از صفر باشد"), apply(ledger, 0))
    assertEquals(
      TillReceiveFlow.Outcome.Failed("مشتری پیدا نشد"),
      TillReceiveFlow.apply(ledger, "nobody", 1_000, "", "k9", false, "2026-10-30", 1, ::newId),
    )
  }
}
