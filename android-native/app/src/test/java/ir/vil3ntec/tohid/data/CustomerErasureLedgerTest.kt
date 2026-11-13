package ir.vil3ntec.tohid.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** CRM-15/SEC-10 (د۱۸) — پاک کردنِ پروندهٔ مشتری در دفترِ دستگاه به فرمانِ سرور (`CustomerErasureLedger`). */
class CustomerErasureLedgerTest {
  private val ali = Debtor(id = "d1", name = "علی", phone = "0799777123", notes = "کوچهٔ دوم", phone2 = "0700000001", cardCode = "C-9", creditLimit = 5000.0, group = "vip")
  private val other = Debtor(id = "d2", name = "دیگری", phone = "0700000002")
  private val sale = Sale(id = "s1", invoiceNumber = 1, finalTotal = 100.0, debtorId = "d1")
  private val d = ShopData(debtors = listOf(ali, other), sales = listOf(sale),
    auditLog = listOf(AuditEntry(id = "a1", type = "customer_view", date = "2026-11-13", refId = "d1", createdAt = 1)))

  @Test fun `فقط پرونده‌های فرمان پاک می‌شوند؛ سند، سقف، گروه و سابقهٔ دیدن همان`() {
    val next = CustomerErasureLedger.scrub(d, setOf("d1", "gone"))!!
    val a = next.debtors.first { it.id == "d1" }
    assertEquals(CustomerErasureLedger.ERASED_NAME, a.name)
    assertTrue(CustomerErasureLedger.clean(a))
    assertNull(a.phone2)
    assertNull(a.cardCode)
    assertEquals(5000.0, a.creditLimit)
    assertEquals("vip", a.group)
    assertEquals(other, next.debtors.first { it.id == "d2" })
    assertEquals(d.sales, next.sales)
    assertEquals(d.auditLog, next.auditLog)
    //  دوباره ⇒ هیچ نوشتن
    assertNull(CustomerErasureLedger.scrub(next, setOf("d1")))
    assertNull(CustomerErasureLedger.scrub(d, emptySet()))
  }

  @Test fun `پاسخِ سرور و «انجام شد» فقط برای پروندهٔ پاک یا نبوده`() {
    val o = Json.parseToJsonElement("""{"debtors":[{"debtorId":"d1","erasureId":"era_1","at":5},{"debtorId":""},{"x":1},{"debtorId":"d9"}]}""").jsonObject
    assertEquals(listOf("d1", "d9"), CustomerErasureLedger.ids(o))
    assertEquals(listOf("d9"), CustomerErasureLedger.done(d, listOf("d1", "d9")))
    assertEquals(listOf("d1", "d9"), CustomerErasureLedger.done(CustomerErasureLedger.scrub(d, setOf("d1"))!!, listOf("d1", "d9")))
    assertEquals("""{"debtorIds":["d1","d9"]}""", CustomerErasureLedger.appliedBody(listOf("d1", "d9")).toString())
  }
}
