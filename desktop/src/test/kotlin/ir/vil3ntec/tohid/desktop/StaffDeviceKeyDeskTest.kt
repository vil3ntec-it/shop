package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.core.net.TokenStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 *  ⛔ رازِ دستگاهِ شاگرد روی کامپیوتر (سرور ۲.۱۴.۵، `server/src/lib/staff-device-keys.js`): ورودِ دوباره با کدِ شاگرد
 *  فقط با همین راز. پس مثلِ توکن **رمزشده** (`SecretBox`، کلیدِ فایلِ فقط-صاحب — همان روشِ `AtRestKeysDesk`) روی دیسک
 *  می‌نشیند، خروج (`clear()`) پاکش نمی‌کند، و مثلِ نشست برای هر سرور جداست (`stashTo`/`openFrom`) — رازِ یک سرور هرگز به
 *  سرورِ دیگر نمی‌رود.
 */
class StaffDeviceKeyDeskTest {

  private val file = "tohid-session-secure"

  @Test fun `راز رمزشده روی دیسک، پس از خروج می‌ماند، دستکاری یعنی بی راز`() {
    val tokens = TokenStore(DesktopContext)
    tokens.staffDeviceKey = "device-key-راز"
    assertEquals("device-key-راز", tokens.staffDeviceKey)
    val raw = DesktopContext.getSharedPreferences(file, 0).getString("staff_device_key", null)!!
    assertFalse("رازِ خام روی دیسک است", raw.contains("device-key"))

    tokens.save("access-x", "refresh-x", 1L)
    tokens.clear()
    assertFalse(tokens.signedIn)
    assertEquals("خروج رازِ دستگاه را نگه می‌دارد", "device-key-راز", tokens.staffDeviceKey)

    DesktopContext.getSharedPreferences(file, 0).edit().putString("staff_device_key", raw.dropLast(4) + "AAAA").commit()
    assertNull(tokens.staffDeviceKey)
    tokens.staffDeviceKey = null
    assertNull(DesktopContext.getSharedPreferences(file, 0).getString("staff_device_key", null))
  }

  @Test fun `رازِ هر سرور جدا — رفتن به سرورِ دیگر رازِ این یکی را نمی‌برد و برگشتن برش می‌گرداند`() {
    val tokens = TokenStore(DesktopContext)
    tokens.save("access-a", "refresh-a", 1L)
    tokens.staffDeviceKey = "key-server-a"
    tokens.stashTo("a.example")
    assertNull("سرورِ دیگر رازِ این سرور را نمی‌بیند", tokens.staffDeviceKey)
    tokens.staffDeviceKey = "key-server-b"
    tokens.stashTo("b.example")
    tokens.openFrom("a.example")
    assertEquals("key-server-a", tokens.staffDeviceKey)
    assertEquals("access-a", tokens.accessToken)
    tokens.stashTo("a.example")
    tokens.openFrom("b.example")
    assertEquals("key-server-b", tokens.staffDeviceKey)
    tokens.staffDeviceKey = null
    tokens.clear()
  }
}
