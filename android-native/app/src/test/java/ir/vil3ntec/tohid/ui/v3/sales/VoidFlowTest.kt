package ir.vil3ntec.tohid.ui.v3.sales

import ir.vil3ntec.tohid.core.audit.InvoiceVoid
import ir.vil3ntec.tohid.core.i18n.StrInvoices
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.ui.screens.SalesVoid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  SEC-03 در نسخهٔ ۳ — لغو و «اصلاح» ِ فاکتور از تاریخچهٔ نسخهٔ ۳ (`CancelRoute`/`FixRoute`) پیش‌تر مستقیم
 *  `SalesEngine.cancel` را صدا می‌زد: نه `InvoiceVoid.check` ِ هسته، نه سندِ معکوس در زنجیرهٔ اسنادِ دستگاه
 *  (`DocChainSync.recordVoid`)، نه لغوکننده (`FraudTrailPref.by`). حالا هر دو از `SalesVoid.run` می‌گذرند.
 */
class VoidFlowTest {

  private var ids = 0
  private fun newId() = "id-${++ids}"
  private val ledger = SalesTestLedger.ledger.let { d ->
    d.copy(sales = d.sales + Sale(id = "w1", invoiceNumber = 3, status = "completed", channel = "online"))
  }

  private fun run(saleId: String, kind: InvoiceVoid.Kind, reason: String, canCancel: Boolean = true, by: String? = "مدیر") =
    SalesVoid.run(ledger, saleId, canCancel, kind, reason, "2026-11-03", 1_000L, ::newId, by, "بی اجازه")

  @Test
  fun `لغو با دلیل از کنترلِ هسته می‌گذرد و لغوکننده روی سابقه می‌نشیند`() {
    val r = run("s1", InvoiceVoid.Kind.VOID, "  مشتری پشیمان شد  ")
    r as SalesVoid.Outcome.Ok
    assertEquals("مشتری پشیمان شد", r.reason)
    val sale = r.data.sales.single { it.id == "s1" }
    assertEquals("cancelled", sale.status)
    assertEquals("مشتری پشیمان شد", sale.cancelReason)
    assertEquals("مدیر", r.data.auditLog.single { it.type == "cancel_sale" }.by)
  }

  @Test
  fun `اصلاح دلیلِ ثابتِ هسته را می‌گیرد، نه متنِ صفحه`() {
    val r = run("s1", InvoiceVoid.Kind.CORRECTION, "هر چه")
    r as SalesVoid.Outcome.Ok
    assertEquals(InvoiceVoid.CORRECTION_REASON, r.reason)
    assertEquals(InvoiceVoid.CORRECTION_REASON, r.data.sales.single { it.id == "s1" }.cancelReason)
  }

  @Test
  fun `ردهای هسته پیش از موتور و دفتر دست نمی‌خورد`() {
    assertEquals(SalesVoid.Outcome.Denied(StrInvoices.reasonRequired), run("s1", InvoiceVoid.Kind.VOID, "   "))
    assertEquals(SalesVoid.Outcome.Denied(StrInvoices.reasonTooLong(InvoiceVoid.REASON_MAX)), run("s1", InvoiceVoid.Kind.VOID, "x".repeat(121)))
    assertEquals(SalesVoid.Outcome.Denied("بی اجازه"), run("s1", InvoiceVoid.Kind.VOID, "دلیل", canCancel = false))
    assertEquals(SalesVoid.Outcome.Denied("بی اجازه"), run("s1", InvoiceVoid.Kind.CORRECTION, "", canCancel = false))
    assertEquals(SalesVoid.Outcome.Denied(StrInvoices.alreadyCancelled), run("s2", InvoiceVoid.Kind.VOID, "دلیل"))
    assertEquals(SalesVoid.Outcome.Denied(SalesEngine.ONLINE_ORDER_LOCKED), run("w1", InvoiceVoid.Kind.CORRECTION, ""))
    assertEquals(SalesVoid.Outcome.Denied(StrInvoices.invoiceNotFound), run("nope", InvoiceVoid.Kind.VOID, "دلیل"))
    assertEquals("هیچ شناسه‌ای ساخته نشد", 0, ids)
  }

  @Test
  fun `بی سابقهٔ ضدتقلب لغوکننده نوشته نمی‌شود`() {
    val r = run("s1", InvoiceVoid.Kind.VOID, "دلیل", by = null) as SalesVoid.Outcome.Ok
    assertEquals(null, r.data.auditLog.single { it.type == "cancel_sale" }.by)
  }

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/$rel") }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  @Test
  fun `لغو و اصلاحِ نسخهٔ ۳ از همان کنترل و پس از ذخیره سندِ معکوس در زنجیره`() {
    val routes = src("ui/v3/sales/history/HistoryRoutes.kt")
    assertEquals("هیچ لغوِ مستقیمی بی کنترلِ هسته", 0, Regex("""SalesEngine\.cancel\(""").findAll(routes).count())
    for (kind in listOf("VOID", "CORRECTION")) {
      assertTrue(kind, Regex("""SalesVoid\.run\(before, sale\.id, canCancel, InvoiceVoid\.Kind\.$kind,""").containsMatchIn(routes))
      assertTrue(
        "$kind: سندِ معکوس فقط پس از ذخیرهٔ موفق",
        routes.contains("if (store.save(r.data)) DocChainSync.recordVoid(context, before, sale.id, InvoiceVoid.Kind.$kind, r.reason)"),
      )
    }
    assertEquals("لغوکننده در هر دو مسیر", 2, Regex("""val by = remember \{ FraudTrailPref\.by\(context\) \}""").findAll(routes).count())
  }
}
