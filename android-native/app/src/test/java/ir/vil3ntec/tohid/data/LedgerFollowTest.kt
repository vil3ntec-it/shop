package ir.vil3ntec.tohid.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 *  `LedgerFollow` — دفترِ حافظهٔ دو نمونهٔ `ShopStore` (صفحه و کارگر) روی یک فایل، بی `Context`:
 *  نوشتنِ یکی به دیگری می‌رسد، دیسکِ جلوتر از نو خوانده می‌شود و فقط آن وقت، دیسکِ ناخوانا هیچ
 *  نوشتنی نمی‌گیرد، و پس از عوضِ حساب نمونهٔ پیشین دیگر نمی‌نویسد. پرچمِ خاموش ⇒ هیچ‌کدام (همان `main`)؛
 *  پایه‌ای که دیگر نگه داشته نشده ⇒ `baseOf` تهی (ذخیره رد می‌شود).
 */
class LedgerFollowTest {

  private val dir: File = Files.createTempDirectory("ledger-follow").toFile()
  private val file = File(dir, "shop-data.json")
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

  @After fun after() { dir.deleteRecursively() }

  /** پرچمِ `shop.ledger_merge` ِ همهٔ نمونه‌های این آزمون */
  private var on = true

  private fun write(d: ShopData) = file.writeText(json.encodeToString(ShopData.serializer(), d))
  private fun disk(): ShopData? = runCatching { json.decodeFromString(ShopData.serializer(), file.readText()) }.getOrNull()

  /** همان `ShopStore.load`: نشان ⇐ خواندن ⇐ پایان */
  private fun open(): Pair<MutableStateFlow<ShopData>, LedgerFollow> {
    val flow = MutableStateFlow(ShopData())
    val f = LedgerFollow(file, flow, unreadable = { false }, readDisk = { disk() }, enabled = { on })
    val mark = f.mark()
    disk()?.let { f.replace(it) }
    f.loadedLocked(mark)
    return flow to f
  }

  private val p1 = Product(id = "f-p1", name = "برنج")
  private val p2 = Product(id = "f-p2", name = "شکر")

  @Test fun `نوشتنِ کارگر به صفحه می‌رسد و صفحه بی خواندنِ دوباره همان را دارد`() {
    write(ShopData(products = listOf(p1)))
    val (screen, ui) = open()
    val (_, worker) = open()
    val next = ShopData(products = listOf(p1, p2))
    write(next)
    worker.publishedLocked(next)
    assertSame(next, screen.value)
    assertSame("دیسک جلوتر نیست ⇒ همان دفترِ حافظه", next, ui.freshLocked())
  }

  @Test fun `نویسندهٔ بیرون از فرایند — مُهرِ فایل ⇒ از نو خوانده می‌شود و پایهٔ صفحه می‌ماند`() {
    write(ShopData(products = listOf(p1)))
    val (screen, ui) = open()
    val inHand = screen.value
    assertSame(inHand, ui.freshLocked())
    write(ShopData(products = listOf(p1, p2)))
    val fresh = ui.freshLocked()!!
    assertEquals(listOf("f-p1", "f-p2"), fresh.products.map { it.id })
    assertSame(fresh, screen.value)
    assertNotSame(inHand, fresh)
    assertSame("ذخیره‌ای که از دفترِ پیشین ساخته شد پایه‌اش را پیدا می‌کند", inHand, ui.baseOf(inHand.copy(sales = listOf(Sale(id = "f-s1")))))
  }

  @Test fun `دیسکِ جلوتری که خوانده نشد هیچ نوشتنی نمی‌گیرد`() {
    write(ShopData(products = listOf(p1)))
    val (_, ui) = open()
    file.writeText("{نیمه")
    assertNull(ui.freshLocked())
  }

  @Test fun `بارِ هم‌زمان با نوشتنِ همتا — پایانِ بار تازه‌ترین را می‌خواند`() {
    write(ShopData(products = listOf(p1)))
    val (_, worker) = open()
    val flow = MutableStateFlow(ShopData())
    val ui = LedgerFollow(file, flow, unreadable = { false }, readDisk = { disk() }, enabled = { on })
    val mark = ui.mark()
    val old = disk()!!
    val next = ShopData(products = listOf(p1, p2))
    write(next)
    worker.publishedLocked(next)                      // صفحه هنوز بار نشده: همتا نمی‌گیرد
    ui.replace(old)
    ui.loadedLocked(mark)
    assertEquals(listOf("f-p1", "f-p2"), flow.value.products.map { it.id })
  }

