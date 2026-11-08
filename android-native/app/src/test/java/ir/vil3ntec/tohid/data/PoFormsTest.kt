package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrPurchaseOrders
import ir.vil3ntec.tohid.core.i18n.StrPurchasePages
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  منطقِ فرم‌های سفارشِ خرید که کامپیوتر و گوشیِ نسخهٔ ۳ هر دو صدا می‌زنند (`PoForms`؛ PUR-02/03/04/14): پالایهٔ فهرست،
 *  شمارشِ رسید (جزئی، اضافهٔ ردشده، خراب)، اختلاف‌های تطبیق، سدِ دو بار زدن، مجوزِ هر کار و درِ صفحه.
 */
class PoFormsTest {

  private val d0 = ShopData(
    products = listOf(
      Product(id = "pA", name = "کالای A", unit = "عدد", purchasePrice = 100.0, minStock = 5.0, barcodes = listOf("111")),
      Product(id = "pB", name = "کالای B", unit = "عدد", purchasePrice = 200.0, barcodes = listOf("222")),
      Product(id = "pC", name = "کالای C", unit = "عدد", purchasePrice = 50.0),
    ),
    suppliers = listOf(Supplier(id = "s1", name = "شرکتِ نمونه")),
  )

  private val r1 = PoReceiptDto(
    id = "01JS0000000000000000000R01", poId = "po1",
    lines = listOf(PoReceiptLineDto("l1", 10_000, 0, 10_000), PoReceiptLineDto("l2", 7_000, 0, 20_000)),
  )

  /** فرستاده و بخشی: A ۱۰ از ۱۰ رسید، B ۷ از ۱۰ (۳ باز) */
  private val partial = PurchaseOrderDto(
    id = "po1", no = 7, supplierId = "s1", supplierName = "شرکتِ نمونه", state = "sent", status = "partial",
    allowed = listOf("cancel", "receive"),
    lines = listOf(PoLineDto("l1", "pA", "کالای A", "عدد", 10_000, 10_000), PoLineDto("l2", "pB", "کالای B", "عدد", 10_000, 20_000)),
    progress = listOf(
      PoProgressDto("l1", "pA", 10_000, 10_000, 0, 10_000, 0, 0),
      PoProgressDto("l2", "pB", 10_000, 7_000, 0, 7_000, 3_000, 0),
    ),
    receipts = listOf(r1),
  )

  private fun order(id: String, state: String, status: String) = PurchaseOrderDto(id = id, state = state, status = status)

  private val rid = "01JS0000000000000000000R02"

  private fun rows(vararg x: Pair<String, PoForms.RecvRow>) = PoForms.recvRows(partial) + x

  /* ── فهرست ── */

  @Test fun `پالایهٔ فهرست — بخشی و کامل از صفحهٔ «فرستاده» ِ سرور و پالایشِ دوم در برنامه`() {
    assertNull(PoForms.serverState(PoForms.Filter.ALL))
    assertEquals("draft", PoForms.serverState(PoForms.Filter.DRAFT))
    assertEquals("sent", PoForms.serverState(PoForms.Filter.SENT))
    assertEquals("sent", PoForms.serverState(PoForms.Filter.PARTIAL))
    assertEquals("sent", PoForms.serverState(PoForms.Filter.COMPLETE))
    assertEquals("cancelled", PoForms.serverState(PoForms.Filter.CANCELLED))
    val all = listOf(
      order("a", "draft", "draft"), order("b", "sent", "sent"), order("c", "sent", "partial"),
      order("d", "sent", "complete"), order("e", "cancelled", "cancelled"), order("f", "sent", "partial"),
    )
    assertEquals(all, PoForms.shown(all, PoForms.Filter.ALL))
    assertEquals(listOf("c", "f"), PoForms.shown(all, PoForms.Filter.PARTIAL).map { it.id })
    assertEquals(listOf("b"), PoForms.shown(all, PoForms.Filter.SENT).map { it.id })
    assertEquals(listOf("d"), PoForms.shown(all, PoForms.Filter.COMPLETE).map { it.id })
    assertEquals(listOf("e"), PoForms.shown(all, PoForms.Filter.CANCELLED).map { it.id })
    assertEquals(PoForms.Filter.PARTIAL, PoForms.Filter.of("partial"))
    assertEquals(PoForms.Filter.ALL, PoForms.Filter.of("هرچه"))
    assertEquals(6, PoForms.Filter.entries.map(PoForms::label).toSet().size)
  }

  /* ── دریافت ── */

