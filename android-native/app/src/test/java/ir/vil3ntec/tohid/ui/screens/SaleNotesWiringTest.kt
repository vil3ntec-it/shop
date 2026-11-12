package ir.vil3ntec.tohid.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  POS-11 — یادداشتِ فاکتور مالِ `CartStore` است و هر صندوقی که سبد را ثبت می‌کند باید آن را به `Checkout.note`
 *  بدهد؛ وگرنه یادداشتی که در صفحهٔ دیگر روی همین سبد نوشته شد بی‌صدا گم می‌شد (`QuickSale`). نمای فاکتور
 *  (تاریخچه، پس از فروش) یادداشتِ ردیف و فاکتور را نشان می‌دهد. حالتِ Compose، پس متن سنجیده می‌شود.
 */
class SaleNotesWiringTest {

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, rel) }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  private fun count(s: String, x: String) = Regex(Regex.escape(x)).findAll(s).count()

  @Test fun `هر ثبتِ سبد یادداشتِ فاکتورِ همان سبد را می‌برد`() {
    //  کامپیوتر
    val desk = src("ir/vil3ntec/tohid/ui/screens/DeskSale.kt")
    assertEquals(count(desk, "cartStore.post("), count(desk, "sale.copy(note = cartStore.note.value)"))
    assertTrue(desk.contains("InvoiceNoteButton(cartStore.note.collectAsState().value, cart.isNotEmpty())"))
    //  گوشیِ نسخهٔ ۳: صندوق و فروشِ سریع هر دو با همان برگهٔ پرداخت (`CheckoutRoute`) ثبت می‌کنند و یادداشت کنارِ سبد است
    //  (`PhoneTillTools`)؛ `ui/screens/Sale.kt` و `QuickSale.kt` فقط پیش‌نمایشِ DevShot‌اند
    val pay = src("ir/vil3ntec/tohid/ui/v3/sales/checkout/CheckoutRoute.kt")
    assertEquals(1, count(pay, "cart.post("))
    assertTrue(pay.contains("val note = cart.note.value"))
    assertTrue(Regex("""TillSale\.record\(\s*d, priced, sale, note,""").containsMatchIn(pay))
    assertTrue(src("ir/vil3ntec/tohid/ui/v3/sales/cart/PhoneTillTools.kt").contains("InvoiceNoteButton(cart.note.collectAsState().value, lines.isNotEmpty()) { cart.setNote(it) }"))
  }

  @Test fun `نمای فاکتور یادداشتِ ردیف و فاکتور را نشان می‌دهد`() {
    val s = src("ir/vil3ntec/tohid/ui/screens/SaleDialogs.kt")
    val dialog = s.substring(s.indexOf("fun InvoiceDialog("))
    assertTrue(dialog.contains("item.note?.takeIf { it.isNotBlank() }"))
    assertTrue(dialog.contains("sale.note?.takeIf { it.isNotBlank() }"))
  }
}
