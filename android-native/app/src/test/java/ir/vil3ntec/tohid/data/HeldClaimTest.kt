package ir.vil3ntec.tohid.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  S09 — «در دستِ دستگاهِ X» (`HeldSyncEngine.claim`) و «یک فاکتور در دو دستگاه» با سرورِ ساختگیِ همان قراردادِ
 *  `DELETE /api/shop/held-invoices/:id?…&base=&claim=1&force=1` (`server/test/shop-held-invoices.test.js` خودِ سرور را،
 *  روی PGlite و PostgreSQL و با رقابتِ هم‌زمان، می‌سنجد).
 *
 *  پرچمِ خاموش ⇒ هیچ پرسشی؛ دستگاهِ دوم پیش از گرفتن تأییدِ صریح می‌خواهد و با کلیدِ تازهٔ فروش باز می‌کند؛ دستگاهِ نخست
 *  می‌فهمد؛ بازگرداندنِ بی شبکه آزاد است و پس از وصل «دوتایی» دیده می‌شود — دو فروشِ یک نگه‌داشته هرگز یک شناسه نمی‌گیرند.
 */
class HeldClaimTest {

  /** همان قاعده‌های `lib/shop-held-sync.js` (`remove` با ادعا، base، force و دستگاه) — بی پایگاه */
  class Server {
    var enabled = true
    var now = 1_700_000_000_000L
    private var rev = 0L
    class Row(val id: String, var version: Long, var body: JsonObject?, var device: String, var reason: String, var rev: Long, var at: Long)
    val rows = LinkedHashMap<String, Row>()
    val series = HashMap<String, String>()
    var claims = 0

    private fun err(code: String, extra: Pair<String, JsonObject>? = null) = buildJsonObject {
      put("code", JsonPrimitive(code)); if (extra != null) put(extra.first, extra.second)
    }

    private fun view(r: Row) = buildJsonObject {
      put("id", JsonPrimitive(r.id)); put("version", JsonPrimitive(r.version)); put("rev", JsonPrimitive(r.rev))
      if (r.body == null) {
        put("deleted", JsonPrimitive(true)); put("reason", JsonPrimitive(r.reason))
        put("device_id", JsonPrimitive(r.device)); put("series", JsonPrimitive(series[r.device].orEmpty()))
      } else { put("body", r.body!!); put("mine", JsonPrimitive(true)) }
    }

    private fun holder(r: Row) = buildJsonObject {
      put("device_id", JsonPrimitive(r.device)); put("series", JsonPrimitive(series[r.device].orEmpty()))
      put("reason", JsonPrimitive(if (r.body == null) r.reason else "held")); put("at", JsonPrimitive(r.at))
      put("fresh", JsonPrimitive(r.body == null && r.reason == "resumed" && now - r.at < 10 * 60_000L))
    }

    private fun ok(body: JsonObject = buildJsonObject {}) = HeldSyncEngine.Reply(200, body)
    private fun no(status: Int, e: JsonObject) = HeldSyncEngine.Reply(status, error = e)

    private fun granted(version: Long, takeover: JsonObject? = null, deleted: Boolean) = ok(buildJsonObject {
      put("deleted", JsonPrimitive(deleted)); put("version", JsonPrimitive(version))
      put("claim", buildJsonObject { put("granted", JsonPrimitive(true)); if (takeover != null) put("takeover", takeover) })
    })

    fun remove(dev: String, id: String, version: Long, reason: String, base: Long?, claiming: Boolean, forcing: Boolean): HeldSyncEngine.Reply {
      if (!enabled) return no(403, err("held_sync_disabled"))
      val cur = rows[id] ?: return if (claiming) granted(0, deleted = false) else ok()
      if (cur.body == null) {
        val mine = dev.isNotBlank() && cur.device == dev
        val taken = !mine && base != null && cur.version > base && (cur.reason == "resumed" || cur.reason == "sold")
        if (!claiming) return if (taken && reason == "resumed") no(409, err("held_conflict", "holder" to holder(cur))) else ok()
        if (mine) return granted(cur.version, deleted = false)
        if (taken && !forcing) return no(409, err("held_claimed", "holder" to holder(cur)))
        val take = if (taken) holder(cur) else null
        cur.version = maxOf(version, cur.version + 1); cur.reason = "resumed"; cur.device = dev; cur.rev = ++rev; cur.at = now
        return granted(cur.version, take, deleted = true)
      }
      if (base != null && cur.version > base) {
        if (claiming) return no(409, err("held_stale", "current" to view(cur)))
        if (reason == "resumed") return no(409, err("held_conflict", "holder" to holder(cur)))
      }
      if (cur.version > version) return no(409, err("held_stale", "current" to view(cur)))
      cur.version = version; cur.body = null; cur.reason = reason; cur.device = dev; cur.rev = ++rev; cur.at = now
      return if (claiming) granted(version, deleted = true) else ok()
    }

