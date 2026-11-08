package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.market.PublicOrderLine
import ir.vil3ntec.tohid.core.market.PublicProduct
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** MKT-06: «وقتِ دوباره خریدن؟» — خریدِ همین دستگاه، چرخهٔ سرور، موعد، «بعداً/دیگر نه» و سبد با قیمتِ امروزِ سرور */
class ShopperRefillTest {
  private val day = Refills.DAY_MS
  private val t0 = 1_800_000_000_000L
  private fun line(id: String, qty: Long = 2000) = PublicOrderLine(id, "کالای $id", "بسته", qty, 10000, 20000)
  private fun prod(id: String, days: Int? = null, price: Long? = 12000) = PublicProduct(id, "کالای تازهٔ $id", priceMinor = price, consumptionDays = days)

  @Test fun recordKeepsLatestPurchasePerProductAndTheKnownCycle() {
    var list = Refills.record(emptyList(), "golestan", listOf(line("tea"), line("rice")), t0)
    list = Refills.withCycles(list, "golestan", listOf("tea", "rice"), listOf(prod("tea", 30), prod("rice")), t0)
    assertEquals(30, list.first { it.productId == "tea" }.days)
    assertNull(list.first { it.productId == "rice" }.days)
    //  خریدِ دوباره ⇒ زمانِ تازه، چرخهٔ پیشین می‌ماند، «دیگر نه» پاک
    list = Refills.mute(list, "golestan", t0 + 40 * day)
    list = Refills.record(list, "golestan", listOf(line("tea", 3000)), t0 + 41 * day)
    val tea = list.first { it.productId == "tea" }
    assertEquals(listOf<Long?>(t0 + 41 * day, 3000L, 30L), listOf(tea.at, tea.qtyMilli, tea.days?.toLong()))
    assertFalse(tea.muted)
    assertEquals(2, list.size)
  }

  @Test fun dueOneDayBeforeTheCycleEndsAndOnlyWithAServerCycle() {
    var list = Refills.record(emptyList(), "golestan", listOf(line("tea"), line("rice"), line("salt")), t0)
    list = Refills.withCycles(list, "golestan", listOf("tea", "rice", "salt"), listOf(prod("tea", 30), prod("salt", 1)), t0)
    assertEquals(emptyList(), Refills.cards(list, t0 + 28 * day, emptyMap()).flatMap { it.items }.map { it.productId } - "salt")
    val cards = Refills.cards(list, t0 + 29 * day, mapOf("golestan" to "گلستان"))
    assertEquals(1, cards.size)
    assertEquals("گلستان", cards[0].storeTitle)
    //  کهنه‌ترین موعد اول؛ «برنج» بی چرخه هرگز
    assertEquals(listOf("salt", "tea"), cards[0].items.map { it.productId })
    //  سرور چرخه را برداشت یا کالا از ویترین رفت ⇒ بی کارت
    val off = Refills.withCycles(list, "golestan", listOf("tea", "salt"), emptyList(), t0 + 29 * day)
    assertTrue(Refills.cards(off, t0 + 29 * day, emptyMap()).isEmpty())
    //  چرخهٔ نادرست (بیرون از ۱ تا ۳۶۵) پذیرفته نمی‌شود
    assertNull(Refills.withCycles(list, "golestan", listOf("tea"), listOf(prod("tea", 400)), t0).first { it.productId == "tea" }.days)
  }

  @Test fun staleAsksTheServerAtMostEverySixHoursPerShop() {
    var list = Refills.record(emptyList(), "a-shop", listOf(line("tea")), t0) + Refills.record(emptyList(), "b-shop", listOf(line("oil")), t0)
    assertEquals(mapOf("a-shop" to listOf("tea"), "b-shop" to listOf("oil")), Refills.stale(list, t0))
    list = Refills.withCycles(list, "a-shop", listOf("tea"), listOf(prod("tea", 7)), t0)
    assertEquals(mapOf("b-shop" to listOf("oil")), Refills.stale(list, t0 + Refills.CHECK_MS - 1))
    assertEquals(setOf("a-shop", "b-shop"), Refills.stale(list, t0 + Refills.CHECK_MS).keys)
  }

  @Test fun laterTomorrowAndNotAgainUntilTheNextPurchase() {
    var list = Refills.record(emptyList(), "golestan", listOf(line("tea")), t0)
    list = Refills.withCycles(list, "golestan", listOf("tea"), listOf(prod("tea", 7)), t0)
    val due = t0 + 6 * day
    list = Refills.snooze(list, "golestan", due)
    assertTrue(Refills.cards(list, due + day - 1, emptyMap()).isEmpty())
    assertEquals(1, Refills.cards(list, due + day, emptyMap()).size)
    list = Refills.mute(list, "golestan", due + day)
    assertTrue(Refills.cards(list, due + 60 * day, emptyMap()).isEmpty())
  }

  @Test fun cartUsesTodaysServerPriceNeverTheOldOrder() {
    var list = Refills.record(emptyList(), "golestan", listOf(line("tea", 2000), line("oil", 1000), line("gone")), t0)
    list = Refills.withCycles(list, "golestan", listOf("tea", "oil", "gone"), listOf(prod("tea", 7), prod("oil", 7), prod("gone", 7)), t0)
    val card = Refills.cards(list, t0 + 7 * day, emptyMap()).single()
    val today = listOf(
      prod("tea", 7, price = 15500),
      prod("oil", 7, price = null),   //  دیگر قیمت ندارد ⇒ ناسفارشی
    )
    val (cart, gone) = Refills.cartFrom(Cart("golestan"), card.items, today, t0 + 7 * day)
    assertEquals(2, gone)
    val l = cart.lines.single()
    assertEquals(listOf("tea", "کالای تازهٔ tea"), listOf(l.productId, l.name))
    assertEquals(15500L, l.priceMinor, "قیمتِ امروزِ سرور، نه ۱۰۰ افغانیِ سفارشِ پیشین")
    assertEquals(2000L, l.qtyMilli)
    //  کالایی که از پیش در سبد است دست نمی‌خورد
    val before = Cart("golestan", listOf(CartLine("tea", "چای", "بسته", 15500, 5000)))
    assertEquals(5000L, Refills.cartFrom(before, card.items, today, t0).first.lines.single().qtyMilli)
  }

  @Test fun savedStateRoundTripsAndOldStateHasNoPurchases() {
    val list = Refills.record(emptyList(), "golestan", listOf(line("tea")), t0)
    val s = ShopperState(bought = list)
    val back = ShopperJson.decodeFromString(ShopperState.serializer(), ShopperJson.encodeToString(ShopperState.serializer(), s))
    assertEquals(list, back.bought)
    assertEquals(emptyList(), ShopperJson.decodeFromString(ShopperState.serializer(), "{}").bought)
    assertEquals("new-slug", ShopperMemory.moved(s, "golestan", "new-slug").bought.single().slug)
    //  همان کلیدی که ویترین می‌فرستد
    val p = ShopperJson.decodeFromString(PublicProduct.serializer(), """{"id":"tea","name":"چای","priceMinor":5000,"consumptionDays":30}""")
    assertEquals(30, p.consumptionDays)
  }
}