  @Test fun `رسیدِ جزئی پذیرفته می‌شود و باقی‌مانده باز می‌ماند`() {
    val c = PoForms.receipt(partial, rows("l2" to PoForms.RecvRow(received = "2", price = "200")), rid, allowOver = false)
    c as PoForms.ReceiptCheck.Ok
    assertEquals(listOf(PoReceiptLineDto("l2", 2_000, 0, 20_000)), c.lines)
    val p = c.posted.single()
    assertEquals(3_000, p.expectedMilli)
    assertEquals(2_000, p.acceptedMilli)
    assertEquals(1_000, p.shortMilli)
    assertEquals(0, p.overMilli)
    assertEquals(40_000, c.totalMinor)
    assertEquals(PoForms.After(leftMilli = 1_000, overMilli = 0), PoForms.after(partial, "l2", PoForms.RecvRow(received = "2")))
    assertTrue(PoForms.after(partial, "l2", PoForms.RecvRow(received = "3"))!!.done)
  }

  @Test fun `بیش از باقی‌مانده رد می‌شود مگر با «بیش از سفارش»`() {
    val over = rows("l2" to PoForms.RecvRow(received = "5", price = "200"))
    val bad = PoForms.receipt(partial, over, rid, allowOver = false)
    assertEquals(PoForms.ReceiptCheck.Bad(StrPurchaseOrders.rowOver(2, "3")), bad)
    val ok = PoForms.receipt(partial, over, rid, allowOver = true) as PoForms.ReceiptCheck.Ok
    assertEquals(2_000, ok.posted.single().overMilli)
    assertEquals(PoForms.After(leftMilli = 0, overMilli = 2_000), PoForms.after(partial, "l2", PoForms.RecvRow(received = "5")))
    //  ردیفی که کامل رسیده هم یک واحدِ دیگر را بی تیک نمی‌پذیرد
    val full = PoForms.receipt(partial, rows("l1" to PoForms.RecvRow(received = "1", price = "100")), rid, allowOver = false)
    assertEquals(PoForms.ReceiptCheck.Bad(StrPurchaseOrders.rowOver(1, "0")), full)
  }

  @Test fun `خراب از پذیرفته کم می‌شود و هرگز بیش از رسیده نیست`() {
    val c = PoForms.receipt(partial, rows("l2" to PoForms.RecvRow(received = "4", damaged = "1", price = "200")), rid, allowOver = false)
    c as PoForms.ReceiptCheck.Ok
    assertEquals(3_000, c.posted.single().acceptedMilli)
    assertEquals(1_000, c.posted.single().damagedMilli)
    assertEquals(60_000, c.totalMinor)
    assertTrue(PoForms.after(partial, "l2", PoForms.RecvRow(received = "4", damaged = "1"))!!.done)
    assertEquals(
      PoForms.ReceiptCheck.Bad(StrPurchasePages.rowDamagedOver(2)),
      PoForms.receipt(partial, rows("l2" to PoForms.RecvRow(received = "1", damaged = "2", price = "200")), rid, false),
    )
    assertNull(PoForms.after(partial, "l2", PoForms.RecvRow(received = "1", damaged = "2")))
  }

  @Test fun `فرمِ خالی، عددِ نادرست، کرایهٔ نادرست و سفارشِ دریافت‌نگیر پیش از سرور رد می‌شوند`() {
    assertEquals(PoForms.ReceiptCheck.Bad(StrPurchasePages.nothingEntered), PoForms.receipt(partial, PoForms.recvRows(partial), rid, false))
    assertEquals(PoForms.ReceiptCheck.Bad(StrPurchasePages.rowBadQty(2)), PoForms.receipt(partial, rows("l2" to PoForms.RecvRow(received = "x", price = "200")), rid, false))
    assertEquals(PoForms.ReceiptCheck.Bad(StrPurchasePages.rowBadPrice(2)), PoForms.receipt(partial, rows("l2" to PoForms.RecvRow(received = "1", price = "")), rid, false))
    assertEquals(
      PoForms.ReceiptCheck.Bad(StrPurchasePages.freightBad),
      PoForms.receipt(partial, rows("l2" to PoForms.RecvRow(received = "1", price = "200")), rid, false, freight = "-5"),
    )
    val cancelled = partial.copy(state = "cancelled", status = "cancelled", allowed = emptyList())
    assertEquals(
      PoForms.ReceiptCheck.Bad(StrPurchaseOrders.notReceivable),
      PoForms.receipt(cancelled, rows("l2" to PoForms.RecvRow(received = "1", price = "200")), rid, false),
    )
    //  رقمِ فارسی (بارکدخوان یا صفحه‌کلید) همان عدد است
    val fa = PoForms.receipt(partial, rows("l2" to PoForms.RecvRow(received = "۲", price = "۲۰۰")), rid, false) as PoForms.ReceiptCheck.Ok
    assertEquals(2_000, fa.lines.single().receivedMilli)
  }

