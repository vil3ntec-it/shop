package ir.vil3ntec.tohid.core.crm

import ir.vil3ntec.tohid.core.crm.CustomerAccess.Kind
import ir.vil3ntec.tohid.core.crm.CustomerAccessOutbox.Note
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** CRM-15 (د۱۸) — صفِ دیدنِ حسابدارِ بی‌اینترنت (`CustomerAccessOutbox`). */
class CustomerAccessOutboxTest {
  private val now = 10_000_000_000L
  private val min = 60_000L

  @Test fun addMergesRepeatsInWindowAndDropsBadOrOld() {
    var q = CustomerAccessOutbox.add(emptyList(), Note("n1", "d1", Kind.DETAIL, now - 30 * min), now)
    q = CustomerAccessOutbox.add(q, Note("n2", "d1", Kind.DETAIL, now - 25 * min), now)
    q = CustomerAccessOutbox.add(q, Note("n3", "d1", Kind.PHONE, now - 25 * min), now)
    q = CustomerAccessOutbox.add(q, Note("n4", "d1", Kind.DETAIL, now - 5 * min), now)
    q = CustomerAccessOutbox.add(q, Note("n5", " d2 ", Kind.DETAIL, now), now)
    assertEquals(listOf("n1", "n3", "n4", "n5"), q.map { it.id })
    assertEquals("d2", q.last().customerId)
    //  شناسهٔ نادرست، «*» بی خروجی، و دیدنِ کهنه‌تر از ۳۰ روز ⇒ هیچ
    for (bad in listOf(Note("x", "erased:1", Kind.DETAIL, now), Note("y", "*", Kind.DETAIL, now), Note("z", "d3", Kind.DETAIL, now - CustomerAccessOutbox.MAX_AGE_MS - 1))) {
      assertEquals(q, CustomerAccessOutbox.add(q, bad, now))
    }
    assertEquals(listOf("n5"), CustomerAccessOutbox.add(q, Note("n6", "d2", Kind.DETAIL, now + 1), now + CustomerAccessOutbox.MAX_AGE_MS - 1).map { it.id })
  }

  @Test fun capKeepsNewest() {
    var q = emptyList<Note>()
    for (i in 0 until CustomerAccessOutbox.CAP + 7) q = CustomerAccessOutbox.add(q, Note("n$i", "d$i", Kind.DETAIL, now - 1000 + i), now)
    assertEquals(CustomerAccessOutbox.CAP, q.size)
    assertEquals("n7", q.first().id)
  }

  @Test fun batchBodyAndSettle() {
    val q = (0 until 60).map { Note("n$it", "d$it", Kind.EXPORT, now - it) }
    val b = CustomerAccessOutbox.batch(q)
    assertEquals(CustomerAccessOutbox.BATCH, b.size)
    val body = CustomerAccessOutbox.body(b.take(1))
    assertEquals("""{"notes":[{"id":"n0","customerId":"d0","kind":"export","at":$now}]}""", body.toString())
    val res = Json.parseToJsonElement(
      """{"results":[{"id":"n0","status":"recorded"},{"id":"n1","status":"duplicate"},{"id":"n2","status":"rejected","reason":"bad_at"},{"id":"n3","status":"weird"},{"status":"recorded"}]}""",
    ).jsonObject
    val left = CustomerAccessOutbox.settle(q, res)
    assertEquals(57, left.size)
    assertTrue(left.none { it.id in setOf("n0", "n1", "n2") })
    assertEquals(q, CustomerAccessOutbox.settle(q, Json.parseToJsonElement("{}").jsonObject))
  }

  @Test fun encodeDecodeRoundTrip() {
    val q = listOf(Note("n1", "d1", Kind.PHONE, now), Note("n2", "*", Kind.EXPORT, now + 1))
    assertEquals(q, CustomerAccessOutbox.decode(CustomerAccessOutbox.encode(q)))
    assertEquals(emptyList(), CustomerAccessOutbox.decode("broken"))
    assertEquals(2, Json.parseToJsonElement(CustomerAccessOutbox.encode(q)).jsonArray.size)
  }
}
