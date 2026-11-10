package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.crm.CustomerAccess
import ir.vil3ntec.tohid.core.crm.CustomerAccess.Kind
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** CRM-15: «چه کسی پرونده را دید» روی دفترِ دستگاه — ردیفِ فقط‌افزودنیِ `auditLog`، بی دادهٔ مشتری، و بخشِ هر دو پرونده. */
class CustomerAccessLogTest {

  private var seq = 0
  private fun newId() = "cv${seq++}"
  private val today = "2026-11-08"
  private val t0 = 1_800_000_000_000L
  private val who = CustomerAccessLog.Viewer(id = "usr_1", name = "کریم", role = "cashier", device = "dev-7")
  private val debtor = Debtor(id = "d1", name = "رحیمِ پرونده‌دار", phone = "0799123456", notes = "بدهیِ کهنه", phone2 = "0700111222")
  private val ledger = ShopData(debtors = listOf(debtor, Debtor(id = "d2", name = "بی شماره")))

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/$rel") }.firstOrNull { it.isFile }?.readText()
      ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  @Test fun `هر دیدن یک ردیفِ customer_view با بیننده و نقش و دستگاه و نوع و بی دادهٔ مشتری`() {
    val d = assertNotNullData(CustomerAccessLog.record(ledger, "d1", Kind.PHONE, who, today, t0, ::newId))
    val row = d.auditLog.single()
    assertEquals(CustomerAccess.TYPE, row.type)
    assertEquals("d1", row.refId)
    assertEquals("usr_1", row.viewer)
    assertEquals("کریم", row.by)
    assertEquals("cashier", row.role)
    assertEquals("dev-7", row.device)
    assertEquals("phone", row.view)
    assertEquals(t0, row.createdAt)
    assertEquals(today, row.date)
    assertEquals("", row.notes)
    //  ⛔ هیچ دادهٔ مشتری در ردیفی که همگام می‌شود
    val json = Json.encodeToString(row)
    for (s in listOf("رحیم", "0799123456", "799123456", "0700111222", "بدهیِ کهنه")) assertFalse("«$s» در $json", json.contains(s))
    //  دفتر جز همان یک ردیف دست نخورد
    assertEquals(ledger.debtors, d.debtors)
    assertEquals(ledger.copy(auditLog = emptyList()), d.copy(auditLog = emptyList()))
  }

  @Test fun `تکرار در ده دقیقه نوشته نمی‌شود و پس از آن ردیفِ تازه؛ نوع و مشتریِ دیگر جدا`() {
    val a = assertNotNullData(CustomerAccessLog.record(ledger, "d1", Kind.DETAIL, who, today, t0, ::newId))
    assertNull(CustomerAccessLog.record(a, "d1", Kind.DETAIL, who, today, t0 + 60_000, ::newId))
    assertNull(CustomerAccessLog.record(a, "d1", Kind.DETAIL, who, today, t0 + CustomerAccess.WINDOW_MS - 1, ::newId))
    val b = assertNotNullData(CustomerAccessLog.record(a, "d1", Kind.DETAIL, who, today, t0 + CustomerAccess.WINDOW_MS, ::newId))
    assertEquals(2, b.auditLog.size)
    assertEquals(3, assertNotNullData(CustomerAccessLog.record(b, "d1", Kind.EXPORT, who, today, t0 + 1, ::newId)).auditLog.size)
    assertEquals(3, assertNotNullData(CustomerAccessLog.record(b, "d2", Kind.DETAIL, who, today, t0 + 1, ::newId)).auditLog.size)
    assertEquals(3, assertNotNullData(CustomerAccessLog.record(b, "d1", Kind.DETAIL, who.copy(id = "usr_2"), today, t0 + 1, ::newId)).auditLog.size)
    //  فقط‌افزودنی: ردیف‌های پیشین بایت‌به‌بایت همان
    assertEquals(a.auditLog, b.auditLog.take(1))
  }

  @Test fun `پروندهٔ ناموجود یا شناسهٔ خالی هیچ؛ خروجیِ همهٔ مشتریان بی پرونده`() {
    assertNull(CustomerAccessLog.record(ledger, "gone", Kind.DETAIL, who, today, t0, ::newId))
    assertNull(CustomerAccessLog.record(ledger, "  ", Kind.DETAIL, who, today, t0, ::newId))
    val all = assertNotNullData(CustomerAccessLog.record(ledger, CustomerAccess.ALL, Kind.EXPORT, who, today, t0, ::newId))
    assertEquals(CustomerAccess.ALL, all.auditLog.single().refId)
    //  نامِ بلند کوتاه می‌شود؛ نقش و دستگاهِ خالی نوشته نمی‌شوند
    val bare = assertNotNullData(CustomerAccessLog.record(ledger, "d2", Kind.DETAIL, CustomerAccessLog.Viewer("", "ن".repeat(90), "", ""), today, t0, ::newId))
    val r = bare.auditLog.single()
    assertEquals(60, r.by!!.length)
    assertNull(r.role)
    assertNull(r.device)
    assertFalse(Json.encodeToString(r).contains("\"role\""))
  }

