package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.shopper.ShopperSamples.FakeHttp
import ir.vil3ntec.tohid.core.shopper.ShopperSamples.fail
import ir.vil3ntec.tohid.core.shopper.ShopperSamples.ok
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 *  CHAT-14 ِ برنامهٔ خرید (همان `store/chat-acts.js`): خواندنِ نرمِ پاسخِ سرور، تصمیمِ «پاسخ/ویرایش/سنجاق»، بدنه و مسیرِ
 *  درخواست‌ها، و پیوندِ هر نوشته به رشته‌ای که در آن نوشته شد (دستهٔ ۴۲ ِ `store/chat.js`)؛ و کارتِ بازپرداخت (NTF-08).
 */
class ShopperChatActsTest {

  @AfterTest fun resetLang() {
    ShopperText.lang = ShopperLang.FA
  }

  private val now = 1_000_000L
  private val conv = ChatConversation("sch_1")
  private fun own(id: String, until: Long? = now + 60_000, kind: String = "text") =
    ChatMessage(id, seq = 2, rev = 2, sender = "customer", kind = kind, body = "سلام", mine = true, clientId = "c-abcdefgh$id", editUntil = until)
  private val theirs = ChatMessage("m1", seq = 1, rev = 1, sender = "shop", body = "بفرمایید")
  private fun thread(vararg m: ChatMessage, c: ChatConversation = conv) = ChatThread(c).merge(ChatChanges(c, m.toList(), cursor = 9))

  @Test fun lenientParsingOldServerHasNoPinsNoEditUntilAndNoVoice() {
    val old = ShopperJson.decodeFromString(ChatConversation.serializer(), """{"id":"sch_1","status":"open"}""")
    assertTrue(old.pinned.isEmpty(), "سرورِ کهنه ⇒ بی سنجاق")
    val pinned = ShopperJson.decodeFromString(ChatConversation.serializer(),
      """{"id":"sch_1","pinned":[{"messageId":"m2","seq":2,"pinnedAt":55,"extra":1}],"unknown":{"x":1}}""")
    assertEquals(listOf(ChatPin("m2", 2, 55)), pinned.pinned)
    val m = ShopperJson.decodeFromString(ChatMessage.serializer(), """{"id":"m2","editUntil":123,"replyTo":"m1"}""")
    assertEquals(123L, m.editUntil)
    assertEquals("m1", m.replyTo)
    assertNull(ShopperJson.decodeFromString(ChatMessage.serializer(), """{"id":"m3"}""").editUntil)

    //  `limits` نیامده، درست، یا نادرست ⇒ رشته همیشه باز می‌شود؛ صدا فقط با `voice: true`
    val http = FakeHttp { ok("""{"conversation":{"id":"sch_1"}}""", 201) }
    val api = ShopperApi(http)
    val bare = api.chatStart("golestan", "v2.t", null, "").valueOrNull()
    assertEquals("sch_1", assertNotNull(bare).conversation.id)
    assertFalse(bare.limits.voice)
    http.reply = { ok("""{"conversation":{"id":"sch_1"},"limits":{"imageMax":1572864,"thumbMax":65536,"audioMax":3145728,"voice":true}}""", 201) }
    val on = assertNotNull(api.chatStart("golestan", "v2.t", 7, "").valueOrNull())
    assertEquals(ChatOpenLimits(1_572_864, 65_536, 3_145_728, true), on.limits)
    http.reply = { ok("""{"conversation":{"id":"sch_1"},"limits":"خراب"}""", 201) }
    assertFalse(assertNotNull(api.chatStart("golestan", "v2.t", null, "").valueOrNull()).limits.voice, "سقفِ نادرست ⇒ پیش‌فرض، نه شکستِ گشودن")
    assertEquals("sch_1", api.chatOpen("golestan", "v2.t", null, "").valueOrNull()?.id, "chatOpen همان رشته")
  }

