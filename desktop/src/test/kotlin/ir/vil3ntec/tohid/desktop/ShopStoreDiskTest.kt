package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 *  F0-01 — دفترِ کامپیوتر واقعاً به دیسک می‌رسد.
 *
 *  `ShopStore.writeToDisk` با `tmp.renameTo(file)` می‌نوشت و نتیجه را
 *  نمی‌دید. روی ویندوز `renameTo` فایلِ موجود را جایگزین نمی‌کند، پس از
 *  ذخیرهٔ دوم به بعد هیچ چیزی به دیسک نمی‌رسید و با بستنِ برنامه می‌رفت.
 *
 *  ⚠️ آزمونِ اول همان سناریوی ویندوز است و فقط روی ویندوز قرمز می‌شد —
 *  برای همین در CI روی `windows-latest` هم می‌دود (`tohid-desktop.yml`).
 *  آزمونِ دوم همان ریشه (نتیجهٔ نادیده‌گرفته‌شده) را روی هر سیستمی نشان
 *  می‌دهد: جای فایلِ دفتر را پوشه‌ای پر گرفته و هیچ جابه‌جایی‌ای نمی‌گیرد.
 */
class ShopStoreDiskTest {

  private val ledger get() = File(DesktopContext.filesDir, "shop-data.json")

  private fun clean() {
    ledger.deleteRecursively()
    File(ledger.parentFile, "${ledger.name}.tmp").delete()
  }

  @Before fun before() = clean()
  @After fun after() = clean()

  private fun open(): ShopStore = ShopStore(DesktopContext).also { runBlocking { it.load() } }

  private val first = ShopData(products = listOf(Product(id = "f001-p1", name = "برنج", salePrice = 120.0)))
  private val second = first.copy(
    products = first.products + Product(id = "f001-p2", name = "شکر", salePrice = 90.0),
  )

  @Test fun `دو بار ذخیره، بستن و باز کردن — همان دفترِ دوم`() {
    val store = open()
    assertTrue(runBlocking { store.save(first) })
    assertTrue(runBlocking { store.save(second) })

    //  «بستن و باز کردن»: نمونهٔ تازه، فقط از روی دیسک
    assertEquals(second, open().data.value)
    assertFalse("فایلِ کناری نباید بماند", File(ledger.parentFile, "${ledger.name}.tmp").exists())
  }

  @Test fun `ذخیره‌ای که به دیسک نرسید «ثبت شد» نمی‌گیرد و صفحه همان دیسک را نشان می‌دهد`() {
    //  جای فایلِ دفتر را یک پوشهٔ پر گرفته: روی هیچ سیستمی جایگزین نمی‌شود
    ledger.mkdirs()
    File(ledger, "blocker").writeText("x")

    val store = open()
    val ok = runBlocking { store.save(first) }

    assertEquals(
      "آنچه صفحه نشان می‌دهد باید همان باشد که پس از بستن و باز کردن می‌ماند",
      open().data.value, store.data.value,
    )
    assertFalse("ذخیره‌ای که به دیسک نرسید نباید «ثبت شد» بگیرد", ok)
  }
}
