package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.core.atrest.AtRestEnvelope
import ir.vil3ntec.tohid.data.AtRest
import ir.vil3ntec.tohid.data.AtRestKeys
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.desktop.keystore.DeskKeystoreFlag
import ir.vil3ntec.tohid.desktop.keystore.KekVault
import ir.vil3ntec.tohid.desktop.keystore.KeyStoreFailure
import ir.vil3ntec.tohid.desktop.keystore.OsKeyStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 *  SEC-01 فاز ۲ سرتاسری — `desk.keystore` با همان `ShopStore`، همان `AtRest` و همان دیسک (روی ویندوز و مک هم در CI)،
 *  با کلیددانِ ساختگی: دفترِ رمزدارِ فاز ۱ بی هیچ بازپیچیدنی به کلیددانِ سیستم می‌رود، فایلِ ساده در اجرای بعد پاک
 *  می‌شود، کلیددانِ قفل دفتر را نمی‌بَرد، و خاموش کردنِ پرچم فایل را برمی‌گرداند.
 */
class KeystoreDiskTest {

  private val ctx = DesktopContext
  private val dir get() = ctx.filesDir
  private val ledger get() = File(dir, "shop-data.json")
  private val keys get() = File(ctx.root, "keys")
  private val kekFile get() = File(keys, KekVault.PLAIN)
  private val osFile get() = File(keys, KekVault.OS)

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
    AtRestKeys.storeOverride = null
    AtRest.setWanted(ctx, false)
    dir.listFiles().orEmpty()
      .filter { it.name.startsWith("shop-data.json") || it.name.startsWith("before-restore") || it.name.startsWith("ledger-") }
      .forEach { it.deleteRecursively() }
    File(dir, "atrest").deleteRecursively()
    keys.listFiles().orEmpty().filter { it.name.startsWith("atrest-kek") }.forEach { it.delete() }
    File(ctx.root, DeskKeystoreFlag.KEY).delete()
    relaunch()
  }

  /** «برنامه بسته و دوباره باز شد» برای کلید و کلیددانِ دفتر */
  private fun relaunch() {
    AtRestKeys.relaunchForTest()
    AtRest.keyring(ctx).forget()
  }

  private fun flag(on: Boolean) {
    System.setProperty(DeskKeystoreFlag.KEY, on.toString())
  }

  @Before fun before() {
    clean()
    AtRestKeys.storeOverride = { store }
  }

  @After fun after() = clean()

  private fun open(): ShopStore = ShopStore(ctx).also { runBlocking { it.load() } }

  private val first = ShopData(products = listOf(Product(id = "ks-p1", name = "برنج", salePrice = 120.0)))
  private val second = first.copy(products = first.products + Product(id = "ks-p2", name = "شکر", salePrice = 90.0))

  @Test fun `پرچمِ خاموش (پیش‌فرض) — همان فایلِ کلیدِ فاز ۱ و هیچ نشانی`() {
    System.clearProperty(DeskKeystoreFlag.KEY)
    AtRest.setWanted(ctx, true)
    assertTrue(runBlocking { open().save(first) })
    assertTrue(AtRestEnvelope.isSealed(ledger))
    assertTrue(kekFile.exists())
    assertFalse(osFile.exists())
    assertTrue(store.items.isEmpty())
    relaunch()
    assertEquals(first, open().data.value)
    assertEquals(KekVault.Status.Off, AtRestKeys.vault.status)
    assertTrue(AtRestKeys.note().contains(ir.vil3ntec.tohid.core.i18n.StrAppData.deskNote1))
  }

  @Test fun `دفترِ رمزدارِ فاز ۱ به کلیددانِ سیستم می‌رود — همان دفتر، بی بازپیچیدن، فایلِ کلید در اجرای بعد پاک`() {
    AtRest.setWanted(ctx, true)
    assertTrue(runBlocking { open().save(first) })
    val kek = kekFile.readBytes()
    val keyring = File(File(dir, "atrest"), "keyring").readBytes()

    flag(true)
    relaunch()
    assertEquals(first, open().data.value)
    assertTrue(osFile.exists())
    assertTrue("تا اجرای بعد می‌ماند", kekFile.exists())
    assertArrayEquals(kek, store.items.values.single())
    assertEquals(KekVault.Status.Protected("fake", KekVault.Plain.KEPT), AtRestKeys.vault.status)
    assertTrue(AtRestKeys.note().contains(ir.vil3ntec.tohid.core.i18n.StrAppData.deskOsPlainPending))

    relaunch()
    assertEquals(first, open().data.value)
    assertFalse("اجرای بعدِ موفق فایلِ ساده را پاک کرد", kekFile.exists())
    assertArrayEquals("کلیددانِ دفتر دست نخورد", keyring, File(File(dir, "atrest"), "keyring").readBytes())
    assertTrue("دفتر همچنان رمزدار", AtRestEnvelope.isSealed(ledger))

    //  نوشتن و خواندنِ تازه فقط با کلیددانِ سیستم
    relaunch()
    assertTrue(runBlocking { open().save(second) })
    relaunch()
    assertEquals(second, open().data.value)
    assertFalse(kekFile.exists())
  }

  @Test fun `کلیددانِ قفل پس از کوچ — دفتر کنار نمی‌رود، کلیدِ تازه ساخته نمی‌شود، و باز شدنِ قفل همان دفتر را می‌دهد`() {
    flag(true)
    AtRest.setWanted(ctx, true)
    assertTrue(runBlocking { open().save(first) })
    relaunch()
    assertEquals(first, open().data.value)
    assertFalse(kekFile.exists())
    val sealed = ledger.readBytes()

    store.locked = true
    relaunch()
    open()
    relaunch()
    val s = open()
    assertTrue("دفترِ رمزدار باز نشد", s.unreadable.value != null)
    assertFalse("کلیدِ ساده‌ای ساخته نشد", kekFile.exists())
    assertArrayEquals(sealed, ledger.readBytes())
    assertTrue(File(File(dir, "atrest"), "keyring").exists())

    store.locked = false
    relaunch()
    assertEquals(first, open().data.value)
  }

  @Test fun `پرچم خاموش پس از کوچ — فایلِ کلید برمی‌گردد و دفتر همان است`() {
    flag(true)
    AtRest.setWanted(ctx, true)
    assertTrue(runBlocking { open().save(first) })
    relaunch()
    open()
    assertFalse(kekFile.exists())
    val kek = store.items.values.single()

    flag(false)
    relaunch()
    assertEquals(first, open().data.value)
    assertArrayEquals(kek, kekFile.readBytes())
    store.items.clear()
    relaunch()
    assertEquals("پس از برگشت کلیددانِ سیستم لازم نیست", first, open().data.value)
  }

  @Test fun `کلیددانِ سیستم نیست — پرچمِ روشن همان فایل را نگه می‌دارد و صفحهٔ امنیت می‌گوید`() {
    AtRestKeys.storeOverride = { null }
    flag(true)
    AtRest.setWanted(ctx, true)
    assertTrue(runBlocking { open().save(first) })
    repeat(2) {
      relaunch()
      assertEquals(first, open().data.value)
    }
    assertTrue(kekFile.exists())
    assertFalse(osFile.exists())
    assertTrue(AtRestKeys.vault.status is KekVault.Status.Fallback)
    assertTrue(AtRestKeys.note().contains(ir.vil3ntec.tohid.core.i18n.StrAppData.deskNote1))
  }
}
