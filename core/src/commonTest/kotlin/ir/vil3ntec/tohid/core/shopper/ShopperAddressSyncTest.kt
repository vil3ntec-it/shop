package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.shopper.ShopperSamples.FakeHttp
import ir.vil3ntec.tohid.core.shopper.ShopperSamples.fail
import ir.vil3ntec.tohid.core.shopper.ShopperSamples.line
import ir.vil3ntec.tohid.core.shopper.ShopperSamples.ok
import ir.vil3ntec.tohid.core.shopper.ShopperSamples.page
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 *  CRM-09 — دفترِ نشانیِ برنامهٔ خرید ⇄ دفترِ نشانیِ حساب ([AddressSync]) و درهای عکسِ دروازه/یادداشتِ صوتی، روی یک سرورِ
 *  ساختگی با همان قاعده‌های `lib/store-customers.js` (سقفِ ۱۰، ۴۰۴ ِ نشانیِ دیگر) و `lib/store-address-media.js`.
 */
class ShopperAddressSyncTest {

  @AfterTest fun reset() {
    ShopperText.lang = ShopperLang.FA
  }

  /** دفترِ حسابِ ساختگی: شناسهٔ سرور بی پیشوند؛ هر درخواست باید نشستِ حساب داشته باشد */
  private class Book(val mediaOn: Boolean = true) {
    val rows = LinkedHashMap<String, SavedAddress>()
    val media = HashSet<String>()
    var n = 0
    var down = false

    fun add(a: SavedAddress) {
      rows[a.id] = a
    }

    private fun view(a: SavedAddress): String {
      val m = if (mediaOn) ""","media":{"gatePhoto":${"${a.id}/gate_photo" in media},"voiceNote":${"${a.id}/voice_note" in media}}""" else ""
      val pin = if (a.lat != null) ""","lat":${a.lat},"lng":${a.lng}""" else ""","lat":null,"lng":null"""
      return """{"id":"${a.id}","label":"${a.label}","text":"${a.text}","landmark":"${a.landmark}"$pin,"area":"","updatedAt":${a.updatedAt}$m}"""
    }

    private fun input(r: ShopperRequest, id: String): SavedAddress {
      val i = ShopperJson.decodeFromString(AddressInput.serializer(), r.body!!)
      return SavedAddress(id, i.label, i.text, i.landmark, i.lat, i.lng, (++n).toLong())
    }

    val http = FakeHttp { r ->
      val tail = r.path.substringAfter("/account/addresses")
      val id = tail.removePrefix("/").substringBefore('/')
      when {
        down -> throw IllegalStateException("بی اینترنت")
        r.headers["X-Store-Session"] != "sc1.s" -> fail(401, "session_required")
        r.method == "GET" && tail.isEmpty() -> ok(
          """{"addresses":[${rows.values.joinToString(",") { view(it) }}]""" +
            (if (mediaOn) ""","mediaLimits":{"photoMax":1048576,"voiceMax":786432}}""" else "}"),
        )
        r.method == "POST" -> if (rows.size >= 10) fail(409, "too_many_addresses") else {
          val a = input(r, "01SRV" + (++n).toString().padStart(3, '0'))
          rows[a.id] = a
          ok("""{"address":${view(a)}}""", 201)
        }
        id !in rows -> fail(404, "address_not_found")
        r.method == "PUT" && "/media/" !in tail -> {
          val a = input(r, id)
          rows[id] = a
          ok("""{"address":${view(a)}}""")
        }
        r.method == "DELETE" && "/media/" !in tail -> {
          rows.remove(id)
          ok("""{"deleted":true}""")
        }
        else -> fail(404, "media_not_found")
      }
    }
  }

  private fun dev(n: Int, label: String, text: String = "کوچهٔ $n", lat: Double? = null, lng: Double? = null) =
    SavedAddress("adr-0$n", label, text, "", lat, lng, n.toLong())

  private fun srv(id: String, label: String, text: String = "", lat: Double? = null, lng: Double? = null) = SavedAddress(id, label, text, "", lat, lng, 1)

