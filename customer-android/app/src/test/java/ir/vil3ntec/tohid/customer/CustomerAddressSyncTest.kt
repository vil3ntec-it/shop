package ir.vil3ntec.tohid.customer

import ir.vil3ntec.tohid.core.shopper.AddressInput
import ir.vil3ntec.tohid.core.shopper.AddressMedia
import ir.vil3ntec.tohid.core.shopper.AddressMediaGate
import ir.vil3ntec.tohid.core.shopper.AddressSync
import ir.vil3ntec.tohid.core.shopper.ChatPhoto
import ir.vil3ntec.tohid.core.shopper.GeoPin
import ir.vil3ntec.tohid.core.shopper.PlaceText
import ir.vil3ntec.tohid.core.shopper.ShopperApi
import ir.vil3ntec.tohid.core.shopper.ShopperHttp
import ir.vil3ntec.tohid.core.shopper.ShopperJson
import ir.vil3ntec.tohid.core.shopper.ShopperKeyValue
import ir.vil3ntec.tohid.core.shopper.ShopperLang
import ir.vil3ntec.tohid.core.shopper.ShopperMemory
import ir.vil3ntec.tohid.core.shopper.ShopperRequest
import ir.vil3ntec.tohid.core.shopper.ShopperResponse
import ir.vil3ntec.tohid.core.shopper.ShopperText
import ir.vil3ntec.tohid.customer.ui.CustomerModel
import ir.vil3ntec.tohid.customer.ui.Route
import ir.vil3ntec.tohid.customer.ui.VoiceDevice
import ir.vil3ntec.tohid.customer.ui.accountSendCode
import ir.vil3ntec.tohid.customer.ui.accountSignIn
import ir.vil3ntec.tohid.customer.ui.accountSignOut
import ir.vil3ntec.tohid.customer.ui.closeAddressMedia
import ir.vil3ntec.tohid.customer.ui.deleteAddressMedia
import ir.vil3ntec.tohid.customer.ui.mapFeature
import ir.vil3ntec.tohid.customer.ui.mediaGate
import ir.vil3ntec.tohid.customer.ui.openAddressMedia
import ir.vil3ntec.tohid.customer.ui.sendAddressVoice
import ir.vil3ntec.tohid.customer.ui.sendGatePhoto
import ir.vil3ntec.tohid.customer.ui.signedIn
import ir.vil3ntec.tohid.customer.ui.startAddressVoice
import ir.vil3ntec.tohid.customer.ui.syncAddresses
import ir.vil3ntec.tohid.customer.ui.tickAddressVoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  CRM-09 در برنامهٔ خرید (بی اندروید): نشانی‌های همین گوشی پس از ورود به دفترِ حساب می‌روند (و شناسهٔ سرور می‌گیرند)،
 *  ویرایش و پاک کردن به سرور می‌رسد، عکسِ دروازه و یادداشتِ صوتی فقط برای نشانیِ حساب و فقط با پرچمِ دکان، و خروج نشانی‌های
 *  حساب را از گوشی برمی‌دارد. CHK-08: «انتخاب روی نقشه» فقط با `features.mapPin` ِ درست.
 */
class CustomerAddressSyncTest {

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

  /** میکروفونِ ساختگی: ضبط بی درنگ، بایت‌های ثابت */
  private class FakeVoice : VoiceDevice {
    var cancelled = 0
    override val canRecord = true
    override fun record(done: (String?) -> Unit) = done(null)
    override fun recorded(): Long = 2048
    override fun finish(): ByteArray = byteArrayOf(0, 0, 0, 0x18, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())
    override fun cancel() { cancelled++ }
    override fun canPlay(mime: String?) = true
    override fun play(bytes: ByteArray, ended: () -> Unit) = true
    override fun pause() = Unit
    override fun resume() = Unit
    override fun stopPlay() = Unit
    override fun position(): Long = 0
    override fun length(): Long = 0
  }

  private val store = """{"store":{"slug":"golestan","title":"گلستان","contactPhone":"0700000001",
    "status":{"badge":"open_online","connection":"confirmed","orderAcceptance":"accepting"},"fulfillment":{"delivery":true,"pickup":true},"deliveryFeeMinor":0,"minOrderMinor":0,
    "categories":[],"delivery":{"zoned":false,"allowAsap":true,"scheduling":{"delivery":false,"pickup":false},"zones":[]}},
    "verification":{"available":true},"market":{"available":false},
    "features":{"webAccount":true,"addressMedia":true,"mapPin":{"tiles":"/tiles/{z}/{x}/{y}.png","attribution":"OpenStreetMap","copyright":"https://www.openstreetmap.org/copyright"}}}"""