  @Test fun `اسکن یک واحد به «رسیده» ِ همان ردیف می‌افزاید و بارکدِ بیگانه را می‌گوید`() {
    val index = ShopStore.barcodeIndex(d0)
    val s1 = PoForms.scan(partial, PoForms.recvRows(partial), index, " 222 ") as PoForms.Scan.Added
    assertEquals("l2", s1.lineId)
    assertEquals("1", s1.received)
    val s2 = PoForms.scan(partial, s1.rows, index, "۲۲۲") as PoForms.Scan.Added
    assertEquals("2", s2.rows.getValue("l2").received)
    assertEquals("200", s2.rows.getValue("l2").price)
    assertEquals(PoForms.Scan.NotInPo("999"), PoForms.scan(partial, s2.rows, index, "999"))
  }

  /* ── تطبیق ── */

  @Test fun `تطبیق — فاکتورِ بیش از رسیده و فیِ بالاتر برجسته و بالای فهرست`() {
    val billed = partial.copy(
      billCount = 1,
      bills = listOf(
        PoBillDto(
          id = "b1", poId = "po1", billNo = "F-1",
          lines = listOf(PoBillLineDto("l1", "pA", 10_000, 100_000), PoBillLineDto("l2", "pB", 8_000, 170_000)),
          totalMinor = 270_000,
        ),
      ),
    )
    val m = PoForms.matchView(d0, billed)
    assertTrue(m.supplier)
    assertTrue(m.hasData)
    assertFalse(m.matched)
    assertEquals(1, m.flaggedCount)
    val top = m.rows.first()
    assertEquals("l2", top.lineId)
    assertEquals(7_000, top.acceptedMilli)
    assertEquals(8_000, top.billedMilli)
    assertEquals(160_000, top.expectedMinor)
    assertEquals(170_000, top.billedMinor)
    assertEquals(
      listOf(
        PurchaseOrderBook.issueText(PurchaseOrderCore.Issue.BILLED_OVER_RECEIVED, true),
        PurchaseOrderBook.issueText(PurchaseOrderCore.Issue.PRICE_OVER_ORDER, true),
      ),
      top.issues,
    )
    assertFalse(m.rows[1].flagged)
    assertEquals(270_000, m.billedMinor)

    //  فاکتورِ باطل‌شده طرفِ سوم نیست ⇒ همان دفتر (این‌جا خالی) و بی رسید ⇒ چیزی برای تطبیق نیست
    val none = PoForms.matchView(d0, partial.copy(receipts = emptyList(), bills = billed.bills.map { it.copy(voidedAt = 1) }))
    assertFalse(none.supplier)
    assertFalse(none.hasData)
  }

  @Test fun `تطبیق — کالای بیرون از سفارش با نامِ کالا`() {
    val out = partial.copy(bills = listOf(PoBillDto(id = "b2", billNo = "F-2", lines = listOf(PoBillLineDto(null, "pC", 1_000, 5_000)))))
    val row = PoForms.matchView(d0, out).rows.first { it.lineId == null }
    assertEquals("کالای C", row.name)
    assertEquals(listOf(PurchaseOrderBook.issueText(PurchaseOrderCore.Issue.NOT_ON_ORDER, true)), row.issues)
  }

  /* ── دو بار زدن ── */

  @Test fun `سدِ دو بار زدن — لمسِ دوم تا پایانِ کارِ اول هیچ نمی‌فرستد`() {
    val g = PoForms.SubmitGuard()
    var sent = 0
    fun tap() { if (g.start()) sent++ }
    tap(); tap(); tap()
    assertEquals(1, sent)
    assertTrue(g.running)
    g.done()
    tap()
    assertEquals(2, sent)
  }

  /* ── ساختن و ویرایش ── */

