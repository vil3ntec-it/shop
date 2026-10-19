package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** مشتریِ تکراری: پیدا کردن، ادغام با حفظِ سوابق، و ردیفِ دیررسیده — CRM-10 */
class CustomerMergeTest {

  private var seq = 0
  private fun newId() = "m${seq++}"

  private fun give(id: String, who: String, amount: Double, date: String, notes: String = "") =
    DebtTransaction(id = id, debtorId = who, type = "give", amount = amount, date = date, notes = notes)
  private fun receive(id: String, who: String, amount: Double, date: String) =
    DebtTransaction(id = id, debtorId = who, type = "receive", amount = amount, date = date)

  /** احمد دو بار ثبت شده: یک‌بار با 07… و یک‌بار با ‎+93 (شمارهٔ دوم)؛ «علي»/«علی» فقط هم‌نام‌اند */
  private val d0 = ShopData(
    debtors = listOf(
      Debtor(id = "c1", name = "احمد", phone = "+93700123456", notes = "دکانِ کنار", createdAt = 10, tags = listOf("عمده")),
      Debtor(id = "c2", name = "احمد کابلی", phone = "+93799555444", phone2 = "0700123456", notes = "قدیمی", createdAt = 20,
        tags = listOf("همسایه", "عمده"), creditLimit = 5000.0, marketingConsent = false, group = "همکار"),
      Debtor(id = "c3", name = "علي", createdAt = 30),
      Debtor(id = "c4", name = "علی", createdAt = 40),
      Debtor(id = "c5", name = "تنها", phone = "+93788000000", createdAt = 50),
    ),
    transactions = listOf(
      give("t1", "c1", 300.0, "2026-09-01", "فروش نسیه — فاکتور #7"),
      give("t2", "c2", 1000.0, "2026-06-01", "فروش نسیه — فاکتور #3"),
      receive("t3", "c2", 400.0, "2026-07-01"),
      give("t4", "c5", 50.0, "2026-10-01"),
    ),
    sales = listOf(
      Sale(id = "s3", invoiceNumber = 3, finalTotal = 1000.0, paymentMethod = "credit", debtorId = "c2", date = "2026-06-01"),
      Sale(id = "s7", invoiceNumber = 7, finalTotal = 300.0, paymentMethod = "credit", debtorId = "c1", date = "2026-09-01"),
      Sale(id = "s8", invoiceNumber = 8, finalTotal = 90.0, debtorId = "c2", date = "2026-09-05"),
    ),
  )

  private fun merged(r: CustomerMerge.Result) = r as CustomerMerge.Result.Ok
  private fun failed(r: CustomerMerge.Result) = (r as CustomerMerge.Result.Failed).message

  @Test fun `تکراری‌ها — شمارهٔ یکسان قوی و اول، هم‌نامی ضعیف، پرکارتر پیشنهادِ ماندگار`() {
    val g = CustomerMerge.duplicates(d0)
    assertEquals(2, g.size)
    assertTrue(g[0].strong)
    assertEquals(setOf(CustomerMerge.Reason.PHONE), g[0].reasons)
    //  c2 سه ردیف و دو فاکتور دارد، c1 یکی و یکی ⇒ پیشنهاد c2
    assertEquals(listOf("c2", "c1"), g[0].members.map { it.id })
    assertFalse(g[1].strong)
    assertEquals(setOf(CustomerMerge.Reason.NAME), g[1].reasons)
    assertEquals(listOf("c3", "c4"), g[1].members.map { it.id })

    val sheet = CustomerMerge.duplicatesSheet(d0, "2026-10-19")
    assertEquals("مشتریان-تکراری-2026-10-19", sheet.name)
    assertEquals(5, sheet.rows.size)
    assertEquals(listOf("1", "پیشنهادِ ماندگار", "احمد کابلی", "+93799555444", "0700123456", "600", "2", "2", "شمارهٔ یکسان"), sheet.rows[1])
  }