  @Test fun requestPathsAndBodiesForEditPinSearchAndReply() {
    val http = FakeHttp { r ->
      when {
        r.path.endsWith("/edit") -> ok("""{"message":{"id":"m2","rev":5,"body":"تازه","editedAt":9}}""")
        r.path.contains("/pins/") -> ok("""{"conversation":{"id":"sch_1","pinned":[{"messageId":"m2","seq":2,"pinnedAt":1}]}}""")
        r.path.contains("/search") -> ok("""{"results":[{"conversationId":"sch_1","id":"m2","seq":2,"sender":"customer","kind":"text","body":"برنج","createdAt":1},{"bad":true}]}""")
        else -> fail(404, "not_found")
      }
    }
    val api = ShopperApi(http)
    val edited = assertNotNull(api.chatEdit("golestan", "v2.t", "sch_1", "m2", "«تازه» \"x\"").valueOrNull())
    assertEquals(5, edited.rev)
    val e = http.seen.last()
    assertEquals("POST", e.method, "HttpURLConnection PATCH نمی‌فرستد ⇒ همان ویرایش با POST")
    assertEquals("/api/store/golestan/chat/conversations/sch_1/messages/m2/edit", e.path)
    assertEquals("v2.t", e.headers["X-Store-Ticket"])
    assertEquals(mapOf("body" to "«تازه» \"x\""), Json.parseToJsonElement(e.body!!).jsonObject.mapValues { it.value.jsonPrimitive.content })

    assertEquals(listOf("m2"), api.chatPin("golestan", "v2.t", "sch_1", "m2", true).valueOrNull()?.pinned?.map { it.messageId })
    assertEquals("PUT" to "/api/store/golestan/chat/conversations/sch_1/pins/m2", http.seen.last().method to http.seen.last().path)
    api.chatPin("golestan", "v2.t", "sch_1", "m2", false)
    assertEquals("DELETE", http.seen.last().method)
    assertNull(http.seen.last().body)

    val hits = assertNotNull(api.chatSearch("golestan", "v2.t", "برنج سفید").valueOrNull())
    assertEquals(listOf("m2"), hits.map { it.id }, "نتیجهٔ بی شناسه کنار می‌رود و برنامه را نمی‌شکند")
    assertEquals("/api/store/golestan/chat/search?q=%D8%A8%D8%B1%D9%86%D8%AC%20%D8%B3%D9%81%DB%8C%D8%AF&limit=30", http.seen.last().path)

    //  پاسخ از همان صفِ CHAT-02: `replyTo` در بدنه (فقط وقتی هست) و روی دیسک می‌ماند
    val (q, item) = ChatOutbox.add(emptyList(), "golestan", "sch_1", "text", "بله", now, Random(3), replyTo = "m1")
    val body = Json.parseToJsonElement(ChatOutbox.body(item)).jsonObject
    assertEquals("m1", body["replyTo"]!!.jsonPrimitive.content)
    assertEquals("m1", ChatOutbox.load(ChatOutbox.dump(q)).single().replyTo)
    assertFalse("replyTo" in Json.parseToJsonElement(ChatOutbox.body(item.copy(replyTo = null))).jsonObject)
  }