    fun port(dev: String, online: () -> Boolean) = object : HeldSyncEngine.Port {
      override suspend fun pull(since: Long): JsonObject? {
        if (!online()) return null
        if (!enabled) return buildJsonObject { put("enabled", JsonPrimitive(false)) }
        val page = rows.values.filter { it.rev > since && (it.body != null || since > 0) }.sortedBy { it.rev }
        return buildJsonObject {
          put("enabled", JsonPrimitive(true)); put("rev", JsonPrimitive(rev))
          put("items", buildJsonArray { page.filter { it.body != null }.forEach { add(view(it)) } })
          put("gone", buildJsonArray { page.filter { it.body == null }.forEach { add(view(it)) } })
        }
      }

      override suspend fun put(id: String, body: JsonObject): HeldSyncEngine.Reply {
        if (!online()) return HeldSyncEngine.Reply(0)
        if (!enabled) return no(403, err("held_sync_disabled"))
        val v = body["version"]!!.jsonPrimitive.long
        val b = body["body"] as JsonObject
        val cur = rows[id]
        if (cur != null && cur.version >= v) return if (cur.body == b && cur.version == v) ok() else no(409, err("held_stale", "current" to view(cur)))
        if (cur == null) rows[id] = Row(id, v, b, dev, "", ++rev, now)
        else { cur.version = v; cur.body = b; cur.reason = ""; cur.device = dev; cur.rev = ++rev; cur.at = now }
        return ok()
      }

      override suspend fun delete(id: String, version: Long, reason: String): HeldSyncEngine.Reply =
        if (!online()) HeldSyncEngine.Reply(0) else remove(dev, id, version, reason, null, claiming = false, forcing = false)

      override suspend fun delete(id: String, version: Long, reason: String, base: Long): HeldSyncEngine.Reply =
        if (!online()) HeldSyncEngine.Reply(0) else remove(dev, id, version, reason, base, claiming = false, forcing = false)

      override suspend fun claim(id: String, version: Long, base: Long, force: Boolean): HeldSyncEngine.Reply {
        if (!online()) return HeldSyncEngine.Reply(0)
        claims++
        return remove(dev, id, version, "resumed", base, claiming = true, forcing = force)
      }
    }
  }

  /** یک دستگاه: دفتر، سبد، وضع و موتور — همان سیم‌کشیِ `HeldSync` (شناسهٔ دستگاه و کلیدِ تازهٔ فروش) */
  inner class Device(val server: Server, val dev: String, series: String) {
    var now = 1_700_000_000_000L
    var online = true
    private var keys = 0
    val cart = HeldInvoicesTest.MemCart()
    val store = HeldInvoiceStore(HeldInvoicesTest.MemDisk(), device = { series }, clock = { now })
    val state = HeldSyncTest.MemState()
    val engine = HeldSyncEngine(
      store, server.port(dev) { online }, state, clock = { now }, series = { series }, activeKey = { cart.snapshot().key },
      deviceId = { dev }, newKey = { "$dev-k${++keys}" },
    )
    init { server.series[dev] = series; store.onChange = { b, a, why -> engine.changed(b, a, why) } }
    fun sync() = runBlocking { now += 1000; server.now = now; engine.cycle() }
  }

  private suspend fun hold(d: Device, key: String, label: String) {
    d.now += 1000
    d.cart.put(key, "p1" to 2.0)
    assertTrue(d.store.hold(d.cart, HeldExtras(), label = label) is HeldInvoiceStore.Outcome.Done)
  }

