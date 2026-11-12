package ir.vil3ntec.tohid.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  S09 / POS-12 — پشتیبانِ سرورِ فاکتورهای نگه‌داشته (`HeldSyncEngine`) با سرورِ ساختگیِ همان قراردادِ
 *  `/api/shop/held-invoices` (`server/test/shop-held-invoices.test.js` خودِ سرور را می‌سنجد).
 *
 *  پرچمِ خاموش ⇒ هیچ نوشتن و هیچ فرستادنی و فایلِ نگه‌داشته‌ها بایت‌به‌بایت همان دستگاهِ بی پشتیبان؛ روشن ⇒ نگه‌داشتهٔ
 *  دستگاهِ «الف» با همهٔ فیلدهایش روی نصبِ تازهٔ «ب» برمی‌گردد، بازگرداندن آن را از «الف» هم برمی‌دارد، نوشتنِ کهنه
 *  هرگز روی تازه نمی‌نشیند، و هیچ نگه‌داشته‌ای (حتی وقتی جا نیست) گم نمی‌شود.
 */
class HeldSyncTest {

  /** همان قاعده‌های `lib/shop-held-sync.js` — بی پایگاه */
  class Server {
    var enabled = true
    var rev = 0L
    class Row(val id: String, var version: Long, var body: JsonObject?, var heldBy: String, var rev: Long)
    val rows = LinkedHashMap<String, Row>()
    var puts = 0
    var deletes = 0
    var pulls = 0

    private fun err(code: String, current: JsonObject? = null) = buildJsonObject {
      put("code", JsonPrimitive(code)); if (current != null) put("current", current)
    }

    private fun view(r: Row, me: String) = buildJsonObject {
      put("id", JsonPrimitive(r.id)); put("version", JsonPrimitive(r.version)); put("rev", JsonPrimitive(r.rev))
      if (r.body == null) put("deleted", JsonPrimitive(true))
      else { put("body", r.body!!); put("mine", JsonPrimitive(r.heldBy == me)) }
    }

    fun port(user: String, cancel: Boolean = false) = object : HeldSyncEngine.Port {
      override suspend fun pull(since: Long): JsonObject {
        pulls++
        if (!enabled) return buildJsonObject { put("enabled", JsonPrimitive(false)) }
        val page = rows.values.filter { it.rev > since && (it.body != null || since > 0) }.sortedBy { it.rev }
        return buildJsonObject {
          put("enabled", JsonPrimitive(true))
          put("rev", JsonPrimitive(rev))
          put("items", buildJsonArray { page.filter { it.body != null }.forEach { add(view(it, user)) } })
          put("gone", buildJsonArray { page.filter { it.body == null }.forEach { add(view(it, user)) } })
          put("permissions", buildJsonObject { put("cancel", JsonPrimitive(cancel)) })
        }
      }

      override suspend fun put(id: String, body: JsonObject): HeldSyncEngine.Reply {
        puts++
        if (!enabled) return HeldSyncEngine.Reply(403, error = err("held_sync_disabled"))
        val v = body["version"]!!.jsonPrimitive.long
        val b = body["body"] as JsonObject
        val cur = rows[id]
        if (cur != null && cur.version >= v) {
          return if (cur.body == b && cur.version == v) HeldSyncEngine.Reply(200) else HeldSyncEngine.Reply(409, error = err("held_stale", view(cur, user)))
        }
        rev++
        if (cur == null) rows[id] = Row(id, v, b, user, rev)
        else { if (cur.body == null) cur.heldBy = user; cur.version = v; cur.body = b; cur.rev = rev }
        return HeldSyncEngine.Reply(200)
      }

      override suspend fun delete(id: String, version: Long, reason: String): HeldSyncEngine.Reply {
        deletes++
        if (!enabled) return HeldSyncEngine.Reply(403, error = err("held_sync_disabled"))
        val cur = rows[id] ?: return HeldSyncEngine.Reply(200)
        if (cur.body == null) return HeldSyncEngine.Reply(200)
        if (cur.version > version) return HeldSyncEngine.Reply(409, error = err("held_stale", view(cur, user)))
        if (reason == "deleted" && cur.heldBy != user && !cancel) return HeldSyncEngine.Reply(403, error = err("held_not_yours"))
        rev++
        cur.version = version; cur.body = null; cur.rev = rev
        return HeldSyncEngine.Reply(200)
      }
    }
  }