  @Test fun `ادغام — همهٔ ردیف‌ها با همان تاریخ منتقل، مانده جمعِ دو مانده، تکراری می‌ماند با اشاره`() {
    val before = ShopStore.debt(d0, "c2") + ShopStore.debt(d0, "c1")
    val r = merged(CustomerMerge.merge(d0, "c2", "c1", "2026-10-19", 99, ::newId))
    val d = r.data

    assertEquals(before, ShopStore.debt(d, "c2"), 0.0)
    assertEquals(0.0, ShopStore.debt(d, "c1"), 0.0)
    assertEquals(listOf("t1", "t2", "t3"), d.transactions.filter { it.debtorId == "c2" }.map { it.id }.sorted())
    assertEquals(listOf("s3", "s7", "s8"), d.sales.filter { it.debtorId == "c2" }.map { it.id }.sorted())
    //  مبلغ، تاریخ و شرح دست نخورده
    assertEquals(d0.transactions.map { it.copy(debtorId = "") }, d.transactions.map { it.copy(debtorId = "") })
    assertEquals(d0.sales.map { it.copy(debtorId = null) }, d.sales.map { it.copy(debtorId = null) })

    val keep = d.debtors.first { it.id == "c2" }
    val drop = d.debtors.first { it.id == "c1" }
    assertEquals("c2", drop.mergedInto)
    assertEquals("احمد", drop.name)                       // سابقه پاک نشد
    assertEquals(listOf("c2", "c3", "c4", "c5"), CustomerCrm.active(d).map { it.id })
    //  شمارهٔ «تکراری» همان شمارهٔ دومِ ماندگار بود ⇒ چیزی اضافه نشد
    assertEquals("+93799555444", keep.phone)
    assertEquals("0700123456", keep.phone2)
    assertEquals("قدیمی\nدکانِ کنار", keep.notes)
    assertEquals(listOf("همسایه", "عمده"), keep.tags)
    assertEquals(5000.0, keep.creditLimit!!, 0.0)
    assertEquals(false, keep.marketingConsent)

    //  صورت‌حساب و دفترِ قرض: فاکتورِ پشتِ هر برد هنوز پیدا می‌شود
    val ledger = CustomerEngine.ledger(d, "c2")
    assertEquals(listOf("s3", null, "s7"), ledger.map { it.saleId })
    assertEquals(900.0, ledger.last().balance, 0.0)
    val book = DebtBook.book(d, "c2", "2026-10-19")!!
    assertEquals(listOf("s3", "s7"), book.entries.filter { it.tx.type == "give" }.map { it.sale?.id })
    assertEquals(3, CustomerEngine.invoices(d, "c2").size)

    val a = d.auditLog.single()
    assertEquals("customer_merge", a.type)
    assertEquals("c2", a.refId)
    assertEquals("ادغامِ «احمد» (c1) در «احمد کابلی»: 1 تراکنش و 1 فاکتور منتقل شد — ماندهٔ منتقل‌شده 300 افغانی", a.notes)
    assertEquals(1, r.preview.transactions)
    assertEquals(900.0, r.preview.balanceAfter, 0.0)

    //  دیگر تکراری نیست؛ فقط هم‌نام‌ها می‌مانند
    assertEquals(listOf(listOf("c3", "c4")), CustomerMerge.duplicates(d).map { g -> g.members.map { it.id } })
  }

  @Test fun `سنِ بدهی پس از ادغام همان تاریخِ بردِ کهنه را دارد`() {
    val d = merged(CustomerMerge.merge(d0, "c1", "c2", "2026-10-19", 99, ::newId)).data
    val age = CustomerInsight.agingOf(d, "c1", "2026-10-19")!!
    assertEquals(900.0, age.balance, 0.0)
    //  600 ِ باقیِ بردِ ۲۰۲۶-۰۶-۰۱ در «بیش از ۹۰»، 300 ِ ۲۰۲۶-۰۹-۰۱ در «۳۱ تا ۶۰»
    assertEquals(listOf(0.0, 300.0, 0.0, 600.0), age.buckets)
    assertEquals(140L, age.oldestDays)
  }

