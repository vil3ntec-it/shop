package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import ir.vil3ntec.tohid.sync.v1.SyncOp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  ادغامِ سه‌طرفهٔ ذخیرهٔ کهنه (`LedgerMerge`): پایه = دفتری که صفحه با آن ساخت، مالِ ما = دفترِ
 *  تازهٔ صفحه، مالِ آن‌ها = دیسک پس از Pull ِ کارگر. سنجهٔ نهایی همیشه همان `record` است: تفاضل
 *  با سایه‌ای که کارگر جلو برده فقط تغییرِ واقعیِ کاربر را بسازد.
 */
class LedgerMergeTest {

  private val p1 = Product(id = "p1", name = "برنج", salePrice = 100.0)
  private val p2 = Product(id = "p2", name = "شکر", salePrice = 90.0)
  private val p3 = Product(id = "p3", name = "چای", salePrice = 50.0)
  private val sale = Sale(id = "s1", finalTotal = 500.0, remaining = 500.0, createdAt = 10)

  /** همان شکلِ دیسک: هر بار JSON ِ جدا، بی شیءِ مشترک */
  private fun disk(d: ShopData): ShopData = LedgerDiff.fromJson(LedgerDiff.toJson(d))

  /** opهایی که `record` پس از نوشتنِ [merged] می‌سازد، وقتی سایه همان دیسک است */
  private fun ops(theirs: ShopData, merged: ShopData): List<String> =
    LedgerDiff.diff(LedgerDiff.shadowOf(theirs), LedgerDiff.toJson(merged)).map { "${it.table}:${it.rowId}:${it.type}" }

  @Test fun `ردیف و ویرایشِ تازهٔ دیسک می‌مانند و فقط فروشِ صفحه op می‌سازد`() {
    val base = ShopData(products = listOf(p1))
    val theirs = disk(ShopData(products = listOf(p1.copy(name = "برنج اعلا"), p2)))
    val ours = base.copy(sales = listOf(sale))
    val merged = LedgerMerge.merge(base, ours, theirs)
    assertEquals(listOf("برنج اعلا", "شکر"), merged.products.map { it.name })
    assertEquals(listOf("s1"), merged.sales.map { it.id })
    assertEquals(listOf("sales:s1:insert"), ops(theirs, merged))
  }

  @Test fun `بی ادغام همان باگ — دفترِ کهنه ردیفِ تازه را حذف و ویرایش را برمی‌گرداند`() {
    val base = ShopData(products = listOf(p1))
    val theirs = disk(ShopData(products = listOf(p1.copy(name = "برنج اعلا"), p2)))
    val ours = base.copy(sales = listOf(sale))
    val raw = ops(theirs, ours)
    assertTrue(raw.toString(), "products:p2:delete" in raw && "products:p1:update" in raw)
  }

  @Test fun `یک ردیف، هم‌زمان — فیلدهای جدا هر دو می‌مانند؛ فیلدِ یکسان کاربر برنده`() {
    val base = ShopData(products = listOf(p1))
    val theirs = disk(ShopData(products = listOf(p1.copy(name = "برنج اعلا", salePrice = 200.0))))
    val ours = base.copy(products = listOf(p1.copy(salePrice = 150.0, notes = "قفسهٔ دوم")))
    val merged = LedgerMerge.merge(base, ours, theirs).products.single()
    assertEquals("برنج اعلا", merged.name)
    assertEquals(150.0, merged.salePrice, 0.0)
    assertEquals("قفسهٔ دوم", merged.notes)
    val op = LedgerDiff.diff(LedgerDiff.shadowOf(theirs), LedgerDiff.toJson(ShopData(products = listOf(merged)))).single()
    assertEquals(SyncOp.UPDATE, op.type)
    assertEquals(setOf("salePrice", "notes"), op.fields!!.keys)
  }

  @Test fun `حذفِ واقعیِ کاربر هنوز DELETE می‌سازد و ردیفِ تازهٔ دیسک می‌ماند`() {
    val base = ShopData(products = listOf(p1, p2))
    val theirs = disk(ShopData(products = listOf(p1, p2, p3)))
    val ours = base.copy(products = base.products.filter { it.id != "p1" })
    val merged = LedgerMerge.merge(base, ours, theirs)
    assertEquals(listOf("p2", "p3"), merged.products.map { it.id })
    assertEquals(listOf("products:p1:delete"), ops(theirs, merged))
  }

