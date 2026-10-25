package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.sync.v1.OpQueue
import ir.vil3ntec.tohid.sync.v1.SyncOp
import ir.vil3ntec.tohid.sync.v1.SyncStateV1
import ir.vil3ntec.tohid.sync.v1.SyncV1Engine
import ir.vil3ntec.tohid.sync.v1.Ulid
import ir.vil3ntec.tohid.sync.v3.SyncChoice
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 *  بازتولید: دو نمونهٔ `ShopStore` روی یک پوشه — صفحه و کارگرِ پس‌زمینه.
 */
class StaleSaveTest {

  private val ctx = DesktopContext
  private val engine get() = SyncV1Engine.of(ctx)
  private lateinit var account: String

  private fun clean() {
    ctx.filesDir.listFiles().orEmpty().filter { it.name.startsWith("shop-data.json") }.forEach { it.deleteRecursively() }
  }

  @Before fun before() {
    clean()
    SyncChoice.set(ctx, SyncChoice.V1, 0)
    account = "stale-${System.nanoTime()}"
    SyncStateV1.setCurrentAccount(ctx, account)
    engine.rebind()
  }

  @After fun after() {
    SyncStateV1.setCurrentAccount(ctx, "")
    engine.rebind()
    clean()
  }

  private fun open(): ShopStore = ShopStore(ctx).also { runBlocking { it.load() } }
  private fun queued(): List<String> = OpQueue(ctx, account).all().map { "${it.table}:${it.rowId}:${it.type}" }

  private val p1 = Product(id = "stale-p1", name = "برنج", salePrice = 120.0)
  private val seed = ShopData(products = listOf(p1))
  private val sale = Sale(id = "stale-s1", finalTotal = 120.0, createdAt = 1_800_000_000_000L)

  private fun op(table: String, id: String, type: String, vararg f: Pair<String, Any>) = SyncOp(
    Ulid.next(), System.currentTimeMillis(), table, id, type,
    buildJsonObject { for ((k, v) in f) put(k, if (v is Number) JsonPrimitive(v) else JsonPrimitive(v.toString())) },
  )

  private val fromOther = listOf(
    op("products", "stale-p2", SyncOp.INSERT, "name" to "شکر", "salePrice" to 90),
    op("products", "stale-p1", SyncOp.UPDATE, "name" to "برنج اعلا"),
  )

  @Test fun `بازتولید — ذخیرهٔ صفحه پس از Pull ِ کارگر`() {
    val ui = open()
    assertTrue(runBlocking { ui.save(seed) })
    assertEquals(emptyList<String>(), queued())
    val screen = ui.data.value
    val w = open()
    engine.absorbRemote(fromOther, { w.data.value }, { w.applyFromServerNow(it) }, 7)
    assertTrue(runBlocking { ui.save(screen.copy(sales = screen.sales + sale)) })
    assertEquals(listOf("sales:stale-s1:insert"), queued())
  }

  @Test fun `بازتولید — Pull ِ کارگر فروشِ صفحه را از دیسک پاک می‌کند`() {
    val ui = open()
    assertTrue(runBlocking { ui.save(seed) })
    val w = open()
    assertTrue(runBlocking { ui.save(ui.data.value.copy(sales = listOf(sale))) })
    engine.absorbRemote(fromOther, { w.data.value }, { w.applyFromServerNow(it) }, 7)
    assertEquals(listOf("stale-s1"), open().data.value.sales.map { it.id })
  }

  @Test fun `بازتولید — صفحه پس از نوشتنِ کارگر تازه نمی‌شود`() {
    val ui = open()
    assertTrue(runBlocking { ui.save(seed) })
    val w = open()
    engine.absorbRemote(fromOther, { w.data.value }, { w.applyFromServerNow(it) }, 7)
    assertEquals(listOf("stale-p1", "stale-p2"), ui.data.value.products.map { it.id })
  }
}
