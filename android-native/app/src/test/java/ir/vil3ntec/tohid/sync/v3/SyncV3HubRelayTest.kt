package ir.vil3ntec.tohid.sync.v3

import ir.vil3ntec.tohid.core.hub.HubRelay
import ir.vil3ntec.tohid.core.hub.HubRelayPort
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.sync.v1.SyncCommit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 *  SYN-09 — موتورِ v3 و هابِ محلی: هاب فقط وقتی سرور **نرسید**؛ پاسخِ هاب «در انتظار» است، صفِ دستگاه و
 *  `lastOkAt` دست نمی‌خورند و حالِ دیسک همان است که بی هاب می‌شد (پرچمِ خاموش ⇒ `hub = null` ⇒ همان امروز).
 */
class SyncV3HubRelayTest {

  private class Cloud {
    var reachable = true
    var status = 200
    val pushes = ArrayList<JsonObject>()
    private val seen = HashMap<String, Long>()

    val transport = V3Transport { _, path, body ->
      if (!reachable) throw IOException("offline")
      when {
        path == "/snapshot" -> V3Response(200, buildJsonObject {
          put("tables", JsonObject(emptyMap()))
          put("cursor", JsonPrimitive(0))
          put("complete", JsonPrimitive(true))
          put("rows", JsonPrimitive(0))
        })
        path == "/push" && status != 200 -> V3Response(status, buildJsonObject { put("error", JsonPrimitive("busy")) })
        path == "/push" -> {
          pushes += body!!
          val results = body.getValue("ops").jsonArray.map { o ->
            val id = o.jsonObject.getValue("op_id").jsonPrimitive.content
            val dup = id in seen
            val seq = seen.getOrPut(id) { seen.size + 1L }
            buildJsonObject {
              put("op_id", JsonPrimitive(id))
              put("status", JsonPrimitive(if (dup) "duplicate" else "applied"))
              put("server_seq", JsonPrimitive(seq))
            }
          }
          V3Response(200, buildJsonObject { put("results", JsonArray(results)); put("head", JsonPrimitive(seen.size)) })
        }
        else -> V3Response(404, null)
      }
    }
  }

  /** هابی که همان قراردادِ `HubServer` را پاسخ می‌دهد */
  private class Hub(val status: String = "hub_queued") : HubRelayPort {
    val bodies = ArrayList<JsonObject>()
    var down = false
    override suspend fun relay(body: JsonObject): HubRelay.Outcome {
      if (down) throw IOException("hub down")
      bodies += body
      val ids = HubRelay.opIdsOf(body)
      val reply = """{"ok":true,"hub":true,"accepted":true,"results":[""" +
        ids.joinToString(",") { """{"op_id":"$it","status":"$status"}""" } + "]}"
      return HubRelay.interpret(200, reply, ids)
    }
  }

  private class MemLedger(var data: ShopData) : V3Ledger {
    override fun read() = data
    override fun write(next: ShopData) { data = next }
  }

  private class MemStore(dir: File) : V3Store {
    var meta: V3Meta? = null
    var queue: V3Queue? = null
    override val journal = SyncCommit.Journal(File(dir, "journal.json"))
    override fun loadMeta() = meta
    override fun saveMeta(meta: V3Meta) { this.meta = meta }
    override fun loadQueue() = queue
    override fun saveQueue(queue: V3Queue) { this.queue = queue }
  }

  private val tmp: File = Files.createTempDirectory("v3-hub").toFile()
  private var clock = 1_000L

  /** `from`: همان حالِ دیسکِ دستگاهِ دیگر (برای مقایسهٔ «با هاب» و «بی هاب» از یک نقطه) */
  private inner class Device(name: String, val cloud: Cloud, val hub: HubRelayPort?, from: MemStore? = null) {
    val ledger = MemLedger(ShopData(products = listOf(Product(id = "p1", name = "شیر", salePrice = 10.0), Product(id = "p2", name = "نان", salePrice = 5.0))))
    val store = MemStore(File(tmp, name).apply { mkdirs() }).apply { meta = from?.meta; queue = from?.queue }
    val engine = SyncV3Engine(cloud.transport, store, "devA", ledger, rawNow = { clock }, random = { 0.5 }, hub = hub).load()

    /** راه‌اندازی با اینترنت و ثبتِ دو کالای محلی ⇒ صف پر */
    fun ready(): Int = runBlocking {
      assertTrue(engine.initialize(keepLocalOnly = true).ok)
      engine.record(ledger.read())
      engine.queued()
    }
  }

