package ir.vil3ntec.tohid.core.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** SYN-07 — صفِ تصمیم‌های صندوقِ v3 ([V3InboxOutbox]) و فرستادنش ([V3InboxClient]) با سرورِ جعلی، بی شبکه */
class V3InboxOutboxTest {
  private fun <T> runSync(block: suspend () -> T): T {
    var out: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { out = it })
    return checkNotNull(out).getOrThrow()
  }

  private class Mem : V3InboxQueueStore {
    var text: String? = null
    var writes = 0
    override fun read(): String? = text
    override fun write(text: String) { this.text = text; writes++ }
  }

  /** سرورِ جعلی: پاسخ‌ها به ترتیب برای هر مسیر؛ هر درخواست ثبت می‌شود */
  private class Fake : V3InboxTransport {
    val replies = HashMap<String, ArrayDeque<V3InboxReply>>()
    val calls = ArrayList<Pair<String, String>>()
    fun on(path: String, vararg r: V3InboxReply) { replies.getOrPut(path) { ArrayDeque() }.addAll(r) }
    private fun next(path: String) = replies[path]?.removeFirstOrNull() ?: V3InboxReply(0)
    override suspend fun get(path: String): V3InboxReply { calls += "GET" to path; return next(path.substringBefore('?')) }
    override suspend fun post(path: String, body: JsonObject): V3InboxReply { calls += "POST $path" to body.toString(); return next(path) }
  }

  private fun obj(s: String) = Json.parseToJsonElement(s).jsonObject
  private fun row(id: String, status: String) = """{"id":"$id","kind":"lww","revertible":${status == "open"},"field":"name","status":"$status","at":1}"""
  private fun ok(id: String, status: String, dup: Boolean = false) =
    V3InboxReply(200, obj("""{"ok":true,"conflict":${row(id, status)},"op":null${if (dup) ",\"duplicate\":true" else ""}}"""))
  private fun d(id: String, a: String = V3Inbox.KEEP, f: List<String> = emptyList()) = V3Inbox.Decision(id, a, f)

  @Test fun oneDecisionPerConflictNewestWinsAndOldOnesAge() {
    val t = 1_000_000_000L
    var q = V3InboxOutbox.add(emptyList(), V3InboxOutbox.Entry(d("cf_a"), t), t)
    q = V3InboxOutbox.add(q, V3InboxOutbox.Entry(d("cf_b", V3Inbox.REVERT), t + 1), t + 1)
    q = V3InboxOutbox.add(q, V3InboxOutbox.Entry(d("cf_a", V3Inbox.REVERT), t + 2), t + 2)
    assertEquals(listOf("cf_b" to "revert", "cf_a" to "revert"), q.map { it.conflictId to it.decision.action })
    val late = t + V3InboxOutbox.MAX_AGE_MS + 5
    assertEquals(listOf("cf_c"), V3InboxOutbox.add(q, V3InboxOutbox.Entry(d("cf_c"), late), late).map { it.conflictId })
    assertEquals(emptyList(), V3InboxOutbox.prune(q, late))
    val many = (1..V3InboxOutbox.CAP + 5).fold(emptyList<V3InboxOutbox.Entry>()) { acc, i -> V3InboxOutbox.add(acc, V3InboxOutbox.Entry(d("cf_$i"), t + i), t + i) }
    assertEquals(V3InboxOutbox.CAP, many.size)
    assertEquals("cf_6", many.first().conflictId)
  }

  @Test fun fatesFollowServerReply() {
    assertEquals(V3InboxOutbox.Fate.DONE, V3InboxOutbox.fate(200, ""))
    assertEquals(V3InboxOutbox.Fate.CLOSED, V3InboxOutbox.fate(409, "already_resolved"))
    assertEquals(V3InboxOutbox.Fate.CLOSED, V3InboxOutbox.fate(404, "conflict_not_found"))
    for (s in listOf(400, 403, 413)) assertEquals(V3InboxOutbox.Fate.REFUSED, V3InboxOutbox.fate(s, "x"))
    for (s in listOf(0, 401, 404, 408, 429, 500, 503)) assertEquals(V3InboxOutbox.Fate.RETRY, V3InboxOutbox.fate(s, "not_found"))
  }

  @Test fun encodeDecodeRoundTripAndBadTextIsEmpty() {
    val q = listOf(
      V3InboxOutbox.Entry(d("cf_a", V3Inbox.MERGE, listOf("name", "salePriceMinor")), 5, tries = 2),
      V3InboxOutbox.Entry(d("cf_b", V3Inbox.DISMISS), 6),
    )
    assertEquals(q, V3InboxOutbox.decode(V3InboxOutbox.encode(q)))
    assertEquals(emptyList(), V3InboxOutbox.decode("{nope"))
    assertEquals(emptyList(), V3InboxOutbox.decode(null))
    assertEquals(emptyList(), V3InboxOutbox.decode("""[{"c":"cf_x","a":"merge","f":[],"t":1},{"c":"../y","a":"keep","t":1},{"c":"cf_z","a":"drop","t":1}]"""))
    assertEquals("""{"action":"merge","fields":["name","salePriceMinor"]}""", V3InboxOutbox.body(q[0]).toString())
    assertEquals("""{"action":"dismiss"}""", V3InboxOutbox.body(q[1]).toString())
    assertEquals("/sync/v3/conflicts/cf_b/resolve", V3InboxOutbox.path(q[1]))
  }

  @Test fun offlineDecisionStaysOnDiskAndGoesOnceOnline() = runSync {
    val net = Fake()
    val disk = Mem()
    var clock = 10L
    val client = V3InboxClient(net, disk) { clock++ }
    client.decide(d("cf_a", V3Inbox.REVERT))
    client.decide(d("cf_b"))
    //  بی شبکه: نخستین «نرسید» می‌ایستد و صف دست‌نخورده می‌ماند
    val offline = client.flush()
    assertEquals(listOf(V3InboxOutbox.Fate.RETRY), offline.map { it.fate })
    assertEquals(listOf("cf_a", "cf_b"), client.queued().map { it.conflictId })
    assertEquals(1, client.queued().first().tries)
    //  برنامه دوباره باز شد (همان دیسک) و شبکه آمد
    val again = V3InboxClient(net, disk) { clock++ }
    net.on("/sync/v3/conflicts/cf_a/resolve", ok("cf_a", "reverted"))
    net.on("/sync/v3/conflicts/cf_b/resolve", V3InboxReply(409, code = "already_resolved"))
    val sent = again.flush()
    assertEquals(listOf(V3InboxOutbox.Fate.DONE, V3InboxOutbox.Fate.CLOSED), sent.map { it.fate })
    assertEquals(V3Inbox.REVERTED, sent[0].conflict?.status)
    assertNull(sent[1].conflict)
    assertEquals(emptyList(), again.queued())
    assertEquals("""{"action":"revert"}""", net.calls.first { it.first.endsWith("cf_a/resolve") }.second)
    assertEquals(0, again.flush().size, "صفِ خالی هیچ درخواستی نمی‌فرستد")
    assertEquals(3, net.calls.size)
  }

  @Test fun lostReplyRetryIsHarmlessDuplicateAndRefusedIsDropped() = runSync {
    val net = Fake()
    val client = V3InboxClient(net, Mem()) { 50L }
    client.decide(d("cf_m", V3Inbox.MERGE, listOf("name")))
    net.on("/sync/v3/conflicts/cf_m/resolve", V3InboxReply(0), ok("cf_m", "reverted", dup = true))
    assertEquals(V3InboxOutbox.Fate.RETRY, client.flush().single().fate)
    val second = client.flush().single()
    assertEquals(V3InboxOutbox.Fate.DONE, second.fate)
    assertTrue(second.duplicate)
    client.decide(d("cf_r", V3Inbox.MERGE, listOf("name")))
    net.on("/sync/v3/conflicts/cf_r/resolve", V3InboxReply(400, code = "bad_action"))
    assertEquals(V3InboxOutbox.Fate.REFUSED, client.flush().single().fate)
    assertEquals(emptyList(), client.queued(), "ردِ روشنِ سرور در صف نمی‌ماند")
  }

  @Test fun newerDecisionMadeWhileSendingIsKept() {
    val e = V3InboxOutbox.Entry(d("cf_a"), 1)
    val newer = V3InboxOutbox.Entry(d("cf_a", V3Inbox.REVERT), 2)
    assertEquals(listOf(newer), V3InboxOutbox.settle(listOf(newer), e, V3InboxOutbox.Fate.DONE))
  }

  @Test fun listParsesPageAndReportsFailure() = runSync {
    val net = Fake()
    val client = V3InboxClient(net, Mem()) { 1L }
    net.on("/sync/v3/conflicts", V3InboxReply(200, obj("""{"ok":true,"conflicts":[${row("cf_1", "open")}],"inbox":{"v":1,"merge":true}}""")))
    val l = client.list()
    assertTrue(l.page!!.on)
    assertEquals("cf_1", l.page!!.items.single().id)
    assertEquals("GET" to "/sync/v3/conflicts?status=open&limit=200", net.calls.single())
    net.on("/sync/v3/conflicts", V3InboxReply(404, code = "not_found"))
    val off = client.list(all = true)
    assertNull(off.page)
    assertEquals(404, off.status)
  }
}