  private val seen = mutableListOf<ShopperRequest>()
  private val rows = LinkedHashMap<String, AddressInput>()
  private val media = HashSet<String>()
  private var n = 0
  private var now = 1_000_000L

  private fun view(id: String, a: AddressInput) =
    """{"id":"$id","label":"${a.label}","text":"${a.text}","landmark":"${a.landmark}","lat":${a.lat},"lng":${a.lng},"updatedAt":1,""" +
      """"media":{"gatePhoto":${"$id/gate-photo" in media},"voiceNote":${"$id/voice-note" in media}}}"""

  private val http = ShopperHttp { r ->
    seen += r
    val p = r.path
    val book = "/api/store/golestan/account/addresses"
    val id = p.removePrefix(book).removePrefix("/").substringBefore('/')
    when {
      p == "/api/store/golestan" -> ShopperResponse(200, store)
      p.startsWith("/api/store/golestan/products") -> ShopperResponse(200, """{"items":[],"nextCursor":null}""")
      p.endsWith("/verify/start") -> ShopperResponse(200, """{"sent":true,"expiresAt":999}""")
      p.endsWith("/verify/check") -> ShopperResponse(200, """{"email":"c700000002@test.local","ticket":"v2.ticket","expiresAt":99999999999}""")
      p == "/api/store/golestan/account" && r.method == "POST" ->
        ShopperResponse(201, """{"session":"sc1.s","expiresAt":99999999999,"customer":{"id":"c1","email":"c700000002@test.local","phone":null,"name":""}}""")
      p.startsWith(book) && r.headers["X-Store-Session"] != "sc1.s" -> ShopperResponse(401, """{"error":{"code":"session_required"}}""")
      p == book && r.method == "GET" ->
        ShopperResponse(200, """{"addresses":[${rows.entries.joinToString(",") { view(it.key, it.value) }}],"mediaLimits":{"photoMax":1048576,"voiceMax":786432}}""")
      p == book && r.method == "POST" -> {
        val sid = "01SRV" + (++n)
        rows[sid] = ShopperJson.decodeFromString(AddressInput.serializer(), r.body!!)
        ShopperResponse(201, """{"address":${view(sid, rows.getValue(sid))}}""")
      }
      id !in rows -> ShopperResponse(404, """{"error":{"code":"address_not_found"}}""")
      "/media/" in p -> {
        val key = id + "/" + p.substringAfterLast('/')
        if (r.method == "PUT") { media += key; ShopperResponse(201, """{"media":{"kind":"x","mime":"image/jpeg","size":3,"createdAt":1},"replaced":false}""") }
        else { media -= key; ShopperResponse(200, """{"deleted":true}""") }
      }
      r.method == "PUT" -> {
        rows[id] = ShopperJson.decodeFromString(AddressInput.serializer(), r.body!!)
        ShopperResponse(200, """{"address":${view(id, rows.getValue(id))}}""")
      }
      r.method == "DELETE" -> { rows.remove(id); ShopperResponse(200, """{"deleted":true}""") }
      else -> ShopperResponse(404, """{"error":{"code":"not_found"}}""")
    }
  }

  private val kv = MapKv()
  private val voice = FakeVoice()

  private fun model() = CustomerModel(
    ShopperApi(http) { ShopperText.lang.code }, kv, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined, { now }, debounceMs = 0,
    secureRandom = { ByteArray(it) { 7 } }, chatWaitMs = 0, secrets = MapKv(), voice = voice,
  )

  private fun calls() = seen.filter { "/account/addresses" in it.path }.map { it.method + " " + it.path.substringAfter("/account/addresses") }

  private fun saveNew(m: CustomerModel, label: String, text: String) {
    m.go(Route.AddressEdit(null))
    m.book.label = label
    m.book.text = text
    m.saveAddress()
  }