  @Test fun replyEditPinOnlyWithinTheAllowedScope() {
    val mine = own("m2")
    val gone = own("m3").copy(seq = 3, rev = 3, body = "", deleted = true)
    val photo = own("m4", kind = "image").copy(seq = 4, rev = 4, body = "")
    val t = thread(theirs, mine, gone, photo)
    assertTrue(ChatActs.canReply(t, theirs) && ChatActs.canReply(t, mine))
    assertFalse(ChatActs.canReply(t, gone), "سنگِ قبر نه")
    assertFalse(ChatActs.canReply(t, own("x9")), "پیامی که در این رشته نیست")
    assertTrue(ChatActs.canEdit(t, mine, now))
    assertFalse(ChatActs.canEdit(t, mine, now + 60_000), "سرِ editUntil ِ سرور بسته")
    assertFalse(ChatActs.canEdit(t, own("m2", until = null), now), "بی editUntil ِ سرور ⇒ نه (حدس نه)")
    assertFalse(ChatActs.canEdit(t, mine, now, passed = setOf("m2")), "سرور گفت مهلت گذشت ⇒ حتی با ساعتِ عقبِ گوشی")
    assertFalse(ChatActs.canEdit(t, theirs.copy(editUntil = now + 60_000), now), "پیامِ دکان")
    assertFalse(ChatActs.canEdit(t, photo, now), "فقط متن")
    assertTrue(ChatActs.canPin(t, theirs) && !ChatActs.canPin(t, gone))

    val closed = t.merge(ChatChanges(conv.copy(life = ChatLife(state = "closed"), canSend = false), emptyList(), 9))
    assertFalse(ChatActs.canReply(closed, theirs), "رشتهٔ بسته ⇒ نه پاسخ")
    assertFalse(ChatActs.canEdit(closed, mine, now), "نه ویرایش")
    assertTrue(ChatActs.canPin(closed, theirs), "سنجاق کارِ خواندن است")
    val blocked = t.merge(ChatChanges(conv.copy(canSend = false), emptyList(), 9))
    assertFalse(ChatActs.canReply(blocked, theirs))

    val pinned = t.merge(ChatChanges(conv.copy(pinned = listOf(ChatPin("m1", 1, 5), ChatPin("zz", 9, 6))), emptyList(), 9))
    assertTrue(ChatActs.pinned(pinned, "m1") && !ChatActs.pinned(pinned, "m2"))
    assertEquals(listOf("m1" to "بفرمایید", "zz" to "پیامِ پیشین"), ChatActs.pins(pinned), "متن فقط از رشتهٔ همین صفحه")
    assertEquals(1, ChatActs.indexOf(t, "m2"))
    assertNull(ChatActs.indexOf(t, "zz"))

    assertTrue(ChatActs.editEnds(ShopperError(403, "edit_window_passed")) && ChatActs.editEnds(ShopperError(403, "not_author")))
    assertFalse(ChatActs.editEnds(ShopperError(0, ShopperError.NETWORK)), "بی‌اینترنت ⇒ ویرایش باز، متن در کادر")
    assertNull(ChatActs.query(" ب "), "کمتر از ۲ نویسه ⇒ بی درخواست")
    assertEquals("بر", ChatActs.query("  بر  "))
    assertEquals(ChatActs.QUERY_MAX, ChatActs.query("ا".repeat(300))!!.length)
  }

  @Test fun snipAndSearchLinesMirrorTheWeb() {
    assertEquals("پیامِ پیشین", ChatActs.snip(null))
    assertEquals("پیامِ پیشین", ChatActs.snip(theirs.copy(deleted = true)))
    assertEquals("عکس", ChatActs.snip(theirs.copy(kind = "image", body = "")))
    assertEquals("پیامِ صوتی", ChatActs.snip(theirs.copy(kind = "audio", body = "")))
    assertEquals("ب".repeat(80) + "…", ChatActs.snip(theirs.copy(body = "ب".repeat(81))))
    val emoji = "a".repeat(79) + "😀" + "z"
    assertEquals("a".repeat(79) + "…", ChatActs.snip(theirs.copy(body = emoji)), "نیمهٔ نویسهٔ دوتایی بریده نمی‌شود")
    val here = ChatHit("sch_1", "m2", sender = "customer", body = "برنج")
    assertEquals("شما، برنج", ChatActs.hitLine(here, "sch_1"))
    assertEquals("دکان، برنج، در گفت‌وگوی دیگر", ChatActs.hitLine(here.copy(conversationId = "sch_9", sender = "shop"), "sch_1"))
    ShopperText.lang = ShopperLang.EN
    assertEquals("You, Photo", ChatActs.hitLine(here.copy(kind = "image", body = ""), "sch_1"))
    assertEquals("1 message found", ShopperText.chatHits(1))
    assertEquals("3 messages found", ShopperText.chatHits(3))
  }

