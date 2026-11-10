package ir.vil3ntec.tohid.core.crm

import ir.vil3ntec.tohid.core.crm.CustomerAccess.Kind
import ir.vil3ntec.tohid.core.crm.CustomerAccess.View
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** CRM-15: «چه کسی این پرونده را دید» — پنجرهٔ تکرار، پاسخِ سرور و فهرستِ پرونده (دستگاه + سرور). */
class CustomerAccessTest {

  private fun v(id: String, at: Long, kind: Kind = Kind.DETAIL, viewer: String = "u1", customer: String = "d1", server: Boolean = false, count: Int = 1) =
    View(id = id, customerId = customer, viewerId = viewer, viewerName = "", role = "cashier", device = "", kind = kind, at = at, count = count, server = server)

  @Test fun `همان بیننده و مشتری و نوع در ده دقیقه تکرار است و پس از آن نه`() {
    val t0 = 1_800_000_000_000L
    val log = listOf(v("a", t0))
    assertTrue(CustomerAccess.isRepeat(log, "u1", "d1", Kind.DETAIL, t0 + 60_000))
    assertTrue(CustomerAccess.isRepeat(log, "u1", "d1", Kind.DETAIL, t0 + CustomerAccess.WINDOW_MS - 1))
    assertFalse(CustomerAccess.isRepeat(log, "u1", "d1", Kind.DETAIL, t0 + CustomerAccess.WINDOW_MS))
    assertFalse(CustomerAccess.isRepeat(log, "u1", "d1", Kind.PHONE, t0 + 1), "نوعِ دیگر")
    assertFalse(CustomerAccess.isRepeat(log, "u2", "d1", Kind.DETAIL, t0 + 1), "بینندهٔ دیگر")
    assertFalse(CustomerAccess.isRepeat(log, "u1", "d2", Kind.DETAIL, t0 + 1), "مشتریِ دیگر")
    //  ساعتِ عقب‌رفته (ردیفِ «آینده») سابقه را پر نمی‌کند
    assertTrue(CustomerAccess.isRepeat(log, "u1", "d1", Kind.DETAIL, t0 - 3_600_000))
    assertFalse(CustomerAccess.isRepeat(emptyList(), "u1", "d1", Kind.DETAIL, t0))
  }

  @Test fun `نشانیِ پرسش فقط با شناسهٔ امن؛ کلیدِ شماره و ناشناس‌شده نه`() {
    assertEquals("/shop/customer-access?customerId=01JCX9-a_b.c", CustomerAccess.path("01JCX9-a_b.c"))
    for (bad in listOf("", "a b", "x&y=1", "../d", "erased:era1", "ph:abc", "a".repeat(81), "دفتر")) assertNull(CustomerAccess.path(bad), bad)
  }

  @Test fun `پاسخِ سرور ⇐ ردیف با شمار؛ ردیفِ بدشکل و نوعِ ناشناخته کنار می‌رود`() {
    val o = Json.parseToJsonElement(
      """{"customerId":"d1","entries":[
        {"id":"cav1","viewerId":"u9","viewerName":"احمد","role":"manager","device":"dev-1","kind":"phone","door":"debtor","firstAt":100,"lastAt":200,"count":3},
        {"id":"cav2","viewerId":"u9","kind":"peek","lastAt":300},
        {"viewerId":"u9","kind":"detail","lastAt":300},
        {"id":"cav3","viewerId":"u8","kind":"export","firstAt":50,"count":0},
        "x"
      ],"retentionDays":0}""",
    ) as JsonObject
    val rows = CustomerAccess.parse(o, "d1")
    assertEquals(listOf("cav1", "cav3"), rows.map { it.id })
    val a = rows[0]
    assertEquals(Kind.PHONE, a.kind)
    assertEquals(200L, a.at)
    assertEquals(3, a.count)
    assertEquals("احمد", a.viewerName)
    assertEquals("manager", a.role)
    assertTrue(a.server)
    assertEquals("d1", a.customerId)
    assertEquals(50L, rows[1].at, "بی lastAt ⇒ firstAt")
    assertEquals(1, rows[1].count, "شمارِ صفر ⇒ ۱")
    assertEquals(emptyList(), CustomerAccess.parse(JsonObject(emptyMap()), "d1"))
  }