  /** همان راهِ `HeldInvoicesUi.resume`: پرسش ⇐ بازگرداندن (با شناسهٔ تازه اگر Rekey)؛ Held ⇒ هیچ (برگهٔ تأیید) */
  private suspend fun resume(d: Device, id: String, force: Boolean = false): HeldClaim {
    d.now += 1000
    d.server.now = d.now
    val c = d.engine.claim(id, force)
    val target = when (c) { is HeldClaim.Held -> return c; is HeldClaim.Rekey -> c.id; HeldClaim.Free -> id }
    assertTrue(d.store.resume(d.cart, target, HeldExtras()) is HeldInvoiceStore.Outcome.Done)
    return c
  }

  @Test
  fun `پرچمِ خاموش — بازگرداندن هیچ پرسشی ندارد و همان پیشین است`() = runBlocking<Unit> {
    val server = Server().apply { enabled = false }
    val a = Device(server, "dev-a", "A1")
    hold(a, "K0", "کریمی")
    a.sync()
    assertEquals(HeldClaim.Free, resume(a, "K0"))
    assertEquals("K0", a.cart.snapshot().key)
    assertEquals("هیچ درخواستِ ادعا", 0, server.claims)
    assertEquals("وضعِ همگام‌سازی نوشته نشد", HeldSyncState(), a.state.s)
  }

  @Test
  fun `دستگاهِ دوم در دستِ دستگاهِ A1 را می‌بیند، فقط با تأیید و با کلیدِ تازه می‌گیرد و دستگاهِ نخست هم می‌بیند`() = runBlocking<Unit> {
    val server = Server()
    val a = Device(server, "dev-a", "A1")
    val b = Device(server, "dev-b", "B1")
    hold(a, "K1", "کریمی")
    a.sync(); b.sync()
    assertEquals(setOf("K1"), b.store.book.value.items.map { it.id }.toSet())

    assertEquals(HeldClaim.Free, resume(a, "K1"))
    assertEquals("K1", a.cart.snapshot().key)
    a.sync()
    //  «ب» هنوز دور نزده و برگه را در فهرست دارد ⇒ پرسش ⇒ در دستِ «A1»، هیچ چیزی باز نشد
    val held = resume(b, "K1")
    assertEquals(HeldClaim.Held("A1", "resumed", fresh = true), held)
    assertEquals(null, b.cart.snapshot().key)
    assertEquals(listOf("K1"), b.store.book.value.items.map { it.id })
    assertEquals("dev-a", server.rows.getValue("K1").device)

    //  تأییدِ صریح ⇒ گرفتن، با کلیدِ تازهٔ فروش (اگر «الف» هم بفروشد، دو فروشِ جدا؛ نه یک ردیفِ آمیخته)
    val took = resume(b, "K1", force = true)
    assertTrue(took is HeldClaim.Rekey)
    val bKey = b.cart.snapshot().key
    assertEquals((took as HeldClaim.Rekey).id, bKey)
    assertNotEquals("K1", bKey)
    assertEquals("dev-b", server.rows.getValue("K1").device)
    assertEquals("همان ردیف‌ها", a.cart.snapshot().lines, b.cart.snapshot().lines)

    //  «الف» که همان را در صندوق دارد با دورِ بعد می‌بیند
    a.sync()
    assertEquals(mapOf("K1" to HeldConflict("کریمی", "B1", "resumed", a.now)), a.engine.conflicts())
    a.engine.ack("K1")
    assertTrue(a.engine.conflicts().isEmpty())
    b.sync()
    assertTrue("«ب» خودش گرفت؛ دوتایی برای خودش نیست", b.engine.conflicts().isEmpty())
  }

  @Test
  fun `بی شبکه باز کردن آزاد است و پس از وصل یک فاکتور در دو دستگاه دیده می‌شود — با دو شناسهٔ جدا`() = runBlocking<Unit> {
    val server = Server()
    val a = Device(server, "dev-a", "A1")
    val b = Device(server, "dev-b", "B1")
    hold(a, "K2", "آقای رحیمی")
    a.sync(); b.sync()

    b.online = false
    val off = resume(b, "K2")
    assertTrue("بی شبکه هم باز می‌شود", off is HeldClaim.Rekey)
    val bKey = b.cart.snapshot().key!!
    assertNotEquals("K2", bKey)
    //  همان وقت «الف» با شبکه برش گرداند
    assertEquals(HeldClaim.Free, resume(a, "K2"))
    a.sync()

    b.online = true
    b.sync()
    val seen = b.engine.conflicts()
    assertEquals(setOf("K2"), seen.keys)
    assertEquals(Triple("آقای رحیمی", "A1", "resumed"), seen.getValue("K2").let { Triple(it.title, it.series, it.reason) })
    assertEquals("ردیفِ سرور همان «الف» ماند", "dev-a", server.rows.getValue("K2").device)
    assertTrue("صف خالی شد", b.state.s.out.isEmpty())
    //  دو فروشِ ممکن هرگز یک شناسه نیستند
    assertNotEquals(a.cart.snapshot().key, b.cart.snapshot().key)
  }

