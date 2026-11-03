package ir.vil3ntec.tohid.scenario

import ir.vil3ntec.tohid.core.keymap.KeyName
import ir.vil3ntec.tohid.core.receipt.ReceiptBlock
import ir.vil3ntec.tohid.core.receipt.ReceiptFormat
import ir.vil3ntec.tohid.core.receipt.ReturnReceiptLayout
import ir.vil3ntec.tohid.core.receipt.ShopHeader
import ir.vil3ntec.tohid.core.receipt.TextStyle
import ir.vil3ntec.tohid.data.CashShift
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ProductInfoMap
import ir.vil3ntec.tohid.data.ReturnFlow
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.print.Receipts
import ir.vil3ntec.tohid.ui.components.pos.PosHit
import ir.vil3ntec.tohid.ui.components.pos.PosKeys
import ir.vil3ntec.tohid.ui.components.pos.PosSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  S15 ⌨ — تعویض (RET-05)، سر تا ته روی همان راهِ صندوقِ کامپیوتر: Shift+F9 (`PosKeys.returnMode`، `sale.returnMode` ِ
 *  `keymap.json`) ⇐ برگشتِ کفشِ ۴۰ از فاکتورِ اصل ⇐ اسکنِ کفشِ ۴۲ (`PosSearch`) ⇐ `ReturnFlow.exchange` (یک سند و یک ذخیره)
 *  ⇐ رسیدِ همان سندِ ثبت‌شده (`Receipts.returnInputOf` ⇐ `ReturnReceiptLayout`، RET-12). همه پشتِ تنظیمِ خاموشِ برگشتِ
 *  تازه (`ReturnSettings`)؛ رسیدِ هسته فقط با پرچمِ `shop.receipt_v2`.
 *
 *  پیش‌تر: سند و تفاوت در `ReturnFlowTest`، چیدمانِ دوبخشی با ورودیِ دست‌ساز در `ReturnReceiptLayoutTest`؛ «Shift+F9 و رسیدِ
 *  دوبخشی» ِ همین تعویض آزمونی نداشت.
 */
class ScenarioS15Test {

  private var seq = 0
  private fun newId() = "s15-${seq++}"

