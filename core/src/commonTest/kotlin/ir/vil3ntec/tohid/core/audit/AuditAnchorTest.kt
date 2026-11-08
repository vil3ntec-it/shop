package ir.vil3ntec.tohid.core.audit

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 *  STF-09 — `AuditAnchor`: کدام سر کِی به سرور می‌رود (فقط وقتی عوض شده، دورهٔ کهنه اول، فقط همین دستگاه)،
 *  تکرار و بی‌اینترنتی (پاسخِ نرسیده ⇐ دورِ بعد دوباره)، بدنهٔ درخواست و خواندنِ پاسخ و حکمِ سرور.
 */
class AuditAnchorTest {
  private val dev = "0123456789abcdef0123456789abcdef"
  private val other = "ffeeddccbbaa99887766554433221100"
  private val h1 = "1".repeat(64)
  private val h2 = "2".repeat(64)
  private val h3 = "3".repeat(64)

  private fun head(chain: String, seq: Long, hash: String) = AuditChain.Head(chain, seq, hash)

  /** لنگرِ محلی همان‌طور که `AuditSeal` پس از هر نوشتن می‌سازد */
  private fun anchors(vararg heads: AuditChain.Head): AuditChain.Anchors = heads.fold(AuditChain.Anchors()) { a, h -> a.with(h) }

  @Test fun nothingWithoutAnchors() {
    assertEquals(emptyList(), AuditAnchor.due(dev, null, AuditAnchor.Sent()))
    assertEquals(emptyList(), AuditAnchor.due("", anchors(head("$dev/1", 3, h1)), AuditAnchor.Sent()))
  }

  @Test fun onlyChangedHeadsOfThisDevice() {
    val a = anchors(head("$other/1", 9, h3), head("$dev/1", 3, h1))
    val first = AuditAnchor.due(dev, a, AuditAnchor.Sent())
    assertEquals(listOf(head("$dev/1", 3, h1)), first, "زنجیرهٔ دستگاهِ دیگر هرگز از این دستگاه نمی‌رود")
    //  پذیرفته ⇐ تا عوض نشده دوباره نمی‌رود
    val sent = AuditAnchor.after(AuditAnchor.Sent(), first, AuditAnchor.Reply(true, acks = first))
    assertEquals(emptyList(), AuditAnchor.due(dev, a, sent))
    //  ردیفِ تازه ⇐ سرِ تازه
    val grown = a.with(head("$dev/1", 4, h2))
    assertEquals(listOf(head("$dev/1", 4, h2)), AuditAnchor.due(dev, grown, sent))
  }

  @Test fun offlineOrFlagOffKeepsItDue() {
    val a = anchors(head("$dev/1", 3, h1))
    val heads = AuditAnchor.due(dev, a, AuditAnchor.Sent())
    val none = AuditAnchor.Sent()
    //  پاسخ نرسید (بی‌اینترنت، خطای سرور) ⇐ همان؛ دورِ بعد همان سر دوباره
    assertSame(none, AuditAnchor.after(none, heads, null))
    assertEquals(heads, AuditAnchor.due(dev, a, AuditAnchor.after(none, heads, null)))
    //  پرچمِ سرور خاموش ⇐ هیچ چیزی ننشست
    assertSame(none, AuditAnchor.after(none, heads, AuditAnchor.Reply(false)))
    //  پاسخِ بی تأیید برای این سر (نیمه‌کاره) ⇐ دوباره
    assertSame(none, AuditAnchor.after(none, heads, AuditAnchor.Reply(true)))
  }

  @Test fun problemIsAnsweredNotResentEveryCycle() {
    val a = anchors(head("$dev/1", 3, h1))
    val heads = AuditAnchor.due(dev, a, AuditAnchor.Sent())
    val sent = AuditAnchor.after(AuditAnchor.Sent(), heads, AuditAnchor.Reply(true, problems = listOf(AuditAnchor.Problem("$dev/1", 3, "fork"))))
    assertEquals(head("$dev/1", 3, h1), sent.heads["$dev/1"])
    assertEquals(emptyList(), AuditAnchor.due(dev, a, sent))
  }

