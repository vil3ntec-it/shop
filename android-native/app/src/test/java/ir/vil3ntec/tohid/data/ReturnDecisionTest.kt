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
 *  RET-09، RET-10 — سدی که برگهٔ برگشت نشان می‌دهد ([ReturnDecision.of]: `ReturnFlowSheet` ِ نسخهٔ ۳ و `ReturnFlowDialog` ِ
 *  کامپیوتر) همان است که `ReturnFlow.post` هنگامِ ثبت می‌سنجد. پیش از این، برگهٔ نسخهٔ ۳ سیاست و برگشت‌های پیشینِ فاکتور را
 *  به سد نمی‌داد: صفحه «مجاز» می‌گفت، دکمهٔ «درخواستِ تأیید» نمی‌آمد و ثبت با APPROVAL_REQUIRED رد می‌شد (بن‌بست)،
 *  یا شاگرد بی تأییدِ مدیر بیرون از مهلت برمی‌گرداند. دفتر همان بردارِ `core/test-vectors/returns-v1.json` است
 *  (s13: سه قلم، ۹۰۰ ؋؛ s30: «کفش سایز ۴۰» ِ ۲۰۲۶-۱۰-۰۳).
 */
class ReturnDecisionTest {

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

  /** همان که برگه حساب می‌کند: پیش‌فاکتورِ قلم‌های برگزیده و سدِ آن */
  private fun screen(d: ShopData, saleId: String, picks: Map<String, ReturnFlow.Pick>, role: String, limit: Double, policy: ReturnPolicy.Policy?): ReturnDecision.Decision {
    val q = (ReturnFlow.quote(d, saleId, picks) as ReturnFlow.Outcome.Ok).value
    return ReturnDecision.of(d, d.sales.first { it.id == saleId }, q, role, null, limit, policy, today)
  }

  /** همان که برگه با دکمهٔ «ثبت» می‌فرستد */
  private fun post(
    d: ShopData, doc: String, saleId: String, picks: Map<String, ReturnFlow.Pick>, role: String, limit: Double,
    policy: ReturnPolicy.Policy?, perm: String?, approval: ReturnFlow.Approval? = null,
  ) = ReturnFlow.post(
    d,
    ReturnFlow.Request(docId = doc, saleId = saleId, picks = picks, reason = "دیگر", by = "x", role = role, perm = perm, limit = limit, approval = approval, policy = policy),
    today, now, ::newId,
  )

  @Test fun `RET-10 برگشتِ دومِ فاکتور که جمعش از سقفِ دستگاه می‌گذرد در برگه هم تأییدِ صاحب می‌خواهد`() {
    var d = ledger()
    //  فاکتورِ s13 با تخفیف (۱٬۰۰۰ ⇐ ۹۰۰ ؋)، پس سهمِ هر قلم ۹۰٪ ِ بهایش
    val b = mapOf("i13b" to ReturnFlow.Pick(2.0))   // ۴۵۰ ؋ — زیرِ سقفِ ۶۰۰
    val first = screen(d, "s13", b, "staff", 600.0, null)
    assertEquals("allow", first.gate.decision)
    d = (post(d, "r1", "s13", b, "staff", 600.0, null, first.perm) as ReturnFlow.Outcome.Ok).value.data

    val c = mapOf("i13c" to ReturnFlow.Pick(1.0))   // ۳۱۵ ؋ — تنها زیرِ سقف، با ۴۵۰ ِ پیشین ۷۶۵
    val second = screen(d, "s13", c, "staff", 600.0, null)
    assertEquals(45_000L, second.priorMinor)
    assertEquals("approve", second.gate.decision)
    assertEquals(listOf(ReturnFlow.WHY_OVER_LIMIT), second.gate.reasons)
    assertEquals("owner", second.gate.approver)
    //  ثبت همان را می‌گوید: بی تأیید رد، با تأییدِ مدیر رد، با تأییدِ صاحب ثبت
    assertEquals(ReturnFlow.Code.APPROVAL_REQUIRED, (post(d, "r2", "s13", c, "staff", 600.0, null, second.perm) as ReturnFlow.Outcome.Bad).code)
    assertFalse(ReturnFlow.canApprove(second.gate, "manager"))
    assertEquals(
      ReturnFlow.Code.APPROVAL_REQUIRED,
      (post(d, "r2", "s13", c, "staff", 600.0, null, second.perm, ReturnFlow.Approval("مدیر", "manager")) as ReturnFlow.Outcome.Bad).code,
    )
    assertTrue(post(d, "r2", "s13", c, "staff", 600.0, null, second.perm, ReturnFlow.Approval("صاحب", "owner")) is ReturnFlow.Outcome.Ok)
    //  همین برگشت برای صاحبِ دکان سقف ندارد
    assertEquals("allow", screen(d, "s13", c, "owner", 600.0, null).gate.decision)
  }

