package ir.vil3ntec.tohid.ui.v3.onboarding

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** قفل، ساختِ دکان، تأییدِ دوبارهٔ هویت و رنگِ نشان */
class OnboardingModelsTest {

  /* ------------------------------ قفل ------------------------------ */

  @Test
  fun `بازکردن — تا طولِ رمز و نه بیشتر، پاک کردن یکی`() {
    var e = PinEntry(4)
    "12345".forEach { e = e.press(it) }
    assertEquals("1234", e.digits)
    assertTrue(e.full)
    assertEquals("123", e.erase().digits)
    assertEquals("", e.clear().digits)
  }

  @Test
  fun `گذاشتنِ رمز — دو بار یکی ⇐ همان رمز`() {
    var s = PinSetup(6)
    "123456".forEach { s = s.press(it).first }
    assertTrue(s.confirming)
    var pin: String? = null
    "123456".forEach { d -> s.press(d).let { (n, p) -> s = n; pin = p } }
    assertEquals("123456", pin)
    assertFalse(s.mismatch)
  }

  @Test
  fun `گذاشتنِ رمز — یکی نبود ⇐ از اول و پیام می ماند`() {
    var s = PinSetup(6)
    "123456".forEach { s = s.press(it).first }
    var pin: String? = "none"
    "654321".forEach { d -> s.press(d).let { (n, p) -> s = n; pin = p } }
    assertEquals(null, pin)
    assertFalse(s.confirming)
    assertEquals("", s.first)
    assertTrue(s.mismatch)
    s = s.press('1').first
    assertTrue(s.mismatch)
  }

  @Test
  fun `پاک کردن در آغازِ تکرار به رقمِ آخرِ بارِ اول برمی گردد`() {
    var s = PinSetup(6)
    "123456".forEach { s = s.press(it).first }
    s = s.erase()
    assertFalse(s.confirming)
    assertEquals("12345", s.first)
    s = s.press('9').first
    assertTrue(s.confirming)
    s = s.press('1').first.erase()
    assertEquals("", s.again)
    assertTrue(s.confirming)
  }

  @Test
  fun `صفحه کلیدِ سخت افزاری — لاتین، فارسی و عربی، و هیچ چیزِ دیگر`() {
    assertEquals('7', PinRules.keyDigit('7'))
    assertEquals('7', PinRules.keyDigit('۷'))
    assertEquals('7', PinRules.keyDigit('٧'))
    assertEquals(null, PinRules.keyDigit('a'))
  }

  /* ------------------------------ ساختِ دکان ------------------------------ */

  @Test
  fun `نامِ دکان — بی نام همان «دکان من»`() {
    assertEquals("دکان من", ShopSetupRules.nameToCreate("   "))
    assertEquals("فروشگاه نور", ShopSetupRules.nameToCreate(" فروشگاه نور "))
  }

  /* ------------------------------ تأییدِ دوبارهٔ هویت ------------------------------ */

  @Test
  fun `راهِ اثبات — حسابِ بی رمز کد، بقیه رمز`() {
    assertEquals(ReauthProof.PASSWORD, ReauthRules.proofFor(known = false, hasPassword = false))
    assertEquals(ReauthProof.PASSWORD, ReauthRules.proofFor(known = true, hasPassword = true))
    assertEquals(ReauthProof.CODE, ReauthRules.proofFor(known = true, hasPassword = false))
  }

  @Test
  fun `کدِ تأیید — رقمِ لاتین تا هشت`() {
    assertEquals("12345678", ReauthRules.codeDigits("۱۲۳ ۴۵۶۷۸۹"))
    assertTrue(ReauthRules.canSubmit(busy = false, proof = ReauthProof.CODE, secret = "1"))
    assertFalse(ReauthRules.canSubmit(busy = false, proof = null, secret = "1"))
    assertFalse(ReauthRules.canSubmit(busy = true, proof = ReauthProof.PASSWORD, secret = "1"))
  }

  /* ------------------------------ رنگِ نشان ------------------------------ */

  @Test
  fun `فامِ رنگ ها`() {
    assertEquals(0f, BrandHue.hueOf(Color(1f, 0f, 0f)), 0.01f)
    assertEquals(120f, BrandHue.hueOf(Color(0f, 1f, 0f)), 0.01f)
    assertEquals(240f, BrandHue.hueOf(Color(0f, 0f, 1f)), 0.01f)
  }

  @Test
  fun `چرخشِ نشان کوتاه ترین راه است و برای همان فام صفر`() {
    assertEquals(0f, BrandHue.shiftTo(Color.hsv(BrandHue.LOGO_HUE, 0.8f, 0.9f)), 0.5f)
    assertEquals(-28f, BrandHue.shiftTo(Color.hsv(190f, 0.8f, 0.9f)), 0.5f)
    assertEquals(-58f, BrandHue.shiftTo(Color.hsv(160f, 0.8f, 0.9f)), 0.5f)
    assertEquals(142f, BrandHue.shiftTo(Color.hsv(0f, 0.8f, 0.9f)), 0.5f)
  }

  @Test
  fun `ماتریسِ صفر درجه همانی است`() {
    val m = BrandHue.rotation(0f)
    val identity = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
    m.indices.forEach { assertEquals(identity[it], m[it], 0.001f) }
  }
}
