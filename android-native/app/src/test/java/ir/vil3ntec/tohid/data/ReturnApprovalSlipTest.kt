package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.perm.ApprovalSlip
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  RET-04، RET-10، STF-02 (S14): برگهٔ امضاشدهٔ تأییدِ برگشت در `ReturnFlow.post`.
 *
 *  دفتر همان بردارِ `core/test-vectors/returns-v1.json` است (فاکتورِ S13: A ۱۵۰، B ۲×۲۵۰، C ۳۵۰، تخفیفِ ۱۰٪؛
 *  فاکتورِ s60: چهار عدد pM به ۱۰۰۰). عددها از قاعدهٔ سرور (`groupReturns`، `cumulativeNeed`) نوشته شده‌اند.
 */
class ReturnApprovalSlipTest {

  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  private val ledger: ShopData = run {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/returns-v1.json")
      if (f.isFile) {
        val v = Json.parseToJsonElement(f.readText()).jsonObject
        return@run json.decodeFromJsonElement(ShopData.serializer(), v.getValue("ledger"))
      }
      dir = dir.parentFile
    }
    error("core/test-vectors/returns-v1.json پیدا نشد")
  }

  private var seq = 0
  private fun newId(): String = "n${seq++}"
  private val today = "2026-10-30"
  private val now = 1_761_800_000_000L

  private fun slip(doc: String, amount: Long, by: String = "u-mgr") = ApprovalSlip.Slip(
    shop = "shop-1", doc = doc, perm = "returns.create", pctBp = null, amount = amount, by = by, key = "k-1",
    iat = now - 1_000, exp = now + 60_000, nonce = "abcdefghijklmnop", reason = "کالای خراب", sig = "sig",
  )

  /**  شاگرد، فاکتورِ S13: A ضایعات + B سالم ⇒ ۱۳۵ + ۲۲۵ = ۳۶۰ و تأییدِ مدیر (ضایعات) */
  private fun damagedByStaff(docId: String, approval: ReturnFlow.Approval?) = ReturnFlow.Request(
    docId = docId, saleId = "s13",
    picks = mapOf(
      "i13a" to ReturnFlow.Pick(1.0, ReturnFlow.DAMAGED),
      "i13b" to ReturnFlow.Pick(1.0, ReturnFlow.RESTOCK),
    ),
    reason = "خراب یا معیوب", mode = ReturnFlow.CASH, by = "شاگرد", role = "staff", approval = approval,
  )

  private fun rowsOf(d: ShopData, docId: String) = d.saleReturns.filter { it.docId == docId }

  @Test
  fun signedSlipRidesOnTheFirstRowOfTheDocumentOnly() {
    val r = ReturnFlow.post(ledger, damagedByStaff("doc-a", ReturnFlow.Approval("مدیر", "manager", slip("returns/doc-a", 36_000))), today, now, ::newId)
    assertTrue(r.toString(), r is ReturnFlow.Outcome.Ok)
    val posted = (r as ReturnFlow.Outcome.Ok).value
    assertEquals(360.0, posted.total, 0.0)
    val rows = rowsOf(posted.data, "doc-a")
    assertEquals(2, rows.size)
    val first = rows[0].approval
    assertEquals("returns/doc-a", first?.get("doc")?.jsonPrimitive?.content)
    assertEquals("returns.create", first?.get("perm")?.jsonPrimitive?.content)
    assertEquals("36000", first?.get("amount")?.jsonPrimitive?.content)
    assertEquals("u-mgr", first?.get("by")?.jsonPrimitive?.content)
    assertNull(rows[1].approval)
    //  ردیف‌ها نامِ تأییدکننده را برای گزارشِ ضدتقلب دارند (S14)
    assertTrue(rows.all { it.approvedBy == "مدیر" && it.by == "شاگرد" })
    //  روی سیم: فقط ردیفِ اول کلیدِ approval دارد (همان `fields.approval` که سرور می‌خواند)
    val wire = rows.map { Json.encodeToString(SaleReturn.serializer(), it) }
    assertTrue(wire[0].contains("\"approval\""))
    assertFalse(wire[1].contains("\"approval\""))
  }

  @Test
  fun slipOfAnotherDocumentOrSmallerAmountIsRefusedAndLedgerUntouched() {
    val other = ReturnFlow.post(ledger, damagedByStaff("doc-b", ReturnFlow.Approval("مدیر", "manager", slip("returns/doc-x", 36_000))), today, now, ::newId)
    assertEquals(ReturnFlow.Code.APPROVAL_REQUIRED, (other as ReturnFlow.Outcome.Bad).code)
    val small = ReturnFlow.post(ledger, damagedByStaff("doc-b", ReturnFlow.Approval("مدیر", "manager", slip("returns/doc-b", 35_999))), today, now, ::newId)
    assertEquals(ReturnFlow.Code.APPROVAL_REQUIRED, (small as ReturnFlow.Outcome.Bad).code)
    assertTrue(rowsOf(ledger, "doc-b").isEmpty())
  }

  @Test
  fun approvalWithoutSlipWritesTodaysRowsUnchanged() {
    val r = ReturnFlow.post(ledger, damagedByStaff("doc-c", ReturnFlow.Approval("مدیر", "manager")), today, now, ::newId)
    val rows = rowsOf((r as ReturnFlow.Outcome.Ok).value.data, "doc-c")
    assertEquals(2, rows.size)
    assertTrue(rows.all { it.approval == null })
    assertTrue(rows.none { Json.encodeToString(SaleReturn.serializer(), it).contains("\"approval\"") })
  }

  @Test
  fun noSlipIsWrittenWhenNoApprovalIsNeeded() {
    //  صاحب: نه ضایعات، نه سقف ⇒ «مجاز»؛ برگهٔ اضافه روی ردیف نمی‌نشیند
    val req = damagedByStaff("doc-d", ReturnFlow.Approval("مدیر", "manager", slip("returns/doc-d", 36_000))).copy(role = "owner")
    val rows = rowsOf((ReturnFlow.post(ledger, req, today, now, ::newId) as ReturnFlow.Outcome.Ok).value.data, "doc-d")
    assertTrue(rows.all { it.approval == null && it.approvedBy == null })
  }

  @Test
  fun roleCapIsCumulativePerInvoiceAndAboveItOnlyOwnerApproves() {
    //  صاحب پیش‌تر ۲ عدد از s60 را برگرداند (۵۰۰ افغانی)
    val first = ReturnFlow.post(
      ledger,
      ReturnFlow.Request(docId = "doc-e1", saleId = "s60", picks = mapOf("i60" to ReturnFlow.Pick(2.0)), reason = "پشیمانی", mode = ReturnFlow.CASH, by = "صاحب", role = "owner"),
      today, now, ::newId,
    )
    val d = (first as ReturnFlow.Outcome.Ok).value.data
    assertEquals(50_000L, ReturnFlow.priorMinor(d, "s60"))
    assertEquals(0L, ReturnFlow.priorMinor(d, null))

    //  سقفِ شاگرد ۶۰۰ افغانی: این سند (۲۵۰) تنها زیرِ سقف است، با پیشین (۷۵۰) بالای سقف
    val layers = listOf(mapOf("returns.create" to mapOf("mode" to "allow", "caps" to mapOf("maxAmount" to 60_000))))
    assertEquals("allow", ReturnFlow.permDecision("staff", layers, 25_000))
    val perm = ReturnFlow.permDecision("staff", layers, 25_000, ReturnFlow.priorMinor(d, "s60"))
    assertEquals("approve", perm)

    fun req(approval: ReturnFlow.Approval?) = ReturnFlow.Request(
      docId = "doc-e2", saleId = "s60", picks = mapOf("i60" to ReturnFlow.Pick(1.0)), reason = "پشیمانی",
      mode = ReturnFlow.CASH, by = "شاگرد", role = "staff", perm = perm, approval = approval,
    )
    //  تأییدِ عادیِ مدیر بس نیست (S14)
    val byManager = ReturnFlow.post(d, req(ReturnFlow.Approval("مدیر", "manager", slip("returns/doc-e2", 25_000))), today, now, ::newId)
    assertEquals(ReturnFlow.Code.APPROVAL_REQUIRED, (byManager as ReturnFlow.Outcome.Bad).code)
    //  برگهٔ صاحب همین سند (۲۵۰، نه ۷۵۰) را می‌پوشاند
    val byOwner = ReturnFlow.post(d, req(ReturnFlow.Approval("صاحب", "owner", slip("returns/doc-e2", 25_000, by = "u-owner"))), today, now, ::newId)
    val rows = rowsOf((byOwner as ReturnFlow.Outcome.Ok).value.data, "doc-e2")
    assertEquals("returns/doc-e2", rows.single().approval?.get("doc")?.jsonPrimitive?.content)
  }
}
