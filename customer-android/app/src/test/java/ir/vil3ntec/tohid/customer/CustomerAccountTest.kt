package ir.vil3ntec.tohid.customer

import ir.vil3ntec.tohid.core.shopper.AccountRules
import ir.vil3ntec.tohid.core.shopper.ShopperApi
import ir.vil3ntec.tohid.core.shopper.ShopperHttp
import ir.vil3ntec.tohid.core.shopper.ShopperKeyValue
import ir.vil3ntec.tohid.core.shopper.ShopperLang
import ir.vil3ntec.tohid.core.shopper.ShopperMemory
import ir.vil3ntec.tohid.core.shopper.ShopperRequest
import ir.vil3ntec.tohid.core.shopper.ShopperResponse
import ir.vil3ntec.tohid.core.shopper.ShopperText
import ir.vil3ntec.tohid.customer.ui.AccountBox
import ir.vil3ntec.tohid.customer.ui.CustomerModel
import ir.vil3ntec.tohid.customer.ui.Route
import ir.vil3ntec.tohid.customer.ui.accountSendCode
import ir.vil3ntec.tohid.customer.ui.accountSignIn
import ir.vil3ntec.tohid.customer.ui.accountSignOut
import ir.vil3ntec.tohid.customer.ui.isFav
import ir.vil3ntec.tohid.customer.ui.listLines
import ir.vil3ntec.tohid.customer.ui.saveCartAsList
import ir.vil3ntec.tohid.customer.ui.signedIn
import ir.vil3ntec.tohid.customer.ui.toggleFav
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  WEB-11/CHK-01 در برنامهٔ خرید (بی اندروید): بی نشست ♥ و فهرست فقط روی دستگاه؛ ورود با کدِ سرور ⇒ ادغامِ همان
 *  درهای وب (سبد، ♥، فهرست)؛ نشست فقط در نگه‌دارِ رمزشده (نه در حالِ دستگاه)؛ اجرای دوباره همان نشست؛ خروج ⇒ پاک.
 */
class CustomerAccountTest {

  @After fun reset() {
    ShopperText.lang = ShopperLang.FA
  }

  private class MapKv : ShopperKeyValue {
    val map = HashMap<String, String>()
    override fun get(key: String): String? = map[key]
    override fun put(key: String, value: String?) {
      if (value == null) map.remove(key) else map[key] = value
    }
  }

  private val store = """{"store":{"slug":"golestan","title":"گلستان","contactPhone":"0700000001",
    "status":{"badge":"open_online","connection":"confirmed","orderAcceptance":"accepting"},"fulfillment":{"delivery":false,"pickup":true},"deliveryFeeMinor":0,"minOrderMinor":0,
    "categories":[],"delivery":{"zoned":false,"allowAsap":true,"scheduling":{"delivery":false,"pickup":false},"zones":[]}},
    "verification":{"available":true},"market":{"available":false},"features":{"favorites":true,"webAccount":true,"accountCart":true}}"""
  private val rice = """{"id":"rice","name":"برنج","unit":"کیلو","priceMinor":12000,"photo":false}"""

  private val seen = mutableListOf<ShopperRequest>()
  private val favs = mutableListOf<String>()
  private var cartJson = """{"version":0,"lines":[]}"""
  private var listJson = """{"version":0,"lines":[],"items":[]}"""
  private var version = 0

  private fun echo(body: String?, list: Boolean): String {
    val items = Json.parseToJsonElement(body ?: "{}").jsonObject["items"]?.jsonArray.orEmpty().joinToString(",") { x ->
      val id = x.jsonObject["productId"]!!.jsonPrimitive.content
      val q = x.jsonObject["qtyMilli"]!!.jsonPrimitive.content
      if (list) """{"productId":"$id","qtyMilli":$q,"available":true}"""
      else """{"productId":"$id","qtyMilli":$q,"note":"","available":true,"name":"برنج","unit":"کیلو","priceMinor":12000}"""
    }
    version++
    return if (list) """{"version":$version,"lines":[$items],"items":[$rice]}""" else """{"version":$version,"lines":[$items]}"""
  }