  @Test fun `پس از عوضِ حساب نمونهٔ پیشین نه می‌نویسد نه همتا می‌گیرد`() {
    write(ShopData(products = listOf(p1)))
    val (_, ui) = open()
    val (inWorker, worker) = open()
    val before = inWorker.value
    ui.movedLocked(stop = false, next = ShopData())
    assertFalse(worker.sameAccount())
    assertNull(worker.freshLocked())
    //  حسابِ تازه می‌نویسد: کارگرِ پیشین دفترِ حسابِ تازه را نمی‌گیرد
    val next = ShopData(products = listOf(p2))
    write(next)
    ui.publishedLocked(next)
    assertSame(before, inWorker.value)
  }

  /* ------------------- پرچمِ خاموش ⇒ همان رفتارِ `main` ------------------- */

  @Test fun `پرچمِ خاموش — نه نوشتنِ همتا گرفته می‌شود، نه دیسکِ جلوتر خوانده، نه عوضِ حساب نوشتن را می‌بندد`() {
    on = false
    write(ShopData(products = listOf(p1)))
    val (screen, ui) = open()
    val (_, worker) = open()
    val inHand = screen.value
    val next = ShopData(products = listOf(p1, p2))
    write(next)
    worker.publishedLocked(next)
    assertSame("دفترِ صفحه همان دفترِ خودش می‌ماند", inHand, screen.value)
    assertSame("دیسکِ جلوتر از نو خوانده نمی‌شود", inHand, ui.freshLocked())
    file.writeText("{نیمه")
    assertSame("دیسکِ ناخوانا هم مثلِ main نوشتن را نمی‌بندد", inHand, ui.freshLocked())
    ui.replace(ShopData(products = listOf(p2)))
    assertSame("دفترِ پیشین پایه نگه داشته نمی‌شود", screen.value, ui.baseOf(inHand.copy(sales = listOf(Sale(id = "f-s1")))))
    worker.movedLocked(stop = false, next = ShopData())
    assertTrue(ui.sameAccount())
  }

  @Test fun `پرچمِ روشن‌شده در میانه — دیسکی که در خاموشی جلو رفت همان لحظه از نو خوانده می‌شود`() {
    on = false
    write(ShopData(products = listOf(p1)))
    val (screen, ui) = open()
    val (_, worker) = open()
    val next = ShopData(products = listOf(p1, p2))
    write(next)
    worker.publishedLocked(next)
    on = true
    assertEquals(listOf("f-p1", "f-p2"), ui.freshLocked()!!.products.map { it.id })
    assertEquals(listOf("f-p1", "f-p2"), screen.value.products.map { it.id })
  }

  /* --------------- پایه‌ای که دیگر نگه داشته نشده (EARLIER = 2) --------------- */

  @Test fun `پس از سه جایگزینی، ذخیره از دفترِ بیرون‌رفته پایه ندارد؛ از دفترِ مانده دارد`() {
    write(ShopData(products = listOf(p1)))
    val (screen, ui) = open()
    val first = screen.value
    val sale = Sale(id = "f-s1")
    //  دو جایگزینی از بیرون: دفترِ نخست هنوز نگه داشته می‌شود
    ui.replace(ShopData(products = listOf(p1, p2)))
    ui.replace(ShopData(products = listOf(p2)))
    assertSame(first, ui.baseOf(first.copy(sales = listOf(sale))))
    //  سومی: دفترِ نخست بیرون رفت ⇒ ذخیره‌ای که از آن ساخته شد پایه ندارد (نه دفترِ همین حالا)
    val second = ui.baseOf(screen.value.copy(sales = listOf(sale)))
    ui.replace(ShopData(products = listOf(p1)))
    assertNull(ui.baseOf(first.copy(sales = listOf(sale))))
    //  ذخیره‌ای از دفترِ همین حالا یا دفترِ مانده هنوز پایه دارد
    assertSame(screen.value, ui.baseOf(screen.value.copy(sales = listOf(sale))))
    assertSame(second, ui.baseOf(second!!.copy(sales = listOf(sale))))
    //  عوضِ حساب همه را پاک می‌کند: دیگر «بیرون‌رفته»ای نیست
    ui.reset(ShopData(products = listOf(p2)))
    assertSame(screen.value, ui.baseOf(first.copy(sales = listOf(sale))))
  }
}
