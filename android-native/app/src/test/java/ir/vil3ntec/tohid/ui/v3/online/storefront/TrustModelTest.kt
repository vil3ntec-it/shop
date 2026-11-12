package ir.vil3ntec.tohid.ui.v3.online.storefront

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrTrust
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.data.repo.TrustAppealDto
import ir.vil3ntec.tohid.data.repo.TrustCaseDto
import ir.vil3ntec.tohid.data.repo.TrustConfigDto
import ir.vil3ntec.tohid.data.repo.TrustEventDto
import ir.vil3ntec.tohid.data.repo.TrustPageDto
import ir.vil3ntec.tohid.data.repo.TrustPermsDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 *  ABUSE-01 — قاعده‌های «شکایت و گزارشِ خریداران» ِ برنامهٔ دکان (`TrustModel`)، همان مرزهای `lib/market-trust.js`.
 */
class TrustModelTest {

  @Before fun fa() { I18n.source = { Lang.FA } }

  private val owner = TrustConfigDto(enabled = true, permissions = TrustPermsDto(respond = true))
  private val staff = TrustConfigDto(enabled = true, permissions = TrustPermsDto(respond = false))
  private val open = TrustCaseDto(id = "tc_1", kind = "complaint", subject = "shop", category = "rude", status = "open", createdAt = 1000)

  @Test fun `پاسخ فقط با اجازهٔ پاسخ، پروندهٔ نابسته و زیرِ ۲۰ پاسخ`() {
    assertTrue(TrustModel.canReply(owner, open))
    assertFalse(TrustModel.canReply(staff, open))
    assertFalse(TrustModel.canReply(null, open))
    assertFalse(TrustModel.canReply(owner, open.copy(status = "closed")))
    val full = open.copy(history = List(20) { TrustEventDto(type = "shop_response", at = it.toLong()) })
    assertFalse(TrustModel.canReply(owner, full))
    assertTrue(TrustModel.canReply(owner, full.copy(history = full.history.drop(1))))
  }

  @Test fun `اعتراض فقط به «وارد است»، یک بار، در مهلت و با اجازه`() {
    val upheld = open.copy(status = "decided", outcome = "upheld", canAppeal = true, appealDeadline = 5000)
    assertTrue(TrustModel.canAppeal(owner, upheld, now = 5000))
    assertFalse("پس از مهلت", TrustModel.canAppeal(owner, upheld, now = 5001))
    assertFalse(TrustModel.canAppeal(staff, upheld, now = 10))
    assertFalse(TrustModel.canAppeal(owner, upheld.copy(canAppeal = false), now = 10))
    assertFalse(TrustModel.canAppeal(owner, upheld.copy(appeal = TrustAppealDto(body = "x".repeat(12), at = 3)), now = 10))
  }

  @Test fun `متنِ پاسخ ۱ تا ۲۰۰۰؛ اعتراض ۱۰ تا ۲۰۰۰`() {
    assertEquals(TrustModel.Problem.EMPTY, TrustModel.replyProblem("   "))
    assertNull(TrustModel.replyProblem(" پوزش "))
    assertNull(TrustModel.replyProblem("x".repeat(2000)))
    assertEquals(TrustModel.Problem.LONG, TrustModel.replyProblem("x".repeat(2001)))
    assertEquals(TrustModel.Problem.SHORT, TrustModel.appealProblem("کوتاه"))
    assertEquals(TrustModel.Problem.SHORT, TrustModel.appealProblem("  ۱۲۳۴۵۶۷۸۹  "))
    assertNull(TrustModel.appealProblem("کالا اصل بود؛ فاکتورِ خرید داریم"))
    assertEquals(TrustModel.Problem.LONG, TrustModel.appealProblem("x".repeat(2001)))
  }

  @Test fun `فهرست — بی‌پاسخ و قابلِ اعتراض بالا، سپس تازه‌ترین`() {
    val closedNew = open.copy(id = "tc_closed", status = "closed", createdAt = 9000)
    val replied = open.copy(id = "tc_replied", createdAt = 2000, history = listOf(TrustEventDto(type = "shop_response")))
    val oldOpen = open.copy(id = "tc_old", createdAt = 500)
    val appealable = open.copy(id = "tc_app", status = "decided", outcome = "upheld", canAppeal = true, createdAt = 100)
    assertEquals(listOf("tc_1", "tc_old", "tc_app", "tc_closed", "tc_replied"),
      TrustModel.sorted(listOf(closedNew, oldOpen, appealable, open, replied)).map { it.id })
    assertTrue(TrustModel.waiting(open))
    assertFalse(TrustModel.waiting(replied))
    assertFalse(TrustModel.waiting(closedNew))
  }

  @Test fun `دربارهٔ — کالا با نامِ دفتر، سفارش بی شماره، دکان`() {
    val report = open.copy(kind = "report", subject = "product", productId = "rice", category = "counterfeit")
    assertEquals(StrTrust.aboutProduct("برنج"), TrustModel.subject(report) { if (it == "rice") "برنج" else null })
    assertEquals(StrTrust.aboutProduct("rice"), TrustModel.subject(report) { null })
    assertEquals(StrTrust.aboutOrder, TrustModel.subject(open.copy(subject = "order")) { null })
    assertEquals(StrTrust.aboutShop, TrustModel.subject(open) { null })
    assertEquals(StrTrust.repCounterfeit, TrustModel.category("report", "counterfeit"))
    assertEquals(StrTrust.cmpRude, TrustModel.category("complaint", "rude"))
    assertEquals(StrTrust.cmpOther, TrustModel.category("complaint", "nope"))
  }

  @Test fun `پاسخِ سرور خوانده می‌شود و هیچ کلیدِ شاکی جایی در مدل ندارد`() {
    val raw = """{"cases":[{"id":"tc_a","kind":"complaint","subject":"order","category":"wrong_item","status":"open",
      "createdAt":7,"updatedAt":8,"severity":"medium","outcome":null,"appealOutcome":null,"evidenceCount":1,"reporterHint":"0791•••233"}]}"""
    val page = ApiJson.decode<TrustPageDto>(Json.parseToJsonElement(raw) as JsonObject)
    assertEquals("tc_a", page.cases.single().id)
    assertEquals(1, page.cases.single().evidenceCount)
    val fields = TrustCaseDto::class.java.declaredFields.map { it.name.lowercase() }
    assertTrue(fields.none { "phone" in it || "reporter" in it || "token" in it || "orderno" in it })
  }
}