  private val http = ShopperHttp { r ->
    seen += r
    val p = r.path
    val acct = "/api/store/golestan/account"
    when {
      p == "/api/store/golestan" -> ShopperResponse(200, store)
      p.startsWith("/api/store/golestan/products") -> ShopperResponse(200, """{"items":[$rice],"nextCursor":null}""")
      p.endsWith("/verify/start") -> ShopperResponse(200, """{"sent":true,"expiresAt":999}""")
      p.endsWith("/verify/check") -> ShopperResponse(200, """{"phone":"+93700000002","ticket":"v2.ticket","expiresAt":99999999999}""")
      p == acct && r.method == "POST" ->
        ShopperResponse(201, """{"session":"sc1.SECRET-SESSION","expiresAt":99999999999,"customer":{"id":"c1","phone":"+93700000002","name":""}}""")
      p == "$acct/logout" -> ShopperResponse(200, """{"signedOut":true}""")
      p == "$acct/cart/merge" || (p == "$acct/cart" && r.method == "PUT") -> {
        cartJson = echo(r.body, list = false)
        ShopperResponse(200, """{"cart":$cartJson}""")
      }
      p == "$acct/cart" -> ShopperResponse(200, """{"cart":$cartJson}""")
      p.startsWith("$acct/favorites/") -> {
        val id = p.substringAfterLast('/')
        if (r.method == "PUT") { if (id !in favs) favs.add(0, id) } else favs.remove(id)
        ShopperResponse(200, "{}")
      }
      p == "$acct/favorites" -> ShopperResponse(200, """{"items":[${if ("rice" in favs) rice else ""}],"max":200}""")
      p == "$acct/shopping-list/merge" || (p == "$acct/shopping-list" && r.method == "PUT") -> {
        listJson = echo(r.body, list = true)
        ShopperResponse(200, """{"list":$listJson}""")
      }
      p == "$acct/shopping-list" -> ShopperResponse(200, """{"list":$listJson}""")
      else -> ShopperResponse(404, """{"error":{"code":"not_found"}}""")
    }
  }

  private val kv = MapKv()
  private val secrets = MapKv()

  private fun model() = CustomerModel(
    ShopperApi(http) { ShopperText.lang.code }, kv, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined, { 1_000_000L }, debounceMs = 0,
    secureRandom = { ByteArray(it) { 7 } }, chatWaitMs = 0, secrets = secrets,
  )

  private fun accountCalls() = seen.filter { "/account" in it.path }.map { it.method + " " + it.path.substringAfter("/account") }

  @Test fun favouritesAndListStayLocalUntilSignInThenFollowTheAccount() {
    val m = model()
    m.go(Route.Shop("golestan"))
    val p = m.items.single()
    m.toggleFav("golestan", p)
    m.add("golestan", p)
    m.saveCartAsList("golestan")
    assertTrue(m.isFav("golestan", "rice"))
    assertEquals(listOf("برنج"), m.listLines("golestan").map { it.second })
    assertTrue("بی نشست هیچ درِ حساب زده نمی‌شود", accountCalls().isEmpty())

    m.go(Route.CartPage("golestan"))
    m.account.phone = "0700000002"
    m.accountSendCode("golestan")
    assertTrue(m.account.codeSent)
    m.account.code = "۱۲۳۴۵۶"
    m.accountSignIn("golestan")
    assertNotNull(m.signedIn())
    val calls = accountCalls()
    assertEquals("POST ", calls.first())
    assertTrue(calls.toString(), calls.containsAll(listOf("POST /cart/merge", "PUT /favorites/rice", "POST /shopping-list/merge", "GET /favorites", "GET /shopping-list")))
    assertEquals(listOf("rice"), favs)
    assertTrue(listJson.contains("\"rice\""))
    //  کدِ فارسی ⇒ رقمِ لاتین؛ نشست فقط در نگه‌دارِ امن، هرگز در حالِ دستگاه
    assertTrue(seen.first { it.path.endsWith("/verify/check") }.body!!.contains("123456"))
    assertNotNull(secrets.map[AccountBox.KEY])
    assertFalse(kv.map.values.any { "SECRET-SESSION" in it })
    assertEquals("sc1.SECRET-SESSION", seen.last { "/account/" in it.path }.headers["X-Store-Session"])

    //  اجرای دوباره: همان نشست از نگه‌دارِ امن
    val again = model()
    assertEquals("sc1.SECRET-SESSION", again.signedIn()?.session)
    //  خروج ⇒ نشست و صف‌ها پاک؛ ♥ و فهرست روی همین دستگاه می‌مانند
    again.go(Route.Shop("golestan"))
    again.accountSignOut()
    assertNull(again.signedIn())
    assertNull(secrets.map[AccountBox.KEY])
    assertTrue(ShopperMemory.load(kv).sync.isEmpty())
    assertEquals(listOf("rice"), AccountRules.favsOf(ShopperMemory.load(kv), "golestan"))
    assertEquals("POST /logout", accountCalls().last())
  }
}
