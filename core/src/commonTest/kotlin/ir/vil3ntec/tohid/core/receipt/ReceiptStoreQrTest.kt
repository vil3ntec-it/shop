package ir.vil3ntec.tohid.core.receipt

import ir.vil3ntec.tohid.core.marketing.CampaignLinks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 *  MKT-10: QR ِ ویترینِ دکان زیرِ فاکتورِ فروش — فقط پیوندِ کمپینِ درست، بالای پایان‌نوشته و زیرِ QR ِ خودِ فاکتور؛
 *  بی تنظیم همان رسیدِ امروز (بایت‌به‌بایت همان بلوک‌ها).
 */
class ReceiptStoreQrTest {

  private class Fmt : ReceiptFormat {
    override fun money(afn: Double) = "$afn"
    override fun amount(afn: Double) = "$afn"
    override fun qty(q: Double) = "$q"
    override fun digits(text: String) = text
    override fun phone(text: String) = text
    override fun date(iso: String) = iso
  }

  private val good = "https://api.vill3n.top/store/go/7K2M9QXR4T"

  private fun input(storeQr: String? = null, receiptQr: String? = null) = ReceiptInput(
    shop = ShopHeader("دکان"), invoiceNo = "B1-1", dateIso = "2026-11-01", time = "10:00", cashier = "علی", kind = "نقدی",
    cancelled = false, items = listOf(LineView("نان", 1.0, "عدد", 20.0, 20.0)), subtotal = 20.0,
    discounts = emptyList(), total = 20.0, payments = emptyList(), paid = 20.0, qrUrl = receiptQr, storeQrUrl = storeQr,
  )

  @Test
  fun withoutTheSettingTheReceiptIsUnchanged() {
    assertEquals(ReceiptLayout.build(input(), 384, Fmt()), ReceiptLayout.build(input(storeQr = null), 384, Fmt()))
    assertEquals(0, ReceiptLayout.build(input(), 384, Fmt()).blocks.filterIsInstance<ReceiptBlock.Qr>().size)
  }

  @Test
  fun storeQrComesUnderTheReceiptQrAndAboveTheFooter() {
    val doc = ReceiptLayout.build(input(storeQr = good, receiptQr = "https://api.vill3n.top/r/abc"), 384, Fmt())
    val qrs = doc.blocks.filterIsInstance<ReceiptBlock.Qr>()
    assertEquals(listOf("https://api.vill3n.top/r/abc", good), qrs.map { it.url })
    val i = doc.blocks.indexOfFirst { it is ReceiptBlock.Text && it.text == ReceiptLabels.FA.storeScan }
    val q = doc.blocks.indexOf(qrs[1])
    val f = doc.blocks.indexOfFirst { it is ReceiptBlock.Text && it.text == ReceiptLabels.FA.footer }
    assertTrue(i in 0 until q && q < f, "نوشته، سپس QR، سپس پایان")
  }

  @Test
  fun onlyCampaignLinksAreAccepted() {
    assertTrue(CampaignLinks.receiptUrlOk(good))
    assertTrue(CampaignLinks.receiptUrlOk("http://192.168.1.5:8080/store/go/7K2M9QXR4T"))
    for (bad in listOf(
      "", "https://evil.example/pay", "https://api.vill3n.top/store/go/7K2M9QXR4T?x=1", "javascript:alert(1)",
      "https://api.vill3n.top/store/go/short", "ftp://api.vill3n.top/store/go/7K2M9QXR4T", "https:///store/go/7K2M9QXR4T",
    )) {
      assertFalse(CampaignLinks.receiptUrlOk(bad), bad)
      assertEquals(0, ReceiptLayout.build(input(storeQr = bad), 384, Fmt()).blocks.filterIsInstance<ReceiptBlock.Qr>().size, bad)
    }
  }
}
