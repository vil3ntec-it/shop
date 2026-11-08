package ir.vil3ntec.tohid.desktop.keystore

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.SecureRandom

/**
 *  SEC-01 فاز ۲ — کلیددانِ **واقعیِ** همین سیستم با پرچمِ `desk.keystore` که در آزمون روشن شده: سپردن ⇐ بازخوانی، و
 *  همان کوچِ کامل (فایلِ ساده ⇐ نشان ⇐ پاک شدنِ فایل در اجرای بعد ⇐ خواندن فقط از سیستم).
 *
 *  ویندوز (DPAPI) و مک (Keychain) در کارِ «ذخیره روی دیسکِ …» ِ CI با `DESK_KEYSTORE_REQUIRE=1` می‌دوند: آن‌جا نبودن
 *  یا شکستِ کلیددان **سرخ** است، نه «رد شد». بی آن (لینوکسِ بی `secret-tool`، یا D-Bus ِ نبود) همان راهِ برگشت سنجیده
 *  می‌شود: پرچمِ روشن، کلیددان نشد ⇒ همان فایل و همان کلید. مک بی‌پنجره (`interactive = false`) تا CI هرگز منتظرِ
 *  پنجرهٔ Keychain نماند.
 */
class OsKeystoreRoundTripTest {

  private val required = System.getenv("DESK_KEYSTORE_REQUIRE") == "1"
  private val random = SecureRandom()
  private lateinit var dir: File
  private val store: OsKeyStore? = OsKeyStores.forThisOs(interactive = false)
  private val refs = ArrayList<String>()

  @Before fun before() {
    dir = Files.createTempDirectory("kek-os").toFile()
  }

  @After fun after() {
    for (ref in refs) runCatching { store?.remove(ref) }
    dir.deleteRecursively()
  }

  private fun os(): String = System.getProperty("os.name").orEmpty()

  @Test fun `کلیددانِ سیستم — سپردن و بازخوانیِ همان ۳۲ بایت`() {
    if (required) assertNotNull("این سیستم کلیددان ندارد: ${os()}", store)
    val s = store ?: return assertFallback(null)
    val key = ByteArray(32).also { random.nextBytes(it) }
    val ref = KekVault.REF_PREFIX + "test-" + KekVault.hex(ByteArray(6).also { random.nextBytes(it) })
    refs += ref
    val blob = try {
      s.protect(ref, key)
    } catch (e: KeyStoreFailure) {
      if (required) fail("کلیددانِ ${s.id} روی ${os()} نپذیرفت: ${e.reason}")
      return assertFallback(s)
    }
    assertArrayEquals(key, s.unprotect(ref, blob))
    //  دوباره سپردنِ همان حساب (کوچِ نیمه‌کارهٔ پیشین) همان را می‌دهد
    val blob2 = s.protect(ref, key)
    assertArrayEquals(key, s.unprotect(ref, blob2))
    if (s.id == "dpapi") {
      assertTrue("DPAPI بایت‌های پیچیده می‌دهد", blob.isNotEmpty())
      assertFalse("کلیدِ خام در بایت‌های پیچیده نیست", blob.contains(java.util.Base64.getEncoder().encodeToString(key)))
    } else {
      s.remove(ref)
      try {
        s.unprotect(ref, blob)
        fail("پس از پاک کردن هنوز بود")
      } catch (e: KeyStoreFailure) {
        assertTrue(e.reason, e.notFound)
      }
    }
  }

  @Test fun `کوچِ کامل با کلیددانِ سیستم — فایل تا اجرای بعد، بعد فقط سیستم`() {
    val key = ByteArray(32).also { random.nextBytes(it) }
    val keys = File(dir, "keys").apply { mkdirs() }
    File(keys, KekVault.PLAIN).writeBytes(key)
    val v = KekVault(keys, flag = { true }, store = { store })
    assertArrayEquals(key, v.forRead().first())
    v.opened(key)
    val st = v.status
    if (st is KekVault.Status.Fallback) {
      if (required) fail("کوچ روی ${os()} نشد: ${st.reason}")
      assertTrue(File(keys, KekVault.PLAIN).exists())
      assertArrayEquals(key, v.forWrite())
      return
    }
    assertTrue("$st", st is KekVault.Status.Protected)
    refs += KekVault.REF_PREFIX + KekVault.check(key).take(16)
    assertTrue(File(keys, KekVault.OS).exists())
    assertTrue(File(keys, KekVault.PLAIN).exists())

    v.relaunch()
    assertArrayEquals(key, v.forRead().first())
    v.opened(key)
    assertFalse("اجرای بعد فایلِ ساده را پاک کرد", File(keys, KekVault.PLAIN).exists())

    v.relaunch()
    assertArrayEquals("فقط از کلیددانِ سیستم", key, v.forRead().single())
    assertEquals(KekVault.Status.Protected(store!!.id, KekVault.Plain.NONE), v.status)
  }

  /** بی کلیددان: پرچمِ روشن همان فایل را نگه می‌دارد و همان کلید را می‌دهد — هرگز «کلید نیست» */
  private fun assertFallback(s: OsKeyStore?) {
    assertFalse(required)
    val key = ByteArray(32).also { random.nextBytes(it) }
    val keys = File(dir, "fallback").apply { mkdirs() }
    File(keys, KekVault.PLAIN).writeBytes(key)
    val v = KekVault(keys, flag = { true }, store = { s })
    repeat(2) {
      assertArrayEquals(key, v.forRead().first())
      v.opened(key)
      v.relaunch()
    }
    assertTrue(File(keys, KekVault.PLAIN).exists())
    assertFalse(File(keys, KekVault.OS).exists())
  }
}