  @Test fun `RET-09 شاگرد بیرون از مهلت در برگه تأییدِ مدیر می‌بیند و ثبت همان را می‌خواهد`() {
    val d = ledger()
    val p = mapOf("i30" to ReturnFlow.Pick(1.0))
    val policy = ReturnPolicy.Policy(days = 7)
    val staff = screen(d, "s30", p, "staff", 0.0, policy)
    assertTrue(staff.verdict.late)
    assertEquals(16, staff.verdict.ageDays)
    assertEquals("approve", staff.gate.decision)
    assertEquals(listOf(ReturnFlow.WHY_POLICY), staff.gate.reasons)
    assertEquals("manager", staff.gate.approver)
    assertEquals(ReturnFlow.Code.APPROVAL_REQUIRED, (post(d, "r1", "s30", p, "staff", 0.0, policy, staff.perm) as ReturnFlow.Outcome.Bad).code)
    val ok = (post(d, "r1", "s30", p, "staff", 0.0, policy, staff.perm, ReturnFlow.Approval("مدیر", "manager")) as ReturnFlow.Outcome.Ok).value
    assertTrue(ok.data.auditLog.last().notes.contains("استثنای سیاستِ برگشت: 16 روز پس از فاکتور"))
    //  مدیر خودش استثنا می‌گذارد: برگه «مجاز» و هشدارِ سیاست (verdict) را نشان می‌دهد
    val manager = screen(d, "s30", p, "manager", 0.0, policy)
    assertEquals("allow", manager.gate.decision)
    assertFalse(manager.verdict.ok)
    //  درونِ مهلت ⇒ آزاد
    assertEquals("allow", screen(d, "s30", p, "staff", 0.0, ReturnPolicy.Policy(days = 16)).gate.decision)
  }

  @Test fun `RET-09 قلمِ غیرقابلِ برگشت در برگه همان نامِ کالا و همان سد`() {
    val d = ledger()
    val p = mapOf("i30" to ReturnFlow.Pick(1.0))
    val policy = ReturnPolicy.Policy(blocked = listOf("کفش سایز ۴۰"))
    val staff = screen(d, "s30", p, "staff", 0.0, policy)
    assertEquals(listOf("p40"), staff.verdict.blockedIds)
    assertEquals(listOf(ReturnFlow.WHY_POLICY), staff.gate.reasons)
    assertEquals(ReturnFlow.Code.APPROVAL_REQUIRED, (post(d, "r1", "s30", p, "staff", 0.0, policy, staff.perm) as ReturnFlow.Outcome.Bad).code)
  }

  @Test fun `برگه و ثبت همیشه یک سد می‌گویند — نقش، سقف، سیاست و برگشتِ پیشین`() {
    val base = ledger()
    //  فاکتورِ s13 با یک برگشتِ پیشینِ ۴۵۰ ؋ (صاحب، بی سقف)
    val withPrior = (post(base, "r0", "s13", mapOf("i13b" to ReturnFlow.Pick(2.0)), "owner", 0.0, null, null) as ReturnFlow.Outcome.Ok).value.data
    val cases = listOf(
      Triple(base, "s30", mapOf("i30" to ReturnFlow.Pick(1.0))),
      Triple(base, "s13", mapOf("i13c" to ReturnFlow.Pick(1.0, ReturnFlow.DAMAGED))),
      Triple(withPrior, "s13", mapOf("i13c" to ReturnFlow.Pick(1.0))),
    )
    val policies = listOf(null, ReturnPolicy.Policy(), ReturnPolicy.Policy(days = 7), ReturnPolicy.Policy(days = 30), ReturnPolicy.Policy(blocked = listOf("کفش سایز ۴۰")))
    var n = 0
    for ((d, saleId, picks) in cases) for (role in listOf("staff", "manager", "owner")) for (limit in listOf(0.0, 400.0, 600.0, 2_000.0)) for (policy in policies) {
      val shown = screen(d, saleId, picks, role, limit, policy)
      val what = "$saleId $role $limit $policy ⇒ ${shown.gate}"
      val out = post(d, "p${n++}", saleId, picks, role, limit, policy, shown.perm)
      when (shown.gate.decision) {
        "allow" -> assertTrue(what, out is ReturnFlow.Outcome.Ok)
        "approve" -> {
          assertEquals(what, ReturnFlow.Code.APPROVAL_REQUIRED, (out as ReturnFlow.Outcome.Bad).code)
          //  تأییدِ همان نقشی که برگه نام می‌برد کافی است
          val by = ReturnFlow.Approval("تأییدکننده", shown.gate.approver)
          assertTrue(what, post(d, "q${n++}", saleId, picks, role, limit, policy, shown.perm, by) is ReturnFlow.Outcome.Ok)
        }
        else -> assertEquals(what, ReturnFlow.Code.RETURN_DENIED, (out as ReturnFlow.Outcome.Bad).code)
      }
    }
    assertTrue(n >= cases.size * 3 * 4 * policies.size)
  }

  @Test fun `هنوز قلمی برگزیده نشده — مبلغِ صفر، سیاستِ پذیرفته`() {
    val d = ledger()
    val none = ReturnDecision.of(d, d.sales.first { it.id == "s30" }, null, "staff", null, 600.0, ReturnPolicy.Policy(days = 7), today)
    assertTrue(none.verdict.ok)
    assertEquals("allow", none.gate.decision)
  }
}
