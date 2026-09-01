package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  قرض‌داران.
 *
 *  مانده هیچ‌جا ذخیره نمی‌شود؛ همیشه از تراکنش‌ها حساب می‌شود. این آزمون‌ها
 *  می‌سنجند که همان یک فرمول از هر راهی که تراکنش ساخته شود — با دست یا
 *  از فروشِ نسیه — یک جواب بدهد.
 */
class DebtorEngineTest {

  private var seq = 0
  private fun newId(): String = "q${seq++}"

  private val base = ShopData(
    products = listOf(Product(id = "p1", name = "برنج", salePrice = 300.0, purchasePrice = 200.0)),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "p1", units = 100.0)),
    debtors = listOf(Debtor(id = "d1", name = "احمد", phone = "0700")),
  )

  private fun ok(r: DebtorEngine.Result): DebtorEngine.Result.Ok {
    assertTrue("انتظار می‌رفت ثبت شود، ولی: $r", r is DebtorEngine.Result.Ok)
    return r as DebtorEngine.Result.Ok
  }

  private fun failed(r: DebtorEngine.Result): String {
    assertTrue("انتظار می‌رفت رد شود", r is DebtorEngine.Result.Failed)
    return (r as DebtorEngine.Result.Failed).message
  }

  private fun give(d: ShopData, amount: Double, date: String = "2026-08-10") =
    ok(DebtorEngine.addTransaction(d, "d1", DebtorEngine.Kind.GIVE, amount, date, "", "2026-08-28", 0, ::newId)).data

  private fun receive(d: ShopData, amount: Double, date: String = "2026-08-20") =
    ok(DebtorEngine.addTransaction(d, "d1", DebtorEngine.Kind.RECEIVE, amount, date, "", "2026-08-28", 0, ::newId)).data

  /* ---------------------------- خودِ قرض‌دار ---------------------------- */

  @Test
  fun `قرض دار تازه با نام تمیز ثبت می شود`() {
    val r = ok(DebtorEngine.add(base, DebtorEngine.DebtorDraft(name = "  محمود  ", phone = "0788"), 5, ::newId))
    val fresh = r.data.debtors.first { it.id == r.id }
    assertEquals("محمود", fresh.name)
    assertEquals("0788", fresh.phone)
    assertEquals(5L, fresh.createdAt)
    assertEquals(0.0, ShopStore.debt(r.data, r.id), 0.0)
  }

  @Test
  fun `قرض دار بی نام ثبت نمی شود`() {
    assertEquals(
      "نام قرض‌دار را بنویسید",
      failed(DebtorEngine.add(base, DebtorEngine.DebtorDraft(name = "   "), 0, ::newId)),
    )
  }

  @Test
  fun `حذف قرض دار تراکنش هایش را هم می برد`() {
    val d = receive(give(base, 5000.0), 2000.0)
    assertEquals(2, d.transactions.size)

    val r = ok(DebtorEngine.delete(d, "d1"))
    assertTrue(r.data.debtors.isEmpty())
    assertTrue(r.data.transactions.isEmpty())
  }

  @Test
  fun `هشدار حذف، بدهی باقی مانده را می گوید`() {
    val d = give(base, 5000.0)
    val warning = DebtorEngine.deleteWarning(d, "d1")
    assertTrue(warning, warning.contains("5,000 افغانی بدهی دارد"))
    assertTrue(warning, warning.contains("1 تراکنش"))

    // حسابِ صاف، هشدارِ دیگری دارد
    val settled = receive(d, 5000.0)
    assertTrue(DebtorEngine.deleteWarning(settled, "d1").contains("2 تراکنش او حذف خواهند شد"))
  }

  /* ---------------------------- تراکنش‌ها ---------------------------- */

  @Test
  fun `قرض دادن مانده را بالا می برد و پرداخت پایین`() {
    var d = give(base, 5000.0)
    assertEquals(5000.0, ShopStore.debt(d, "d1"), 0.0)

    d = receive(d, 2000.0)
    assertEquals(3000.0, ShopStore.debt(d, "d1"), 0.0)

    // پرداختِ بیشتر از بدهی یعنی موجودی — مانده منفی می‌شود
    d = receive(d, 4000.0)
    assertEquals(-1000.0, ShopStore.debt(d, "d1"), 0.0)
    assertEquals("1,000 افغانی موجودی دارد", DebtorEngine.stateText(ShopStore.debt(d, "d1")))
  }

  @Test
  fun `فقط پرداخت در دفترچه ی ثبت می نشیند نه قرض دادن`() {
    val given = give(base, 5000.0)
    assertTrue("قرض دادن نباید رویداد بسازد", given.auditLog.isEmpty())

    val paid = receive(given, 2000.0)
    val entry = paid.auditLog.single()
    assertEquals("customer_payment", entry.type)
    assertEquals("پرداخت مشتری «احمد» به مبلغ 2,000 افغانی", entry.notes)
  }

  @Test
  fun `تراکنش با مبلغ صفر یا منفی ثبت نمی شود`() {
    listOf(0.0, -100.0).forEach { amount ->
      assertEquals(
        "مبلغ معتبر وارد کنید",
        failed(
          DebtorEngine.addTransaction(base, "d1", DebtorEngine.Kind.GIVE, amount, "", "", "2026-08-28", 0, ::newId)
        ),
      )
    }
  }

  @Test
  fun `تراکنش برای قرض دار ناموجود ثبت نمی شود`() {
    assertEquals(
      "قرض‌دار پیدا نشد",
      failed(
        DebtorEngine.addTransaction(base, "نیست", DebtorEngine.Kind.GIVE, 100.0, "", "", "2026-08-28", 0, ::newId)
      ),
    )
  }

  @Test
  fun `تاریخ خالی یعنی امروز`() {
    val r = ok(
      DebtorEngine.addTransaction(base, "d1", DebtorEngine.Kind.GIVE, 100.0, "", "", "2026-08-28", 0, ::newId)
    )
    assertEquals("2026-08-28", r.data.transactions.single().date)
  }

  @Test
  fun `حذف تراکنش مانده را برمی گرداند`() {
    val d = give(base, 5000.0)
    val txId = d.transactions.single().id
    val r = ok(DebtorEngine.deleteTransaction(d, txId))
    assertEquals(0.0, ShopStore.debt(r.data, "d1"), 0.0)
    assertTrue(r.data.transactions.isEmpty())
  }

  /* ------------------------------ حساب ------------------------------ */

  @Test
  fun `حساب، داده و گرفته را جدا و تازه ترین را اول نشان می دهد`() {
    var d = base
    d = ok(DebtorEngine.addTransaction(d, "d1", DebtorEngine.Kind.GIVE, 5000.0, "2026-08-01", "", "2026-08-28", 100, ::newId)).data
    d = ok(DebtorEngine.addTransaction(d, "d1", DebtorEngine.Kind.RECEIVE, 2000.0, "2026-08-10", "", "2026-08-28", 200, ::newId)).data
    d = ok(DebtorEngine.addTransaction(d, "d1", DebtorEngine.Kind.GIVE, 1000.0, "2026-08-20", "", "2026-08-28", 300, ::newId)).data

    val account = DebtorEngine.account(d, "d1")!!
    assertEquals(6000.0, account.given, 0.0)
    assertEquals(2000.0, account.received, 0.0)
    assertEquals(4000.0, account.balance, 0.0)
    assertEquals(3, account.transactions.size)
    assertEquals(300L, account.transactions.first().createdAt)   // تازه‌ترین اول
  }

  /* --------------------- پیوند با فروشِ نسیه --------------------- */

  @Test
  fun `فروش نسیه در همان حساب می نشیند، نه جای جدا`() {
    val sold = (SalesEngine.record(
      base, listOf(SalesEngine.CartLine("p1", 10.0)),
      SalesEngine.Checkout(payment = SalesEngine.Payment.CREDIT, paidAmount = 0.0, debtorId = "d1"),
      "2026-08-28", 0, ::newId,
    ) as SalesEngine.Result.Ok).data

    val account = DebtorEngine.account(sold, "d1")!!
    assertEquals(3000.0, account.given, 0.0)
    assertEquals(3000.0, account.balance, 0.0)
    assertEquals(1, account.transactions.size)

    // و پرداختِ دستی همان بدهی را کم می‌کند
    val paid = receive(sold, 1000.0)
    assertEquals(2000.0, ShopStore.debt(paid, "d1"), 0.0)
    assertEquals("2,000 افغانی بدهکار", DebtorEngine.stateText(ShopStore.debt(paid, "d1")))
  }

  @Test
  fun `حساب صاف همان جمله ی نسخه ی وب را می دهد`() {
    assertEquals("حساب صاف است", DebtorEngine.stateText(0.0))
  }

  /* --------------------------- مدتِ قرض --------------------------- */

  private fun tx(
    d: ShopData,
    kind: DebtorEngine.Kind,
    amount: Double,
    date: String,
    at: Long,
  ): ShopData = ok(
    DebtorEngine.addTransaction(d, "d1", kind, amount, date, "", "2026-08-28", at, ::newId)
  ).data

  @Test
  fun `حساب صاف مدت قرض ندارد`() {
    var d = tx(base, DebtorEngine.Kind.GIVE, 1000.0, "2026-08-01", 100)
    d = tx(d, DebtorEngine.Kind.RECEIVE, 1000.0, "2026-08-05", 200)
    assertEquals(null, DebtorEngine.debtSince(d, "d1"))
    assertEquals(null, DebtorEngine.debtDays(d, "d1"))
  }

  @Test
  fun `قرض داری که تراکنشی ندارد مدت هم ندارد`() {
    assertEquals(null, DebtorEngine.debtSince(base, "d1"))
  }

  @Test
  fun `مدت از قدیمی ترین قرضِ پس داده نشده حساب می شود`() {
    var d = tx(base, DebtorEngine.Kind.GIVE, 1000.0, "2026-08-01", 100)
    d = tx(d, DebtorEngine.Kind.GIVE, 500.0, "2026-08-20", 200)
    //  هزار گرفته شد: قرضِ اولِ کامل صاف می‌شود و فقط دومی می‌ماند
    d = tx(d, DebtorEngine.Kind.RECEIVE, 1000.0, "2026-08-25", 300)
    assertEquals(500.0, ShopStore.debt(d, "d1"), 0.0)
    assertEquals(200L, DebtorEngine.debtSince(d, "d1"))
  }

  @Test
  fun `پرداختِ نصفه، قرضِ قدیمی را باز نگه می دارد`() {
    var d = tx(base, DebtorEngine.Kind.GIVE, 1000.0, "2026-08-01", 100)
    d = tx(d, DebtorEngine.Kind.GIVE, 500.0, "2026-08-20", 200)
    d = tx(d, DebtorEngine.Kind.RECEIVE, 400.0, "2026-08-25", 300)
    assertEquals(1100.0, ShopStore.debt(d, "d1"), 0.0)
    //  از قرضِ اول ۶۰۰ مانده، پس تاریخِ همان است
    assertEquals(100L, DebtorEngine.debtSince(d, "d1"))
  }

  @Test
  fun `پیش پرداخت روی قرضِ بعدی می نشیند`() {
    var d = tx(base, DebtorEngine.Kind.RECEIVE, 300.0, "2026-08-01", 100)
    d = tx(d, DebtorEngine.Kind.GIVE, 200.0, "2026-08-10", 200)
    //  ۳۰۰ پیش داده و ۲۰۰ برده: هنوز بدهکار نیست
    assertEquals(-100.0, ShopStore.debt(d, "d1"), 0.0)
    assertEquals(null, DebtorEngine.debtSince(d, "d1"))

    d = tx(d, DebtorEngine.Kind.GIVE, 400.0, "2026-08-15", 300)
    assertEquals(300.0, ShopStore.debt(d, "d1"), 0.0)
    assertEquals(300L, DebtorEngine.debtSince(d, "d1"))
  }

  @Test
  fun `ردیفِ بدونِ ساعت، از تاریخش خوانده می شود`() {
    //  ردیف‌های نسخهٔ وب و فایلِ پشتیبانِ قدیمی `createdAt` ندارند
    val d = base.copy(
      transactions = listOf(
        DebtTransaction(id = "t1", debtorId = "d1", type = "give", amount = 900.0, date = "2026-08-01"),
      )
    )
    assertEquals(ir.vil3ntec.tohid.isoMillis("2026-08-01"), DebtorEngine.debtSince(d, "d1"))
  }

  @Test
  fun `روزهای قرض از همان تاریخ شمرده می شود`() {
    val d = base.copy(
      transactions = listOf(
        DebtTransaction(id = "t1", debtorId = "d1", type = "give", amount = 900.0, date = "2026-08-01"),
      )
    )
    val since = ir.vil3ntec.tohid.isoMillis("2026-08-01")
    assertEquals(10L, DebtorEngine.debtDays(d, "d1", since + 10 * 86_400_000L))
    //  ساعتِ عقب‌رفتهٔ گوشی، عددِ منفی نمی‌دهد
    assertEquals(0L, DebtorEngine.debtDays(d, "d1", since - 86_400_000L))
  }
}
