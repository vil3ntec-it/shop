package ir.vil3ntec.tohid.ui.v3

import ir.vil3ntec.tohid.data.RejectedDocs
import ir.vil3ntec.tohid.sync.v3.V3Entry
import ir.vil3ntec.tohid.ui.v3.shell.ShellPaths
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  صندوقِ سندهای ردشده — سیم‌کشیِ رابط (Compose با آزمونِ JVM ساخته نمی‌شود، پس مثلِ `RemoteApprovalWiringTest` با متنِ
 *  همان پرونده‌ها) و خواندنِ دفترِ ردشده‌های v1 و v3. منطق در `ConflictInboxTest` (core)؛ کامپیوتر: `ConflictInboxDesktopTest`.
 */
class ConflictInboxWiringTest {

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/$rel") }.firstOrNull { it.isFile }?.readText()
      ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  @Test
  fun `صندوقِ صاحب و مدیر در پوستهٔ گوشی و کامپیوتر، با نوار`() {
    assertTrue(src("ui/v3/shell/ShellNotice.kt").contains("ConflictInboxNotice()"))
    val routes = src("ui/v3/shell/ShellRoutes.kt")
    assertTrue(routes.contains("V3Route(ShellPaths.CONFLICTS, RouteKind.PAGE"))
    assertTrue(routes.contains("ConflictInboxRoute()"))
    assertEquals("conflicts", ShellPaths.CONFLICTS)
    val inbox = src("ui/v3/shell/ConflictInbox.kt")
    //  فقط صاحب/مدیر از سرور می‌پرسند؛ نوار فقط با سندِ باز
    assertTrue(inbox.contains("ShopRole.current(context) in RemoteApproval.APPROVER_ROLES"))
    assertTrue(inbox.contains("if (!on || count <= 0) return"))
    val sheet = src("ui/v3/shell/ConflictInboxSheet.kt")
    //  «دوباره ثبت» با پرسشِ دوباره و دکمهٔ قفل تا پاسخ؛ «اصلاح» سبدِ پُر را دست نمی‌زند
    assertTrue(sheet.contains("V3ConfirmSheet("))
    assertTrue(sheet.contains("enabled = busy == null, loading = busy == \"resubmit\""))
    assertTrue(sheet.contains("if (cart.lines.value.isNotEmpty())"))
  }

  @Test
  fun `«اصلاح و ثبت» از همان ثبتِ سبد بسته می‌شود — گوشی و کامپیوتر`() {
    val cart = src("data/CartStore.kt")
    assertTrue(cart.contains("ConflictFix.posted(app, cartKey, out.ok.saleId)"))
    //  هر دو صندوق از همان `CartStore.post` ثبت می‌کنند
    assertTrue(src("ui/screens/DeskSale.kt").contains("cartStore.post(store"))
    assertTrue(src("ui/v3/sales/checkout/CheckoutRoute.kt").contains("cart.post(store"))
  }

  @Test
  fun `نشانِ «سرور نپذیرفت» روی ردیفِ تاریخچه و بالای فاکتور`() {
    assertTrue(src("ui/v3/sales/history/HistoryParts.kt").contains("RejectedLine(s.id)"))
    assertTrue(src("ui/v3/sales/history/InvoiceScreen.kt").contains("RejectedBanner(sale.id)"))
    assertTrue(src("ui/v3/sales/history/HistoryHostAndroid.kt").contains("RejectedWatch()"))
  }

  private fun obj(s: String) = Json.parseToJsonElement(s) as JsonObject

  @Test
  fun `دفترِ ردشده‌های v1 و v3 ⇒ فاکتورِ هر op`() {
    val v1 = RejectedDocs.v1(listOf(
      obj("""{"op":{"op_id":"o1","table":"sales","row_id":"s-1","type":"insert","fields":{"finalTotal":1}},"reason":"approval_required","at":5}"""),
      obj("""{"op":{"op_id":"o2","table":"transactions","row_id":"t-1","type":"insert","fields":{"refId":"s-1"}},"reason":"tx_rejected","at":5}"""),
      obj("""{"op":{"op_id":"o3","table":"products","row_id":"p-1"},"reason":"table_not_allowed","at":6}"""),
      obj("""{"reason":"bad"}"""),
    ))
    assertEquals(listOf("s-1" to "approval_required", "s-1" to "tx_rejected", "" to "table_not_allowed"), v1.map { it.saleId to it.reason })
    val v3 = RejectedDocs.v3(listOf(V3Entry(
      key = "r1", id = "e1", fmt = V3Entry.V3, table = "sales", rowId = "s-3", at = 1, rejectedAt = 9,
      ops = listOf(
        obj("""{"op_id":"a","table":"sales","row_id":"s-3","kind":"insert","fields":{}}"""),
        obj("""{"op_id":"b","table":"saleItems","row_id":"i-3","kind":"insert","fields":{"saleId":"s-3"}}"""),
      ),
      reasons = listOf("credit_not_allowed", "tx_rejected"),
    )))
    assertEquals(listOf(Triple("a", "s-3", "credit_not_allowed"), Triple("b", "s-3", "tx_rejected")), v3.map { Triple(it.opId, it.saleId, it.reason) })
    assertTrue(v3.all { it.at == 9L })
  }
}
