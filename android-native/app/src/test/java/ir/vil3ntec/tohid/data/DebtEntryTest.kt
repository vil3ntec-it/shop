package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.ledger.AppendOnce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  رسیدِ قرضِ پروندهٔ مشتری (کامپیوتر F7) و «قرض‌داران» (گوشی): `DebtEntry` روی دفترِ همین لحظه ساخته
 *  می‌شود، نه دفترِ در دستِ صفحه. همان ترکیبی که `DebtEntry.record` با `ShopStore.update` (قفلِ موتور) و دفترِ
 *  دیسک می‌دود؛ این‌جا دیسک یک متغیر است و «همگام‌سازی» با همان قفل می‌نویسد. سنجهٔ دیسک و دو نمونهٔ `ShopStore`:
 *  `DebtReceiptStaleTest` (کامپیوتر).
 */
class DebtEntryTest {

  /** همان قراردادِ `ShopStore.update` (خواندن ⇐ تبدیل ⇐ نوشتن زیرِ یک قفل)؛ «همگام‌سازی» با همان قفل می‌نویسد */
  private class Disk(var ledger: ShopData) {
    private val lock = Any()
    var writes = 0
    fun update(transform: (ShopData?) -> ShopData?): Boolean? = synchronized(lock) {
      val next = transform(ledger) ?: return@synchronized null
      ledger = next
      writes++
      true
    }
    /** Pull ِ همگام‌سازی، زیرِ همان قفل */
    fun pull(change: (ShopData) -> ShopData) = synchronized(lock) { ledger = change(ledger) }
  }

  private val ahmad = Debtor(id = "d1", name = "احمد")
  private val seed = ShopData(
    debtors = listOf(ahmad),
    transactions = listOf(DebtTransaction(id = "g1", debtorId = "d1", type = "give", amount = 1000.0, date = "2026-10-01", createdAt = 1)),
  )
  /** رسیدِ همان مشتری که گوشیِ دیگر ثبت کرد و Pull آورد */
  private val fromPhone = DebtTransaction(id = "phone-r", debtorId = "d1", type = "receive", amount = 300.0, date = "2026-10-30", createdAt = 2)

  private var seq = 0
  private fun audit() = "a${seq++}"
  private val receipt = DebtEntry.Entry(debtorId = "d1", kind = DebtorEngine.Kind.RECEIVE, amount = 200.0, date = "2026-10-30", notes = "")

  private fun record(disk: Disk, key: String, e: DebtEntry.Entry = receipt): DebtEntry.Result {
    var failed: String? = null
    val out = AppendOnce.append(key, DebtEntry::has, DebtEntry.change(e, "2026-10-30", 1_761_800_000_000, ::audit) { failed = it }) { t -> disk.update(t) }
    return DebtEntry.result(out, key, failed)
  }

  @Test fun `رسیدی که Pull پس از بازشدنِ صفحه آورد با رسیدِ صفحه می‌ماند`() {
    val disk = Disk(seed)
    val inHand = disk.ledger                      // دفتری که پروندهٔ مشتری نشان می‌دهد
    disk.pull { it.copy(transactions = it.transactions + fromPhone) }

    assertEquals(DebtEntry.Result.Ok("k1", again = false), record(disk, "k1"))

    assertEquals(listOf("g1", "phone-r", "k1"), disk.ledger.transactions.map { it.id })
    assertEquals("مانده = ۱۰۰۰ − ۳۰۰ − ۲۰۰", 500.0, ShopStore.debt(disk.ledger, "d1"), 0.0)
    assertTrue(disk.ledger.auditLog.any { it.type == "customer_payment" && it.refId == "k1" })
    //  راهِ کهنه همین دفتر را می‌نوشت: رسیدِ گوشی گم و `record` برایش DELETE می‌ساخت
    val old = DebtorEngine.addTransaction(inHand, "d1", DebtorEngine.Kind.RECEIVE, 200.0, "2026-10-30", "", "2026-10-30", 1, ::audit)
    assertTrue((old as DebtorEngine.Result.Ok).data.transactions.none { it.id == "phone-r" })
  }

  @Test fun `دو ضربه با یک کلید یک ردیف و یک سابقه`() {
    val disk = Disk(seed)
    assertEquals(DebtEntry.Result.Ok("k1", again = false), record(disk, "k1"))
    assertEquals(DebtEntry.Result.Ok("k1", again = true), record(disk, "k1"))
    assertEquals(1, disk.ledger.transactions.count { it.type == "receive" })
    assertEquals(1, disk.ledger.auditLog.count { it.type == "customer_payment" })
    assertEquals(1, disk.writes)
  }

  @Test fun `کلیدِ تازه یعنی رسیدِ تازه`() {
    val disk = Disk(seed)
    record(disk, "k1")
    record(disk, "k2")
    assertEquals(listOf("g1", "k1", "k2"), disk.ledger.transactions.map { it.id })
  }

  @Test fun `ردِ DebtorEngine روی دفترِ همین لحظه هیچ نمی‌نویسد`() {
    val disk = Disk(seed)
    assertEquals(DebtEntry.Result.Failed("مبلغ معتبر وارد کنید"), record(disk, "k1", receipt.copy(amount = 0.0)))
    //  مشتری در دستگاهِ دیگر حذف شد و Pull رسید: رسید روی مشتریِ ناموجود ثبت نمی‌شود
    disk.pull { it.copy(debtors = emptyList()) }
    assertEquals(DebtEntry.Result.Failed("قرض‌دار پیدا نشد"), record(disk, "k1"))
    assertEquals(0, disk.writes)
  }

  @Test fun `سقفِ اعتبار روی دفترِ همین لحظه سنجیده می‌شود`() {
    val limited = seed.copy(debtors = listOf(ahmad.copy(creditLimit = 1500.0)))
    val disk = Disk(limited)
    //  صفحه هنوز ۱۰۰۰ بدهی می‌بیند و برد ۴۰۰ را زیرِ سقف می‌داند؛ Pull بردِ ۳۰۰ ِ دستگاهِ دیگر را آورد
    disk.pull { it.copy(transactions = it.transactions + DebtTransaction(id = "phone-g", debtorId = "d1", type = "give", amount = 300.0, date = "2026-10-30", createdAt = 3)) }
    val give = DebtEntry.Entry(debtorId = "d1", kind = DebtorEngine.Kind.GIVE, amount = 400.0, date = "2026-10-30", notes = "")
    val r = record(disk, "k1", give)
    assertTrue(r is DebtEntry.Result.Failed)
    assertEquals(0, disk.writes)
    assertEquals(DebtEntry.Result.Ok("k1", again = false), record(disk, "k1", give.copy(overLimit = true)))
    assertTrue(disk.ledger.auditLog.any { it.type == "credit_over_limit" && it.refId == "k1" })
  }

  @Test fun `شناسهٔ ردیف همان کلید است و بی کلید همان newId`() {
    val r = DebtorEngine.addTransaction(seed, "d1", DebtorEngine.Kind.RECEIVE, 50.0, "", "", "2026-10-30", 1, { "fresh" }, txId = "key") as DebtorEngine.Result.Ok
    assertEquals("key", r.id)
    assertEquals("fresh", r.data.auditLog.single().id)
    val plain = DebtorEngine.addTransaction(seed, "d1", DebtorEngine.Kind.RECEIVE, 50.0, "", "", "2026-10-30", 1, { "fresh" }) as DebtorEngine.Result.Ok
    assertEquals("fresh", plain.id)
    assertNull(plain.data.transactions.last().dueDate)
  }
}