  @Test fun oldEpochGoesBeforeTheNewOneAndAtMostFour() {
    //  بازیابی: دورهٔ ۱ تا ۱۲ نوشته شد ولی نرفت، بعد دورهٔ ۲ (همان `AuditChain.resume`)؛ هر دو، کهنه اول
    val a = anchors(head("$dev/1", 12, h1), head("$dev/2", 3, h2))
    assertEquals(listOf("$dev/1", "$dev/2"), AuditAnchor.due(dev, a, AuditAnchor.Sent()).map { it.chain })
    //  دورهٔ ۱۰ پس از ۹ (نه به ترتیبِ متن)، و فقط چهارتای تازه‌تر
    val many = anchors(*(1L..10L).map { head("$dev/$it", it, h3) }.toTypedArray())
    assertEquals(listOf(7L, 8L, 9L, 10L), AuditAnchor.due(dev, many, AuditAnchor.Sent()).map { it.seq })
  }

  @Test fun sentSurvivesEncodeAndBrokenTextMeansResend() {
    val s = AuditAnchor.Sent(mapOf("$dev/1" to head("$dev/1", 3, h1), "$dev/2" to head("$dev/2", 1, h2)))
    assertEquals(s, AuditAnchor.Sent.decode(s.encode()))
    for (bad in listOf(null, "", "v=2\n", "v=1\nsent=$dev/1\t0\t$h1\n", "v=1\nsent=$dev/1\t3\tzz\n", "v=1\nwhat\n")) {
      assertEquals(AuditAnchor.Sent(), AuditAnchor.Sent.decode(bad), "$bad")
    }
  }

  @Test fun bodyAndReplyShapes() {
    val heads = listOf(head("$dev/1", 3, h1))
    val body = AuditAnchor.body(dev, heads)
    assertEquals(JsonPrimitive(dev), body["device_id"])
    assertEquals(JsonPrimitive(AuditChain.FORMAT), body["format"])
    val sent = (body["heads"] as JsonArray).single().jsonObject
    assertEquals(JsonPrimitive("$dev/1"), sent["chain"])
    assertEquals(JsonPrimitive(3L), sent["seq"])
    assertEquals(JsonPrimitive(h1), sent["hash"])

    val reply = AuditAnchor.reply(obj("""{"ok":true,"enabled":true,"acks":[{"chain":"$dev/1","seq":3,"hash":"$h1"}],"problems":[{"chain":"$dev/1","seq":3,"problem":"rollback"}],"last_epoch":1}"""))
    assertEquals(AuditAnchor.Reply(true, heads, listOf(AuditAnchor.Problem("$dev/1", 3, "rollback"))), reply)
    assertEquals(AuditAnchor.Reply(false), AuditAnchor.reply(obj("""{"ok":true,"enabled":false}""")))
  }

  @Test fun serverVerdict() {
    assertNull(AuditAnchor.verdict(obj("""{"ok":true,"enabled":false}""")))
    assertNull(AuditAnchor.verdict(obj("""{"ok":true,"enabled":true}""")), "پاسخِ ناخوانا ⇐ فقط سنجشِ همین دستگاه")
    assertEquals(AuditAnchor.Verdict(true, null, 2), AuditAnchor.verdict(obj("""{"ok":true,"enabled":true,"valid":true,"broken":null,"pending":2}""")))
    val v = AuditAnchor.verdict(obj("""{"ok":true,"enabled":true,"valid":false,"broken":{"chain":"$other/1","seq":5,"reason":"truncated","id":null}}"""))!!
    assertEquals(false, v.valid)
    assertEquals(AuditChain.Break("$other/1", 5, "truncated", null), v.broken)
    val e = AuditAnchor.verdict(obj("""{"ok":true,"enabled":true,"valid":false,"broken":{"chain":"$dev/1","seq":2,"reason":"anchor_mismatch","id":"aud_2"}}"""))!!
    assertEquals("aud_2", e.broken?.id)
    assertTrue(!e.valid)
  }

  private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject
}
