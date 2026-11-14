package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.shopper.ShopperSamples.line
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** نگه‌داریِ روی دستگاه و نشانیِ دکان. */
class ShopperMemoryTest {

  private class MapKv : ShopperKeyValue {
    val map = HashMap<String, String>()
    override fun get(key: String): String? = map[key]
    override fun put(key: String, value: String?) {
      if (value == null) map.remove(key) else map[key] = value
    }
  }

  @Test fun stateRoundTripsAndCorruptDataFallsBackToEmpty() {
    val kv = MapKv()
    assertEquals(ShopperState(), ShopperMemory.load(kv))
    val s = ShopperState(
      carts = listOf(Cart("golestan", listOf(line("rice", 12050, 2000)), 5)),
      orders = listOf(SavedOrder("golestan", "v2.t", 12, 27100, 6, "گلستان")),
      ticket = CustomerTicket("c700000002@test.local", "v2.x", 99), name = "احمد", lang = "ps", theme = "dark",
      keys = mapOf("golestan" to KeyMemo("{}", "app-1")),
    )
    ShopperMemory.save(kv, s)
    assertEquals(s, ShopperMemory.load(kv))
    kv.map[ShopperMemory.KEY] = "{not json"
    assertEquals(ShopperState(), ShopperMemory.load(kv), "دادهٔ خراب ⇒ حالتِ تهی، نه کرش")
    kv.map[ShopperMemory.KEY] = """{"carts":[],"futureField":{"a":1},"lang":"en"}"""
    assertEquals("en", ShopperMemory.load(kv).lang, "فیلدِ نسخهٔ تازه‌تر نادیده")
  }

  @Test fun listsAreBoundedNewestFirstAndEmptyCartsDropped() {
    var s = ShopperState()
    repeat(40) { s = ShopperMemory.withOrder(s, SavedOrder("a", "t$it", it.toLong(), 1, it.toLong())) }
    assertEquals(ShopperMemory.MAX_ORDERS, s.orders.size)
    assertEquals("t39", s.orders.first().token)
    s = ShopperMemory.withOrder(s, SavedOrder("a", "t20", 20, 1, 99))
    assertEquals(1, s.orders.count { it.token == "t20" }, "همان توکن دو بار نمی‌آید")
    repeat(15) { s = ShopperMemory.withRecent(s, RecentShop("shop$it", "دکان", it.toLong())) }
    assertEquals(ShopperMemory.MAX_RECENT, s.recent.size)
    s = ShopperMemory.withCart(s, Cart("x", listOf(line("a", 100, 1000))))
    assertEquals(listOf("x"), s.carts.map { it.slug })
    s = ShopperMemory.withCart(s, Cart("x"))
    assertTrue(s.carts.isEmpty())
    assertEquals(Cart("nope"), ShopperMemory.cartOf(s, "nope"))
  }

  @Test fun movedShopCarriesCartOrdersAndKeysToTheNewAddress() {
    val s = ShopperState(
      carts = listOf(Cart("old", listOf(line("a", 100, 1000)))),
      orders = listOf(SavedOrder("old", "t", 1, 1, 1)),
      recent = listOf(RecentShop("old", "دکان", 1)),
      keys = mapOf("old" to KeyMemo("{}", "app-1")),
    )
    val m = ShopperMemory.moved(s, "old", "new-slug")
    assertEquals("new-slug", m.carts.single().slug)
    assertEquals("new-slug", m.orders.single().slug)
    assertEquals("new-slug", m.recent.single().slug)
    assertEquals(setOf("new-slug"), m.keys.keys)
  }

  @Test fun shopAddressAcceptsSlugLinkAndQrText() {
    assertEquals("golestan", ShopAddress.slugOf(" Golestan "))
    assertEquals("golestan", ShopAddress.slugOf("https://api.vill3n.top/store/golestan"))
    assertEquals("golestan", ShopAddress.slugOf("https://api.vill3n.top/store/golestan#/p/rice"))
    assertEquals("nan-o-namak", ShopAddress.slugOf("vill3n.top/store/nan-o-namak?c=qr1"))
    assertEquals("golestan", ShopAddress.slugOf("@golestan"))
    assertNull(ShopAddress.slugOf("ab"))
    assertNull(ShopAddress.slugOf("-bad-"))
    assertNull(ShopAddress.slugOf("دکان"))
    assertNull(ShopAddress.slugOf(""))
  }
}
