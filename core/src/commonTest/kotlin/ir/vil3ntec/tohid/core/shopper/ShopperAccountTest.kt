package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.shopper.ShopperSamples.FakeHttp
import ir.vil3ntec.tohid.core.shopper.ShopperSamples.line
import ir.vil3ntec.tohid.core.shopper.ShopperSamples.product
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  سبد، ♥ و «فهرستِ خرید» همراهِ حساب ([AccountRules]) روی سرورِ جعلیِ هم‌قرارداد با `routes/storefront.js`
 *  (`lib/store-cart.js`، `lib/storefront-extras.js`، `lib/store-shopping-list.js`): همان ادغام، CAS، ۴۰۹ و سقف‌ها.
 */
class ShopperAccountTest {

  private val slug = "golestan"
  private val session = AccountSession("sc1." + "A".repeat(43), expiresAt = 10_000, customer = AccountCustomer("cus_1", "+93791114888"))
  private val all = AccountFeatures(favorites = true, cart = true)

  /** همان قاعده‌های سرور، کوچک: سبد و فهرست با نسخه، ♥ تازه‌ترین اول */
  private class Server {
    var cart = mutableListOf<Triple<String, Long, String>>()
    var cartV = 0L
    val favs = mutableListOf<String>()
    var list = mutableListOf<Pair<String, Long>>()
    var listV = 0L
    var down = false
    var unauthorized = false
    var tooManyLists = false
    val live = setOf("rice", "oil", "soap")
    val seen = mutableListOf<String>()

    private fun items(body: String?): List<JsonObject> =
      ShopperJson.parseToJsonElement(body ?: "{}").jsonObject["items"]?.jsonArray?.map { it.jsonObject }.orEmpty()

    private fun version(body: String?): Long = ShopperJson.parseToJsonElement(body ?: "{}").jsonObject["version"]?.jsonPrimitive?.longOrNull ?: -1

    private fun cartJson(): String = """{"version":$cartV,"lines":[${cart.joinToString(",") { (id, q, n) ->
      """{"productId":"$id","qtyMilli":$q,"note":"$n","available":${id in live},"name":"$id","unit":"دانه","priceMinor":100,"maxQtyMilli":null}"""
    }}]}"""

    private fun listJson(): String = """{"version":$listV,"lines":[${list.joinToString(",") { (id, q) ->
      """{"productId":"$id","qtyMilli":$q,"available":${id in live}}"""
    }}],"items":[${list.filter { it.first in live }.joinToString(",") { """{"id":"${it.first}","name":"${it.first}","priceMinor":100}""" }}]}"""

    private fun err(status: Int, code: String, extra: String = "") =
      ShopperResponse(status, """{"error":{"code":"$code","message":"x"$extra}}""")

