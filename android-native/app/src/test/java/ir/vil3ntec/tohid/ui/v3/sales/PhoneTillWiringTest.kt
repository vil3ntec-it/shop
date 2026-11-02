package ir.vil3ntec.tohid.ui.v3.sales

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  PRM-13 و POS-11 در صندوقِ گوشیِ نسخهٔ ۳ — همان راهِ صندوقِ کامپیوتر (`DeskSale`): `rememberLoyaltyTill` برای مشتریِ همین
 *  فاکتور ⇐ سبدِ `loyal` در جمع و سقفِ اعتبار ⇐ ثبت با `TillSale.record` (یادداشتِ `CartStore` و امتیازِ
 *  `LoyaltyAtTill.points`)؛ نوارِ امتیاز در برگهٔ پرداخت و «یادداشتِ فاکتور» کنارِ سبد. Compose است، پس متن سنجیده
 *  می‌شود؛ رفتارِ خودِ ثبت در `TillSaleTest`.
 */
class PhoneTillWiringTest {

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/$rel") }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  private val checkout = src("ui/v3/sales/checkout/CheckoutRoute.kt")

  @Test
  fun `پرداختِ گوشی امتیاز را از همان راهِ صندوقِ کامپیوتر می‌گیرد`() {
    assertTrue(checkout.contains("val loyalty = rememberLoyaltyTill(data, priced, form.debtorId)"))
    assertTrue(checkout.contains("val loyal = loyalty?.cart ?: priced"))
    assertTrue("جمع با سبدِ پس از امتیاز", checkout.contains("CheckoutModel.numbers(data, loyal, form, checkout)"))
    assertTrue("سقفِ اعتبار با جمعِ پس از امتیاز", checkout.contains("SalesEngine.creditOver(data, loyal, gated)"))
    assertTrue("ردیفِ «مصرفِ امتیاز»", checkout.contains("CheckoutModel.rows(n, promo, payV2, loyalty?.points ?: 0, loyalty?.off ?: 0.0)"))
    assertTrue("نوارِ امتیاز در برگه", checkout.contains("LoyaltyBar(loyalty)"))
    assertTrue(checkout.contains("val points = LoyaltyAtTill.points(context, loyalty)"))
  }

  @Test
  fun `ثبتِ گوشی یادداشت و امتیاز را با همان زنجیره می‌برد`() {
    assertTrue(checkout.contains("val note = cart.note.value"))
    assertTrue(Regex("""TillSale\.record\(\s*d, priced, sale, note, promo, points,""").containsMatchIn(checkout))
    //  هیچ ثبتِ مستقیمی که یادداشت یا امتیاز را جا بیندازد نمانده
    assertEquals(0, Regex("""SalesEngine\.record\(""").findAll(checkout).count())
    //  صندوقِ کامپیوتر هم امتیاز را از همان `Points` می‌گیرد (یک قاعده، دو صفحه)
    assertTrue(src("ui/components/pos/LoyaltyBarAndroid.kt").contains("return points(context, till).post(r, by)"))
  }

  @Test
  fun `یادداشتِ فاکتور کنارِ سبدِ گوشی است و رسید آن را و امتیاز را نشان می‌دهد`() {
    assertTrue(src("ui/v3/sales/cart/PhoneTillTools.kt").contains("InvoiceNoteButton(cart.note.collectAsState().value, lines.isNotEmpty()) { cart.setNote(it) }"))
    assertTrue(src("ui/v3/sales/history/ReceiptCard.kt").contains("ReceiptModel.extras(d, sale)"))
  }
}
