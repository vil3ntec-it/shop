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
    val wired = mapOf(
      "Sale.kt" to "options.copy(note = cartStore.note.value)",
      "QuickSale.kt" to "options.copy(note = cartStore.note.value)",
      "DeskSale.kt" to "sale.copy(note = cartStore.note.value)",
    )
    for ((f, x) in wired) {
      val s = src("ir/vil3ntec/tohid/ui/screens/$f")
      assertEquals(f, count(s, "cartStore.post("), count(s, x))
      assertTrue(f, s.contains("InvoiceNoteButton(cartStore.note.collectAsState().value, cart.isNotEmpty())"))
    }
  }

  @Test fun `نمای فاکتور یادداشتِ ردیف و فاکتور را نشان می‌دهد`() {
    val s = src("ir/vil3ntec/tohid/ui/screens/SaleDialogs.kt")
    val dialog = s.substring(s.indexOf("fun InvoiceDialog("))
    assertTrue(dialog.contains("item.note?.takeIf { it.isNotBlank() }"))
    assertTrue(dialog.contains("sale.note?.takeIf { it.isNotBlank() }"))
  }
}