    fun handle(r: ShopperRequest): ShopperResponse {
      if (down) throw IllegalStateException("offline")
      if (unauthorized || r.headers["X-Store-Session"].isNullOrEmpty()) return err(401, "session_required")
      val p = r.path.removePrefix("/api/store/golestan/account")
      seen += r.method + " " + p
      return when {
        r.method == "GET" && p == "/cart" -> ShopperResponse(200, """{"cart":${cartJson()}}""")
        r.method == "POST" && p == "/cart/merge" -> {
          for (x in items(r.body)) {
            val id = x["productId"]!!.jsonPrimitive.content
            val q = x["qtyMilli"]!!.jsonPrimitive.long
            val i = cart.indexOfFirst { it.first == id }
            if (i < 0) cart.add(Triple(id, q, "")) else if (q > cart[i].second) cart[i] = cart[i].copy(second = q)
          }
          cartV++
          ShopperResponse(200, """{"cart":${cartJson()}}""")
        }
        r.method == "PUT" && p == "/cart" -> {
          if (version(r.body) != cartV) return err(409, "cart_changed", ""","cart":${cartJson()}""")
          cart = items(r.body).map { x ->
            Triple(x["productId"]!!.jsonPrimitive.content, x["qtyMilli"]!!.jsonPrimitive.long, x["note"]?.jsonPrimitive?.content.orEmpty())
          }.toMutableList()
          cartV++
          ShopperResponse(200, """{"cart":${cartJson()}}""")
        }
        r.method == "GET" && p == "/favorites" ->
          ShopperResponse(200, """{"items":[${favs.filter { it in live }.joinToString(",") { """{"id":"$it","name":"$it","priceMinor":100}""" }}],"max":200}""")
        p.startsWith("/favorites/") -> {
          val id = p.removePrefix("/favorites/")
          if (r.method == "PUT") {
            if (id !in live) return err(404, "product_not_found")
            if (id !in favs) favs.add(0, id)
          } else favs.remove(id)
          ShopperResponse(200, "{}")
        }
        r.method == "GET" && p == "/shopping-list" -> ShopperResponse(200, """{"list":${listJson()}}""")
        r.method == "POST" && p == "/shopping-list/merge" -> {
          if (tooManyLists) return err(409, "too_many_lists")
          val before = list.toList()
          for (x in items(r.body)) {
            val id = x["productId"]!!.jsonPrimitive.content
            val q = x["qtyMilli"]!!.jsonPrimitive.long
            val i = list.indexOfFirst { it.first == id }
            if (i < 0) list.add(id to q) else if (q > list[i].second) list[i] = id to q
          }
          if (list.size > 100) {
            list = before.toMutableList()
            return err(409, "list_full", ""","list":${listJson()}""")
          }
          if (list != before) listV++
          ShopperResponse(200, """{"list":${listJson()}}""")
        }
        r.method == "PUT" && p == "/shopping-list" -> {
          val next = items(r.body).map { it["productId"]!!.jsonPrimitive.content to it["qtyMilli"]!!.jsonPrimitive.long }
          if (next == list) return ShopperResponse(200, """{"list":${listJson()}}""")
          if (version(r.body) != listV) return err(409, "list_changed", ""","list":${listJson()}""")
          list = next.toMutableList()
          listV++
          ShopperResponse(200, """{"list":${listJson()}}""")
        }
        else -> err(404, "not_found")
      }
    }
  }

  /** همان حلقهٔ `ModelAccount.kt`: یک کار در هر بار، حالِ اکنون پس از هر پاسخ */
  private fun run(api: ShopperApi, start: ShopperState, pulls: AccountPulls = AccountPulls.ALL, f: AccountFeatures = all): Pair<ShopperState, Boolean> {
    var s = AccountRules.begin(start, slug, session.expiresAt)
    var p = pulls
    repeat(60) {
      val step = AccountRules.next(s, slug, f, p) ?: return s to false
      val reply = AccountRules.send(api, session.session, slug, step)
      val out = AccountRules.apply(s, slug, step, reply, 50)
      s = out.state
      p = AccountRules.after(p, step, reply)
      if (out.signedOut) return s to true
      if (!out.go) return s to false
    }
    error("حلقه تمام نشد")
  }

  private fun withServer(srv: Server): ShopperApi = ShopperApi(FakeHttp { srv.handle(it) })

  private fun device(cart: List<CartLine> = emptyList(), favs: List<String> = emptyList(), list: List<ListItem> = emptyList()) = ShopperState(
    carts = if (cart.isEmpty()) emptyList() else listOf(Cart(slug, cart, 1)),
    favs = if (favs.isEmpty()) emptyMap() else mapOf(slug to favs),
    lists = if (list.isEmpty()) emptyMap() else mapOf(slug to list),
  )

