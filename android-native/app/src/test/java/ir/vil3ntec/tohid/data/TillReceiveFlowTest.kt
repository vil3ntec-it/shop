package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.ledger.AppendOnce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  POS-39: دریافتِ قسط از صندوق روی همان دفترِ قرض‌داران — یک ردیفِ `receive` با کلیدِ یکتا، تکرار بی اثرِ دوم،
 *  پیش‌پرداخت فقط با تأیید. همان ترکیبی که `TillReceivePref.record` ⇐ `DebtEntry.record` می‌دود ([TillReceiveFlow.entry]
 *  و [TillReceiveFlow.check] روی دفترِ همین لحظه در `DebtEntry.change`)؛ این‌جا دیسک یک متغیر است. سنجهٔ دیسک و دو
 *  نمونهٔ `ShopStore`: `TillReceiveStaleTest` (کامپیوتر).
 */
class TillReceiveFlowTest {

  private val ahmad = Debtor(id = "d1", name = "احمد")
  private val ledger = ShopData(
    debtors = listOf(ahmad),
    transactions = listOf(DebtTransaction(id = "g1", debtorId = "d1", type = "give", amount = 1200.5, date = "2026-10-01", createdAt = 1)),
  )
  private var seq = 0
  private fun newId() = "n${seq++}"

  /** همان قراردادِ `ShopStore.update` (خواندن ⇐ تبدیل ⇐ نوشتن)؛ [writes] شمارِ نوشتن */
  private class Disk(var ledger: ShopData) {
    var writes = 0
    fun update(transform: (ShopData?) -> ShopData?): Boolean? {
      val next = transform(ledger) ?: return null
      ledger = next; writes++
      return true
    }
  }

  private fun receive(disk: Disk, amountMinor: Long?, key: String = "k1", confirmed: Boolean = false, debtorId: String = "d1"): DebtEntry.Result {
    var failed: String? = null
    val e = TillReceiveFlow.entry(debtorId, amountMinor, "", "2026-10-30")
    val change = DebtEntry.change(e, "2026-10-30", 1_761_800_000_000, ::newId, { TillReceiveFlow.check(it, debtorId, amountMinor, confirmed) }) { failed = it }
    return DebtEntry.result(AppendOnce.append(key, DebtEntry::has, change) { t -> disk.update(t) }, key, failed)
  }

  @Test
  fun `قسطِ جزئی یک ردیفِ receive با همان کلید و سابقهٔ customer_payment می‌سازد`() {
    val disk = Disk(ledger)
    assertEquals(DebtEntry.Result.Ok("k1", again = false), receive(disk, 50_000))
    val tx = disk.ledger.transactions.single { it.type == "receive" }
    assertEquals("k1", tx.id)
    assertEquals(500.0, tx.amount, 0.0)
    assertEquals("دریافت از صندوق", tx.notes)
    assertNull(tx.dueDate)
    assertEquals(70_050L, TillReceiveFlow.balanceMinor(disk.ledger, "d1"))
    assertTrue(disk.ledger.auditLog.any { it.type == "customer_payment" && it.refId == "k1" })
  }

  @Test
  fun `همان کلید دو بار ثبت نمی‌شود`() {
    val disk = Disk(ledger)
    receive(disk, 50_000)
    assertEquals(DebtEntry.Result.Ok("k1", again = true), receive(disk, 50_000))
    assertEquals(1, disk.ledger.transactions.count { it.type == "receive" })
    assertEquals(1, disk.writes)
  }

  @Test
  fun `همهٔ بدهی تا پول، بی گردِ افغانی`() {
    val disk = Disk(ledger)
    assertEquals(DebtEntry.Result.Ok("k1", again = false), receive(disk, 120_050))
    assertEquals(0L, TillReceiveFlow.balanceMinor(disk.ledger, "d1"))
  }

  @Test
  fun `بیش از بدهی فقط با تأییدِ پیش‌پرداخت`() {
    val disk = Disk(ledger)
    assertEquals(DebtEntry.Result.Failed(TillReceiveFlow.prepayMessage(120_050)), receive(disk, 1_200_500))
    assertEquals(0, disk.writes)
    assertEquals(DebtEntry.Result.Ok("k1", again = false), receive(disk, 1_200_500, confirmed = true))
    assertEquals(-1_080_450L, TillReceiveFlow.balanceMinor(disk.ledger, "d1"))
  }

  @Test
  fun `مبلغِ نادرست یا مشتریِ ناموجود رد می‌شود و دفتر دست نمی‌خورد`() {
    val disk = Disk(ledger)
    assertEquals(DebtEntry.Result.Failed("مبلغ را درست بنویسید"), receive(disk, null))
    assertEquals(DebtEntry.Result.Failed("مبلغ باید بیشتر از صفر باشد"), receive(disk, 0))
    assertEquals(DebtEntry.Result.Failed("مشتری پیدا نشد"), receive(disk, 1_000, key = "k9", debtorId = "nobody"))
    assertEquals(0, disk.writes)
    assertEquals(ledger, disk.ledger)
  }
}