  @Test fun `فهرستِ پرونده دستگاه و سرور را با هم و تازه‌ترین اول می‌آورد؛ خروجیِ همه هم هست؛ مشتریِ دیگر نه`() {
    val local = listOf(
      v("l1", 1000),
      v("l2", 3000, Kind.EXPORT, customer = CustomerAccess.ALL),
      v("l3", 5000, customer = "d2"),
      v("l1", 1000),
    )
    val server = listOf(v("s1", 2000, Kind.PHONE, server = true, count = 4), v("l1", 4000, server = true))
    val rows = CustomerAccess.rows(local, server, "d1")
    assertEquals(listOf("l1@4000", "l2@3000", "s1@2000", "l1@1000"), rows.map { "${it.id}@${it.at}" })
    assertEquals(4, rows.first { it.id == "s1" }.count)
    //  سقفِ نمایش
    val many = (1..80).map { v("m$it", it.toLong()) }
    assertEquals(CustomerAccess.SHOWN, CustomerAccess.rows(many, emptyList(), "d1").size)
    assertEquals("m80", CustomerAccess.rows(many, emptyList(), "d1").first().id)
  }

  @Test fun `بدنهٔ درِ app فقط شناسه و نوع؛ شناسهٔ ناامن هیچ؛ برگهٔ همه فقط با خروجی`() {
    assertEquals("""{"customerId":"d-1","kind":"phone"}""", CustomerAccess.noteBody(" d-1 ", Kind.PHONE).toString())
    assertEquals(setOf("customerId", "kind"), CustomerAccess.noteBody("01JCX9-a_b.c", Kind.DETAIL)?.keys, "بیننده و دستگاه از توکنِ سرور")
    assertEquals("""{"customerId":"*","kind":"export"}""", CustomerAccess.noteBody(CustomerAccess.ALL, Kind.EXPORT).toString())
    assertNull(CustomerAccess.noteBody(CustomerAccess.ALL, Kind.DETAIL))
    assertNull(CustomerAccess.noteBody(CustomerAccess.ALL, Kind.PHONE))
    for (bad in listOf("", "a b", "x&y=1", "../d", "erased:era1", "ph:abc", "a".repeat(81), "دفتر")) assertNull(CustomerAccess.noteBody(bad, Kind.DETAIL), bad)
  }

  @Test fun `ردیفِ سرورِ برگهٔ همه مشتریِ همه می‌گیرد و در پرونده می‌ماند`() {
    val o = Json.parseToJsonElement(
      """{"entries":[
        {"id":"cav1","viewerId":"u7","role":"accountant","kind":"export","door":"app","lastAt":900,"all":true},
        {"id":"cav2","viewerId":"u7","role":"accountant","kind":"export","door":"app","lastAt":800},
        {"id":"cav3","viewerId":"u7","kind":"export","lastAt":700,"all":"true"}
      ]}""",
    ) as JsonObject
    val rows = CustomerAccess.parse(o, "d1")
    assertEquals(listOf(CustomerAccess.ALL, "d1", "d1"), rows.map { it.customerId }, "فقط بولیِ true")
    assertEquals(listOf("cav1", "cav2", "cav3"), CustomerAccess.rows(emptyList(), rows, "d1").map { it.id })
  }

  @Test fun `برگه‌های مشتریان و نوع‌ها`() {
    assertTrue("debtors" in CustomerAccess.CUSTOMER_SHEETS)
    assertFalse("sales" in CustomerAccess.CUSTOMER_SHEETS)
    assertEquals(Kind.EXPORT, Kind.of("export"))
    assertNull(Kind.of("peek"))
    assertEquals(listOf("detail", "phone", "export"), Kind.entries.map { it.key }, "همان KINDS ِ سرور (lib/customer-access.js)")
  }
}
