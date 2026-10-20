package ir.vil3ntec.tohid.ui.v3

import org.junit.Assert.assertEquals
import org.junit.Test

/** کادرِ پول/عدد: جداکنندهٔ هزارگان فقط در نمایش، جای مکان‌نما درست، رقمِ فارسی لاتین */
class V3NumbersTest {

  @Test
  fun `هزارگان فقط در بخشِ صحیح`() {
    assertEquals("1,234,567.5", V3Numbers.group("1234567.5"))
    assertEquals("999", V3Numbers.group("999"))
    assertEquals("1,000", V3Numbers.group("1000"))
    assertEquals("", V3Numbers.group(""))
    assertEquals("0.25", V3Numbers.group("0.25"))
  }

  @Test
  fun `جای مکان‌نما میانِ خام و جداشده رفت‌وبرگشت دارد`() {
    val raw = "1234567"
    val grouped = V3Numbers.group(raw)
    for (i in 0..raw.length) {
      val t = V3Numbers.toGrouped(raw, i)
      assertEquals("offset $i", i, V3Numbers.toRaw(grouped, t))
    }
    assertEquals(0, V3Numbers.toGrouped(raw, 0))
    assertEquals(grouped.length, V3Numbers.toGrouped(raw, raw.length))
    assertEquals(2, V3Numbers.toGrouped("1234", 1))
  }

  @Test
  fun `پالایشِ کادر به گونه‌اش`() {
    assertEquals("12.53", clean(V3FieldKind.MONEY, "۱۲٫۵.۳"))
    assertEquals("1250", clean(V3FieldKind.NUMBER, "1,250 ؋"))
    assertEquals("626001ABC", clean(V3FieldKind.BARCODE, "۶۲۶۰۰۱ ABC-"))
    assertEquals("+93700", clean(V3FieldKind.PHONE, "+۹۳ ۷۰۰"))
    assertEquals("1405-06-06", clean(V3FieldKind.DATE, "۱۴۰۵-۰۶-۰۶"))
    assertEquals("a@b.af", clean(V3FieldKind.EMAIL, " a@b.af "))
    assertEquals("نامِ کالا", clean(V3FieldKind.TEXT, "نامِ کالا"))
  }
}