  class MemState : HeldSyncEngine.StateStore {
    var s = HeldSyncState()
    var writes = 0
    override fun read() = s
    override fun update(f: (HeldSyncState) -> HeldSyncState): HeldSyncState = synchronized(this) {
      val next = f(s)
      if (next != s) { writes++; s = next }
      next
    }
  }

  /** یک دستگاه: دفتر، سبد، وضع و موتور (و صدا زدنِ موتور با هر تغییر، همان `HeldSync.attach`) */
  inner class Device(val server: Server, user: String, val series: String, disk: HeldInvoicesTest.MemDisk = HeldInvoicesTest.MemDisk(), cancel: Boolean = false) {
    var now = 1_700_000_000_000L
    val disk = disk
    val cart = HeldInvoicesTest.MemCart()
    val store = HeldInvoiceStore(disk, device = { series }, clock = { now })
    val state = MemState()
    val engine = HeldSyncEngine(store, server.port(user, cancel), state, clock = { now }, series = { series }, activeKey = { cart.snapshot().key })
    init { store.onChange = { b, a, why -> engine.changed(b, a, why) } }
    fun sync() = runBlocking { engine.cycle() }
    fun ids() = store.book.value.items.map { it.id }.toSet()
  }

  private val cart = listOf(
    SalesEngine.CartLine("p1", 2.0, unitPrice = 95.0, lotId = "lot-7", note = "بی‌نمک", serials = listOf("SN-1"), specialPrice = 95.0, seller = "زهرا"),
    SalesEngine.CartLine("f1", 1.0, unitPrice = 5.0, label = "کیسه"),
  )
  private fun draft(key: String) = SaleDraft.of(key, cart, "d1", 42L, coupons = listOf("EID5"), note = "فاکتورِ عمده", priceList = "wholesale")
  private fun bytes(d: SaleDraft) = SaleDraftCodec.encode(d.copy(savedAt = 0))

  private suspend fun hold(dev: Device, key: String, label: String = "", seller: String = "") {
    dev.cart.mem = draft(key); dev.cart.disk = dev.cart.mem
    assertTrue(dev.store.hold(dev.cart, HeldExtras(discount = "10", seller = seller), label = label) is HeldInvoiceStore.Outcome.Done)
  }

  @Test
  fun `پرچمِ خاموش — هیچ نوشتن و فرستادنی؛ فایلِ نگه‌داشته‌ها بایت‌به‌بایت همان دستگاهِ بی پشتیبان`() = runBlocking<Unit> {
    val server = Server().apply { enabled = false }
    val a = Device(server, "u-a", "S-A")
    val plain = HeldInvoiceStore(HeldInvoicesTest.MemDisk(), device = { "S-A" }, clock = { a.now })
    val plainCart = HeldInvoicesTest.MemCart()
    a.sync()
    for ((s, c) in listOf(a.store to a.cart, plain to plainCart)) {
      c.mem = draft("K1"); c.disk = c.mem
      s.hold(c, HeldExtras("10"), label = "الف")
      c.mem = draft("K2"); c.disk = c.mem
      s.hold(c, HeldExtras())
      s.rename("K1", "الفِ تازه", "منتظر")
      s.resume(c, "K2", HeldExtras())
      s.discard("K1")
    }
    a.sync()
    assertEquals(0, server.puts + server.deletes)
    assertEquals(2, server.pulls)
    assertEquals("وضعِ همگام‌سازی هرگز نوشته نشد", 0, a.state.writes)
    assertEquals(HeldSyncState(), a.state.s)
    assertEquals((plain.book.value), a.store.book.value)
    val plainText = HeldInvoiceCodec.encode(plain.book.value)
    assertEquals(plainText, a.disk.text)
    assertFalse(a.engine.othersHeld("K1"))
  }