  @Test
  fun `نسخهٔ دیده‌شده کهنه بود — پیش از بازگرداندن تازه‌تر نشانده می‌شود و همان باز می‌شود`() = runBlocking<Unit> {
    val server = Server()
    val a = Device(server, "dev-a", "A1")
    val b = Device(server, "dev-b", "B1")
    hold(a, "K4", "کهنه")
    a.sync(); b.sync()
    a.now += 1000
    assertTrue(a.store.rename("K4", "تازه", "منتظر") is HeldInvoiceStore.Outcome.Done)
    a.sync()
    assertEquals(HeldClaim.Free, resume(b, "K4"))
    assertEquals("K4", b.cart.snapshot().key)
    assertEquals(Pair("تازه", "منتظر"), b.store.book.value.current?.let { it.label to it.note })
    assertEquals("dev-b", server.rows.getValue("K4").device)
  }

  @Test
  fun `برنامهٔ پیشین بایت‌به‌بایت — کارِ بی base و بی دوتایی همان شکلِ پیشینِ وضع را می‌نویسد`() {
    val plain = HeldSyncState(on = true, cursor = 3, out = mapOf("K5" to HeldSyncState.Op(del = true, v = 9, why = "resumed")), known = mapOf("K5" to 9))
    val text = HeldSyncEngine.encodeState(plain)
    for (k in listOf("base", "title", "conflicts")) assertTrue("«$k» نوشته نشد: $text", !text.contains("\"$k\""))
    assertEquals(plain, HeldSyncEngine.decodeState(text))
    val rich = plain.copy(
      out = mapOf("K5" to HeldSyncState.Op(del = true, v = 9, why = "resumed", base = 7, title = "کریمی")),
      conflicts = mapOf("K5" to HeldConflict("کریمی", "A1", "resumed", 5)),
    )
    assertEquals(rich, HeldSyncEngine.decodeState(HeldSyncEngine.encodeState(rich)))
  }

  @Test
  fun `کلیدِ تازه فقط همان نگه‌داشته را با همان شماره و ردیف‌ها عوض می‌کند و روی شناسهٔ گرفته هیچ`() = runBlocking<Unit> {
    val server = Server().apply { enabled = false }
    val a = Device(server, "dev-a", "A1")
    hold(a, "K6", "کریمی")
    hold(a, "K7", "رحیمی")
    val before = a.store.book.value
    assertTrue(!a.store.rekey("K6", "K7"))
    assertTrue(!a.store.rekey("نبود", "K8"))
    assertEquals(before, a.store.book.value)
    assertTrue(a.store.rekey("K6", "K8"))
    val after = a.store.book.value.items.associateBy { it.id }
    assertEquals(setOf("K8", "K7"), after.keys)
    assertEquals(before.items.first { it.id == "K6" }.copy(id = "K8"), after.getValue("K8"))
  }

  @Test
  fun `نگه‌داشته‌ای که فقط در صف بود و هرگز به سرور نرسید — بی شبکه باز می‌شود و پس از وصل نه دوتایی، نه ردیفِ زنده`() = runBlocking<Unit> {
    val server = Server()
    val a = Device(server, "dev-a", "A1")
    a.sync()
    a.online = false
    hold(a, "K9", "تازه")
    assertTrue("در صف (نه هنوز روی سرور)", a.state.s.out.getValue("K9").let { !it.del })
    val off = resume(a, "K9")
    assertTrue(off is HeldClaim.Rekey)
    val key = a.cart.snapshot().key!!
    assertNotEquals("K9", key)
    a.online = true
    a.sync()
    assertTrue(a.engine.conflicts().isEmpty())
    assertTrue(a.state.s.out.isEmpty())
    assertTrue("نه K9 و نه کلیدِ تازه روی سرور زنده نیست", server.rows.values.none { it.body != null })
    assertEquals(key, a.cart.snapshot().key)
  }
}
