package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  POS-26 — «رسیدِ چاپیِ پیش‌پرداخت»: پیش‌پرداختِ سفارشِ ویژه ردیفِ رسیدِ قرض‌دار است (`kind` = `special_order_deposit`)
 *  ولی صندوق پس از ثبت رسیدی نشان نمی‌داد. حالا همان دم (و از فهرستِ سفارش‌ها برای چاپِ دوباره) رسیدِ همان دفترِ قرض
 *  (`DebtBook.receipt` ⇐ «چاپ / PDF») باز می‌شود. سفارشِ بی پیش‌پرداخت هیچ رسیدی ندارد.
 */
class SpecialOrderDepositReceiptTest {

  private val today = "2026-11-03"
  private var seq = 0
  private val newId = { "id${++seq}" }
  private val base = ShopData(debtors = listOf(Debtor(id = "d1", name = "حمید", phone = "0700123456"), Debtor(id = "d2", name = "کریم")))

  private fun built(b: SpecialOrders.Built): ShopData = (b as SpecialOrders.Built.Ok).data
  private fun order(d: ShopData, key: String, debtor: String, depositMinor: Long) =
    built(SpecialOrders.create(d, key, SpecialOrders.NewOrder(debtor, null, "یخچال", 1000, 2_500_000, depositMinor, "2026-11-10"), today, 400, "رضا", newId))

  @Test
  fun `پیش‌پرداختِ ثبت‌شده رسیدِ چاپیِ همان دفترِ قرض را دارد`() {
    val d = order(base, "o1", "d1", 500_000)
    val tx = SpecialOrders.depositTx(d, "o1")
    assertNotNull(tx)
    assertEquals("d1", tx!!.debtorId)
    assertEquals(SpecialOrders.DEPOSIT, tx.kind)
    assertEquals(5000.0, tx.amount, 0.001)
    val r = DebtBook.receipt(d, tx.debtorId, tx.id, "دکانِ نمونه", today)
    assertNotNull(r)
    assertTrue(r!!.title, r.title.contains("رسیدِ دریافت"))
    val rows = r.rows.toMap()
    assertEquals("حمید", rows["مشتری"])
    assertTrue(rows.toString(), rows["شرح"].orEmpty().contains("پیش‌پرداختِ سفارشِ ویژه — یخچال"))
    assertTrue(rows.toString(), rows.containsKey("مبلغِ دریافتی"))
    //  چاپ و PDF از همان برگهٔ `ReportPrint`
    assertTrue(r.sheet().rows.isNotEmpty())
  }

  @Test
  fun `بی پیش‌پرداخت، سفارشِ دیگر یا بردِ بازپرداخت رسیدِ پیش‌پرداخت نیست`() {
    var d = order(base, "o1", "d1", 0)
    assertNull(SpecialOrders.depositTx(d, "o1"))
    d = order(d, "o2", "d2", 100_000)
    assertNull(SpecialOrders.depositTx(d, "o1"))
    assertEquals("d2", SpecialOrders.depositTx(d, "o2")!!.debtorId)
    assertNull(SpecialOrders.depositTx(d, "گم"))
    //  لغو بردِ بازپرداخت با همان `refId` می‌سازد؛ رسیدِ پیش‌پرداخت همان ردیفِ دریافت می‌ماند
    d = built(SpecialOrders.cancel(d, "c2", "o2", "مشتری منصرف شد", today, 450, "رضا", newId))
    assertEquals("receive", SpecialOrders.depositTx(d, "o2")!!.type)
  }

  @Test
  fun `صندوق پس از ثبت و در فهرست رسید را باز می‌کند`() {
    val s = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/ui/components/pos/SpecialOrderBarAndroid.kt") }.first { it.isFile }.readText()
    assertTrue(s.contains("val tx = data?.let { SpecialOrders.depositTx(it, key) }"))
    assertTrue(s.contains("SpecialOrders.depositTx(d, v.id)?.let { tx ->"))
    assertTrue(s.contains("ir.vil3ntec.tohid.ui.screens.DebtReceiptDialog(data, who, txId, onDismiss = { receipt = null })"))
  }
}