  /** یک دورِ کامل، همان ترتیبِ `ModelAddressSync` ِ برنامه */
  private fun round(api: ShopperApi, s0: ShopperState): Pair<ShopperState, AddressRound> {
    var s = s0
    var r = AddressRound()
    repeat(AddressSync.MAX_STEPS) {
      val step = AddressSync.next(s, r) ?: return s to r
      val out = AddressSync.apply(s, step, AddressSync.send(api, "golestan", "sc1.s", step), r)
      s = out.first
      r = out.second
    }
    return s to r
  }

  @Test fun firstRoundUploadsDeviceAddressesOnceAndAdoptsServerIds() {
    val book = Book()
    book.add(srv("01WEB", "دفتر", "از وب"))
    book.add(srv("01TWIN", "مادر", "کوچهٔ 2"))   //  همان نشانیِ دستگاه که پیش‌تر رسیده بود
    val api = ShopperApi(book.http)
    val (s, r) = round(api, ShopperState(addresses = listOf(dev(1, "خانه", lat = 34.5, lng = 69.1), dev(2, "مادر"))))
    assertFalse(r.stopped)
    assertEquals(listOf("GET", "POST"), book.http.seen.map { it.method }, "همانند دوباره فرستاده نمی‌شود")
    assertTrue(book.http.seen.all { it.headers["X-Store-Session"] == "sc1.s" })
    assertEquals(listOf("خانه", "مادر", "دفتر"), s.addresses.map { it.label })
    assertTrue(s.addresses.none { AddressSync.deviceOnly(it.id) }, s.addresses.toString())
    assertEquals(setOf("01WEB", "01TWIN") + s.addresses.first().id, book.rows.keys)
    assertEquals(GeoPin(34.5, 69.1), book.rows.getValue(s.addresses.first().id).pin)
    assertEquals(AddressMediaLimits(1048576, 786432), r.limits)
    assertEquals(0, AddressSync.leftOnDevice(s))
    //  دورِ دوم: هیچ نوشتنی (فقط خواندن)
    book.http.seen.clear()
    round(api, s)
    assertEquals(listOf("GET"), book.http.seen.map { it.method })
  }

  @Test fun editsAndDeletesOfAccountAddressesReachTheServer() {
    val book = Book()
    book.add(srv("01A", "خانه", "کوچه"))
    book.add(srv("01B", "دفتر", "سرک"))
    val api = ShopperApi(book.http)
    var s = ShopperState(addresses = listOf(srv("01A", "خانه", "کوچه"), srv("01B", "دفتر", "سرک")))
    val saved = assertIs<AddressBook.Saved.Ok>(AddressBook.save(s.addresses, "01A", AddressInput("خانه", "کوچهٔ تازه"), 9) { "adr-x" })
    assertTrue(saved.address.dirty, "ویرایشِ نشانیِ حساب ⇒ «هنوز نرفته»")
    s = AddressSync.written(s, saved.list)
    s = AddressSync.written(s, AddressBook.remove(s.addresses, "01B"))
    assertEquals(listOf("01B"), s.addressGone)
    val (after, _) = round(api, s)
    assertEquals(listOf("DELETE", "PUT", "GET"), book.http.seen.map { it.method })
    assertEquals(listOf("01A"), book.rows.keys.toList())
    assertEquals("کوچهٔ تازه", book.rows.getValue("01A").text)
    assertEquals(listOf("01A"), after.addresses.map { it.id })
    assertFalse(after.addresses.single().dirty)
    assertTrue(after.addressGone.isEmpty())
    //  پاک کردنِ نشانیِ فقط‌دستگاهی صفِ DELETE نمی‌سازد
    assertTrue(AddressSync.written(ShopperState(addresses = listOf(dev(1, "x"))), emptyList()).addressGone.isEmpty())
  }

  @Test fun serverIsTheSourceOfTruthAfterThePull() {
    val book = Book()
    book.add(srv("01NEW", "مغازه", "از گوشیِ دیگر"))
    book.media += "01NEW/gate_photo"
    val (s, _) = round(ShopperApi(book.http), ShopperState(addresses = listOf(srv("01OLD", "پاک‌شده در وب"))))
    assertEquals(listOf("01NEW"), s.addresses.map { it.id }, "پاک‌شده در جای دیگر از گوشی هم می‌رود؛ تازه می‌آید")
    assertEquals(AddressMediaFlags(gatePhoto = true, voiceNote = false), s.addresses.single().media)
  }