  @Test fun firstSessionMergesEverythingThenTheAccountHasTheLastWord() {
    val srv = Server().apply {
      cart = mutableListOf(Triple("oil", 1000L, "تازه باشد")); cartV = 3
      favs += "soap"
      list = mutableListOf("oil" to 1000L); listV = 2
    }
    val api = withServer(srv)
    val (s, out) = run(api, device(cart = listOf(line("rice", 100, 2000), line("oil", 100, 500)), favs = listOf("oil", "rice"), list = listOf(ListItem("oil", 3000), ListItem("rice", 1000))))
    assertFalse(out)
    //  سبد: بیشینه، نه جمع؛ یادداشتِ وب ماند؛ سبدِ دستگاه همان سبدِ حساب
    assertEquals(listOf(Triple("oil", 1000L, "تازه باشد"), Triple("rice", 2000L, "")), srv.cart)
    assertEquals(listOf("oil" to 1000L, "rice" to 2000L), ShopperMemory.cartOf(s, slug).lines.map { it.productId to it.qtyMilli })
    //  ♥: کهنه‌ترین اول ⇒ ترتیبِ تازه‌ترین‌اولِ حساب همان ترتیبِ دستگاه، و ♥ ِ دیگرِ حساب هم آمد
    assertEquals(listOf("oil", "rice", "soap"), srv.favs)
    assertEquals(listOf("oil", "rice", "soap"), AccountRules.favsOf(s, slug))
    //  فهرست: ادغام (بیشینه، کالای تازه پشتِ فهرست)، سپس حساب حرفِ آخر
    assertEquals(listOf("oil" to 3000L, "rice" to 1000L), srv.list)
    assertEquals(listOf(ListItem("oil", 3000), ListItem("rice", 1000)), AccountRules.listFor(s, slug))
    assertTrue(srv.seen.containsAll(listOf("POST /cart/merge", "PUT /favorites/rice", "PUT /favorites/oil", "POST /shopping-list/merge", "GET /favorites")))
    //  دورِ دوم بی تغییر ⇒ فقط خواندن، هیچ نوشتنی
    srv.seen.clear()
    run(api, s)
    assertTrue(srv.seen.all { it.startsWith("GET ") }, srv.seen.toString())
  }

  @Test fun deviceChangesAreQueuedAndAConflictReappliesTheSameChangeOnTheNewList() {
    val srv = Server()
    val api = withServer(srv)
    var (s, _) = run(api, device(list = listOf(ListItem("rice", 1000), ListItem("oil", 1000))))
    assertEquals(1L, srv.listV)
    //  دستگاهِ دیگر مقدارِ روغن را عوض کرد؛ این دستگاه نسخهٔ ۱ را دیده و برنج را برمی‌دارد ⇒ ۴۰۹ ⇒ همان «برداشتن» روی فهرستِ تازه
    srv.list = mutableListOf("rice" to 1000L, "oil" to 4000L); srv.listV = 2
    s = AccountRules.removeFromList(s, slug, "rice")
    assertEquals(listOf(ListOp(rm = "rice")), s.sync[slug]!!.listOps)
    s = run(api, s, AccountPulls()).first
    assertEquals(listOf("oil" to 4000L), srv.list, "کارِ دستگاهِ دیگر گم نشد")
    assertEquals(listOf(ListItem("oil", 4000)), AccountRules.listFor(s, slug), "صف خالی ⇒ حساب حرفِ آخر")
    //  بی اینترنت ⇒ کار در صف می‌ماند؛ برگشتِ شبکه ⇒ رفت
    srv.down = true
    s = AccountRules.clearList(s, slug)
    s = run(api, s, AccountPulls()).first
    assertEquals(listOf(ListOp(all = emptyList())), s.sync[slug]!!.listOps)
    assertEquals(listOf("oil" to 4000L), srv.list)
    srv.down = false
    s = run(api, s, AccountPulls()).first
    assertEquals(emptyList(), srv.list)
    assertTrue(s.sync[slug]!!.listOps.isEmpty())
    //  «همه» کارهای پیشین را بی‌اثر می‌کند
    s = AccountRules.removeFromList(s, slug, "x")
    s = AccountRules.saveCart(s, slug, Cart(slug, listOf(line("soap", 100, 2000))))
    assertEquals(listOf(ListOp(all = listOf(ListItem("soap", 2000)))), s.sync[slug]!!.listOps)
  }

