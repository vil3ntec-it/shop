package ir.vil3ntec.tohid.desktop

import android.graphics.Bitmap
import ir.vil3ntec.tohid.core.receipt.ReceiptToken
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.SalePayment
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.print.Receipt
import ir.vil3ntec.tohid.print.Receipts
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 *  فاکتورِ نسخهٔ ۲ پشتِ پرچمِ `shop.receipt_v2` (پیش‌فرض خاموش): خاموش ⇒ همان رفتارِ پیشین
 *  (بی توکن، بی نشانی، همان تصویرِ فاکتور حتی برای فروشِ توکن‌دار)؛ روشن ⇒ توکن، نشانی و
 *  چیدمانِ تازه با ردیف‌های پرداختِ ترکیبیِ همان `PayFlow`.
 */
class ReceiptV2DeskTest {

  private val shopId = "shp_receipt_v2_test"
  private val ledger = DesktopContext.getSharedPreferences("tohid-ledger", 0)
  private val flags = DesktopContext.getSharedPreferences("tohid-receipt", 0)

  private val token = "3t3gq9p07z23q5tf2b58cggxv6"
  private val sale = Sale(
    id = "s-v2", invoiceNumber = 12, total = 500.0, finalTotal = 500.0, paidAmount = 500.0,
    createdAt = 1L, date = "2026-10-24", paymentMethod = "mixed",
    payments = listOf(
      SalePayment(method = "cash", kind = "cash", amountMinor = 30000, status = "confirmed"),
      SalePayment(method = "hesabpay", kind = "wallet", amountMinor = 20000, status = "confirmed"),
    ),
    tenderedMinor = 60000, changeMinor = 10000,
  )
  private val d = ShopData(
    sales = listOf(sale),
    saleItems = listOf(SaleItem(id = "i1", saleId = "s-v2", name = "بیسکویت", quantity = 2.0, unitPrice = 250.0, totalPrice = 500.0)),
  )

  private var prevShop: String? = null

  @Before fun shop() {
    prevShop = ledger.getString("owner_shop_id", null)
    ledger.edit().putString("owner_shop_id", shopId).commit()
    flags.edit().clear().commit()
  }

  @After fun clean() {
    flags.edit().clear().commit()
    ledger.edit().apply { if (prevShop == null) remove("owner_shop_id") else putString("owner_shop_id", prevShop) }.commit()
  }

  private fun on() = flags.edit().putBoolean("v2:$shopId", true).commit()

  private fun same(a: Bitmap, b: Bitmap): Boolean {
    if (a.width != b.width || a.height != b.height) return false
    for (y in 0 until a.height) for (x in 0 until a.width) if (a.getPixel(x, y) != b.getPixel(x, y)) return false
    return true
  }

  @Test fun `خاموش — بی توکن، بی نشانی، و همان تصویرِ پیشین حتی برای فروشِ توکن‌دار`() {
    assertNull(Receipts.newToken(DesktopContext))
    val withToken = sale.copy(receiptToken = token)
    assertNull(Receipts.publicUrl(DesktopContext, withToken))
    val before = Receipt.render(DesktopContext, d, sale, "فروشگاه", 384)
    val after = Receipt.render(DesktopContext, d.copy(sales = listOf(withToken)), withToken, "فروشگاه", 384)
    assertTrue("پرچمِ خاموش: توکن هیچ اثری روی فاکتور ندارد", same(before, after))
  }

  @Test fun `روشن — توکنِ تازه، نشانیِ QR و چیدمانِ تازه با پرداختِ ترکیبی`() {
    val off = Receipt.render(DesktopContext, d, sale, "فروشگاه", 384)
    on()
    val fresh = Receipts.newToken(DesktopContext)
    assertNotNull(fresh)
    assertEquals(fresh, ReceiptToken.normalizeToken(fresh))
    val withToken = sale.copy(receiptToken = token)
    val url = Receipts.publicUrl(DesktopContext, withToken)
    //  ریشهٔ سرورِ ساختِ آزمایشی ممکن است خالی باشد ⇒ بی QR، هرگز نشانیِ نسبی
    if (url != null) assertTrue(url, url.matches(Regex("^https?://.+/r/[0-9a-z]{8}/$token$")))

    val input = Receipts.input(DesktopContext, d, withToken, 384)
    assertEquals(listOf("دریافتی", "پولِ پس"), input.payments.takeLast(2).map { it.label })
    assertEquals(listOf(300.0, 200.0, 600.0, 100.0), input.payments.map { it.amount })
    assertEquals(null, input.change)
    assertFalse(same(off, Receipt.render(DesktopContext, d.copy(sales = listOf(withToken)), withToken, "فروشگاه", 384)))
  }
}
