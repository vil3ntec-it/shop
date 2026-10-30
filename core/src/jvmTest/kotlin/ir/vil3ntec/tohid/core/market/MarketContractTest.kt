package ir.vil3ntec.tohid.core.market

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/*
 *  همان `core/test-vectors/market-public-v<نسخه>.json` که `server/test/public-views.test.js`
 *  می‌خواند (S4): نمونه‌های نمای عمومیِ سرور با مدل‌های `market/` تجزیه می‌شوند و هر فیلدِ
 *  قرارداد در مدلِ کاتلین جا دارد. اگر سرور فیلدی بیفزاید و نسخه را بالا ببرد، این آزمون پروندهٔ
 *  تازه را نمی‌یابد تا مدل‌ها هم‌گام شوند.
 */
class MarketContractTest {
  private val root = MarketContract.json.parseToJsonElement(
    File("test-vectors/market-public-v${MarketContract.SCHEMA_VERSION}.json").readText()
  ).jsonObject
  private val samples = root.getValue("samples").jsonObject
  private fun sample(name: String): String = samples.getValue(name).toString()
  private fun each(name: String): List<String> = (samples.getValue(name) as JsonArray).map(JsonElement::toString)

  @Test fun sameVersionAsServer() {
    assertEquals(MarketContract.SCHEMA_VERSION, root.getValue("schemaVersion").jsonPrimitive.int)
  }

  /** هر فیلدِ هر شکلِ سرور در مدلِ کاتلینِ همان شکل هست (جز آن‌چه آگاهانه بیرون است). */
  @OptIn(ExperimentalSerializationApi::class)
  @Test fun everyContractFieldHasAHome() {
    val fields = root.getValue("fields").jsonObject
    val models: Map<String, Pair<KSerializer<*>, Set<String>>> = mapOf(
      "shop" to (PublicShop.serializer() to setOf("delivery")),
      "presence" to (PublicPresence.serializer() to emptySet()),
      "cardPresence" to (PublicPresence.serializer() to emptySet()),
      "product" to (PublicProduct.serializer() to emptySet()),
      "marketProduct" to (PublicProduct.serializer() to emptySet()),
      "searchProduct" to (PublicProduct.serializer() to emptySet()),
      "shopRef" to (PublicShopRef.serializer() to emptySet()),
      "stock" to (StockBadge.serializer() to emptySet()),
      "order" to (PublicOrder.serializer() to emptySet()),
      "orderLine" to (PublicOrderLine.serializer() to emptySet()),
      "orderEvent" to (PublicOrderEvent.serializer() to emptySet()),
      "delivery" to (PublicDelivery.serializer() to emptySet()),
    )
    for ((shape, model) in models) {
      val (ser, outside) = model
      val names = (0 until ser.descriptor.elementsCount).map(ser.descriptor::getElementName).toSet()
      val want = fields.getValue(shape).jsonArray.map { it.jsonPrimitive.content.removeSuffix("?") }
      for (f in want) if (f !in outside) assertTrue(f in names, "$shape.$f در ${ser.descriptor.serialName} نیست")
    }
  }

  @Test fun shopAndPresence() {
    val s = MarketContract.shop(sample("shop"))
    assertEquals("nan-o-namak", s.slug)
    assertEquals(5000L, s.deliveryFeeMinor)
    assertEquals(listOf("cod"), s.payment.methods)
    assertEquals(false, s.payment.online.available)
    assertTrue(s.fulfillment.delivery && s.fulfillment.pickup)
    assertEquals(listOf("خوراکی", "شوینده"), s.categories)
    val p = s.status!!
    assertEquals(PublicPresence.OPEN_ONLINE, p.badge)
    assertEquals(PublicPresence.ACCEPTING, p.orderAcceptance)
    assertEquals(listOf(listOf(480, 1200)), p.today)
    assertEquals(p, MarketContract.presence(sample("presence")))
    //  کارتِ بازار: همان حضور بی `mode`
    val card = samples.getValue("shopCard").jsonObject.getValue("status").toString()
    assertNull(MarketContract.presence(card).mode)
  }

  @Test fun products() {
    val p = MarketContract.product(sample("product"))
    assertEquals("rice", p.id)
    assertEquals(12000L, p.priceMinor)
    assertEquals(5000L, p.maxQtyMilli)
    assertEquals(StockBadge.UNVERIFIED, p.stock!!.status)
    assertNull(p.stock!!.checkedAt)
    val d = MarketContract.product(sample("productDetail"))
    assertEquals("برنجِ دانه‌بلند", d.description)
    assertEquals(listOf(ProductSpec("وزن", "۱ کیلو")), d.specs)
    val m = MarketContract.product(sample("marketProduct"))
    assertEquals(30000L, m.regularPriceMinor)
    assertEquals(1.5, m.shop!!.distanceKm)
    val q = MarketContract.product(sample("searchProduct"))
    assertNull(q.stock)
    assertNull(q.shop!!.distanceKm)
    assertEquals(2, MarketContract.products("[${sample("product")},${sample("searchProduct")}]").size)
  }

  @Test fun stockBadges() {
    val all = each("stock").map(MarketContract::stock)
    assertEquals(
      listOf(StockBadge.UNVERIFIED, StockBadge.UNVERIFIED, StockBadge.UNAVAILABLE, StockBadge.IN_STOCK,
        StockBadge.IN_WAREHOUSE, StockBadge.OUT_OF_STOCK),
      all.map { it.status },
    )
    assertEquals("stale", all[1].reason)
    assertEquals(30, all[4].prepMinutes)
    assertEquals(0L, all[5].sellableMilli)
    assertNull(all[3].sellableMilli)
  }

  @Test fun orderAndDelivery() {
    val o = MarketContract.order(sample("order"))
    assertEquals(42L, o.orderNo)
    assertEquals(41000L, o.totalMinor)
    assertEquals(listOf("rice", "oil"), o.lines.map { it.productId })
    assertEquals(2000L, o.lines[0].qtyMilli)
    assertEquals(listOf("new", "accepted"), o.timeline.map { it.status })
    assertEquals("شهرِ نو", o.zoneName)
    assertNull(o.scheduledFor)
    assertEquals(1761807200000L, o.slotEndsAt)
    assertEquals(PublicDelivery("picked_up", "shop_courier"), MarketContract.delivery(sample("delivery")))
  }

  /** سرورِ تازه‌تر با فیلد و وضعیتِ تازه برنامهٔ کهنه را نمی‌شکند. */
  @Test fun toleratesNewerServer() {
    val p = MarketContract.product(
      """{"id":"x","name":"ن","priceMinor":null,"photo":true,"brand":"تازه","stock":{"status":"reserved_soon","eta":3}}"""
    )
    assertNull(p.priceMinor)
    assertEquals("reserved_soon", p.stock!!.status)
  }
}