  @Test fun cartConflictReappliesTheDeviceChangeAndFullMergesNeverDropAnything() {
    val srv = Server()
    val api = withServer(srv)
    var (s, _) = run(api, device(cart = listOf(line("rice", 100, 1000))))
    assertEquals(1L, srv.cartV)
    //  دستگاهِ دیگر سبد را عوض کرد (نسخهٔ ۲: روغن)؛ این دستگاه «+» ِ برنج زد ⇒ ۴۰۹ ⇒ همان «+» روی سبدِ تازه (CHK-03،
    //  `CartSync.rebase`): کارِ هر دو دستگاه می‌ماند (پیش‌تر سبدِ این دستگاه کلِ سبد را جای‌گزین می‌کرد و روغن گم می‌شد)
    srv.cart = mutableListOf(Triple("oil", 1000L, "")); srv.cartV = 2
    s = ShopperMemory.withCart(s, CartRules.step(ShopperMemory.cartOf(s, slug), "rice", up = true, at = 9))
    s = run(api, s, AccountPulls()).first
    assertEquals(listOf(Triple("oil", 1000L, ""), Triple("rice", 2000L, "")), srv.cart)
    assertEquals(3L, srv.cartV)
    //  فهرستِ پر در ادغام ⇒ فهرستِ دستگاه جای فهرستِ حساب
    val full = Server().apply { list = (1..100).map { "f$it" to 1000L }.toMutableList(); listV = 5 }
    val s2 = run(withServer(full), device(list = listOf(ListItem("rice", 1000)))).first
    assertEquals(listOf("rice" to 1000L), full.list)
    assertEquals(listOf(ListItem("rice", 1000)), AccountRules.listFor(s2, slug))
  }

  @Test fun tooManyListsKeepsTheListOnThisDeviceAndUnauthorizedSignsOut() {
    val srv = Server().apply { tooManyLists = true }
    val api = withServer(srv)
    val (s, _) = run(api, device(list = listOf(ListItem("rice", 1000))))
    assertTrue(s.sync[slug]!!.listOff)
    assertEquals(listOf(ListItem("rice", 1000)), AccountRules.listFor(s, slug), "فهرستِ دستگاه دست نخورد")
    val after = AccountRules.removeFromList(s, slug, "rice")
    assertTrue(after.sync[slug]!!.listOps.isEmpty(), "فهرستِ بیرون از حساب در صف نمی‌رود")
    //  نشستِ باطل (۴۰۱) ⇒ بیرون، صف‌ها پاک؛ ♥ و فهرستِ دستگاه می‌مانند
    srv.unauthorized = true
    val withFav = AccountRules.toggleFav(after, slug, "oil")
    val (gone, signedOut) = run(api, withFav, AccountPulls())
    assertTrue(signedOut)
    assertTrue(gone.sync.isEmpty())
    assertEquals(listOf("oil"), AccountRules.favsOf(gone, slug))
    //  بی نشست (بی حالِ هم‌گامی) ♥ فقط روی دستگاه، بی کار
    assertNull(AccountRules.toggleFav(gone, slug, "rice").sync[slug])
  }

  @Test fun featuresOffMeansNoRequestsAndRejectedFavouritesAreDropped() {
    val srv = Server()
    val api = withServer(srv)
    run(api, device(cart = listOf(line("rice", 100, 1000)), favs = listOf("rice")), f = AccountFeatures())
    assertTrue(srv.seen.isEmpty(), "پرچمِ خاموش ⇒ هیچ درخواستی")
    //  ♥ ِ کالای نبوده ⇒ ۴۰۴ ⇒ کنار (صف گیر نمی‌کند)
    val (s, _) = run(api, device(favs = listOf("ghost", "rice")), f = AccountFeatures(favorites = true))
    assertEquals(listOf("rice"), srv.favs)
    assertTrue(s.sync[slug]!!.favOps.isEmpty())
    assertEquals(listOf("rice"), AccountRules.favsOf(s, slug), "حساب حرفِ آخر")
  }

