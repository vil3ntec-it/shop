package ir.vil3ntec.tohid.core.storefront

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** صفِ آفلاینِ «ویترین و بازار» (M1) — بی دستگاه، در هر پلتفرم */
class StorefrontOutboxTest {

  private val O = StorefrontOutbox

  private fun e(
    change: StorefrontChange, shop: String = "s1", id: String = change.wire + "-1",
    body: String = "{}", base: String = "100", at: Long = 1_000,
  ) = OutboxEntry(id, change, shop, body, base, at)

  @Test fun newEditReplacesOldButKeepsItsBase() {
    var q = O.put(emptyList(), e(StorefrontChange.SETTINGS, id = "a", body = "{\"title\":\"x\"}", base = "100"))
    //  ویرایشِ دوم از روی همان ویرایشِ نرسیده پر شده ⇒ پایه همان ۱۰۰ می‌ماند
    q = O.put(q, e(StorefrontChange.SETTINGS, id = "b", body = "{\"title\":\"y\"}", base = "999"))
    assertEquals(1, q.size)
    assertEquals("b", q[0].id)
    assertEquals("{\"title\":\"y\"}", q[0].body)
    assertEquals("100", q[0].base)
  }

  @Test fun presenceDoesNotInheritBase() {
    var q = O.put(emptyList(), e(StorefrontChange.PRESENCE, id = "p1", base = ""))
    q = O.put(q, e(StorefrontChange.PRESENCE, id = "p2", base = ""))
    assertEquals(listOf("p2"), q.map { it.id })
  }

  @Test fun sendOrderIsSettingsDeliveryMarketPresence() {
    var q = emptyList<OutboxEntry>()
    for (c in listOf(StorefrontChange.PRESENCE, StorefrontChange.MARKET, StorefrontChange.SETTINGS, StorefrontChange.DELIVERY)) q = O.put(q, e(c))
    assertEquals(
      listOf(StorefrontChange.SETTINGS, StorefrontChange.DELIVERY, StorefrontChange.MARKET, StorefrontChange.PRESENCE),
      O.pendingFor(q, "s1").map { it.change },
    )
    assertEquals(StorefrontChange.SETTINGS, O.next(q, "s1", now = 0)?.change)
  }

  @Test fun entriesAreBoundToTheirShop() {
    val q = O.put(O.put(emptyList(), e(StorefrontChange.MARKET, shop = "A", id = "m-a")), e(StorefrontChange.MARKET, shop = "B", id = "m-b"))
    assertEquals(2, q.size)
    assertEquals("m-b", O.next(q, "B", 0)?.id)
    assertEquals(listOf("m-a"), O.pendingFor(q, "A").map { it.id })
    //  دکانِ نامعلوم ⇒ هیچ‌چیز فرستاده نمی‌شود
    assertNull(O.next(q, "", 0))
    assertTrue(O.pendingFor(q, " ").isEmpty())
  }

  @Test fun confirmedOnlyOnTwoHundreds() {
    assertEquals(SendOutcome.CONFIRMED, O.classify(200, "", false))
    assertEquals(SendOutcome.CONFIRMED, O.classify(204, "", false))
    //  بی شبکه / دیرکرد / ۵xx / نشستِ تمام‌شده / ۴۲۹ ⇒ می‌ماند
    assertEquals(SendOutcome.HOLD, O.classify(0, "offline", true))
    assertEquals(SendOutcome.HOLD, O.classify(0, "not_configured", false))
    assertEquals(SendOutcome.HOLD, O.classify(503, "unavailable", true))
    assertEquals(SendOutcome.HOLD, O.classify(500, "x", false))
    assertEquals(SendOutcome.HOLD, O.classify(401, "session_expired", false))
    assertEquals(SendOutcome.HOLD, O.classify(429, "rate_limited", false))
    assertEquals(SendOutcome.HOLD, O.classify(408, "", false))
    //  ردِ قطعی ⇒ بیرون، با پیامِ سرور
    assertEquals(SendOutcome.REJECTED, O.classify(400, "bad_slug", false))
    assertEquals(SendOutcome.REJECTED, O.classify(409, "slug_taken", false))
    assertEquals(SendOutcome.REJECTED, O.classify(403, "subscription_required", false))
    assertEquals(SendOutcome.REJECTED, O.classify(404, "market_not_found", false))
  }

  @Test fun holdKeepsEntryWithBackoffAndConfirmRemoves() {
    val a = e(StorefrontChange.SETTINGS)
    var q = O.put(emptyList(), a)
    q = O.afterSend(q, a, SendOutcome.HOLD, "اینترنت وصل نیست", now = 10_000)
    assertEquals(1, q.size)
    assertEquals(1, q[0].attempts)
    assertEquals(15_000, q[0].nextAt)
    assertEquals("اینترنت وصل نیست", q[0].lastError)
    assertNull(O.next(q, "s1", now = 14_999))
    assertEquals(a.id, O.next(q, "s1", now = 15_000)?.id)
    assertEquals(a.id, O.next(q, "s1", now = 0, force = true)?.id)
    q = O.afterSend(q, q[0], SendOutcome.CONFIRMED, "", now = 20_000)
    assertTrue(q.isEmpty())
  }

