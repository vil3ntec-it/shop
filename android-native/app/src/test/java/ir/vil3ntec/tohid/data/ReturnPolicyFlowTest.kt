package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.returns.ReturnPolicy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  RET-09 — سیاستِ برگشت در `ReturnFlow`: مهلت و اقلامِ غیرقابلِ برگشت، با استثنای مجاز.
 *  دفتر همان بردارِ `core/test-vectors/returns-v1.json` است (فاکتورِ s30 ِ ۲۰۲۶-۱۰-۰۳، «کفش سایز ۴۰»).
 */
class ReturnPolicyFlowTest {

  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  private fun ledger(): ShopData {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/returns-v1.json")
      if (f.isFile) return json.decodeFromJsonElement(ShopData.serializer(), Json.parseToJsonElement(f.readText()).jsonObject.getValue("ledger"))
      dir = dir.parentFile
    }
    error("core/test-vectors/returns-v1.json پیدا نشد")
  }

  private var seq = 0
  private fun newId(): String = "n${seq++}"
  private val today = "2026-10-19"   // ۱۶ روز پس از s30
  private val now = 1_760_000_000_000L

  private fun req(role: String, policy: ReturnPolicy.Policy?, approval: ReturnFlow.Approval? = null) = ReturnFlow.Request(
    docId = "rp", saleId = "s30", picks = mapOf("i30" to ReturnFlow.Pick(1.0)), reason = "دیگر",
    by = "x", role = role, policy = policy, approval = approval,
  )

  private fun post(d: ShopData, r: ReturnFlow.Request) = ReturnFlow.post(d, r, today, now, ::newId)

  @Test fun `بی سیاست یا سیاستِ خالی — همان برگشتِ امروز، همان سابقه`() {
    val d = ledger()
    val before = (post(d, req("staff", null)) as ReturnFlow.Outcome.Ok).value
    seq = 0
    val empty = (post(d, req("staff", ReturnPolicy.Policy())) as ReturnFlow.Outcome.Ok).value
    assertEquals(before.data, empty.data)
    assertEquals("allow", empty.gate.decision)
    assertFalse(empty.data.auditLog.last().notes.contains("سیاست"))
  }

  @Test fun `شاگرد بیرون از مهلت — تأییدِ مدیر لازم و دفتر دست‌نخورده`() {
    val d = ledger()
    val out = post(d, req("staff", ReturnPolicy.Policy(days = 7)))
    assertEquals(ReturnFlow.Code.APPROVAL_REQUIRED, (out as ReturnFlow.Outcome.Bad).code)
    assertTrue(d.saleReturns.isEmpty())
    val g = ReturnFlow.gate("staff", null, 100_000, noReceipt = false, damaged = false, limitMinor = 0, policyBreach = true)
    assertEquals(listOf(ReturnFlow.WHY_POLICY), g.reasons)
    assertEquals("manager", g.approver)
    //  درونِ مهلت ⇒ آزاد
    assertTrue(post(d, req("staff", ReturnPolicy.Policy(days = 16))) is ReturnFlow.Outcome.Ok)
  }

  @Test fun `شاگرد با تأییدِ مدیر — ثبت و استثنا در سابقه`() {
    val d = ledger()
    val ok = (post(d, req("staff", ReturnPolicy.Policy(days = 7), ReturnFlow.Approval("مدیر", "manager"))) as ReturnFlow.Outcome.Ok).value
    assertEquals("approve", ok.gate.decision)
    assertEquals("مدیر", ok.data.saleReturns.single().approvedBy)
    val notes = ok.data.auditLog.last().notes
    assertTrue(notes, notes.contains("استثنای سیاستِ برگشت: 16 روز پس از فاکتور"))
  }

  @Test fun `مدیر و صاحب خودشان استثنا می‌گذارند و در سابقه می‌ماند`() {
    for (role in listOf("manager", "owner")) {
      val d = ledger()
      val ok = (post(d, req(role, ReturnPolicy.Policy(days = 7))) as ReturnFlow.Outcome.Ok).value
      assertEquals(role, "allow", ok.gate.decision)
      assertTrue(role, ok.data.auditLog.last().notes.contains("استثنای سیاستِ برگشت"))
    }
  }

  @Test fun `قلمِ غیرقابلِ برگشت با نامِ کالا — شاگرد تأیید می‌خواهد، نامِ کالا در سابقه`() {
    val d = ledger()
    val policy = ReturnPolicy.Policy(blocked = listOf("کفش سایز ۴۰"))
    val q = (ReturnFlow.quote(d, "s30", mapOf("i30" to ReturnFlow.Pick(1.0))) as ReturnFlow.Outcome.Ok).value
    assertEquals(listOf("p40"), ReturnFlow.policyCheck(d, d.sales.first { it.id == "s30" }, q.lines, policy, today).blockedIds)
    assertEquals(ReturnFlow.Code.APPROVAL_REQUIRED, (post(d, req("staff", policy)) as ReturnFlow.Outcome.Bad).code)
    val ok = (post(d, req("manager", policy)) as ReturnFlow.Outcome.Ok).value
    assertTrue(ok.data.auditLog.last().notes.contains("غیرقابلِ برگشت: کفش سایز ۴۰"))
  }

  @Test fun `برگشتِ بی رسید مهلت ندارد`() {
    val d = ledger()
    val r = ReturnFlow.Request(
      docId = "nr", free = listOf(ReturnFlow.FreeLine("pA", 1.0, 100.0)), reason = "دیگر", by = "x", role = "manager",
      policy = ReturnPolicy.Policy(days = 1),
    )
    val ok = (ReturnFlow.post(d, r, today, now, ::newId) as ReturnFlow.Outcome.Ok).value
    assertFalse(ok.data.auditLog.last().notes.contains("سیاست"))
  }
}
