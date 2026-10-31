package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.money.LedgerBalance
import ir.vil3ntec.tohid.money
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  ماندهٔ حساب از جمعِ چند ردیف، پشتِ پرچمِ `shop.debt_minor` (`DebtMinorFlag`، پیش‌فرض خاموش):
 *  - خاموش ⇒ بیت‌به‌بیت همان main (جمعِ Double و `> 0` / `== 0.0`).
 *  - روشن ⇒ به پول و دقیقاً مثلِ `DebtBook`: تسویهٔ دقیقِ چند ردیفِ اعشاری «حساب صاف» است (بازبینیِ #470)
 *    و برد و رسیدِ نیم‌پول (12.625) هم (بازبینیِ #610).
 */
class DebtSettledTest {

  @After fun reset() { DebtMinorFlag.override = null }

  private fun tx(id: String, who: String, type: String, amount: Double) =
    DebtTransaction(id = id, debtorId = who, type = type, amount = amount, date = "2026-10-01")

  /** c1: دقیقاً تسویه؛ c2: یک پول کمتر پرداخته؛ c3: برد و رسیدِ نیم‌پول؛ c4: تنها یک رسیدِ نیم‌پول */
  private val d = ShopData(
    debtors = listOf(Debtor(id = "c1", name = "صاف"), Debtor(id = "c2", name = "یک پول"), Debtor(id = "c3", name = "نیم‌پول"), Debtor(id = "c4", name = "رسیدِ نیم‌پول")),
    transactions = listOf(
      tx("a", "c1", "give", 966.77), tx("b", "c1", "give", 4963.8), tx("c", "c1", "give", 1920.32), tx("d", "c1", "receive", 7850.89),
      tx("e", "c2", "give", 966.77), tx("f", "c2", "give", 4963.8), tx("g", "c2", "give", 1920.32), tx("h", "c2", "receive", 7850.88),
      tx("i", "c3", "give", 12.625), tx("j", "c3", "receive", 12.625),
      tx("k", "c4", "receive", 0.005),
    ),
  )
  private val ids = listOf("c1", "c2", "c3", "c4")

  /** همان کدِ main: `ShopStore.debt`، `DebtorEngine.stateText`، `ReportEngine.debtors` */
  private fun mainDebt(id: String) = d.transactions.filter { it.debtorId == id }.sumOf { if (it.type == "give") it.amount else -it.amount }
  private fun mainState(b: Double) = when {
    b > 0 -> "${money(b)} افغانی بدهکار"
    b < 0 -> "${money(-b)} افغانی موجودی دارد"
    else -> "حساب صاف است"
  }

  /** فرمولِ خودِ `DebtBook`: `acc ± DebtBook.minor(amount)` */
  private fun book(id: String) = d.transactions.filter { it.debtorId == id }
    .sumOf { if (it.type == "give") DebtBook.minor(it.amount).minor else -DebtBook.minor(it.amount).minor }

  @Test fun `جمعِ Double ِ همین ردیف‌ها پسماند دارد — وگرنه این آزمون چیزی نمی‌سنجید`() {
    assertNotEquals(0.0, mainDebt("c1"), 0.0)
  }

  @Test fun `پیش‌فرض خاموش است`() {
    assertFalse(DebtMinorFlag.on)
  }

  @Test fun `خاموش — بیت‌به‌بیت همان main`() {
    DebtMinorFlag.override = false
    for (id in ids) {
      val b = ShopStore.debt(d, id)
      assertEquals(id, java.lang.Double.doubleToRawLongBits(mainDebt(id)), java.lang.Double.doubleToRawLongBits(b))
      assertEquals(id, mainState(b), DebtorEngine.stateText(b))
      assertEquals(id, b == 0.0, DebtMinorFlag.settled(b))
      val r = ReportEngine.debtors(d).first { it.debtor.id == id }
      var given = 0.0
      var received = 0.0
      d.transactions.filter { it.debtorId == id }.forEach { if (it.type == "give") given += it.amount else received += it.amount }
      assertEquals(id, given, r.given, 0.0)
      assertEquals(id, received, r.received, 0.0)
      assertEquals(id, given - received, r.remaining, 0.0)
    }
    //  همان باگِ main که پرچم درستش می‌کند: پسماندِ c1 «موجودی دارد» است
    assertTrue(DebtorEngine.stateText(ShopStore.debt(d, "c1")).endsWith("افغانی موجودی دارد"))
    assertEquals(ids.filter { mainDebt(it) > 0 }, LedgerSummary.of(d, now = 0L).owing.map { it.id })
    assertEquals(ids.sumOf { mainDebt(it).coerceAtLeast(0.0) }, DashboardEngine.board(d, "2026-10-01", 7, now = 0L).receivables, 0.0)
  }

  @Test fun `روشن — تسویهٔ دقیق صفرِ دقیق و «حساب صاف» است`() {
    DebtMinorFlag.override = true
    for (id in listOf("c1", "c3")) {
      assertEquals(id, 0.0, ShopStore.debt(d, id), 0.0)
      assertEquals(id, "حساب صاف است", DebtorEngine.stateText(ShopStore.debt(d, id)))
      assertTrue(id, DebtMinorFlag.settled(ShopStore.debt(d, id)))
      assertEquals(id, 0.0, DebtorEngine.account(d, id)!!.balance, 0.0)
      assertEquals(id, 0.0, ReportEngine.debtors(d).first { it.debtor.id == id }.remaining, 0.0)
    }
    //  پسماندِ خامی که از جای دیگری برسد هم «صاف» است
    assertEquals("حساب صاف است", DebtorEngine.stateText(mainDebt("c1")))
    //  گزارش: نیم‌پول — داده و گرفته هر دو 12.63 و مانده 0، نه 0.01
    val c3 = ReportEngine.debtors(d).first { it.debtor.id == "c3" }
    assertEquals(12.63, c3.given, 0.0)
    assertEquals(12.63, c3.received, 0.0)
  }

  @Test fun `روشن — یک پول مانده هنوز بدهی است، و رسیدِ تنهای نیم‌پول همان DebtBook`() {
    DebtMinorFlag.override = true
    assertEquals(0.01, ShopStore.debt(d, "c2"), 0.0)
    assertEquals(1, DebtMinorFlag.sign(ShopStore.debt(d, "c2")))
    assertTrue(DebtorEngine.stateText(ShopStore.debt(d, "c2")).endsWith("افغانی بدهکار"))
    assertEquals(0.01, ReportEngine.debtors(d).first { it.debtor.id == "c2" }.remaining, 0.0)
    assertEquals(-0.01, ShopStore.debt(d, "c4"), 0.0)
    assertEquals(listOf("c2"), LedgerSummary.of(d, now = 0L).owing.map { it.id })
    assertEquals(0.01, DashboardEngine.board(d, "2026-10-01", 7, now = 0L).receivables, 0.0)
  }

  @Test fun `روشن — ماندهٔ ShopStore همان ماندهٔ DebtBook است، به پول، با نیم‌پولِ دوطرفه`() {
    DebtMinorFlag.override = true
    for (id in ids) assertEquals(id, book(id), LedgerBalance.rowMinor(ShopStore.debt(d, id)))
    assertEquals(0L, book("c3"))
    assertEquals(-1L, book("c4"))
  }
}
