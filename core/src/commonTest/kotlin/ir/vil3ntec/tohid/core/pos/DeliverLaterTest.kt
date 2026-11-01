package ir.vil3ntec.tohid.core.pos

import ir.vil3ntec.tohid.core.orders.OrderDraft
import ir.vil3ntec.tohid.core.pos.DeliverLater.Contact
import ir.vil3ntec.tohid.core.pos.DeliverLater.Line
import ir.vil3ntec.tohid.core.pos.DeliverLater.Problem
import ir.vil3ntec.tohid.core.pos.DeliverLater.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 *  POS-38 — فروش با تحویلِ بعدی: سبدِ صندوق ⇐ بدنهٔ سفارشِ ارسال. پیش از این صندوق هیچ راهی
 *  به بخشِ تحویل نداشت؛ کالای «بعداً بفرستید» یا همان‌جا فروخته می‌شد (و موجودی پیش از رفتن
 *  کم می‌شد) یا دستی در «سفارشِ دستی» دوباره تایپ می‌شد، بی شناسهٔ کالا.
 */
class DeliverLaterTest {

  private val rice = Line("p-rice", "برنج", "کیلوگرم", qtyMilli = 2_500, unitPriceMinor = 12_000)   // ۲٫۵ کیلو × ۱۲۰
  private val free = Line("", "کیسه", "", qtyMilli = 1_000, unitPriceMinor = 1_050)                 // قلمِ آزاد ۱۰٫۵
  private val who = Contact(name = " احمد ", phone = "۰۷۰۰ ۱۲۳ ۴۵۶", addressText = "کارته سه", fee = "۵۰")

  private fun ok(r: Result) = assertIs<Result.Ok>(r)

  @Test
  fun cartBecomesADeliveryOrderWithProductIdsAndUnits() {
    val r = ok(DeliverLater.build(listOf(rice, free), who))
    val b = r.body
    assertEquals("manual", b.source)
    assertEquals("delivery", b.fulfillment)
    assertEquals("احمد", b.customerName)
    assertEquals("0700123456", b.customerPhone)
    assertEquals("کارته سه", b.addressText)
    assertEquals(5_000L, b.deliveryFeeMinor)
    assertEquals(false, b.test)
    assertEquals(
      listOf(
        OrderDraft.Line("برنج", 2_500, 12_000, productId = "p-rice", unit = "کیلوگرم"),
        OrderDraft.Line("کیسه", 1_000, 1_050, productId = "", unit = ""),
      ),
      b.lines,
    )
    //  ۲٫۵ × ۱۲۰ = ۳۰۰؛ ۱۰٫۵ ⇐ ۱۱ (نیمه رو به بالا، مثلِ سرور و صندوق)؛ با ارسال ۳۶۱
    assertEquals(31_100L, r.subtotalMinor)
    assertEquals(36_100L, r.totalMinor)
  }

  @Test
  fun lineTotalIsTheServerRounding() {
    assertEquals(30_000L, DeliverLater.lineTotalMinor(2_500, 12_000))
    assertEquals(1_100L, DeliverLater.lineTotalMinor(1_000, 1_050))   // ۱۰٫۵ ⇐ ۱۱
    assertEquals(1_000L, DeliverLater.lineTotalMinor(1_000, 1_049))   // ۱۰٫۴۹ ⇐ ۱۰
    assertEquals(0L, DeliverLater.lineTotalMinor(1, 1))
    assertEquals(1_000_000_000_000_000L, DeliverLater.lineTotalMinor(DeliverLater.MAX_QTY_MILLI, DeliverLater.MAX_PRICE_MINOR))
    assertFailsWith<IllegalArgumentException> { DeliverLater.lineTotalMinor(-1, 1) }
  }

  @Test
  fun quantityAndPriceConversions() {
    assertEquals(250L, DeliverLater.milli(0.25))
    assertEquals(3_000L, DeliverLater.milli(3.0))
    assertEquals(0L, DeliverLater.milli(0.0))
    assertEquals(0L, DeliverLater.milli(Double.NaN))
    assertEquals(12_050L, DeliverLater.minor(120.5))
    assertEquals(-1L, DeliverLater.minor(-1.0))
  }

  @Test
  fun missingContactOrBadLinesAreNamed() {
    assertEquals(Result.Error(Problem.EMPTY), DeliverLater.build(emptyList(), who))
    assertEquals(Result.Error(Problem.NAME), DeliverLater.build(listOf(rice), who.copy(name = "  ")))
    assertEquals(Result.Error(Problem.PHONE), DeliverLater.build(listOf(rice), who.copy(phone = " ")))
    assertEquals(Result.Error(Problem.ADDRESS), DeliverLater.build(listOf(rice), who.copy(addressText = "")))
    //  نشانه بی نشانی بس است (همان قاعدهٔ سرور برای سفارشِ تلفنیِ بی پین)
    ok(DeliverLater.build(listOf(rice), who.copy(addressText = "", landmark = "پشتِ مسجد")))
    assertEquals(Result.Error(Problem.FEE), DeliverLater.build(listOf(rice), who.copy(fee = "۵۰.۱۲۳")))
    assertEquals(Result.Error(Problem.FEE), DeliverLater.build(listOf(rice), who.copy(fee = "2000000")))
    assertEquals(Result.Error(Problem.LINE, "برنج"), DeliverLater.build(listOf(rice.copy(qtyMilli = 0)), who))
    assertEquals(Result.Error(Problem.LINE, "برنج"), DeliverLater.build(listOf(rice.copy(unitPriceMinor = -1)), who))
    assertEquals(Result.Error(Problem.LINE, " "), DeliverLater.build(listOf(rice.copy(name = " ")), who))
    assertEquals(Result.Error(Problem.TOO_MANY_LINES), DeliverLater.build(List(201) { rice }, who))
    //  ارسالِ خالی = بی هزینه
    assertEquals(0L, ok(DeliverLater.build(listOf(rice), who.copy(fee = ""))).body.deliveryFeeMinor)
  }

  @Test
  fun longTextsAreCappedLikeTheServer() {
    val r = ok(DeliverLater.build(listOf(rice.copy(name = "x".repeat(300), unit = "u".repeat(40))), who.copy(name = "n".repeat(100), note = "z".repeat(600))))
    assertEquals(DeliverLater.MAX_NAME, r.body.customerName.length)
    assertEquals(DeliverLater.MAX_NOTE, r.body.note.length)
    assertEquals(DeliverLater.MAX_LINE_NAME, r.body.lines.single().name.length)
    assertEquals(DeliverLater.MAX_UNIT, r.body.lines.single().unit.length)
  }
}
