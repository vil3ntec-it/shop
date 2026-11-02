package ir.vil3ntec.tohid.core.orders

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  RSK-08 §۱۹.۹ بندِ ۵ — مسئول و مهلتِ اختلاف در برنامه: خواندنِ `assigneeId`/`dueAt`/`overdue` و فهرستِ `assignees` ِ سرور،
 *  آمادگیِ فرمِ «سپردن» (مسئولِ معتبر + مهلت، یادداشتِ اختیاری) و بدنهٔ `POST …/disputes/assign`.
 */
class OrderEvidenceAssignTest {

  private fun json(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject
  private val id = "aud_0123456789abcdef01234567"

  private fun dispute(extra: String = ""): OrderEvidence.Dispute? = OrderEvidence.dispute(json(
    """{"id":"$id","kind":"cash","reason":"پول کم بود","status":"open","openedBy":{"userId":"u1","role":"owner"},
       "openedAt":10,"notes":[],"resolution":null$extra}""",
  ))

  @Test
  fun readsOwnerDeadlineAndOverdueFromTheServer() {
    val plain = dispute()!!
    assertNull(plain.assigneeId)
    assertNull(plain.dueAt)
    assertFalse(plain.overdue)
    val d = dispute(""","assigneeId":"usr_staff-1","dueAt":7200000,"overdue":true""")!!
    assertEquals("usr_staff-1", d.assigneeId)
    assertEquals(7_200_000L, d.dueAt)
    assertTrue(d.overdue)
    //  شناسهٔ نادرست ⇒ بی مسئول (هرگز نشانی یا متنِ ساخته از دادهٔ بد)
    assertNull(dispute(""","assigneeId":"a b/../c","dueAt":1""")!!.assigneeId)
  }

  @Test
  fun assigneesComeOnlyFromTheFileWithValidIds() {
    val f = OrderEvidence.file(json(
      """{"order":{"id":"o1","orderNo":7},"disputes":[],"people":{"users":{},"couriers":{}},
         "assignees":[{"userId":"u-owner","name":"صاحب","role":"owner"},{"userId":"bad id","name":"x","role":"staff"},
                      {"userId":"u-staff","name":"","role":"staff"}]}""",
    ))!!
    assertEquals(listOf("u-owner", "u-staff"), f.assignees.map { it.userId })
    assertEquals("صاحب", f.assignees[0].name)
    //  پاسخِ سرورِ کهنه (بی `assignees`) ⇒ فهرستِ خالی
    assertTrue(OrderEvidence.file(json("""{"order":{"id":"o1"}}"""))!!.assignees.isEmpty())
  }

  @Test
  fun assignDraftNeedsAnOpenDisputeAValidOwnerAndADeadline() {
    val cur = dispute()!!
    val due = OrderEvidence.dueAt(1_000L, 24)
    assertEquals(1_000L + 24 * 3_600_000L, due)
    val ok = OrderEvidence.Draft(OrderEvidence.Act.ASSIGN, choice = "u-staff", dueAt = due, dueHours = 24)
    assertTrue(ok.ready(cur))
    assertFalse(ok.ready(null), "بی اختلافِ باز")
    assertFalse(ok.copy(dueAt = null).ready(cur), "بی مهلت")
    assertFalse(ok.copy(choice = "").ready(cur), "بی مسئول")
    assertFalse(ok.copy(text = "ab").ready(cur), "یادداشتِ کوتاه")
    assertTrue(ok.copy(text = "  ").ready(cur), "یادداشت اختیاری است")
    assertTrue(ok.copy(text = "تا عصر پیگیری کند").ready(cur))
  }

  @Test
  fun assignBodyCarriesOwnerDeadlineAndOptionalNote() {
    val d = OrderEvidence.Draft(OrderEvidence.Act.ASSIGN, choice = "u-staff", dueAt = 5_000L)
    val b = OrderEvidence.body(d, "asg-key-0001", id)!!
    assertEquals(JsonPrimitive("u-staff"), b["assigneeId"])
    assertEquals(JsonPrimitive(5_000L), b["dueAt"])
    assertEquals(JsonPrimitive(id), b["disputeId"])
    assertFalse("text" in b, "یادداشتِ خالی فرستاده نمی‌شود")
    assertEquals(JsonPrimitive("پیگیری"), OrderEvidence.body(d.copy(text = " پیگیری "), "asg-key-0001", id)!!["text"])
    assertNull(OrderEvidence.body(d.copy(dueAt = null), "asg-key-0001", id))
    assertNull(OrderEvidence.body(d, "asg-key-0001", null))
    assertEquals("/shop/orders/evidence/o-1/disputes/assign", OrderEvidence.assignPath("o-1"))
    assertNull(OrderEvidence.assignPath("../x"))
  }
}