  private val shoe40 = Product(id = "shoe40", name = "کفش ۴۰", unit = "جفت", salePrice = 1000.0, barcodes = listOf("6260000000040"))
  private val shoe42 = Product(id = "shoe42", name = "کفش ۴۲", unit = "جفت", salePrice = 1200.0, barcodes = listOf("6260000000042"))
  private val d0 = ShopData(
    products = listOf(shoe40, shoe42),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "shoe40", units = 3.0), WarehouseEntry(id = "w2", productId = "shoe42", units = 3.0)),
  )

  /** قالبِ سادهٔ آزمون (همان `ReturnReceiptLayoutTest`): عددِ صحیح با «افغانی» */
  private object Fmt : ReceiptFormat {
    private fun n(v: Double): String = if (v == kotlin.math.floor(v)) v.toLong().toString() else v.toString()
    override fun money(afn: Double) = n(afn) + " افغانی"
    override fun amount(afn: Double) = n(afn)
    override fun qty(q: Double) = n(q)
    override fun digits(text: String) = text
    override fun phone(text: String) = text
    override fun date(iso: String) = iso.replace('-', '/')
  }

  @Test
  fun `S15 — Shift+F9 فقط با برگشتِ تازهٔ روشن حالتِ برگشت است؛ خاموش همان کارِ امروزِ کلید`() {
    assertTrue(PosKeys.returnMode(KeyName.F9, primary = false, shift = true, alt = false, enabled = true))
    assertFalse(PosKeys.returnMode(KeyName.F9, primary = false, shift = true, alt = false, enabled = false))
    assertFalse("F9 ِ تنها پرداخت است", PosKeys.returnMode(KeyName.F9, primary = false, shift = false, alt = false, enabled = true))
    assertFalse(PosKeys.returnMode(KeyName.F9, primary = true, shift = true, alt = false, enabled = true))
  }

  @Test
  fun `S15 — برگشتِ کفشِ ۴۰ و اسکنِ کفشِ ۴۲ — یک سند، یک رسیدِ دوبخشی با دریافتِ ۲۰۰ و موجودیِ درستِ هر دو کالا`() {
    //  فاکتورِ اصل: کفشِ ۴۰ دو روز پیش فروخته شد
    val sold = SalesEngine.record(d0, listOf(SalesEngine.CartLine("shoe40", 1.0)), SalesEngine.Checkout(seller = "صندوقدار"), "2026-11-01", 1_000, ::newId)
    val d1 = (sold as SalesEngine.Result.Ok).data
    val sale = d1.sales.single()
    val item = d1.saleItems.single { it.saleId == sale.id }

    //  حالتِ برگشت: کفشِ ۴۰ از فاکتور، و اسکنِ کفشِ ۴۲ در همان جریان
    val hit = PosSearch.resolve("6260000000042", ShopStore.barcodeIndex(d1), d1.products, emptyList(), 0) as PosHit.Add
    val cart = SalesEngine.addToCartCapped(emptyList(), hit.product.id, hit.count, ProductInfoMap.sellable(d1, hit.product.id)).cart
    val req = ReturnFlow.Request(
      docId = "doc-s15", saleId = sale.id, picks = mapOf(item.id to ReturnFlow.Pick(1.0)),
      reason = "اندازه یا مدلِ نامناسب", mode = ReturnFlow.ORIGINAL, by = "صندوقدار",
    )
    val r = ReturnFlow.exchange(d1, req, cart, SalesEngine.Checkout(seller = "صندوقدار"), "sale-s15", "2026-11-03", 2_000, ::newId)
    assertTrue(r.toString(), r is ReturnFlow.Outcome.Ok)
    val x = (r as ReturnFlow.Outcome.Ok).value
    assertEquals(1000.0, x.returned, 0.0)
    assertEquals(1200.0, x.sold, 0.0)
    assertEquals(200.0, x.due, 0.0)
    //  یک سند و یک فروشِ تازه؛ موجودی: ۴۰ ⇐ ۳ − ۱ + ۱، ۴۲ ⇐ ۳ − ۱
    assertEquals(setOf("doc-s15"), x.data.saleReturns.mapNotNull { it.docId }.toSet())
    assertEquals(listOf(sale.id, "sale-s15"), x.data.sales.map { it.id })
    assertEquals(3.0, ShopStore.stock(x.data, "shoe40"), 0.0)
    assertEquals(2.0, ShopStore.stock(x.data, "shoe42"), 0.0)
    //  صندوق: +۱۰۰۰ فروشِ اصل، +۱۲۰۰ فروشِ تازه − ۱۰۰۰ برگشت
    assertEquals(1200.0, CashShift.totals(x.data, 0, 3_000, 0.0).expected, 0.0)

    //  رسید از همان سندِ ثبت‌شده: یک رسید، دو بخش، «دریافتِ تفاوت» ۲۰۰
    val input = Receipts.returnInputOf(x.data, "doc-s15", ShopHeader("دکانِ آزمون", "", ""))
    assertNotNull(input)
    val labels = Receipts.returnLabels()
    val doc = ReturnReceiptLayout.build(input!!, 384, Fmt, labels)
    val texts = doc.blocks.filterIsInstance<ReceiptBlock.Text>().map { it.text }
    assertEquals(1, texts.count { it == labels.receipt.replace("{0}", ReturnFlow.label("doc-s15")) })
    assertEquals(1, texts.count { it.startsWith(labels.newPurchase.substringBefore("{0}")) })
    assertEquals(listOf("کفش ۴۰", "کفش ۴۲"), doc.blocks.filterIsInstance<ReceiptBlock.Item>().map { it.name })
    val rows = doc.blocks.filterIsInstance<ReceiptBlock.Row>()
    assertTrue(rows.toString(), ReceiptBlock.Row(labels.newPurchaseTotal, "1200 افغانی") in rows)
    assertEquals(ReceiptBlock.Row(labels.collect, "200 افغانی", TextStyle.TOTAL), rows.last())
  }
}