  @Test
  fun `روشن — نگه‌داشتهٔ «الف» با همهٔ فیلدهایش روی نصبِ تازهٔ «ب» برمی‌گردد و بازگرداندنش از «الف» هم برش می‌دارد`() = runBlocking<Unit> {
    val server = Server()
    val a = Device(server, "u-a", "S-A")
    a.sync()
    assertTrue(a.state.s.on)
    hold(a, "K1", label = "آقای کریمی", seller = "مریم")
    a.sync()
    assertEquals(setOf("K1"), server.rows.keys)
    //  نصبِ تازه روی دستگاهِ «ب» (فایلِ خالی)
    val b = Device(server, "u-b", "S-B")
    val r = b.sync()
    assertEquals(1, r.absorbed)
    assertEquals(setOf("K1"), b.ids())
    val heldA = a.store.book.value.items.single()
    val heldB = b.store.book.value.items.single()
    assertEquals(heldA, heldB)
    //  INV-03: نگه‌داشتهٔ دستگاهِ دیگر از «قابل‌فروش» ِ کاشیِ صندوقِ «ب» هم کم می‌شود (همان `HeldStock.reserved` ِ کاشی)
    assertEquals(2.0, HeldStock.reserved(b.store.book.value)["p1"])
    assertEquals(HeldStock.reserved(a.store.book.value), HeldStock.reserved(b.store.book.value))
    assertTrue("نگه‌داشتهٔ «الف» برای «ب» مالِ کسِ دیگر است", b.engine.othersHeld("K1"))
    //  بازگشت روی «ب» ⇒ سبدِ «ب» بایت‌به‌بایت همان فاکتورِ «الف»
    b.now += 1000
    val back = b.store.resume(b.cart, "K1", HeldExtras())
    assertTrue(back is HeldInvoiceStore.Outcome.Done)
    //  فروشندهٔ فاکتورِ اصلاح‌شده (`HeldInvoice.seller`) هم از «الف» به «ب» می‌رسد
    assertEquals(HeldExtras("10", seller = "مریم"), (back as HeldInvoiceStore.Outcome.Done).extras)
    assertEquals(bytes(draft("K1")), bytes(b.cart.snapshot()))
    assertEquals(cart, b.cart.snapshot().cart())
    b.sync()
    assertNull("سنگِ قبر", server.rows.getValue("K1").body)
    //  «الف» با دورِ بعد می‌بیند که برگشت
    a.sync()
    assertEquals(emptySet<String>(), a.ids())
    //  پژواک نیست: نشاندنِ ردیفِ سرور چیزی به صف نیفزود
    assertTrue(a.state.s.out.isEmpty() && b.state.s.out.isEmpty())
  }

  @Test
  fun `نوشتنِ کهنه هرگز روی تازه نمی‌نشیند — «الف» ِ بی‌اینترنت پس از «ب» ِ تازه‌تر همان «ب» را می‌گیرد`() = runBlocking<Unit> {
    val server = Server()
    val a = Device(server, "u-a", "S-A")
    val b = Device(server, "u-b", "S-B")
    a.sync(); hold(a, "K1", "اول"); a.sync()
    b.sync()
    //  «الف» بی‌اینترنت نام را عوض می‌کند (نسخهٔ کهنه‌تر)، «ب» دیرتر و زودتر می‌فرستد
    a.now += 1_000
    a.store.rename("K1", "از الف", "")
    b.now += 5_000
    b.store.rename("K1", "از ب", "")
    b.sync()
    a.sync()
    assertEquals("از ب", a.store.book.value.items.single().label)
    assertEquals(b.state.s.known["K1"], server.rows.getValue("K1").version)
    //  «الف» دوباره چیزی نمی‌فرستد
    val before = server.puts
    a.sync()
    assertEquals(before, server.puts)
  }

