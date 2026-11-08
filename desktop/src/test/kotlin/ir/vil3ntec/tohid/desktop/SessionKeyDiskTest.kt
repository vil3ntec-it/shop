package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.core.i18n.StrAppData
import ir.vil3ntec.tohid.core.net.TokenStore
import ir.vil3ntec.tohid.data.AtRestKeys
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.desktop.keystore.DeskKeystoreFlag
import ir.vil3ntec.tohid.desktop.keystore.KekVault
import ir.vil3ntec.tohid.desktop.keystore.KeyStoreFailure
import ir.vil3ntec.tohid.desktop.keystore.OsKeyStore
import ir.vil3ntec.tohid.desktop.keystore.SessionBox
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 *  SEC-01 فاز ۲ سرتاسری برای کلیدِ نشستِ ورود — `desk.keystore` با همان `TokenStore` ِ کامپیوتر، همان `SecretBox` و همان
 *  دیسک، با کلیددانِ ساختگی: توکن‌ها و رازِ دستگاهِ شاگرد (همان کلیدِ `staff_device_key` ِ #1002، رمزشده با `SecretBox`)
 *  بی هیچ بازنویسی به کلیددانِ سیستم می‌روند؛ کلیددانِ قفل فقط «دوباره وارد شوید» است — دفتر دست نمی‌خورد، کلیدِ
 *  تازه ساخته نمی‌شود و نشستِ پیشین با باز شدنِ کلیددان برمی‌گردد؛ خاموش کردنِ پرچم فایل را برمی‌گرداند.
 */
class SessionKeyDiskTest {

  private val ctx = DesktopContext
  private val keys get() = File(ctx.root, "keys")
  private val sessionKey get() = File(keys, KekVault.Slot.SESSION.plain)
  private val sessionOs get() = File(keys, KekVault.Slot.SESSION.os)
  private val prefs get() = ctx.getSharedPreferences("tohid-session-secure", 0)
  private val tokens get() = TokenStore(ctx)

  private class FakeStore : OsKeyStore {
    override val id = "fake"
    override val label = "Fake"
    val items = HashMap<String, ByteArray>()
    var locked = false
    override fun protect(ref: String, key: ByteArray): String {
      if (locked) throw KeyStoreFailure("locked")
      items[ref] = key.copyOf()
      return ""
    }
    override fun unprotect(ref: String, blob: String): ByteArray {
      if (locked) throw KeyStoreFailure("locked")
      return items[ref]?.copyOf() ?: throw KeyStoreFailure("not found", notFound = true)
    }
    override fun remove(ref: String) {
      items.remove(ref)
    }
  }

  private val store = FakeStore()

  private fun clean() {
    System.clearProperty(DeskKeystoreFlag.KEY)
    SecretBox.storeOverride = null
    prefs.edit().remove(ACCESS).remove(REFRESH).remove(EXPIRES).remove(STAFF).commit()
    keys.listFiles().orEmpty().filter { it.name.startsWith("session.") }.forEach { it.delete() }
    ctx.filesDir.listFiles().orEmpty()
      .filter { it.name.startsWith("shop-data.json") || it.name.startsWith("before-restore") || it.name.startsWith("ledger-") }
      .forEach { it.deleteRecursively() }
    SecretBox.relaunchForTest()
  }

  @Before fun before() {
    clean()
    SecretBox.storeOverride = { store }
  }

  @After fun after() = clean()

  private fun flag(on: Boolean) {
    System.setProperty(DeskKeystoreFlag.KEY, on.toString())
  }

  private fun open(): ShopStore = ShopStore(ctx).also { runBlocking { it.load() } }

  /** «برنامه بسته و دوباره باز شد» برای کلیدِ نشست */
  private fun relaunch() = SecretBox.relaunchForTest()

  /** رشته‌های رمزشدهٔ روی دیسک — کوچ هرگز بازنویسی‌شان نمی‌کند */
  private fun raw(): Map<String, String?> = listOf(ACCESS, REFRESH, STAFF).associateWith { prefs.getString(it, null) }

  private fun staff(): String? = prefs.getString(STAFF, null)?.let { SecretBox.open(it) }

  /** نشستِ فاز ۱ (پرچمِ خاموش): توکن‌ها و رازِ دستگاهِ شاگرد با همان فایلِ `keys/session.key` */
  private fun phase1(): ByteArray {
    tokens.save("access-test-value", "refresh-test-value", 123L)
    prefs.edit().putString(STAFF, SecretBox.seal("staff-device-test-value")).commit()
    assertTrue(sessionKey.exists())
    assertFalse(sessionOs.exists())
    return sessionKey.readBytes()
  }

  private fun assertSession() {
    assertEquals("access-test-value", tokens.accessToken)
    assertEquals("refresh-test-value", tokens.refreshToken)
    assertEquals("staff-device-test-value", staff())
    assertTrue(tokens.signedIn)
  }

  @Test fun `توکن‌ها و رازِ دستگاهِ شاگرد به کلیددانِ سیستم می‌روند — همان رشته‌های روی دیسک، همان مقدارها`() {
    val k = phase1()
    val before = raw()
    assertSession()

    flag(true)
    relaunch()
    assertSession()
    assertTrue(sessionOs.exists())
    assertTrue("تا اجرای بعد می‌ماند", sessionKey.exists())
    assertArrayEquals("بایت‌های کلید هرگز عوض نمی‌شوند", k, store.items.values.single())
    assertEquals("هیچ رازی دوباره رمز نشد", before, raw())
    assertTrue(SecretBox.note().contains(StrAppData.deskSessionPlainPending))
    assertTrue("صفحهٔ امنیت حالِ هر دو کلید را می‌گوید", AtRestKeys.note().contains(StrAppData.deskSessionOs("fake").trim()))

    relaunch()
    assertSession()
    assertFalse("اجرای بعدِ موفق فایلِ ساده را پاک کرد", sessionKey.exists())
    assertEquals(before, raw())
    assertFalse(SecretBox.note().contains(StrAppData.deskSessionPlainPending))

    relaunch()
    assertSession()
    assertTrue(SecretBox.persistent())
    assertNull(SecretBox.lostNotice())
  }

  @Test fun `کلیددانِ قفل پس از کوچ — «دوباره وارد شوید»، دفتر سالم، هیچ کلیدِ تازه و همان نشست پس از باز شدن`() {
    phase1()
    flag(true)
    relaunch(); assertSession()
    relaunch(); assertSession()
    assertFalse(sessionKey.exists())
    val first = ShopData(products = listOf(Product(id = "sk-p1", name = "برنج", salePrice = 120.0)))
    assertTrue(runBlocking { open().save(first) })
    val ledger = File(ctx.filesDir, "shop-data.json")
    val ledgerBytes = ledger.readBytes()
    val before = raw()

    store.locked = true
    relaunch()
    assertNull(tokens.accessToken)
    assertNull(tokens.refreshToken)
    assertFalse("بی نشست ⇒ صفحهٔ ورود", tokens.signedIn)
    assertNull(staff())
    val why = (SecretBox.box.state() as SessionBox.State.Unavailable).why
    assertEquals(StrAppData.deskSessionLost(why), SecretBox.lostNotice())
    assertEquals(StrAppData.deskSessionUnavailable(why), SecretBox.note())
    assertFalse("هاب روشن نمی‌شود", SecretBox.persistent())
    assertEquals("خواندن هیچ رازی را پاک نکرد", before, raw())
    assertFalse("هیچ کلیدِ تازه‌ای", sessionKey.exists())
    assertArrayEquals("دفتر دست نخورد", ledgerBytes, ledger.readBytes())
    assertEquals(first, open().data.value)

    //  کاربر دوباره وارد شد: نشستِ همین اجرا کار می‌کند؛ کلیدی ساخته نمی‌شود و رازِ دستگاهِ شاگرد دست نمی‌خورد
    tokens.save("access-again-test-value", "refresh-again-test-value", 0)
    assertEquals("access-again-test-value", tokens.accessToken)
    assertTrue(tokens.signedIn)
    assertFalse(sessionKey.exists())
    assertEquals(1, store.items.size)
    assertEquals(before[STAFF], prefs.getString(STAFF, null))

    relaunch()
    assertNull("ورودِ اجرای قفل فقط تا بستنِ برنامه", tokens.accessToken)

    store.locked = false
    relaunch()
    assertNull("نشستِ اجرای قفل جای نشستِ پیشین نشسته بود ⇒ ورودِ دوباره", tokens.accessToken)
    assertEquals("رازِ دستگاهِ شاگرد همان برگشت", "staff-device-test-value", staff())
    assertNull(SecretBox.lostNotice())
    assertTrue(SecretBox.persistent())
    assertArrayEquals(ledgerBytes, ledger.readBytes())
  }

  @Test fun `کلیددانِ قفل بی ورودِ دوباره — باز شدنِ کلیددان همان نشست را بایت‌به‌بایت برمی‌گرداند`() {
    phase1()
    flag(true)
    relaunch(); assertSession()
    relaunch(); assertSession()
    val before = raw()
    store.locked = true
    relaunch()
    assertFalse(tokens.signedIn)
    assertNotNull(SecretBox.lostNotice())
    store.locked = false
    relaunch()
    assertSession()
    assertEquals(before, raw())
  }

  @Test fun `پرچم خاموش پس از کوچ — فایلِ کلیدِ نشست با همان بایت‌ها برمی‌گردد و نشست همان است`() {
    val k = phase1()
    flag(true)
    relaunch(); assertSession()
    relaunch(); assertSession()
    assertFalse(sessionKey.exists())

    flag(false)
    relaunch()
    assertSession()
    assertArrayEquals("فایلِ ساده برگشت", k, sessionKey.readBytes())
    assertEquals(KekVault.Status.Off, SecretBox.box.vaultStatus)
    assertEquals(StrAppData.deskSessionFile, SecretBox.note())
    assertTrue("نسخهٔ سیستم پاک نمی‌شود", sessionOs.exists() && store.items.isNotEmpty())
  }

  private companion object {
    //  همان نام‌های `TokenStoreDesk` (و `staff_device_key` ِ #1002)
    const val ACCESS = "access_token"
    const val REFRESH = "refresh_token"
    const val EXPIRES = "access_expires_at"
    const val STAFF = "staff_device_key"
  }
}
