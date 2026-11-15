package ir.vil3ntec.tohid.ui.v3

import ir.vil3ntec.tohid.LocaleText
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.i18n.StrGoods
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.PoLineDto
import ir.vil3ntec.tohid.data.PoTotalsDto
import ir.vil3ntec.tohid.data.PurchaseOrderDto
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.ui.v3.account.team.TeamModel
import ir.vil3ntec.tohid.ui.v3.money.PoRowText
import ir.vil3ntec.tohid.ui.v3.sales.SalesTestLedger.ledger
import ir.vil3ntec.tohid.ui.v3.sales.history.HistoryModel
import ir.vil3ntec.tohid.ui.v3.sales.history.RowLookup
import ir.vil3ntec.tohid.ui.v3.sales.history.SaleTime
import ir.vil3ntec.tohid.ui.v3.account.AccountFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 *  سادگیِ ردیف‌ها (`claude/e-rows-63d`): تاریخِ کوتاه (امروز، دیروز، همین سال بی سال، سالِ دیگر با سال) در فارسی و
 *  انگلیسی، عنوانِ معنادارِ فاکتور (مشتری، وگرنه نامِ کالاها)، وضعیتِ آرام («نسیه» کهربایی، نقدیِ عادی هیچ)، ردیفِ سفارشِ
 *  خرید و خطِ آرامِ کارمند.
 */
class RowsSimpleTest {

  private val auto = I18n.CAL_AUTO
  private val dari = LocaleText.MONTHS_DARI

  @Before fun fa() { I18n.source = { Lang.FA }; SaleTime.zoneOffset = { 0 } }
  @After fun reset() { I18n.source = { Lang.FA }; SaleTime.zoneOffset = { 0 } }

  /* ─────────────────────────── تاریخِ کوتاه ─────────────────────────── */

  @Test fun `فارسی — امروز و دیروز با ساعت، همین سال بی سال، سالِ دیگر با سال و بی ساعت`() {
    val today = "2026-11-14"
    assertEquals("امروز 14:05", RowWhen.of("2026-11-14", today, "14:05", auto, dari))
    assertEquals("دیروز 9:30", RowWhen.of("2026-11-13", today, "9:30", auto, dari))
    assertEquals("28 میزان 14:05", RowWhen.of("2026-10-20", today, "14:05", auto, dari))
    assertEquals("28 میزان 1404", RowWhen.of("2025-10-20", today, "14:05", auto, dari))
    assertEquals("امروز", RowWhen.of("2026-11-14", today, calendar = auto, monthsPref = dari))
  }

  @Test fun `انگلیسی — میلادی با نامِ کوتاهِ ماه، سالِ دیگر با سال`() {
    I18n.source = { Lang.EN }
    val today = "2026-11-14"
    assertEquals("Today 14:05", RowWhen.of("2026-11-14", today, "14:05", auto, dari))
    assertEquals("Yesterday 9:30", RowWhen.of("2026-11-13", today, "9:30", auto, dari))
    assertEquals("Oct 20 14:05", RowWhen.of("2026-10-20", today, "14:05", auto, dari))
    assertEquals("Oct 20 2025", RowWhen.of("2025-10-20", today, "14:05", auto, dari))
  }

  @Test fun `سالِ جاری در تقویمِ همان زبان سنجیده می شود — نوروز مرزِ سالِ خورشیدی است`() {
    //  ۱۵ مارچ ۲۰۲۶ = ۲۴ حوت ۱۴۰۴ و ۲۵ مارچ = ۵ حمل ۱۴۰۵: در فارسی سالِ دیگر، در انگلیسیِ میلادی همین سال
    assertEquals("24 حوت 1404", RowWhen.of("2026-03-15", "2026-03-25", "10:00", auto, dari))
    I18n.source = { Lang.EN }
    assertEquals("Mar 15 10:00", RowWhen.of("2026-03-15", "2026-03-25", "10:00", auto, dari))
  }