  @Test fun lateResponseNeverRemovesANewerEdit() {
    val first = e(StorefrontChange.MARKET, id = "m1")
    var q = O.put(emptyList(), first)
    //  در حالِ فرستادنِ m1، کاربر دوباره ذخیره می‌کند
    q = O.put(q, e(StorefrontChange.MARKET, id = "m2", body = "{\"listed\":true}"))
    q = O.afterSend(q, first, SendOutcome.CONFIRMED, "", now = 1)
    assertEquals(listOf("m2"), q.map { it.id })
    q = O.afterSend(q, first, SendOutcome.REJECTED, "x", now = 1)
    assertEquals(listOf("m2"), q.map { it.id })
  }

  @Test fun backoffDoublesAndCaps() {
    assertEquals(0, O.backoffMs(0))
    assertEquals(5_000, O.backoffMs(1))
    assertEquals(10_000, O.backoffMs(2))
    assertEquals(20_000, O.backoffMs(3))
    assertEquals(300_000, O.backoffMs(20))
    assertEquals(300_000, O.backoffMs(10_000))
  }

  @Test fun conflictWhenServerMovedSinceEditStarted() {
    val s = e(StorefrontChange.SETTINGS, base = "100")
    assertFalse(O.isConflict(s, "100"))
    assertTrue(O.isConflict(s, "200"))
    assertFalse(O.isConflict(s, null))                  // خوانده نشد ⇒ خودِ فرستادن تصمیم می‌گیرد
    assertFalse(O.isConflict(s.copy(base = ""), "200")) // بی پایه ⇒ بی سنجش
    assertFalse(O.isConflict(e(StorefrontChange.PRESENCE, base = "1"), "2"))

    var q = O.markConflict(O.put(emptyList(), s), s)
    assertTrue(q[0].conflict)
    assertNull(O.next(q, "s1", now = Long.MAX_VALUE, force = true)) // بی‌پرسش رونویسی نمی‌شود
    q = O.keepMine(q, s.id, "200")
    assertFalse(q[0].conflict)
    assertEquals("200", q[0].base)
    assertEquals(s.id, O.next(q, "s1", 0)?.id)
    assertTrue(O.discard(q, s.id).isEmpty())
  }

  @Test fun encodeDecodeRoundTripWithHostileText() {
    val body = "{\"title\":\"دکانِ ۱\\t%0A\",\"notice\":\"a\nb\tc%25\r\"}"
    val q = listOf(
      OutboxEntry("settings-1", StorefrontChange.SETTINGS, "shop-1", body, "1761000000000", 5, 2, 99, "خطا\tبا\nخط", true),
      OutboxEntry("pres-abc12345", StorefrontChange.PRESENCE, "shop-1",
        PresenceDeclaration.encode(PresenceDeclaration("pres-abc12345", "online_until", 1_800_000_000_000, 1_760_000_000_000)), "", 7),
    )
    assertEquals(q, O.decode(O.encode(q)))
    assertEquals(emptyList(), O.decode(O.encode(emptyList())))
  }

  @Test fun decodeIsDefensive() {
    assertTrue(O.decode(null).isEmpty())
    assertTrue(O.decode("").isEmpty())
    assertTrue(O.decode("sfq9\nx").isEmpty())
    val good = OutboxEntry("m1", StorefrontChange.MARKET, "s1", "{}", "", 1)
    val raw = O.encode(listOf(good)) +
      "\nbroken" +
      "\nm2\tnope\ts1\t{}\t\t1\t0\t0\t\t0" +                 // نوعِ ناشناخته
      "\np9\tpresence\ts1\tgarbage\t\t1\t0\t0\t\t0" +         // اعلامِ خراب
      "\nm3\tmarket\ts1\t{\"listed\":true}\t\t5\t0\t0\t\t0"   // دوتایی ⇒ تازه‌تر می‌ماند
    val q = O.decode(raw)
    assertEquals(listOf("m3"), q.map { it.id })
  }

  @Test fun statusLineNeverSaysConfirmed() {
    val lines = listOf(
      O.statusLine(e(StorefrontChange.SETTINGS)),
      O.statusLine(e(StorefrontChange.MARKET).copy(attempts = 3, lastError = "اینترنت وصل نیست")),
      O.statusLine(e(StorefrontChange.DELIVERY).copy(conflict = true)),
    )
    for (l in lines) assertFalse("ثبت شد" in l, l)
    assertTrue("۳ بار" in lines[1])
  }

  @Test fun fingerprintIsStableAndSensitive() {
    assertEquals("cbf29ce484222325", O.fingerprint(""))
    assertEquals(O.fingerprint("{\"zones\":[]}"), O.fingerprint("{\"zones\":[]}"))
    assertNotEquals(O.fingerprint("{\"slotDays\":3}"), O.fingerprint("{\"slotDays\":4}"))
  }

  @Test fun presenceDeclaration() {
    val p = PresenceDeclaration.create("online_until", 1_800_000_000_000L, now = 1_760_000_000_000L, random = 0xabcdefL)
    assertEquals(p, PresenceDeclaration.decode(PresenceDeclaration.encode(p)))
    assertTrue(p.eventId.endsWith("-abcdef"))
    assertNull(PresenceDeclaration.create("closed", 123L, 1L, 1L).declaredUntil)
    assertNull(PresenceDeclaration.decode("pres-aaaaaaaa|online_until||1"))
    assertNull(PresenceDeclaration.decode("pres-aaaaaaaa|hack||1"))
    assertTrue(runCatching { PresenceDeclaration.create("online_until", null, 1L, 1L) }.isFailure)
    //  تصادفِ منفی هم شناسهٔ مثبت و پایدار می‌دهد
    assertFalse(PresenceDeclaration.newEventId(1, -1).contains("--"))
  }
}