  @Test fun answersApplyToTheStateOfNowNotTheStateThatWasSent() {
    val sent = dev(1, "خانه")
    val server = srv("01S", "خانه", "کوچهٔ 1")
    val r = AddressRound(pulled = true)
    val created = AddressReply(true, address = server)
    //  در میانه ویرایش شد ⇒ شناسهٔ سرور با متنِ تازه و «نرفته»
    val edited = ShopperState(addresses = listOf(sent.copy(text = "متنِ تازه")))
    val a = AddressSync.apply(edited, AddressStep.Add(sent), created, r).first.addresses.single()
    assertEquals("01S", a.id)
    assertEquals("متنِ تازه", a.text)
    assertTrue(a.dirty)
    //  در میانه پاک شد ⇒ نشانیِ تازهٔ سرور هم در صفِ DELETE
    val gone = AddressSync.apply(ShopperState(), AddressStep.Add(sent), created, r).first
    assertEquals(listOf("01S"), gone.addressGone)
    //  PUT ِ موفق ولی در میانه دوباره عوض شد ⇒ همان «نرفته» برای گامِ بعد
    val pushed = server.copy(dirty = true)
    val moved = ShopperState(addresses = listOf(pushed.copy(label = "خانهٔ نو")))
    val p = AddressSync.apply(moved, AddressStep.Push(pushed), AddressReply(true, address = server), r).first.addresses.single()
    assertEquals("خانهٔ نو", p.label)
    assertTrue(p.dirty)
    assertIs<AddressStep.Push>(AddressSync.next(ShopperState(addresses = listOf(p)), r))
  }

  @Test fun renamedIdsLetAnOpenEditorFollowTheSameAddress() {
    val sent = dev(1, "خانه")
    val r = AddressRound(pulled = true)
    val before = listOf(sent, srv("01B", "دفتر"))
    val adopted = AddressSync.apply(ShopperState(addresses = before), AddressStep.Add(sent), AddressReply(true, address = srv("01S", "خانه", "کوچهٔ 1")), r).first
    assertEquals(mapOf("adr-01" to "01S"), AddressSync.renamed(before, adopted.addresses))
    val dirtyLost = ShopperState(addresses = listOf(srv("01B", "دفتر").copy(dirty = true)))
    val moved = AddressSync.apply(dirtyLost, AddressStep.Push(dirtyLost.addresses[0]), AddressReply(false, error = ShopperError(404, "address_not_found")), r).first
    assertEquals(mapOf("01B" to "adr-01B"), AddressSync.renamed(dirtyLost.addresses, moved.addresses))
    assertEquals(emptyMap(), AddressSync.renamed(before, before))
  }

  @Test fun editOfAnAddressDeletedElsewhereIsKeptAndSentAgain() {
    val book = Book()
    val api = ShopperApi(book.http)
    val (s, _) = round(api, ShopperState(addresses = listOf(srv("01GONE", "خانه", "کوچه").copy(dirty = true))))
    assertEquals(listOf("PUT", "GET", "POST"), book.http.seen.map { it.method })
    assertEquals(listOf("خانه"), s.addresses.map { it.label })
    assertFalse(AddressSync.deviceOnly(s.addresses.single().id))
    assertEquals("کوچه", book.rows.values.single().text)
  }

  @Test fun offlineOrDeadSessionStopsTheRoundAndLosesNothing() {
    val book = Book()
    book.down = true
    val start = ShopperState(addresses = listOf(dev(1, "خانه")), addressGone = listOf("01X"))
    val (s, r) = round(ShopperApi(book.http), start)
    assertTrue(r.stopped)
    assertTrue(r.error!!.network)
    assertEquals(start, s)
    val (s2, r2) = round(ShopperApi(Book().http.also { it.reply = { fail(401, "session_required") } }), start)
    assertTrue(r2.stopped)
    assertEquals(start, s2)
    //  DELETE ِ نشانیِ نبوده (۴۰۴) صف را می‌بندد، گیر نمی‌کند
    val (s3, _) = round(ShopperApi(Book().http), ShopperState(addressGone = listOf("01X")))
    assertTrue(s3.addressGone.isEmpty())
  }