  @Test fun `جاهای خالیِ ماندگار پر می‌شوند؛ شمارهٔ بی‌جا به یادداشت؛ «نه» ِ صریح برنده`() {
    val d = ShopData(
      debtors = listOf(
        Debtor(id = "k", name = "ک", phone = "+93700000001", phone2 = "+93700000002", marketingConsent = true),
        Debtor(id = "x", name = "ت", phone = "+93700000003", phone2 = "+93700000001", notes = "یادداشتِ دوم",
          group = "همکار", priceList = "wholesale", marketingConsent = false),
      ),
    )
    val p = merged(CustomerMerge.preview(d, "k", "x")).preview
    assertEquals("+93700000001", p.result.phone)
    assertEquals("+93700000002", p.result.phone2)
    assertEquals("یادداشتِ دوم\nتلفنِ دیگر: +93700000003", p.result.notes)
    assertEquals("همکار", p.result.group)
    assertEquals("wholesale", p.result.priceList)
    assertEquals(false, p.result.marketingConsent)
    assertNull(p.result.creditLimit)
    //  پیش‌نمایش چیزی را عوض نمی‌کند
    assertEquals(d, merged(CustomerMerge.preview(d, "k", "x")).data)
  }

  @Test fun `ادغامِ نادرست رد می‌شود و زنجیره صاف می‌ماند`() {
    assertEquals("یک مشتری با خودش ادغام نمی‌شود", failed(CustomerMerge.merge(d0, "c1", "c1")))
    assertEquals("مشتریِ تکراری پیدا نشد", failed(CustomerMerge.merge(d0, "c1", "nope")))
    var d = merged(CustomerMerge.merge(d0, "c4", "c3", "2026-10-19", 1, ::newId)).data
    assertEquals("«علي» پیش‌تر ادغام شده است", failed(CustomerMerge.merge(d, "c5", "c3")))
    assertEquals("«علي» خودش پیش‌تر ادغام شده است", failed(CustomerMerge.merge(d, "c3", "c5")))
    //  c4 (که c3 در آن است) در c5 ⇒ c3 هم مستقیم به c5
    d = merged(CustomerMerge.merge(d, "c5", "c4", "2026-10-19", 2, ::newId)).data
    assertEquals("c5", d.debtors.first { it.id == "c3" }.mergedInto)
    assertEquals("c5", d.debtors.first { it.id == "c4" }.mergedInto)
  }

  @Test fun `ردیفِ دیررسیده از دستگاهِ آفلاین به ماندگار می‌رود، یک بار`() {
    var d = merged(CustomerMerge.merge(d0, "c2", "c1", "2026-10-19", 1, ::newId)).data
    assertNull(CustomerMerge.sweep(d, "2026-10-19", 2, ::newId))
    d = d.copy(
      transactions = d.transactions + give("late", "c1", 70.0, "2026-10-18"),
      sales = d.sales + Sale(id = "sl", finalTotal = 70.0, debtorId = "c1", date = "2026-10-18"),
    )
    val swept = CustomerMerge.sweep(d, "2026-10-20", 3, ::newId)!!
    assertEquals("c2", swept.transactions.first { it.id == "late" }.debtorId)
    assertEquals("c2", swept.sales.first { it.id == "sl" }.debtorId)
    assertEquals(970.0, ShopStore.debt(swept, "c2"), 0.0)
    assertEquals("انتقالِ ردیف‌های دیررسیده به مشتریِ ماندگار: 1 تراکنش و 1 فاکتور", swept.auditLog.last().notes)
    assertNull(CustomerMerge.sweep(swept, "2026-10-20", 4, ::newId))
  }
}
