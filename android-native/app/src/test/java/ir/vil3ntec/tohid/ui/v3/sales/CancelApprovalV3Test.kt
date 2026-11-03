package ir.vil3ntec.tohid.ui.v3.sales

import ir.vil3ntec.tohid.core.audit.InvoiceVoid
import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import ir.vil3ntec.tohid.sync.v3.V3Convert
import ir.vil3ntec.tohid.ui.screens.SalesVoid
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  STF-02 لغو، فقط Sync v3 — برگهٔ تأییدِ لغو جای «اجازه» برای نقشِ «نیازمند تأیید»؛ در فیلدِ جدای `Sale.cancelApproval`
 *  (برگهٔ تخفیف دست نمی‌خورد)؛ مبدلِ v3 آن را `approval` ِ خودِ op ِ «لغو» می‌کند (همان که سرور در `needOf` می‌سنجد:
 *  `staff-approval.test.js` «لغوِ فاکتور با «نیازمند تأیید»…») و op ِ ویرایشِ سند نمی‌سازد (بی `append_only_edit`).
 */
class CancelApprovalV3Test {

  private var ids = 0
  private fun newId() = "id-${++ids}"
  private val discountSlip = buildJsonObject { put("perm", "sales.discount"); put("nonce", "n-disc") }
  private val ledger = SalesTestLedger.ledger.let { d ->
    d.copy(sales = d.sales.map { if (it.id == "s1") it.copy(approval = discountSlip) else it })
  }
  private val cancelSlip: JsonObject = buildJsonObject {
    put("v", 1); put("shop", "shop-1"); put("doc", "sales/s1"); put("perm", "sales.cancel"); put("by", "u-owner"); put("nonce", "n-cx")
  }

  @Test fun `بی اجازه و بی برگه همان رد؛ با برگه لغو می‌نشیند و برگه جدا از برگهٔ تخفیف می‌ماند`() {
    val denied = SalesVoid.run(ledger, "s1", false, InvoiceVoid.Kind.VOID, "اشتباهِ صندوق", "2026-11-03", 1, ::newId, "شاگرد", "بی اجازه")
    assertEquals(SalesVoid.Outcome.Denied("بی اجازه"), denied)
    val r = SalesVoid.run(ledger, "s1", false, InvoiceVoid.Kind.VOID, "اشتباهِ صندوق", "2026-11-03", 1, ::newId, "شاگرد", "بی اجازه",
      cancelApproval = cancelSlip)
    r as SalesVoid.Outcome.Ok
    val s = r.data.sales.single { it.id == "s1" }
    assertEquals("cancelled", s.status)
    assertEquals(cancelSlip, s.cancelApproval)
    assertEquals("برگهٔ تخفیف بازنویسی نشد", discountSlip, s.approval)
    //  مدیر/صاحب (اجازه دارد) هیچ برگه‌ای نمی‌نویسد — همان امروز
    val own = SalesVoid.run(ledger, "s1", true, InvoiceVoid.Kind.VOID, "اشتباه", "2026-11-03", 1, ::newId, "مدیر", "بی اجازه")
    assertNull((own as SalesVoid.Outcome.Ok).data.sales.single { it.id == "s1" }.cancelApproval)
  }

  @Test fun `مبدلِ v3 — برگه روی op ِ لغو، نه فیلدِ سند؛ op بومی است`() {
    val r = SalesVoid.run(ledger, "s1", false, InvoiceVoid.Kind.VOID, "اشتباهِ صندوق", "2026-11-03", 1, ::newId, "شاگرد", "بی اجازه",
      cancelApproval = cancelSlip) as SalesVoid.Outcome.Ok
    val op = LedgerDiff.diff(LedgerDiff.shadowOf(ledger), LedgerDiff.toJson(r.data), now = 5).single { it.table == "sales" && it.rowId == "s1" }
    assertEquals(cancelSlip, op.fields!!["cancelApproval"])
    val conv = V3Convert.fromLegacyOp(op, "001760000000000:000000:dev-x")
    assertTrue(conv.reason, conv.native)
    val cancel = conv.ops.single { (it["kind"] as JsonPrimitive).content == "cancel" }
    assertEquals(cancelSlip, cancel["approval"])
    assertEquals("اشتباهِ صندوق", cancel["reason"]!!.jsonPrimitive.content)
    assertFalse("هیچ ویرایشِ سندی (append_only_edit)", conv.ops.any { (it["kind"] as JsonPrimitive).content == "update" })
    assertTrue(conv.ops.none { (it["fields"] as? JsonObject)?.containsKey("cancelApproval") == true })
  }

  @Test fun `لغوِ بی برگه همان op ِ پیشین است`() {
    val r = SalesVoid.run(ledger, "s1", true, InvoiceVoid.Kind.VOID, "اشتباه", "2026-11-03", 1, ::newId, "مدیر", "بی اجازه") as SalesVoid.Outcome.Ok
    val op = LedgerDiff.diff(LedgerDiff.shadowOf(ledger), LedgerDiff.toJson(r.data), now = 5).single { it.table == "sales" && it.rowId == "s1" }
    val cancel = V3Convert.fromLegacyOp(op, "001760000000000:000000:dev-x").ops.single { (it["kind"] as JsonPrimitive).content == "cancel" }
    assertNull(cancel["approval"])
  }
}