  @Test fun fullAccountBookLeavesAddressesOnThisDevice() {
    val book = Book()
    repeat(10) { book.add(srv("01F$it", "نشانی $it")) }
    val (s, r) = round(ShopperApi(book.http), ShopperState(addresses = listOf(dev(1, "خانه"))))
    assertEquals(listOf("GET"), book.http.seen.map { it.method }, "پر ⇒ POST ِ بیهوده نه")
    assertEquals(1, AddressSync.leftOnDevice(s))
    assertEquals(11, s.addresses.size)
    assertFalse(r.stopped)
    //  سرور پر شد در میانه (۴۰۹) ⇒ همین دور دیگر POST نه
    val after = AddressSync.apply(ShopperState(addresses = listOf(dev(1, "خانه"))), AddressStep.Add(dev(1, "خانه")),
      AddressReply(false, error = ShopperError(409, "too_many_addresses")), AddressRound(pulled = true))
    assertTrue(after.second.full)
    assertNull(AddressSync.next(after.first, after.second))
    assertTrue(PlaceText.addressesLeft(1).isNotBlank())
  }

  @Test fun signOutDropsAccountAddressesAndKeepsDeviceOnes() {
    val s = ShopperState(addresses = listOf(srv("01A", "خانه"), dev(1, "مادر")), addressGone = listOf("01B"))
    val out = AccountRules.signedOut(s)
    assertEquals(listOf("adr-01"), out.addresses.map { it.id })
    assertTrue(out.addressGone.isEmpty())
  }

  @Test fun checkoutSendsSavedIdAndSessionOnlyForAnAccountAddress() {
    val p = page()
    val c = Cart("golestan", listOf(line("rice", 25000, 1000)))
    val f = CheckoutForm(name = "احمد", fulfillment = "delivery", addressText = "کوچه", addressId = "01A")
    val req = CheckoutRules.request(p, c, f, "+93700000002", "fa", null)
    assertEquals("01A", req.address?.savedId)
    assertEquals("sc1.s", AddressSync.checkoutSession(req, "sc1.s"))
    val local = CheckoutRules.request(p, c, f.copy(addressId = "adr-01"), "+93700000002", "fa", null)
    assertNull(local.address?.savedId, "نشانیِ فقط‌دستگاهی در سرور نیست")
    assertNull(AddressSync.checkoutSession(local, "sc1.s"), "بی نشانیِ حساب، نشست با ثبت نمی‌رود")
    assertFalse("savedId" in CheckoutKeys.json(local), "بدنهٔ پیشین همان می‌ماند")
    val http = FakeHttp { fail(409, "store_paused") }
    val api = ShopperApi(http)
    api.checkout("golestan", req, "k-1", "t.1", AddressSync.checkoutSession(req, "sc1.s"))
    assertEquals("sc1.s", http.seen.last().headers["X-Store-Session"])
    val sentAddress = Json.parseToJsonElement(http.seen.last().body!!).jsonObject["address"] as JsonObject
    assertEquals("\"01A\"", sentAddress["savedId"].toString())
    api.checkout("golestan", local, "k-2", "t.1", AddressSync.checkoutSession(local, "sc1.s"))
    assertNull(http.seen.last().headers["X-Store-Session"])
  }

