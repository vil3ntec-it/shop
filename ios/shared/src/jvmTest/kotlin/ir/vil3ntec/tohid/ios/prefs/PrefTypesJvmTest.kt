package ir.vil3ntec.tohid.ios.prefs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  `SharedPreferences.all` ِ آیفون همان نوع‌های اندروید را می‌دهد (`Boolean`، `Int`، `Long`، `Float`، `String`،
 *  `Set<String>`) — پایهٔ `ShopSettingsSync.readLocal`، `ShopSettingsBundle.export`، `SyncV1Keys.move` و `AccountVault`.
 *  همتای شبیه‌سازش (NSUserDefaults ِ واقعی): ‎iosTest/…/DataSafetyIosTest‎.
 */
class PrefTypesJvmTest {

  @Test
  fun boolNumberIsBoolean() {
    //  CFBoolean ِ `setBool` (در حافظه) و `<true/>` ِ plist هر دو `c` اند
    assertEquals(true, PrefTypes.number("c", null, 1L, 1.0, true))
    assertEquals(false, PrefTypes.number("c", "b", 0L, 0.0, false))
    assertEquals(true, PrefTypes.number("B", null, 1L, 1.0, true))
  }

  @Test
  fun realNumberIsFloat() {
    assertEquals(0.5f, PrefTypes.number("f", null, 0L, 0.5, false))
    //  پس از رفت‌وبرگشتِ plist، `<real/>` دوبل است
    assertEquals(0.25f, PrefTypes.number("d", "f", 0L, 0.25, false))
  }

  @Test
  fun integerIsLongUnlessDeclaredInt() {
    assertEquals(1_730_000_000_000L, PrefTypes.number("q", null, 1_730_000_000_000L, 1.73e12, true))
    //  کلیدِ `l` با مقدارِ کوچک هم Long می‌ماند (گردکردنِ نقد ۵۰۰)
    assertEquals(500L, PrefTypes.number("q", "l", 500L, 500.0, true))
    assertEquals(14, PrefTypes.number("q", "i", 14L, 14.0, true))
    //  بیرون از بازهٔ Int هرگز بریده نمی‌شود
    assertEquals(5_000_000_000L, PrefTypes.number("q", "i", 5_000_000_000L, 5e9, true))
    //  عددِ ۰/۱ ِ `setInteger` بولی نیست
    assertEquals(1L, PrefTypes.number("q", null, 1L, 1.0, true))
  }

  @Test
  fun kotlinValuesKeepAndroidTypes() {
    assertEquals(true, PrefTypes.typed(true, null))
    assertEquals(3, PrefTypes.typed(3, null))
    assertEquals(3L, PrefTypes.typed(3, "l"))
    assertEquals(3, PrefTypes.typed(3L, "i"))
    assertEquals(3L, PrefTypes.typed(3L, null))
    assertEquals(1.5f, PrefTypes.typed(1.5, null))
    assertEquals("x", PrefTypes.typed("x", null))
    assertNull(PrefTypes.typed(null, "b"))
  }

  @Test
  fun stringListIsStringSet() {
    val s = PrefTypes.typed(listOf("a", "b", "a"), "set")
    assertTrue(s is Set<*>)
    assertEquals(setOf("a", "b"), s)
    //  فهرستِ غیرمتنی (مثلاً کلیدِ سراسریِ سیستم) دست نمی‌خورد
    val mixed = listOf("a", 1)
    assertEquals(mixed, PrefTypes.typed(mixed, null))
    assertNull(PrefTypes.strings(mixed))
  }

  @Test
  fun legacyTextOfDeclaredKeyGetsItsType() {
    //  `stringValue` ِ shim ِ پیشین: CFBoolean ⇒ "1"/"0"، عدد ⇒ رقم
    assertEquals(true, PrefTypes.typed("1", "b"))
    assertEquals(false, PrefTypes.typed("0", "b"))
    assertEquals(true, PrefTypes.typed("YES", "b"))
    assertEquals(500L, PrefTypes.typed("500", "l"))
    assertEquals(14, PrefTypes.typed("14", "i"))
    assertEquals(0.5f, PrefTypes.typed("0.5", "f"))
  }

  @Test
  fun textThatIsNotTheDeclaredTypeStaysText() {
    assertEquals("maybe", PrefTypes.typed("maybe", "b"))
    assertEquals("12abc", PrefTypes.typed("12abc", "l"))
    assertEquals("99999999999", PrefTypes.typed("99999999999", "i"))
    assertEquals("NaN", PrefTypes.typed("NaN", "f"))
    //  کلیدِ متنی و کلیدِ ناشناخته هرگز تبدیل نمی‌شوند
    assertEquals("1", PrefTypes.typed("1", "s"))
    assertEquals("1", PrefTypes.typed("1", null))
  }
}