  @Test
  fun `نگه‌داشته‌های پیش از روشن شدن فرستاده می‌شوند، ولی ماندهٔ حسابِ دیگر هرگز`() = runBlocking<Unit> {
    val server = Server().apply { enabled = false }
    val a = Device(server, "u-a", "S-A")
    hold(a, "MINE")
    //  نگه‌داشته‌ای با سریِ حسابِ دیگر (دکانِ پیشینِ همین گوشی)
    assertTrue(a.store.park(HeldInvoice(id = "OLD", number = 0, lines = listOf(SaleDraft.Line("p1", 1.0)), device = "S-OTHER")) is HeldInvoiceStore.Outcome.Done)
    a.sync()
    assertEquals(0, server.puts)
    server.enabled = true
    a.sync()
    assertEquals(setOf("MINE"), server.rows.keys)
    assertEquals(setOf("MINE", "OLD"), a.ids())
  }

  @Test
  fun `جا نیست — نگه‌داشتهٔ سرور گم نمی‌شود و پس از جا باز شدن می‌رسد`() = runBlocking<Unit> {
    val server = Server()
    val a = Device(server, "u-a", "S-A")
    a.sync()
    for (i in 1..8) hold(a, "A$i")
    a.sync()
    //  «ب» پیش از روشن شدنِ پشتیبان سه نگه‌داشتهٔ خودش را دارد
    val b = Device(server, "u-b", "S-B")
    for (i in 1..3) hold(b, "B$i")
    b.sync()
    assertEquals(11, server.rows.size)
    //  «ب» جا برای ۸ دارد (`MAX_OPEN` − سبد): ۳ ِ خودش + ۵ از «الف»؛ سه تای دیگر روی سرور منتظر
    assertEquals(8, b.store.book.value.items.size)
    assertTrue(b.state.s.refill)
    //  سه تای خودش را برمی‌گرداند و می‌فروشد ⇒ دورِ بعد بقیهٔ «الف»
    for (i in 1..3) { b.store.resume(b.cart, "B$i", HeldExtras()); b.cart.mem = null; b.cart.disk = null }
    b.sync()
    assertEquals((1..8).map { "A$it" }.toSet(), server.rows.values.filter { it.body != null }.map { it.id }.toSet())
    assertEquals((1..8).map { "A$it" }.toSet(), b.ids())
    assertFalse(b.state.s.refill)
  }

  @Test
  fun `دور انداختنِ نگه‌داشتهٔ کسِ دیگر بی لغوِ فاکتور روی سرور نمی‌نشیند؛ با آن می‌نشیند`() = runBlocking<Unit> {
    val server = Server()
    val a = Device(server, "u-a", "S-A")
    a.sync(); hold(a, "K1"); hold(a, "K2"); a.sync()
    val b = Device(server, "u-b", "S-B")
    val m = Device(server, "u-m", "S-M", cancel = true)
    b.sync(); m.sync()
    b.store.discard("K1")
    b.sync()
    assertTrue("سرور نگهش داشت", server.rows.getValue("K1").body != null)
    m.store.discard("K2")
    m.sync()
    assertNull(server.rows.getValue("K2").body)
    a.sync()
    assertEquals(setOf("K1"), a.ids())
    assertTrue(m.state.s.cancel)
    assertFalse(b.state.s.cancel)
  }

  @Test
  fun `پرچم پس از روشن بودن خاموش شد — وضع فراموش، دفترِ دستگاه دست‌نخورده`() = runBlocking<Unit> {
    val server = Server()
    val a = Device(server, "u-a", "S-A")
    a.sync(); hold(a, "K1"); a.sync()
    server.enabled = false
    a.store.rename("K1", "تازه", "")
    a.sync()
    assertEquals(HeldSyncState(), a.state.s)
    assertEquals(setOf("K1"), a.ids())
    assertEquals("تازه", a.store.book.value.items.single().label)
    val n = server.puts
    a.store.rename("K1", "تازه‌تر", "")
    a.sync()
    assertEquals(n, server.puts)
    //  سرور همان نسخهٔ پیش از خاموشی را دارد (چیزی پاک نشد)
    assertEquals("", HeldInvoiceCodec.decode("{\"items\":[${server.rows.getValue("K1").body}]}")!!.items.single().label)
  }
}
