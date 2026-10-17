package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 *  F0-06 — «۳*کد» در کادرِ صندوقِ کامپیوتر.
 *
 *  پیش از این الگو فقط `[0-9]` را می‌شناخت: با چیدمانِ فارسی «۳*…» یک
 *  نامِ کالا حساب می‌شد و نه «سه تا»، و بارکدِ فارسی‌رقم پیدا نمی‌شد.
 */
class CartQueryTest {

  @Test
  fun `ریشهٔ خطا — الگوی قبلی رقمِ فارسی را نمی‌شناخت`() {
    val old = Regex("^\\s*([0-9]+(?:\\.[0-9]+)?)\\s*[*×xX]\\s*(.*)$")
    assertNull(old.find("۳*5449000000996"))
  }

  @Test
  fun `تعداد با رقمِ فارسی، عربی و لاتین`() {
    assertEquals(CartQuery.Parsed(3.0, "5449000000996"), CartQuery.parse("۳*5449000000996"))
    assertEquals(CartQuery.Parsed(3.0, "برنج"), CartQuery.parse("٣×برنج"))
    assertEquals(CartQuery.Parsed(1.5, "برنج"), CartQuery.parse("۱٫۵*برنج"))
    assertEquals(CartQuery.Parsed(12.0, "abc"), CartQuery.parse(" 12 x abc"))
  }

  @Test
  fun `بخشِ پس از ستاره دست نمی‌خورد تا نامِ فارسی‌رقم هم پیدا شود`() {
    assertEquals(CartQuery.Parsed(2.0, "برنج ۵ کیلویی"), CartQuery.parse("۲*برنج ۵ کیلویی"))
    assertEquals(CartQuery.Parsed(2.0, "۵۴۴۹۰۰۰۰۰۰۹۹۶"), CartQuery.parse("2*۵۴۴۹۰۰۰۰۰۰۹۹۶"))
  }

  @Test
  fun `بی ستاره، یک عدد و همان نوشته`() {
    assertEquals(CartQuery.Parsed(1.0, "برنج ۵ کیلویی"), CartQuery.parse("برنج ۵ کیلویی"))
    assertEquals(CartQuery.Parsed(1.0, "۵۴۴۹۰۰۰۰۰۰۹۹۶"), CartQuery.parse("۵۴۴۹۰۰۰۰۰۰۹۹۶"))
    assertEquals(CartQuery.Parsed(1.0, ""), CartQuery.parse(""))
  }

  @Test
  fun `بارکد در جست‌وجو با رقمِ فارسی هم می‌خورد`() {
    val codes = listOf("5449000000996")
    assertEquals(true, CartQuery.barcodeStarts(codes, "۵۴۴۹"))
    assertEquals(true, CartQuery.barcodeStarts(codes, "5449"))
    assertEquals(false, CartQuery.barcodeStarts(codes, "۴۰۰۱"))
    //  بارکدِ قدیمیِ فارسی‌رقم هم با رقمِ لاتین پیدا می‌شود
    assertEquals(true, CartQuery.barcodeStarts(listOf("۱۲۳۴۵۶۷۸"), "1234"))
  }
}