  @Test fun addressMediaDoorsCarrySessionKindAndRawBytes() {
    val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)
    val audio = byteArrayOf(0, 0, 0, 0x18, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())
    val http = FakeHttp { r ->
      when {
        r.method == "PUT" -> ok("""{"media":{"kind":"gatePhoto","mime":"image/jpeg","size":7,"createdAt":5},"replaced":false}""", 201)
        r.method == "GET" && r.path.contains("/media/") -> ShopperResponse(200, "", mapOf("Content-Type" to "image/jpeg"), jpeg)
        r.method == "DELETE" -> ok("""{"deleted":true}""")
        else -> fail(404, "address_media_disabled")
      }
    }
    val api = ShopperApi(http)
    val put = assertIs<ShopperResult.Ok<AddressMediaPut>>(api.putAddressMedia("golestan", "sc1.s", "01/A", AddressMedia.PHOTO, jpeg)).value
    assertEquals("image/jpeg", put.media?.mime)
    val r = http.seen.last()
    assertEquals("/api/store/golestan/account/addresses/01%2FA/media/gate-photo", r.path)
    assertEquals("image/jpeg", r.headers["Content-Type"])
    assertEquals("sc1.s", r.headers["X-Store-Session"])
    assertContentEquals(jpeg, r.bytes)
    assertNull(r.body)
    api.putAddressMedia("golestan", "sc1.s", "01A", AddressMedia.VOICE, audio)
    assertEquals("application/octet-stream", http.seen.last().headers["Content-Type"], "نوعِ صدا را سرور از بایت می‌شناسد")
    assertContentEquals(jpeg, assertIs<ShopperResult.Ok<ByteArray>>(api.addressMediaBytes("golestan", "sc1.s", "01A", AddressMedia.PHOTO)).value)
    assertTrue(http.seen.last().binary)
    assertTrue(assertIs<ShopperResult.Ok<Boolean>>(api.removeAddressMedia("golestan", "sc1.s", "01A", AddressMedia.VOICE)).value)
    assertEquals("/api/store/golestan/account/addresses/01A/media/voice-note", http.seen.last().path)
    val off = assertIs<ShopperResult.Fail>(ShopperApi(FakeHttp { fail(404, "address_media_disabled") }).addressBook("golestan", "sc1.s"))
    assertEquals(PlaceText.mediaError(off.error), PlaceText.mediaError(ShopperError(404, "address_media_disabled")))
    assertEquals(PlaceText.amTooLarge, PlaceText.mediaError(ShopperError(413, "too_large")))
  }

  @Test fun mediaGateRecordingLimitsAndLocalMarks() {
    assertEquals(AddressMediaGate.HIDDEN, AddressMedia.gate(false, true, "01A"))
    assertEquals(AddressMediaGate.SIGN_IN, AddressMedia.gate(true, false, "01A"))
    assertEquals(AddressMediaGate.SAVE_FIRST, AddressMedia.gate(true, true, "adr-1"))
    assertEquals(AddressMediaGate.SAVE_FIRST, AddressMedia.gate(true, true, null))
    assertEquals(AddressMediaGate.READY, AddressMedia.gate(true, true, "01A"))
    assertFalse(AddressMedia.stop(59_000, 1000, 786432))
    assertTrue(AddressMedia.stop(60_000, 1000, 786432), "۶۰ ثانیه، همان وب")
    assertTrue(AddressMedia.stop(1000, 786432 - 1000, 786432), "نزدیکِ سقفِ حجم")
    assertFalse(AddressMedia.stop(1000, 1000, 0), "سقفِ نامعلوم ⇒ فقط زمان")
    assertTrue(AddressMedia.tooBig(786433, 786432))
    assertFalse(AddressMedia.tooBig(786433, 0))
    val s = ShopperState(addresses = listOf(srv("01A", "خانه"), srv("01B", "دفتر")))
    val m = AddressMedia.mark(AddressMedia.mark(s, "01A", AddressMedia.PHOTO, true), "01A", AddressMedia.VOICE, true)
    assertEquals(AddressMediaFlags(gatePhoto = true, voiceNote = true), m.addresses[0].media)
    assertNull(m.addresses[1].media)
    assertEquals(AddressMediaFlags(gatePhoto = false, voiceNote = true), AddressMedia.mark(m, "01A", AddressMedia.PHOTO, false).addresses[0].media)
    //  ویرایشِ نشانی رسانه را نگه می‌دارد
    val kept = assertIs<AddressBook.Saved.Ok>(AddressBook.save(m.addresses, "01A", AddressInput("خانه", "x"), 3) { "adr-y" }).address
    assertEquals(m.addresses[0].media, kept.media)
  }
}