  @Test fun `پشتو نامِ ماهِ پشتو و تقویمِ میلادیِ برگزیده در فارسی`() {
    I18n.source = { Lang.PS }
    assertEquals("28 تله 14:05", RowWhen.of("2026-10-20", "2026-11-14", "14:05", auto, dari))
    I18n.source = { Lang.FA }
    assertEquals("20 اکتوبر 14:05", RowWhen.of("2026-10-20", "2026-11-14", "14:05", I18n.CAL_GREGORIAN, dari))
  }

  @Test fun `لحظهٔ میلی ثانیه ای همان روز و ساعتِ تقویمی را می دهد و تاریخِ ناخوانا همان طور می ماند`() {
    //  ۲۰۲۶-۱۱-۱۳ ساعتِ ۲۳:۳۰ ِ UTC؛ با فاصلهٔ +۴:۳۰ (کابل) فردایش ساعتِ ۴:۰۰ است
    val at = java.time.Instant.parse("2026-11-13T23:30:00Z").toEpochMilli()
    assertEquals("دیروز 23:30", RowWhen.at(at, "2026-11-14", 0, auto, dari))
    assertEquals("امروز 4:00", RowWhen.at(at, "2026-11-14", (4.5 * 3_600_000).toInt(), auto, dari))
    assertEquals("", RowWhen.at(0, "2026-11-14", 0, auto, dari))
    assertEquals("بی‌تاریخ 9:00", RowWhen.of("بی‌تاریخ", "2026-11-14", "9:00", auto, dari))
  }

  /* ─────────────────────────── ردیفِ فاکتور ─────────────────────────── */

  private val book = ledger.copy(
    sales = listOf(
      Sale("a", 10, finalTotal = 100.0, paidAmount = 100.0, createdAt = 3, date = "2026-11-14"),
      Sale("b", 11, finalTotal = 200.0, paidAmount = 50.0, remaining = 150.0, paymentMethod = "credit", debtorId = "d1", createdAt = 2, date = "2026-11-13"),
      Sale("c", 12, finalTotal = 300.0, status = "cancelled", createdAt = 1, date = "2026-10-20"),
      Sale("m", 13, finalTotal = 400.0, paidAmount = 400.0, createdAt = 4, date = "2026-11-14"),
    ),
    saleItems = listOf(
      SaleItem("ai", "a", "soap", quantity = 2.0, unitPrice = 50.0, totalPrice = 100.0),
      SaleItem("bi", "b", "rice", quantity = 2.0, unitPrice = 100.0, totalPrice = 200.0),
      SaleItem("ci", "c", "", name = "", quantity = 3.0, unitPrice = 100.0, totalPrice = 300.0),
      SaleItem("m1", "m", "soap", quantity = 1.0, unitPrice = 50.0, totalPrice = 50.0),
      SaleItem("m2", "m", "rice", quantity = 1.0, unitPrice = 120.0, totalPrice = 120.0),
      SaleItem("m3", "m", "tea", quantity = 1.0, unitPrice = 80.0, totalPrice = 80.0),
      SaleItem("m4", "m", "carton", quantity = 1.0, unitPrice = 150.0, totalPrice = 150.0),
    ),
  )

  @Test fun `فاکتورِ بی مشتری با نامِ کالاها و بی وضعیت، شماره کوچک در پایانِ زیرعنوان`() {
    for (look in listOf(null, RowLookup(book))) {
      val cash = HistoryModel.row(book, book.sales[0], returnsV2 = false, look = look, today = "2026-11-14")
      assertEquals("صابون", cash.title)
      assertNull("نقدیِ عادی هیچ وضعیتی ندارد", cash.status)
      assertTrue(cash.subtitle.endsWith("#10"))
      assertFalse("شمارِ قلم در عنوانِ کالایی تکرار نمی‌شود", cash.subtitle.contains(StrSale.itemsCount("1")))
      val many = HistoryModel.row(book, book.sales[3], returnsV2 = false, look = look)
      assertEquals("صابون، برنج و 2 قلمِ دیگر", many.title)
    }
  }