  private fun ids(body: JsonObject) = body.getValue("ops").jsonArray.map { it.jsonObject.getValue("op_id").jsonPrimitive.content }

  @Test fun hubKeepsOpsPendingAndLeavesTheSameStateAsNoHub() {
    val cloud = Cloud()
    val hub = Hub()
    val withHub = Device("hub", cloud, hub)
    val queued = withHub.ready()
    assertTrue(queued > 0)
    val noHub = Device("plain", cloud, null, from = withHub.store)
    assertEquals(queued, noHub.engine.queued())
    val okAt = withHub.engine.meta.lastOkAt
    val outbox = withHub.engine.queue.outbox

    clock = 2_000
    cloud.reachable = false
    val a = runBlocking { withHub.engine.pushOnce() }
    val b = runBlocking { noHub.engine.pushOnce() }
    //  هاب نگه داشت؛ ولی برای موتور همان «به سرور نرسیدیم» است
    assertEquals(queued, a.relayed)
    assertFalse(a.ok)
    assertEquals(b, a.copy(relayed = 0))
    assertEquals(outbox, withHub.engine.queue.outbox)
    assertEquals(okAt, withHub.engine.meta.lastOkAt)
    assertEquals(1, withHub.engine.meta.attempt)
    //  حالِ دیسک همان است که بی هاب
    assertEquals(noHub.store.meta, withHub.store.meta)
    assertEquals(noHub.store.queue, withHub.store.queue)
    assertEquals(1, hub.bodies.size)

    //  اینترنت برگشت ⇒ همان opها با همان بدنه به سرور، و فقط حالا از صف بیرون
    clock = 3_000
    cloud.reachable = true
    val c = runBlocking { withHub.engine.pushOnce() }
    assertTrue(c.ok)
    assertEquals(0, c.relayed)
    assertEquals(1, hub.bodies.size)
    assertEquals(ids(hub.bodies[0]), ids(cloud.pushes[0]))
    assertEquals(hub.bodies[0]["device_id"], cloud.pushes[0]["device_id"])
    assertEquals(hub.bodies[0]["ops"], cloud.pushes[0]["ops"])
    assertTrue(withHub.engine.queue.outbox.isEmpty())
    assertEquals(3_000L, withHub.engine.meta.lastOkAt)
  }

  @Test fun hubSettledIsStillPendingUntilTheCloudAnswers() {
    val cloud = Cloud()
    val hub = Hub(status = "hub_settled")
    val d = Device("settled", cloud, hub)
    val queued = d.ready()
    val outbox = d.engine.queue.outbox
    cloud.reachable = false
    clock = 2_000
    assertEquals(queued, runBlocking { d.engine.pushOnce() }.relayed)
    assertEquals(outbox, d.engine.queue.outbox)
    assertEquals(1_000L, d.engine.meta.lastOkAt)
    //  هاب پیش‌تر فرستاده بود ⇒ سرور «duplicate» می‌گوید و تازه همین‌جا صف خالی می‌شود
    cloud.reachable = true
    runBlocking { cloud.transport.request("POST", "/push", hub.bodies[0]) }
    val r = runBlocking { d.engine.pushOnce() }
    assertTrue(r.ok)
    assertTrue(r.done > 0)
    assertEquals(2, cloud.pushes.size)
    assertTrue(d.engine.queue.outbox.isEmpty())
  }

  @Test fun hubIsNotUsedWhenTheCloudAnswersEvenWithAnError() {
    val cloud = Cloud()
    val hub = Hub()
    val d = Device("reach", cloud, hub)
    d.ready()
    cloud.status = 503
    val r = runBlocking { d.engine.pushOnce() }
    assertFalse(r.ok)
    assertEquals(503, r.status)
    assertEquals(0, r.relayed)
    cloud.status = 200
    assertTrue(runBlocking { d.engine.pushOnce() }.ok)
    assertTrue(hub.bodies.isEmpty())
  }

  @Test fun hubDownLeavesTheSameStateAsNoHub() {
    val cloud = Cloud()
    val hub = Hub().apply { down = true }
    val d = Device("down", cloud, hub)
    d.ready()
    val twin = Device("twin", cloud, null, from = d.store)
    cloud.reachable = false
    clock = 2_000
    val a = runBlocking { d.engine.pushOnce() }
    val b = runBlocking { twin.engine.pushOnce() }
    assertEquals(b, a)
    assertEquals(twin.store.meta, d.store.meta)
    assertEquals(twin.store.queue, d.store.queue)
  }
}