  @Test fun `حذف می‌برد — ویرایشِ کهنهٔ صفحه ردیفِ حذف‌شدهٔ دستگاهِ دیگر را زنده نمی‌کند، و برعکس`() {
    val base = ShopData(products = listOf(p1, p2))
    //  دستگاهِ دیگر p1 را حذف کرد؛ صفحه همان را ویرایش کرد
    val gone = disk(ShopData(products = listOf(p2)))
    val edited = base.copy(products = listOf(p1.copy(salePrice = 130.0), p2))
    assertEquals(listOf("p2"), LedgerMerge.merge(base, edited, gone).products.map { it.id })
    assertEquals(emptyList<String>(), ops(gone, LedgerMerge.merge(base, edited, gone)))
    //  دستگاهِ دیگر p1 را ویرایش کرد؛ کاربر حذفش کرد ⇒ حذفِ کاربر
    val renamed = disk(ShopData(products = listOf(p1.copy(name = "برنج اعلا"), p2)))
    val deleted = base.copy(products = listOf(p2))
    assertEquals(listOf("products:p1:delete"), ops(renamed, LedgerMerge.merge(base, deleted, renamed)))
  }

  @Test fun `شمارنده‌ها جمع می‌شوند — پرداختِ دستگاهِ دیگر گم نمی‌شود`() {
    val base = ShopData(sales = listOf(sale))
    val theirs = disk(ShopData(sales = listOf(sale.copy(paidAmount = 100.0, remaining = 400.0))))
    val ours = base.copy(sales = listOf(sale.copy(paidAmount = 50.0, remaining = 450.0, seller = "علی")))
    val merged = LedgerMerge.merge(base, ours, theirs).sales.single()
    assertEquals(150.0, merged.paidAmount, 0.0)
    assertEquals(350.0, merged.remaining, 0.0)
    assertEquals("علی", merged.seller)
    val f = LedgerDiff.diff(LedgerDiff.shadowOf(theirs), LedgerDiff.toJson(ShopData(sales = listOf(merged)))).single().fields!!
    assertEquals("50", ((f["paidAmount"] as JsonObject)["\$inc"])!!.jsonPrimitive.content)
    assertEquals("-50", ((f["remaining"] as JsonObject)["\$inc"])!!.jsonPrimitive.content)
  }

  @Test fun `فهرستِ نام‌ها — افزوده و حذفِ هر طرف`() {
    val base = ShopData(productCategories = listOf("لبنیات", "نوشیدنی"))
    val theirs = disk(base.copy(productCategories = listOf("لبنیات", "نوشیدنی", "حبوبات")))
    val ours = base.copy(productCategories = listOf("لبنیات", "شوینده"))
    assertEquals(listOf("لبنیات", "شوینده", "حبوبات"), LedgerMerge.merge(base, ours, theirs).productCategories)
  }

  @Test fun `شمارهٔ فاکتورِ بعدی هرگز عقب نمی‌رود`() {
    val base = ShopData(nextInvoiceNo = 5, products = listOf(p1))
    val theirs = disk(base.copy(nextInvoiceNo = 7, products = listOf(p1, p2)))
    assertEquals(7, LedgerMerge.merge(base, base.copy(nextInvoiceNo = 6, sales = listOf(sale)), theirs).nextInvoiceNo)
    val same = disk(base.copy(products = listOf(p1, p2)))
    assertEquals(6, LedgerMerge.merge(base, base.copy(nextInvoiceNo = 6, sales = listOf(sale)), same).nextInvoiceNo)
  }

  @Test fun `دیسکِ هم‌محتوا با پایه (خواندنِ دوباره، کوچِ رمز) همان دفترِ صفحه را می‌دهد`() {
    val base = ShopData(products = listOf(p1, p2))
    val ours = base.copy(sales = listOf(sale))
    assertSame(ours, LedgerMerge.merge(base, ours, disk(base)))
    assertSame(ours, LedgerMerge.merge(base, ours, base))
  }

  @Test fun `پایه — دفتری که صفحه با آن ساخت، با ردیف‌های همان شیء`() {
    val screen = disk(ShopData(products = listOf(p1, p2), sales = listOf(sale)))
    //  کارگر نوشت: دفترِ تازهٔ همین نمونه از JSON ِ جداست
    val current = disk(screen.copy(products = screen.products + p3))
    val stale = screen.copy(sales = screen.sales + sale.copy(id = "s2"))
    assertSame(screen, LedgerMerge.baseOf(stale, current, listOf(screen)))
    val fresh = current.copy(sales = current.sales + sale.copy(id = "s2"))
    assertSame(current, LedgerMerge.baseOf(fresh, current, listOf(screen)))
    //  هیچ نشانه (دفترِ کاملاً تازه، مثلِ بازیابی) ⇒ دفترِ همین حالا
    assertSame(current, LedgerMerge.baseOf(disk(stale), current, listOf(screen)))
    //  حذفِ ردیفی که همین نمونه افزوده بود: پایه همان دفترِ همین حالاست، نه دفترِ پیش از افزودن
    val undo = current.copy(products = current.products.filter { it.id != "p3" })
    assertSame(current, LedgerMerge.baseOf(undo, current, listOf(screen)))
    assertTrue(LedgerMerge.kinship(stale, screen) > LedgerMerge.kinship(stale, current))
  }
}