  @Test fun `نسیه با نامِ مشتری و متنِ کهربایی، لغوشده آرام، و روزِ کوتاه در نمای ماه`() {
    for (look in listOf(null, RowLookup(book))) {
      val credit = HistoryModel.row(book, book.sales[1], returnsV2 = false, look = look, withDate = true, today = "2026-11-14")
      assertEquals("احمد کریمی", credit.title)
      assertEquals(V3RowTone.ATTENTION, credit.status?.tone)
      assertEquals(credit.payLabel, credit.status?.text)
      assertTrue(credit.subtitle, credit.subtitle.startsWith("دیروز 0:00 · "))
      assertTrue(credit.subtitle.contains(StrSale.itemsCount("1")))
      val cancelled = HistoryModel.row(book, book.sales[2], returnsV2 = false, look = look, withDate = true, today = "2026-11-14")
      assertEquals(V3RowStatus(StrSale.cancelled), cancelled.status)
      assertTrue(cancelled.subtitle, cancelled.subtitle.startsWith("28 میزان"))
      //  میدان‌های پیشین (برگشت، لغو، برچسبِ پرداخت) دست نخورده‌اند
      assertFalse(cancelled.canCancel)
    }
  }

  @Test fun `عنوانِ کالایی — یک، دو، بیش از دو، و بی قلم`() {
    assertEquals(StrSale.cashPay, HistoryModel.itemsTitle(emptyList()))
    assertEquals("برنج", HistoryModel.itemsTitle(listOf("برنج", " برنج ")))
    assertEquals("برنج، چای", HistoryModel.itemsTitle(listOf("برنج", "چای")))
    I18n.source = { Lang.EN }
    assertEquals("Rice, Tea and 1 more item", HistoryModel.itemsTitle(listOf("Rice", "Tea", "Soap")))
  }

  /* ─────────────────────────── سفارشِ خرید و کارمند ─────────────────────────── */

  private fun po(status: String, expected: String? = "2026-11-20") = PurchaseOrderDto(
    id = "po1", no = 12, supplierName = "شرکتِ نور", status = status,
    lines = listOf(PoLineDto(id = "l1"), PoLineDto(id = "l2")),
    totals = PoTotalsDto(orderedMinor = 250_000, remainingMinor = 100_000), expectedDate = expected,
  )

  @Test fun `سفارشِ خرید — نامِ تأمین کننده عنوان، شماره کوچک، تکمیل بی وضعیت، نیمه رسیده کهربایی`() {
    val sent = PoRowText.of(po("sent"), "2026-11-14")
    assertEquals("شرکتِ نور", sent.title)
    assertTrue(sent.subtitle, sent.subtitle.startsWith("#12 · 2 ردیف"))
    assertTrue(sent.subtitle, sent.subtitle.endsWith("رسیدن 29 عقرب"))
    assertEquals(V3RowStatus(StrGoods.poSent), sent.status)
    val partial = PoRowText.of(po("partial"), "2026-11-14")
    assertEquals(V3RowTone.ATTENTION, partial.status?.tone)
    assertTrue(partial.subtitle.contains("باقی‌مانده"))
    assertNull(PoRowText.of(po("complete"), "2026-11-14").status)
    assertTrue(PoRowText.of(po("cancelled"), "2026-11-14").struck)
    assertFalse("روزِ رسیدن فقط برای سفارشِ در راه", PoRowText.of(po("complete"), "2026-11-14").subtitle.contains("رسیدن"))
  }

  @Test fun `کارمند — نقش و تماس در یک خطِ آرام`() {
    val m = TeamModel.MemberRow("u1", "کریم", "ک", "0799000111", "فروشنده", AccountFormat.Role.STAFF, active = true, removable = true)
    assertEquals("فروشنده · 0799000111", TeamModel.memberLine(m))
    assertEquals("فروشنده", TeamModel.memberLine(m.copy(contact = "")))
    assertTrue(StrAccount.stateClosed.isNotBlank())
  }
}