  @Test fun `ردیف‌های دفتر ⇐ فهرستِ پرونده با ردیف‌های سرور`() {
    var d = ledger
    d = assertNotNullData(CustomerAccessLog.record(d, "d1", Kind.DETAIL, who, today, t0, ::newId))
    d = assertNotNullData(CustomerAccessLog.record(d, CustomerAccess.ALL, Kind.EXPORT, who, today, t0 + 2000, ::newId))
    d = assertNotNullData(CustomerAccessLog.record(d, "d2", Kind.DETAIL, who, today, t0 + 3000, ::newId))
    //  ردیفِ دیگرِ سابقه و ردیفِ بدشکل کنار می‌روند
    d = d.copy(auditLog = d.auditLog + AuditEntry(id = "x1", type = "sale", refId = "d1", createdAt = t0) +
      AuditEntry(id = "x2", type = CustomerAccess.TYPE, refId = "d1", view = "peek", createdAt = t0))
    val views = CustomerAccessLog.views(d)
    assertEquals(3, views.size)
    val server = listOf(CustomerAccess.View("cav1", "d1", "usr_9", "مدیرِ دکان", "manager", "", Kind.PHONE, t0 + 1000, count = 4, server = true))
    val rows = CustomerAccess.rows(views, server, "d1")
    assertEquals(listOf(CustomerAccess.ALL, "d1", "d1"), rows.map { it.customerId })
    assertEquals(listOf(false, true, false), rows.map { it.server })
    assertEquals("کریم", rows.last().viewerName)
    assertEquals("cashier", rows.last().role)
  }

  @Test fun `نوعِ باز شدن — شمارهٔ کامل پیداست ⇒ شماره، وگرنه پرونده`() {
    assertEquals(Kind.PHONE, CustomerAccessLog.openKind(true, debtor))
    assertEquals(Kind.DETAIL, CustomerAccessLog.openKind(false, debtor))
    assertEquals(Kind.DETAIL, CustomerAccessLog.openKind(true, Debtor(id = "d2", name = "بی شماره")))
    assertEquals(Kind.PHONE, CustomerAccessLog.openKind(true, Debtor(id = "d3", phone2 = "0700")))
    assertEquals(Kind.DETAIL, CustomerAccessLog.openKind(true, null))
  }

  @Test fun `هر دو پرونده بخش را دارند و دیدن را ثبت می‌کنند؛ فقط audit_view؛ حسابدار و حالتِ آموزشی نمی‌نویسند`() {
    assertTrue(src("ui/v3/money/DebtorFileExtras.kt").contains("CustomerAccessPanel(d, debtorId,"))
    val desk = src("ui/screens/DeskCustomers.kt")
    assertTrue(desk.contains("CustomerAccessPanel(d, id,"))
    assertTrue(desk.contains("LaunchedEffect(id) { viewed(id, ir.vil3ntec.tohid.data.CustomerAccessLog.openKind(contacts, debtor)) }"))
    val screen = src("ui/v3/money/DebtorLedgerScreen.kt")
    assertTrue(screen.contains("LaunchedEffect(id) { actions.onViewed(ir.vil3ntec.tohid.data.CustomerAccessLog.openKind(contacts, account.debtor)) }"))
    assertTrue(src("ui/v3/money/MoneyRoutes.kt").contains("onViewed = { kind -> viewed(id, kind) }"))
    val panel = src("ui/screens/CustomerAccessPanelAndroid.kt")
    assertTrue(panel.contains("ShopRole.allows(context, \"audit.view\")"))
    assertTrue(panel.indexOf("if (!allowed) return") in 0 until panel.indexOf("Backend.api(context).get(path)"))
    assertTrue(panel.contains("if (store.training) return@runCatching false"))
    //  نقشِ بی `auditLog` (حسابدار) پیش از نوشتنِ دفتر برمی‌گردد — به درِ `app` ِ سرور (آزمونِ پایین)، نه به دفتر
    val noWriter = panel.indexOf("if (!PermCore.canWrite(role, \"auditLog\")) {")
    assertTrue(noWriter in 0 until panel.indexOf("store.update { d -> CustomerAccessLog.record("))
    assertTrue(panel.contains("}.getOrDefault(false)"))
    assertFalse(ir.vil3ntec.tohid.core.perm.PermCore.canWrite("accountant", "auditLog"))
    assertTrue(ir.vil3ntec.tohid.core.perm.PermCore.canWrite("cashier", "auditLog"))
  }

  @Test fun `حسابدار دیدن را به درِ app ِ سرور می‌گوید نه به دفتر؛ آموزشی و بی سرور هیچ`() {
    val panel = src("ui/screens/CustomerAccessPanelAndroid.kt")
    val training = panel.indexOf("if (store.training) return@runCatching false")
    val branch = panel.indexOf("if (!PermCore.canWrite(role, \"auditLog\")) {")
    val body = panel.indexOf("val body = CustomerAccess.noteBody(customerId, kind) ?: return@runCatching false")
    val ready = panel.indexOf("if (!Backend.isReady(context)) return@runCatching false")
    val post = panel.indexOf("Backend.api(context).post(CustomerAccess.PATH, body)")
    val done = panel.indexOf("return@runCatching true", post)
    val ledger = panel.indexOf("store.update { d -> CustomerAccessLog.record(")
    assertTrue("حالتِ آموزشی پیش از هر راهی", training in 0 until branch)
    assertTrue("شاخهٔ حسابدار پیش از دفتر و با بازگشت", branch < body && body < ready && ready < post && post < done && done < ledger)
    //  بدنه فقط شناسه و نوع است؛ برگهٔ همه فقط با خروجی (core: CustomerAccessTest)
    assertEquals(setOf("customerId", "kind"), CustomerAccess.noteBody("d1", Kind.DETAIL)?.keys)
    assertNull(CustomerAccess.noteBody(CustomerAccess.ALL, Kind.DETAIL))
  }

  private fun assertNotNullData(d: ShopData?): ShopData { assertNotNull(d); return d!! }
}