  @Test fun deviceAddressesJoinTheAccountBookAndMediaFollowTheAccountAddress() {
    val m = model()
    m.go(Route.Shop("golestan"))
    assertEquals("/tiles/{z}/{x}/{y}.png", m.mapFeature()?.tiles)
    saveNew(m, "خانه", "کوچهٔ دوم")
    val local = m.saved.addresses.single()
    assertTrue(AddressSync.deviceOnly(local.id))
    assertTrue("بی نشست هیچ درِ نشانیِ حساب زده نمی‌شود", calls().isEmpty())
    assertEquals(AddressMediaGate.SIGN_IN, m.mediaGate(local.id))

    m.account.email = "c700000002@test.local"
    m.accountSendCode("golestan")
    m.account.code = "123456"
    m.accountSignIn("golestan")
    assertNotNull(m.signedIn())
    assertEquals(listOf("GET ", "POST "), calls())
    val synced = m.saved.addresses.single()
    assertEquals("01SRV1", synced.id)
    assertEquals("کوچهٔ دوم", rows.getValue("01SRV1").text)
    assertTrue(ShopperMemory.load(kv).addresses.none { AddressSync.deviceOnly(it.id) })

    //  ویرایش ⇒ PUT؛ خواندنِ بعدی همان را می‌آورد
    m.go(Route.AddressEdit("01SRV1"))
    m.book.text = "کوچهٔ سوم"
    m.saveAddress()
    assertEquals("PUT /01SRV1", calls().first { it.startsWith("PUT") })
    assertEquals("کوچهٔ سوم", rows.getValue("01SRV1").text)
    assertFalse(m.saved.addresses.single().dirty)

    //  عکسِ دروازه و یادداشتِ صوتی فقط برای نشانیِ حساب
    m.go(Route.AddressEdit("01SRV1"))
    m.openAddressMedia("01SRV1")
    assertEquals(AddressMediaGate.READY, m.mediaGate("01SRV1"))
    val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    m.sendGatePhoto("01SRV1", ChatPhoto(jpeg, null, 10, 10))
    val put = seen.last()
    assertEquals("/api/store/golestan/account/addresses/01SRV1/media/gate-photo", put.path)
    assertArrayEquals(jpeg, put.bytes)
    assertTrue(m.saved.addresses.single().media!!.gatePhoto)
    assertEquals(PlaceText.amSent, m.account.places.mediaNote)

    m.startAddressVoice("01SRV1")
    assertTrue(m.account.places.recording)
    now += AddressMedia.VOICE_MAX_MS
    m.tickAddressVoice()
    assertFalse("۶۰ ثانیه ⇒ ایستاد", m.account.places.recording)
    assertNotNull(m.account.places.take)
    m.sendAddressVoice("01SRV1")
    assertEquals("PUT /01SRV1/media/voice-note", calls().last())
    assertTrue(m.saved.addresses.single().media!!.voiceNote)
    assertNull(m.account.places.take)
    m.deleteAddressMedia("01SRV1", AddressMedia.PHOTO)
    assertEquals("DELETE /01SRV1/media/gate-photo", calls().last())
    assertFalse(m.saved.addresses.single().media!!.gatePhoto)
    //  بستنِ برگه هنگامِ ضبط ⇒ میکروفون بسته
    m.startAddressVoice("01SRV1")
    m.closeAddressMedia()
    assertEquals(1, voice.cancelled)
    assertFalse(m.account.places.recording)

    //  پاک کردن ⇒ DELETE؛ خروج ⇒ نشانی‌های حساب از گوشی، فقط‌دستگاهی می‌ماند
    m.back()
    val before = calls().size
    m.deleteAddress("01SRV1")
    assertEquals(listOf("DELETE /01SRV1", "GET "), calls().drop(before))
    assertTrue(rows.isEmpty())
    rows["01WEB"] = AddressInput("دفتر", "از وب")
    m.go(Route.Addresses)
    m.syncAddresses()   //  همان `LaunchedEffect` ِ «نشانی‌های من»
    assertEquals(listOf("01WEB"), m.saved.addresses.map { it.id })
    m.accountSignOut()
    saveNew(m, "مادر", "کوچهٔ اول")
    assertEquals(listOf("مادر"), m.saved.addresses.map { it.label })
    assertTrue(ShopperMemory.load(kv).addressGone.isEmpty())
    assertEquals(AddressMediaGate.SIGN_IN, m.mediaGate(m.saved.addresses.single().id))
  }

  @Test fun mapButtonOnlyWithASafeTileTemplate() {
    val m = model()
    assertNull(m.mapFeature())
    m.go(Route.Shop("golestan"))
    assertNotNull(m.mapFeature())
    m.go(Route.AddressEdit(null))
    m.book.mapPin(GeoPin(34.531234, 69.172345))
    assertEquals(GeoPin(34.531234, 69.172345), m.book.pin)
  }
}
