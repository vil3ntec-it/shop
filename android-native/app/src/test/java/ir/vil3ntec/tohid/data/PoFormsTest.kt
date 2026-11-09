package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrGoods
import ir.vil3ntec.tohid.core.i18n.StrPurchaseOrders
import ir.vil3ntec.tohid.core.i18n.StrPurchasePages
import ir.vil3ntec.tohid.core.i18n.StrPurchaseUnits
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  منطقِ فرم‌های سفارشِ خرید که کامپیوتر و گوشیِ نسخهٔ ۳ هر دو صدا می‌زنند (`PoForms`؛ PUR-02/03/04/14): پالایهٔ فهرست،
 *  شمارشِ رسید (جزئی، اضافهٔ ردشده، خراب)، اختلاف‌های تطبیق، سدِ دو بار زدن، مجوزِ هر کار و درِ صفحه؛ PRD-07 (ردیف به واحدِ
 *  خرید، اسکنِ بارکدِ واحد، دریافت به واحد با موجودی = مقدار × ضریب و ارزشِ دقیق، و پرچمِ خاموش = همان بدنه) و PUR-13 (شمارشِ برچسب).
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

  @Test fun `دریافتِ همهٔ باقی‌مانده — هر ردیفِ باز کامل، ردیفِ کامل دست‌نخورده، خراب و فی می‌مانند`() {
    //  بخشی: A کامل (۰ باز) و B ۳ باز؛ خرابِ تایپ‌شدهٔ B و فیِ هر دو ردیف می‌مانند
    val typed = rows("l2" to PoForms.RecvRow(received = "1", damaged = "1", price = "210"))
    val filled = PoForms.fillRemaining(partial, typed)
    assertEquals(typed.getValue("l1"), filled.getValue("l1"))
    assertEquals(PoForms.RecvRow(received = "3", damaged = "1", price = "210"), filled.getValue("l2"))
    assertTrue(PoForms.hasRemaining(partial))
    //  همان سنجشِ رسید: ۳ رسیده − ۱ خراب ⇒ ۲ پذیرفته و ۱ باز؛ بی خراب ⇒ این قلم کامل
    val ok = PoForms.receipt(partial, filled, rid, allowOver = false) as PoForms.ReceiptCheck.Ok
    assertEquals(listOf(PoReceiptLineDto("l2", 3_000, 1_000, 21_000)), ok.lines)
    assertEquals(PoForms.After(leftMilli = 1_000, overMilli = 0), PoForms.after(partial, "l2", filled.getValue("l2")))
    assertTrue(PoForms.after(partial, "l2", PoForms.fillRemaining(partial, rows()).getValue("l2"))!!.done)

    //  فرستاده بی رسید (بی `progress`): همان مقدارِ سفارش؛ ردیفِ به واحدِ خرید به هزارمِ همان واحد، دقیق تا ثبت
    val sent = unitPo.copy(lines = listOf(unitPo.lines[0].copy(qtyMilli = 1_042), unitPo.lines[1]))
    val all = PoForms.fillRemaining(sent, PoForms.recvRows(sent))
    assertEquals(listOf("1.042", "10"), sent.lines.map { all.getValue(it.id).received })
    val chk = PoForms.receipt(sent, all, rid, allowOver = false) as PoForms.ReceiptCheck.Ok
    assertEquals(listOf(1_042L, 10_000L), chk.lines.map { it.receivedMilli })
    assertTrue(sent.lines.all { PoForms.after(sent, it.id, all.getValue(it.id))!!.done })

    //  سفارشِ کامل: دکمه نیست و پر کردن هیچ نمی‌کند
    val complete = partial.copy(progress = partial.progress.map { it.copy(remainingMilli = 0) })
    assertFalse(PoForms.hasRemaining(complete))
    assertEquals(PoForms.recvRows(complete), PoForms.fillRemaining(complete, PoForms.recvRows(complete)))
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

  /* ── PRD-07: واحدِ خرید ── */

  /** چای با واحدِ پایهٔ «دانه»: کارتنِ ۱۲ (تأمین‌کنندهٔ s1، بارکدِ ۴۴۴) و کارتنِ ۲۴ (بارکدِ ۵۵۵)؛ بارکدِ خودِ دانه ۳۳۳ */
  private val tea = Product(
    id = "tea", name = "چای", unit = "دانه", purchasePrice = 40.0, salePrice = 50.0, barcodes = listOf("333"),
    purchaseUnits = listOf(PurchaseUnit("کارتن ۱۲", 12_000, "s1", "444"), PurchaseUnit("کارتن ۲۴", 24_000, null, "555")),
  )
  private val du = d0.copy(products = d0.products + tea)

  /** سفارشِ فرستاده: ۲ کارتنِ ۲۴ چای به ۱٬۰۰۰ و ۱۰ عدد B به ۲۰۰ */
  private val unitPo = PurchaseOrderDto(
    id = "po2", no = 8, supplierId = "s1", supplierName = "شرکتِ نمونه", state = "sent", status = "sent", allowed = listOf("cancel", "receive"),
    lines = listOf(
      PoLineDto("l1", "tea", "چای", "کارتن ۲۴", 2_000, 100_000, factorMilli = 24_000),
      PoLineDto("l2", "pB", "کالای B", "عدد", 10_000, 20_000),
    ),
  )

  @Test fun `ردیف به واحدِ خرید — فی به نسبتِ ضریب، مقدار و فی به همان واحد و ضریب روی ردیفِ سفارش`() {
    val base = PoForms.addProduct(du, emptyList(), "s1", tea)!!.single().copy(qty = "2", price = "40")
    assertNull(base.unitName)
    val carton = PoForms.pickUnit(du, base, "کارتن ۲۴")
    assertEquals(PoForms.EditRow(base.key, "tea", "چای", "دانه", "2", "960", "کارتن ۲۴"), carton)
    assertEquals("کارتن ۲۴", PoForms.unitOf(carton))
    assertEquals(1920.0, PoForms.lineTotal(carton), 1e-9)
    assertEquals(StrPurchaseUnits.lineBase(qty(48.0), "دانه", money(40.0)), PoForms.unitLine(du, carton))
    assertNull("ردیفِ پایه زیرنویسِ واحد ندارد", PoForms.unitLine(du, base))
    //  کارتنِ ۲۴ ⇒ ۱۲: فیِ هر دانه همان ۴۰؛ برگشت به پایه همان ۴۰؛ واحدی که روی کالا نیست بی اثر
    assertEquals("480", PoForms.pickUnit(du, carton, "کارتن ۱۲").price)
    assertEquals(base, PoForms.pickUnit(du, carton, null))
    assertEquals(carton, PoForms.pickUnit(du, carton, "بوری"))
    assertEquals(listOf("کارتن ۱۲", "کارتن ۲۴"), PoForms.purchaseUnits(du, "tea").map { it.name })
    assertTrue(PoForms.purchaseUnits(du, "pA").isEmpty())

    val ok = PoForms.draft(du, "s1", listOf(carton.copy(price = "1000")), "", "") as PoForms.DraftCheck.Ok
    val line = ok.draft.lines.single()
    assertEquals(PoLineDto("l1", "tea", "چای", "کارتن ۲۴", 2_000, 100_000, factorMilli = 24_000), line)
    assertEquals(
      """{"supplierId":"s1","supplierName":"شرکتِ نمونه","note":"","lines":[{"id":"l1","productId":"tea","name":"چای","unit":"کارتن ۲۴","qtyMilli":2000,"priceMinor":100000,"factorMilli":24000}]}""",
      ok.draft.json(null).toString(),
    )
    //  ویرایشِ همان پیش‌نویس: واحد و واحدِ پایه برمی‌گردند (بی `d` همان نامِ ردیف)
    val draft = unitPo.copy(state = "draft", status = "draft")
    assertEquals(PoForms.EditRow(0, "tea", "چای", "دانه", "2", "1000", "کارتن ۲۴"), PoForms.rowsOf(draft, du)[0])
    assertEquals("کارتن ۲۴", PoForms.rowsOf(draft)[0].unit)
    assertEquals(PoForms.EditRow(1, "pB", "کالای B", "عدد", "10", "200"), PoForms.rowsOf(draft, du)[1])

    //  همان سنجشِ فاکتورِ خرید: ۰٫۰۱ کارتنِ ۲۴ = ۰٫۲۴ دانه (دانه اعشار نمی‌گیرد)، واحدی که دیگر روی کالا نیست
    assertEquals(
      PoForms.DraftCheck.Bad(StrGoods.rowOfWhy(1, StrPurchaseUnits.wholeBase("دانه"))),
      PoForms.draft(du, "s1", listOf(carton.copy(qty = "0.01")), "", ""),
    )
    assertEquals(
      PoForms.DraftCheck.Bad(StrGoods.rowOfWhy(1, StrPurchaseUnits.unknownUnit)),
      PoForms.draft(du, "s1", listOf(carton.copy(unitName = "بوری")), "", ""),
    )
  }

  @Test fun `اسکنِ بارکدِ واحدِ خرید همان واحد را می‌شمارد و بی پرچم همان اسکنِ پیشین است`() {
    val index = ShopStore.barcodeIndex(du)
    val units = ShopStore.purchaseUnitIndex(du)
    val rows0 = PoForms.recvRows(unitPo)
    //  کارتنِ ۲۴ روی ردیفِ کارتنِ ۲۴ ⇒ +۱، کارتنِ ۱۲ ⇒ +۰٫۵، دانه (بارکدِ کالا) ⇒ +۱ ِ واحدِ ردیف
    val a = PoForms.scan(unitPo, rows0, index, "555", units) as PoForms.Scan.Added
    assertEquals("l1", a.lineId)
    assertEquals("1", a.received)
    assertEquals("کارتن ۲۴", a.unit)
    val b = PoForms.scan(unitPo, a.rows, index, "۴۴۴", units) as PoForms.Scan.Added
    assertEquals("1.5", b.received)
    assertEquals("کارتن ۱۲", b.unit)
    val c = PoForms.scan(unitPo, b.rows, index, "333", units) as PoForms.Scan.Added
    assertEquals("2.5", c.received)
    assertNull(c.unit)
    //  ردیفِ همان کالا به واحدِ پایه: کارتنِ ۲۴ ⇒ +۲۴
    val basePo = unitPo.copy(lines = listOf(PoLineDto("l1", "tea", "چای", "دانه", 48_000, 4_000), unitPo.lines[1]))
    assertEquals("24", (PoForms.scan(basePo, PoForms.recvRows(basePo), index, "555", units) as PoForms.Scan.Added).received)
    //  پرچمِ خاموش (`units` = null): بارکدِ واحد ناشناس، همان پیشین؛ بارکدِ کالا همان +۱
    assertEquals(PoForms.Scan.NotInPo("555"), PoForms.scan(unitPo, rows0, index, "555"))
    assertEquals(PoForms.scan(unitPo, rows0, index, "333", null), PoForms.scan(unitPo, rows0, index, "333"))
    assertEquals("1", (PoForms.scan(unitPo, rows0, index, "333") as PoForms.Scan.Added).received)
  }

  @Test fun `دریافت به واحدِ خرید — موجودی مقدار × ضریب، ارزشِ دقیقِ ردیف و برچسب به واحدِ پایه`() {
    val rows = PoForms.recvRows(unitPo) + ("l1" to PoForms.RecvRow(received = "2", price = "1000")) + ("l2" to PoForms.RecvRow(received = "10", price = "200"))
    assertEquals("1000", PoForms.recvRows(unitPo).getValue("l1").price)
    assertEquals(StrPurchaseUnits.toStock(qty(48.0), "دانه"), PoForms.toStock(du, unitPo.lines[0], rows.getValue("l1")))
    assertEquals(StrPurchaseUnits.toStock(qty(36.0), "دانه"), PoForms.toStock(du, unitPo.lines[0], PoForms.RecvRow(received = "2", damaged = "0.5")))
    assertNull("ردیفِ پایه", PoForms.toStock(du, unitPo.lines[1], rows.getValue("l2")))
    assertNull("خراب بیش از رسیده", PoForms.toStock(du, unitPo.lines[0], PoForms.RecvRow(received = "1", damaged = "2")))

    val chk = PoForms.receipt(unitPo, rows, rid, allowOver = false) as PoForms.ReceiptCheck.Ok
    assertEquals(listOf(PoReceiptLineDto("l1", 2_000, 0, 100_000), PoReceiptLineDto("l2", 10_000, 0, 20_000)), chk.lines)
    assertEquals(200_000L + 200_000L, chk.totalMinor)
    val rdto = PoReceiptDto(
      id = rid, poId = unitPo.id, lines = chk.lines, totalMinor = chk.totalMinor,
      posted = chk.posted.map { PoPostedDto(it.lineId, it.expectedMilli, it.receivedMilli, it.damagedMilli, it.acceptedMilli, it.shortMilli, it.overMilli, it.priceMinor, it.totalMinor) },
    )
    val d = (PurchaseOrderBook.post(du, unitPo.copy(status = "complete", receipts = listOf(rdto)), rdto, "2026-11-02", 1) as PurchaseOrderBook.Posting.Ok).data
    //  ۲ کارتنِ ۲۴ ⇒ ۴۸ دانه؛ بهای هر دانه ۴۱٫۶۷ ولی ارزشِ انبار و بدهی دقیقاً ۲٬۰۰۰ (نه ۲٬۰۰۰٫۱۶)
    assertEquals(48.0, ShopStore.stock(d, "tea"), 1e-9)
    assertEquals(10.0, ShopStore.stock(d, "pB"), 1e-9)
    assertEquals(200_000L, d.warehouseEntries.first { it.productId == "tea" }.costMinor)
    assertNull(d.warehouseEntries.first { it.productId == "pB" }.costMinor)
    assertEquals(2000.0 + 2000.0, ShopStore.supplierDebt(d, "s1"), 1e-9)

    //  PUR-13: برچسب به واحدِ پایه (۴۸، A4 تا ۲۴)؛ ردیفِ پایه همان شمارشِ پیشین
    assertEquals(listOf("tea" to 48, "pB" to 10), PoForms.labels(du, unitPo, rdto, a4 = false).map { it.product.id to it.copies })
    assertEquals(listOf("tea" to 24, "pB" to 10), PoForms.labels(du, unitPo, rdto, a4 = true).map { it.product.id to it.copies })
  }

  @Test fun `برچسبِ رسید — هر واحد یکی تا سقف، وزنی یکی، پذیرفتهٔ صفر و کالای بیرون از دفتر کنار`() {
    val rice = Product(id = "rice", name = "برنج", unit = "کیلو", purchasePrice = 80.0)
    val d = d0.copy(products = d0.products + rice)
    val o = partial.copy(lines = partial.lines + PoLineDto("l3", "rice", "برنج", "کیلو", 25_000, 8_000) + PoLineDto("l4", "gone", "پاک‌شده", "عدد", 1_000, 100))
    val r = r1.copy(
      posted = listOf(
        PoPostedDto("l1", acceptedMilli = 150_000), PoPostedDto("l2", acceptedMilli = 2_500), PoPostedDto("l3", acceptedMilli = 25_000),
        PoPostedDto("l4", acceptedMilli = 1_000), PoPostedDto("l2", acceptedMilli = 0),
      ),
    )
    assertEquals(listOf("pA" to 99, "pB" to 3, "rice" to 1), PoForms.labels(d, o, r, a4 = false).map { it.product.id to it.copies })
    assertEquals(listOf("pA" to 24, "pB" to 3, "rice" to 1), PoForms.labels(d, o, r, a4 = true).map { it.product.id to it.copies })
    assertTrue(PoForms.labels(d, o, r1, a4 = false).isEmpty())
  }

  @Test fun `پرچمِ خاموش — کالای واحددار و بی واحد همان بدنهٔ پیش‌نویسِ پیشین را می‌سازند`() {
    //  بی انتخاب (پرچم خاموش ⇒ صفحه انتخاب را نمی‌کشد): ردیفِ همان کالای واحددار همان ردیفِ واحدِ پایه است
    val row = PoForms.addProduct(du, emptyList(), "s1", tea)!!.single().copy(qty = "2", price = "40")
    val withUnits = PoForms.draft(du, "s1", listOf(row), "", "") as PoForms.DraftCheck.Ok
    val noUnits = PoForms.draft(du.copy(products = du.products.map { it.copy(purchaseUnits = null) }), "s1", listOf(row), "", "") as PoForms.DraftCheck.Ok
    val body = """{"supplierId":"s1","supplierName":"شرکتِ نمونه","note":"","lines":[{"id":"l1","productId":"tea","name":"چای","unit":"دانه","qtyMilli":2000,"priceMinor":4000}]}"""
    assertEquals(body, withUnits.draft.json(null).toString())
    assertEquals(body, noUnits.draft.json(null).toString())
    assertEquals(PoLineDto("l1", "tea", "چای", "دانه", 2_000, 4_000), withUnits.draft.lines.single())
    //  کالای بی واحدِ خرید: همان ردیف‌ها و همان بدنه، با و بی [ShopData] در `rowsOf`
    val base = partial.copy(state = "draft", status = "draft")
    assertEquals(PoForms.rowsOf(base), PoForms.rowsOf(base, du))
    val again = PoForms.draft(du, "s1", PoForms.rowsOf(base, du), "", "") as PoForms.DraftCheck.Ok
    assertEquals(
      """{"supplierId":"s1","supplierName":"شرکتِ نمونه","note":"","lines":[{"id":"l1","productId":"pA","name":"کالای A","unit":"عدد","qtyMilli":10000,"priceMinor":10000},{"id":"l2","productId":"pB","name":"کالای B","unit":"عدد","qtyMilli":10000,"priceMinor":20000}]}""",
      again.draft.json(null).toString(),
    )
    //  اسکن بی نمایهٔ واحد (پرچم خاموش) برای هر بارکد همان نتیجهٔ پیشین
    val index = ShopStore.barcodeIndex(du)
    for (code in listOf("111", "222", "333", "444", "555", "999")) {
      assertEquals(code, PoForms.scan(partial, PoForms.recvRows(partial), index, code), PoForms.scan(partial, PoForms.recvRows(partial), index, code, null))
    }
  }

  @Test fun `کالای واحددار در سفارش — واحدِ پیش‌فرضِ همین تأمین‌کننده پیش‌گزیده و قابلِ عوض کردن`() {
    val d = du.copy(
      products = du.products.map { if (it.id == "tea") it.copy(minStock = 30.0) else it },
      suppliers = du.suppliers + Supplier(id = "s2", name = "دیگر"),
    )
    val tea30 = d.products.first { it.id == "tea" }
    //  تأمین‌کنندهٔ پیش‌فرضِ کارتنِ ۱۲ همین s1 است ⇒ همان واحد؛ ۳۰ دانه کم ⇒ ۳ کارتنِ ۱۲ (رو به بالا)، فی ۴۰ × ۱۲ = ۴۸۰
    val row = PoForms.addProduct(d, emptyList(), "s1", tea30, unitsOn = true)!!.single()
    assertEquals(PoForms.EditRow(0, "tea", "چای", "دانه", "3", "480", "کارتن ۱۲"), row)
    assertEquals(StrPurchaseUnits.lineBase(qty(36.0), "دانه", money(40.0)), PoForms.unitLine(d, row))
    //  کاربر عوضش می‌کند: پایه ⇒ همان عدد و فیِ هر دانه
    assertEquals(PoForms.EditRow(0, "tea", "چای", "دانه", "3", "40"), PoForms.pickUnit(d, row, null))
    //  تأمین‌کنندهٔ بی واحدِ پیش‌فرض، یا پرچمِ خاموش ⇒ همان ردیفِ پیشین به واحدِ پایه
    val plain = PoForms.EditRow(0, "tea", "چای", "دانه", "30", "40")
    assertEquals(plain, PoForms.addProduct(d, emptyList(), "s2", tea30, unitsOn = true)!!.single())
    assertEquals(plain, PoForms.addProduct(d, emptyList(), "s1", tea30)!!.single())
    //  کالای بی واحدِ خرید همان ردیفِ پیشین
    val pA = d.products.first { it.id == "pA" }
    assertEquals(PoForms.addProduct(d, emptyList(), "s1", pA), PoForms.addProduct(d, emptyList(), "s1", pA, unitsOn = true))
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