  //  ⛔ دستهٔ ۴۲: پیام همیشه به رشته‌ای می‌رود که در آن نوشته شد — هرگز رشتهٔ پیشین یا دیگر
  @Test fun everyWritingIsBoundToTheThreadItWasWrittenIn() {
    val a = thread(theirs, own("m2"))
    val b = ChatThread(ChatConversation("sch_2"))
    var c = ChatCompose().type("sch_1", "برنج دارید؟")
    c = assertNotNull(ChatActs.reply(c, a, theirs))
    assertEquals(ChatOutgoing("sch_1", "برنج دارید؟", "m1"), c.outgoing(a), "پاسخ با متنِ نوشته در همان رشته")
    assertNull(c.outgoing(b), "رشتهٔ دیگری پیش روست ⇒ هیچ پیامی (نه به آن، نه به رشتهٔ پیشین)")
    val other = c.bind("sch_2")
    assertEquals(ChatCompose("sch_2"), other, "رشتهٔ دیگر ⇒ کادرِ تهی، بی پاسخ")
    assertEquals(c, c.bind("sch_1"), "همان رشته (گشودنِ دوباره) ⇒ نوشته می‌ماند")
    assertEquals(ChatCompose(null), c.bind(null))
    assertNull(ChatCompose().type("sch_1", "   ").outgoing(a), "تهی ⇒ هیچ")
    assertEquals(ChatActs.TEXT_MAX, ChatCompose().type("sch_1", "x".repeat(5000)).text.length, "کادر بیش از سقفِ سرور نمی‌گیرد")

    //  ویرایش: کادر همان متن؛ پیامِ تازه نیست؛ فقط همان پیامِ همان رشته
    val e = assertNotNull(ChatActs.edit(a, own("m2"), now))
    assertEquals(ChatCompose("sch_1", "سلام", editing = "m2"), e)
    assertNull(e.outgoing(a), "ویرایش پیامِ تازه نمی‌سازد")
    assertEquals(ChatSave("sch_1", "m2", "سلامِ تازه"), e.copy(text = " سلامِ تازه ").saving(a))
    assertNull(e.saving(b), "رشتهٔ دیگر ⇒ هیچ ذخیره‌ای")
    assertNull(e.copy(text = " ").saving(a))
    assertEquals(ChatCompose("sch_1", ""), e.stop(true), "انصراف ⇒ متنِ ویرایش هم می‌رود")
    assertEquals(ChatCompose("sch_1", "سلام"), e.stop(false), "ردِ سرور ⇒ متن می‌ماند تا پیامِ تازه شود")
    assertEquals("برنج دارید؟", assertNotNull(ChatActs.reply(ChatCompose().type("sch_1", "برنج دارید؟"), a, theirs)).text, "پاسخ متنِ نوشته را نگه می‌دارد")
    assertEquals("", assertNotNull(ChatActs.reply(e, a, theirs)).text, "پاسخ وسطِ ویرایش ⇒ متنِ ویرایش می‌رود")

    //  نوبتِ گشودن: پاسخِ گشودنِ پیشین هیچ‌جا نمی‌نشیند
    val o = ChatOpening()
    val first = o.next()
    val second = o.next()
    assertFalse(o.live(first))
    assertTrue(o.live(second))
  }