  @Test fun listToCartUsesOnlyTodaysServerProductsAndCapsQuantity() {
    val items = listOf(product("rice", 12000, max = 3000), product("oil", 30000, stock = "out_of_stock"))
    val (c, gone) = AccountRules.listToCart(Cart(slug), listOf(ListItem("rice", 9000), ListItem("oil", 1000), ListItem("ghost", 1000)), items, 7)
    assertEquals(listOf(Triple("rice", 3000L, 12000L)), c.lines.map { Triple(it.productId, it.qtyMilli, it.priceMinor) })
    assertEquals(2, gone)
    //  فهرست فقط شناسه و مقدار؛ ردیفِ بد، تکراری یا بیش از سقف بیرون
    assertEquals(listOf(ListItem("a", 1)), AccountRules.cleanList(listOf(ListItem("a", 1), ListItem("a", 2), ListItem("bad id", 1), ListItem("b", 0), ListItem("c", 10_000_001))))
    assertEquals("0791•••888", AccountRules.maskPhone("+93791114888"))
  }

  @Test fun apiSendsTheSessionHeaderAndTheSameBodiesAsTheWeb() {
    val http = FakeHttp { ShopperResponse(200, """{"list":{"version":4,"lines":[{"productId":"rice","qtyMilli":2000,"available":true}],"items":[]},"session":"sc1.x","expiresAt":9,"customer":{"phone":"+93791114888"}}""") }
    val api = ShopperApi(http)
    assertEquals(4L, api.putShoppingList(slug, "sc1.s", 3, listOf(ListItem("rice", 2000))).valueOrNull()?.version)
    val put = http.seen.last()
    assertEquals("PUT", put.method)
    assertEquals("/api/store/golestan/account/shopping-list", put.path)
    assertEquals("sc1.s", put.headers["X-Store-Session"])
    val body = ShopperJson.parseToJsonElement(put.body!!).jsonObject
    assertEquals(3L, body["version"]!!.jsonPrimitive.long)
    assertEquals("""[{"productId":"rice","qtyMilli":2000}]""", (body["items"] as JsonArray).toString(), "بی قیمت، بی نام")
    api.mergeShoppingList(slug, "sc1.s", emptyList())
    assertNull(ShopperJson.parseToJsonElement(http.seen.last().body!!).jsonObject["version"], "ادغام نسخه نمی‌فرستد")
    val signed = api.signIn(slug, "v2.ticket", " احمد ").valueOrNull()
    assertNotNull(signed)
    assertEquals("sc1.x", signed.session)
    assertEquals("v2.ticket", http.seen.last().headers["X-Store-Ticket"])
    assertEquals("/api/store/golestan/account", http.seen.last().path)
    api.setFavorite(slug, "sc1.s", "rice oil", on = false)
    assertEquals("DELETE", http.seen.last().method)
    assertEquals("/api/store/golestan/account/favorites/rice%20oil", http.seen.last().path)
  }

  @Test fun oldSavedStateStillLoadsAndNewFieldsRoundTrip() {
    val old = ShopperJson.decodeFromString(ShopperState.serializer(), """{"carts":[],"lang":"ps"}""")
    assertEquals(emptyMap(), old.favs)
    assertEquals(emptyMap(), old.sync)
    val s = AccountRules.begin(device(favs = listOf("oil"), list = listOf(ListItem("rice", 1000))), slug, 77)
    val back = ShopperJson.decodeFromString(ShopperState.serializer(), ShopperJson.encodeToString(ShopperState.serializer(), s))
    assertEquals(s, back)
    assertEquals(listOf(FavOp("oil", true)), back.sync[slug]!!.favOps)
    assertFalse("sc1." in ShopperJson.encodeToString(ShopperState.serializer(), s), "نشست هرگز در حالِ دستگاه نیست")
    //  دکان نشانی‌اش را عوض کرد ⇒ ♥، فهرست و هم‌گامی همراهش
    val moved = ShopperMemory.moved(s, slug, "golestan-2")
    assertEquals(listOf("oil"), AccountRules.favsOf(moved, "golestan-2"))
    assertNotNull(moved.sync["golestan-2"])
  }
}