  @Test fun `فرمِ سفارش — کالای تکراری، پیشنهادها، ردیفِ نادرست و پیش‌نویسِ درست`() {
    val a = PoForms.addProduct(d0, emptyList(), "s1", d0.products[0])!!
    assertEquals(PoForms.EditRow(0, "pA", "کالای A", "عدد", "5", "100"), a.single())
    assertNull(PoForms.addProduct(d0, a, "s1", d0.products[0]))
    val picks = listOf(
      PurchaseOrderBook.Pick("pA", "کالای A", "عدد", 9_000, 9_000, true),
      PurchaseOrderBook.Pick("pB", "کالای B", "عدد", 2_500, 21_050, false),
    )
    val b = PoForms.addPicks(a, picks)
    assertEquals(listOf("pA", "pB"), b.map { it.productId })
    assertEquals(PoForms.EditRow(1, "pB", "کالای B", "عدد", "2.5", "210.5"), b[1])
    assertEquals(b, PoForms.addPicks(b, picks))
    assertEquals(1026.25, PoForms.total(b), 1e-9)

    assertEquals(PoForms.DraftCheck.Bad(StrPurchasePages.chooseSupplier), PoForms.draft(d0, "", b, "", ""))
    assertEquals(PoForms.DraftCheck.Bad(StrPurchasePages.needOneItem), PoForms.draft(d0, "s1", emptyList(), "", ""))
    assertEquals(PoForms.DraftCheck.Bad(StrPurchasePages.rowBadQty(2)), PoForms.draft(d0, "s1", b.map { if (it.key == 1) it.copy(qty = "0") else it }, "", ""))
    assertEquals(PoForms.DraftCheck.Bad(StrPurchasePages.arrivalDateFormat), PoForms.draft(d0, "s1", b, "1405/08/20", ""))
    val ok = PoForms.draft(d0, "s1", b, "۲۰۲۶-۱۱-۲۰", "  یادداشت ") as PoForms.DraftCheck.Ok
    assertEquals("2026-11-20", ok.draft.expectedDate)
    assertEquals("یادداشت", ok.draft.note)
    assertEquals(listOf("l1", "l2"), ok.draft.lines.map { it.id })
    assertEquals(listOf(5_000L, 2_500L), ok.draft.lines.map { it.qtyMilli })
    assertEquals(listOf(10_000L, 21_050L), ok.draft.lines.map { it.priceMinor })
    //  ویرایش: ردیف‌های همان پیش‌نویس
    val base = partial.copy(state = "draft", status = "draft")
    assertEquals(listOf(PoForms.EditRow(0, "pA", "کالای A", "عدد", "10", "100"), PoForms.EditRow(1, "pB", "کالای B", "عدد", "10", "200")), PoForms.rowsOf(base))
  }

  /* ── مجوز و درِ صفحه ── */

  @Test fun `مجوزِ هر کار همان سرور — مدیر می‌سازد و می‌فرستد، شاگرد فقط دریافت`() {
    val manager = PoPermsDto(manage = true, receive = true)
    val clerk = PoPermsDto(manage = false, receive = true)
    val viewer = PoPermsDto()
    val draft = partial.copy(state = "draft", status = "draft", allowed = listOf("edit", "send", "cancel"), receipts = emptyList())
    assertEquals(PoForms.Actions(receive = false, send = true, edit = true, cancel = true, bill = false), PoForms.actions(draft, manager))
    assertEquals(PoForms.Actions(receive = false, send = false, edit = false, cancel = false, bill = false), PoForms.actions(draft, clerk))
    assertEquals(PoForms.Actions(receive = true, send = false, edit = false, cancel = true, bill = true), PoForms.actions(partial, manager))
    assertEquals(PoForms.Actions(receive = true, send = false, edit = false, cancel = false, bill = false), PoForms.actions(partial, clerk))
    assertEquals(PoForms.Actions(receive = false, send = false, edit = false, cancel = false, bill = false), PoForms.actions(partial, viewer))
    assertFalse(PoForms.cancelNeedsReason(draft))
    assertTrue(PoForms.cancelNeedsReason(partial))
  }

  @Test fun `درِ صفحه — بی اینترنت، خطا، خاموش، بی اجازه و باز`() {
    val on = PoConfigDto(enabled = true, permissions = PoPermsDto(manage = false, receive = true))
    assertEquals(PoForms.Gate.LOADING, PoForms.gate(null, null))
    assertEquals(PoForms.Gate.OFFLINE, PoForms.gate(null, ApiFailure.Offline()))
    assertEquals(PoForms.Gate.OFFLINE, PoForms.gate(null, ApiFailure.Timeout()))
    assertEquals(PoForms.Gate.FAILED, PoForms.gate(null, ApiFailure.Invalid("بد", "bad")))
    assertEquals(PoForms.Gate.OFF, PoForms.gate(PoConfigDto(enabled = false), null))
    assertEquals(PoForms.Gate.DENIED, PoForms.gate(on, null) { it.manage })
    assertEquals(PoForms.Gate.OPEN, PoForms.gate(on, null) { it.receive })
    assertEquals(PoForms.Gate.LOADING, PoForms.loaded(PoForms.Gate.OPEN, has = false, failure = null))
    assertEquals(PoForms.Gate.OFFLINE, PoForms.loaded(PoForms.Gate.OPEN, has = false, failure = ApiFailure.Unreachable()))
    assertEquals(PoForms.Gate.OPEN, PoForms.loaded(PoForms.Gate.OPEN, has = true, failure = ApiFailure.Offline()))
    assertEquals(PoForms.Gate.OFF, PoForms.loaded(PoForms.Gate.OFF, has = true, failure = null))
  }
}