  @Test fun refundKeyIsReadApartAndShownAsTheWebTrackingPage() {
    val order = ShopperSamples.ORDER_JSON
    val http = FakeHttp { ok("""{"order":${order.dropLast(1)},"chat":true,"refund":{"status":"in_progress","requestedAt":1761800000000,
      "refundedMinor":5000,"remainingMinor":22100,"currency":"AFN","entries":[{"amountMinor":5000,"method":"cash","at":1761803600000,"final":false}],
      "updatedAt":1761803600000}}}""") }
    val api = ShopperApi(http)
    val page = assertNotNull(api.trackPage("golestan", "v1.abc").valueOrNull())
    val r = assertNotNull(page.extras.refund)
    assertTrue(page.extras.chat)
    assertEquals("in_progress", r.status)
    assertEquals(listOf(RefundEntry(5000, "cash", 1_761_803_600_000)), r.entries)
    assertEquals("بخشی از پول برگشته است؛ دکان هنوز کار را نبسته.", RefundRules.note(r))
    assertFalse(RefundRules.closed(r))
    assertEquals("برگشته به شما: ۵۰ افغانی", ShopperText.rfSum(r.refundedMinor))
    //  ۱۷۶۱۸۰۳۶۰۰۰۰۰ = ۲۰۲۵-۱۰-۳۰ ۰۵:۵۳ UTC = ۱۰:۲۳ ِ کابل = ۸ عقرب ۱۴۰۴ (`calendar-core.toSolar`)
    assertEquals("۸ عقرب ۱۴۰۴، ۱۰:۲۳ — نقد" to "۵۰ افغانی", RefundRules.line(r.entries.single()))
    ShopperText.lang = ShopperLang.EN
    assertEquals("2025-10-30 10:23", ShopperText.stamp(1_761_803_600_000))
    assertEquals("Refund closed (part of the amount).", RefundRules.note(r.copy(status = "partial")))
    assertEquals("Your return request is recorded; no money has been returned yet.", RefundRules.note(r.copy(status = "تازه")), "ناشناخته ⇒ همان «درخواست ثبت شد» ِ وب")
    assertTrue(RefundRules.closed(r.copy(status = "full")))

    //  پرچمِ خاموش (کلید نیست)، هنوز درخواستی نیست (`null`) یا شکلِ نادرست ⇒ هیچ کارتی، و نظر و گفت‌وگو سرِ جای خود
    for (refund in listOf("", ""","refund":null""", ""","refund":"خراب"""", ""","refund":{"entries":"x"}""")) {
      http.reply = { ok("""{"order":${order.dropLast(1)},"chat":true$refund}}""") }
      val p = assertNotNull(api.trackPage("golestan", "v1.abc").valueOrNull(), refund)
      assertNull(p.extras.refund, refund)
      assertTrue(p.extras.chat, "کلیدِ نادرستِ بازپرداخت گفت‌وگو را نمی‌برد: $refund")
    }
  }

  /**
   *  قراردادِ نسخهٔ ۲ (`market-public-v2.json`): `refund` با `currency`، `final` ِ هر ردیف و `receipts` (پرچمِ `shop.orders.refund_receipt`)؛
   *  و سرورِ تازه‌تر با کلیدهای ناشناخته (درونِ سفارش، بازپرداخت، ردیف و رسید) برنامه را نمی‌شکند (`ignoreUnknownKeys` ِ [ShopperJson]).
   */
  @Test fun refundV2ReceiptsAndKeysFromANewerServerAreTolerated() {
    assertTrue(ShopperJson.configuration.ignoreUnknownKeys)
    val order = ShopperSamples.ORDER_JSON
    val refund = """{"status":"partial","requestedAt":1761800000000,"refundedMinor":5000,"remainingMinor":22100,"currency":"AFN",
      "entries":[{"amountMinor":5000,"method":"cash","at":1761803600000,"final":true,"proof":"x"}],"updatedAt":1761803600000,
      "receipts":[{"no":"12-R1","seq":1,"at":1761803600000,"orderNo":12,"currency":"AFN","method":"cash","final":true,"totalMinor":27100,
      "refundedBeforeMinor":0,"amountMinor":5000,"refundedAfterMinor":5000,"netMinor":22100,"pdf":"/r/12-R1"}],"appeal":{"open":false}}"""
    val http = FakeHttp { ok("""{"order":${order.dropLast(1)},"chat":true,"refund":$refund,"futureCard":{"x":[1,2]}}}""") }
    val page = assertNotNull(ShopperApi(http).trackPage("golestan", "v1.abc").valueOrNull())
    val r = assertNotNull(page.extras.refund)
    assertEquals("partial", r.status)
    assertTrue(RefundRules.closed(r))
    assertEquals(listOf(RefundEntry(5000, "cash", 1_761_803_600_000, final = true)), r.entries)
    assertEquals(listOf("12-R1"), r.receipts.map { it.no })
    assertEquals(22100L, r.receipts.single().netMinor)
    assertTrue(page.extras.chat)
    //  همان سفارش، با یا بی کلیدهای تازه
    val plain = ShopperApi(FakeHttp { ok("""{"order":$order}""") }).track("golestan", "v1.abc").valueOrNull()
    assertEquals(plain, page.order)
  }
}
