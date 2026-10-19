package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.core.atrest.AtRestEnvelope
import ir.vil3ntec.tohid.core.atrest.AtRestException
import ir.vil3ntec.tohid.core.atrest.AtRestMigration
import ir.vil3ntec.tohid.data.AtRest
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
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
 *  SEC-01 فاز ۱ — رمزِ دفتر با همان `ShopStore` و همان دیسکِ واقعی (روی
 *  ویندوز هم در CI): خاموشِ پیش‌فرض دست‌نخورده، روشن/خاموش کردن، و از دست
 *  رفتنِ کلید بی هیچ دادهٔ ازدست‌رفته‌ای.
 */
class AtRestDiskTest {

  private val ctx = DesktopContext
  private val dir get() = ctx.filesDir
  private val ledger get() = File(dir, "shop-data.json")
  private val kekFile get() = File(File(ctx.root, "keys"), "atrest-kek.key")

  private fun clean() {
    AtRest.setWanted(ctx, false)
    dir.listFiles().orEmpty()
      .filter { it.name.startsWith("shop-data.json") || it.name.startsWith("before-restore") || it.name.startsWith("ledger-") }
      .forEach { it.deleteRecursively() }
    File(dir, "atrest").deleteRecursively()
    kekFile.delete()
    AtRest.keyring(ctx).forget()
  }

  @Before fun before() = clean()
  @After fun after() = clean()

  private fun open(): ShopStore = ShopStore(ctx).also { runBlocking { it.load() } }

  private val first = ShopData(products = listOf(Product(id = "sec1-p1", name = "برنج", salePrice = 120.0)))
  private val second = first.copy(products = first.products + Product(id = "sec1-p2", name = "شکر", salePrice = 90.0))

  @Test fun `خاموش (پیش‌فرض) — همان JSONِ ساده و هیچ کلیدی ساخته نمی‌شود`() {
    val store = open()
    assertTrue(runBlocking { store.save(first) })
    assertFalse(AtRestEnvelope.isSealed(ledger))
    assertTrue(ledger.readText().startsWith("{"))
    val r = runBlocking { store.convergeAtRest() }
    assertTrue(r.ok)
    assertEquals(0, r.converted)
    assertFalse("خاموش به کلید دست نمی‌زند", kekFile.exists())
    assertFalse(File(File(dir, "atrest"), "keyring").exists())
    assertEquals(first, open().data.value)
  }

  @Test fun `روشن — دفتر رمز می‌شود، باز کردن همان دفتر را می‌دهد، خاموش کردن برش می‌گرداند`() {
    val store = open()
    assertTrue(runBlocking { store.save(first) })

    assertTrue(AtRest.setWanted(ctx, true))
    val on = runBlocking { store.convergeAtRest() }
    assertTrue(on.ok)
    assertTrue(on.converted >= 1)
    assertTrue(AtRestEnvelope.isSealed(ledger))
    assertFalse("نامِ کالا در فایل خوانا نیست", String(ledger.readBytes(), Charsets.UTF_8).contains("برنج"))
    assertEquals(first, open().data.value)

    assertTrue(runBlocking { store.save(second) })
    assertTrue("ذخیرهٔ تازه هم رمزدار است", AtRestEnvelope.isSealed(ledger))
    assertEquals(second, open().data.value)
    assertFalse(AtRestMigration.sidecarOf(ledger).exists())

    //  راهِ برگشت
    assertTrue(AtRest.setWanted(ctx, false))
    val off = runBlocking { store.convergeAtRest() }
    assertTrue(off.ok)
    assertFalse(AtRestEnvelope.isSealed(ledger))
    assertEquals(second, open().data.value)
  }

  @Test fun `کلید از دست رفت — ثبت بسته، دفتر دست‌نخورده؛ بازیابی کنارش می‌گذارد و می‌نشیند`() {
    AtRest.setWanted(ctx, true)
    assertTrue(runBlocking { open().save(first) })
    assertTrue(AtRestEnvelope.isSealed(ledger))
    val before = ledger.readBytes()

    //  نصبِ دوباره / انتقال: کلیدِ اصلی نیست
    assertTrue(kekFile.delete())
    AtRest.keyring(ctx).forget()

    val store = open()
    assertEquals(AtRestException.Kind.KEY_MISSING, store.unreadable.value)
    assertEquals(ShopData(), store.data.value)
    assertFalse("دفترِ خالیِ حافظه هرگز روی دفترِ واقعی نمی‌نشیند", runBlocking { store.save(second) })
    assertArrayEquals(before, ledger.readBytes())

    //  بازیابی از پشتیبان
    assertTrue(runBlocking { store.applyRestore(second) })
    assertNull(store.unreadable.value)
    assertEquals(second, open().data.value)
    val aside = dir.listFiles().orEmpty().firstOrNull { it.name.startsWith("shop-data.json.unreadable-") }
    assertNotNull("دفترِ خوانده‌نشده کنار رفت، پاک نشد", aside)
    assertArrayEquals(before, aside!!.readBytes())
  }
}
