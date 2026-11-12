package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.core.net.TestHttpServer
import ir.vil3ntec.tohid.data.PoBillDto
import ir.vil3ntec.tohid.data.PoBillLineDto
import ir.vil3ntec.tohid.data.PoBilledDto
import ir.vil3ntec.tohid.data.PoConfigDto
import ir.vil3ntec.tohid.data.PoLineDto
import ir.vil3ntec.tohid.data.PoOrderDto
import ir.vil3ntec.tohid.data.PoPageDto
import ir.vil3ntec.tohid.data.PoPermsDto
import ir.vil3ntec.tohid.data.PoPostedDto
import ir.vil3ntec.tohid.data.PoProgressDto
import ir.vil3ntec.tohid.data.PoReceiptDto
import ir.vil3ntec.tohid.data.PoReceiptLineDto
import ir.vil3ntec.tohid.data.PoReceivedDto
import ir.vil3ntec.tohid.data.PoTotalsDto
import ir.vil3ntec.tohid.data.PurchaseOrderDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

/**
 *  سرورِ ساختگیِ `/api/shop/purchase-orders` با حالت — همان قاعده‌های سرور (`server/src/routes/purchase-orders.js` و
 *  `lib/purchase-order-core.js`) تا جایی که صفحه‌ها می‌بینند: وضعیتِ نمایشی از رسیدها (فرستاده ⇐ بخشی ⇐ کامل)، `allowed` ِ هر
 *  وضعیت، کلیدِ یکتای ساختن (`Idempotency-Key` ⇒ همان سفارش)، شناسهٔ یکتای رسید و فاکتور (تکرار ⇒ همان)، لغوِ فرستاده بی دلیل ⇒
 *  ۴۰۰ `reason_required`، فاکتور برای پیش‌نویس ⇒ ۴۰۹، و نقش (`perms`): کارِ مدیریتی بی `manage` و رسید بی `receive` ⇒ ۴۰۳.
 *  هر نوشتن در [writes] می‌ماند تا آزمون شمار و بدنه را بسنجد.
 */
internal class FakePurchaseOrders(
  private val server: TestHttpServer,
  @Volatile var perms: PoPermsDto = PoPermsDto(manage = true, receive = true),
  seed: List<PurchaseOrderDto> = emptyList(),
  /** شناسهٔ سفارش‌هایی که ساخته می‌شوند (مسیرهای سرورِ ساختگی پیشاپیش ثبت می‌شوند) */
  private val fresh: List<String> = listOf("po-new-1", "po-new-2"),
) {
  companion object {
    const val ROOT = "/api/v1/shop/purchase-orders"
    val json = Json { encodeDefaults = true; ignoreUnknownKeys = true; explicitNulls = false }
  }

  class Write(val method: String, val path: String, val body: JsonObject, val key: String?)

  private val lock = Any()
  private val orders = LinkedHashMap<String, PurchaseOrderDto>()
  private val keys = HashMap<String, String>()
  private val writes = ArrayList<Write>()
  private var no = 2000
  private var t = System.currentTimeMillis()

  fun writes(): List<Write> = synchronized(lock) { writes.toList() }
  fun writes(suffix: String): List<Write> = writes().filter { it.path.endsWith(suffix) }
  fun order(id: String): PurchaseOrderDto? = synchronized(lock) { orders[id] }

  init {
    seed.forEach { orders[it.id] = shape(it) }
    server.on("$ROOT/config") { 200 to json.encodeToString(PoConfigDto(enabled = true, permissions = perms)) }
    server.on(ROOT) { r -> if (r.method == "POST") create(r) else list(r) }
    (seed.map { it.id } + fresh).forEach(::routes)
  }

  private fun routes(id: String) {
    server.on("$ROOT/$id") { r -> if (r.method == "PUT") edit(id, r) else get(id) }
    server.on("$ROOT/$id/send") { r -> act(id, r, manage = true) { o -> if (o.state != "draft") conflict("bad_state") else ok(o.copy(state = "sent", sentAt = tick())) } }
    server.on("$ROOT/$id/cancel") { r ->
      act(id, r, manage = true) { o ->
        val why = r.str("reason")
        when {
          o.state == "cancelled" -> conflict("bad_state")
          o.state == "sent" && why.isBlank() -> 400 to err("reason_required")
          else -> ok(o.copy(state = "cancelled", cancelledAt = tick(), cancelReason = why.ifBlank { null }))
        }
      }
    }
    server.on("$ROOT/$id/receipts") { r -> act(id, r, manage = false, receive = true) { o -> receipt(o, r) } }
    server.on("$ROOT/$id/bills") { r -> act(id, r, manage = true) { o -> bill(o, r) } }
  }

  /* ── پاسخ‌ها ── */

  private fun err(code: String) = """{"error":{"code":"$code","message":"$code"}}"""
  private fun conflict(code: String) = 409 to err(code)
  private fun ok(o: PurchaseOrderDto): Pair<Int, String> {
    val s = shape(o)
    orders[s.id] = s
    return 200 to json.encodeToString(PoOrderDto(s, changed = true))
  }
  private fun tick(): Long { t += 1000; return t }

  private fun TestHttpServer.Request.obj(): JsonObject = if (body.isBlank()) JsonObject(emptyMap()) else Json.parseToJsonElement(body).jsonObject
  private fun TestHttpServer.Request.str(k: String): String = obj()[k]?.jsonPrimitive?.content.orEmpty()

  private fun get(id: String): Pair<Int, String> = synchronized(lock) {
    val o = orders[id] ?: return@synchronized 404 to err("not_found")
    200 to json.encodeToString(PoOrderDto(o))
  }

  private fun list(r: TestHttpServer.Request): Pair<Int, String> = synchronized(lock) {
    val state = r.param("state")
    val status = r.param("status")
    val shown = orders.values.toList().asReversed().filter { o ->
      (state == null || o.state == state) && (status == null || o.status == status || (status == "open" && o.status in setOf("sent", "partial")))
    }.map { it.copy(receipts = emptyList(), bills = emptyList()) }
    200 to json.encodeToString(PoPageDto(shown))
  }

  private fun act(id: String, r: TestHttpServer.Request, manage: Boolean, receive: Boolean = false, block: (PurchaseOrderDto) -> Pair<Int, String>): Pair<Int, String> =
    synchronized(lock) {
      writes += Write(r.method, r.path, r.obj(), r.headers["idempotency-key"])
      if (manage && !perms.manage) return@synchronized 403 to err("forbidden")
      if (receive && !perms.receive) return@synchronized 403 to err("forbidden")
      val o = orders[id] ?: return@synchronized 404 to err("not_found")
      block(o)
    }

  private fun create(r: TestHttpServer.Request): Pair<Int, String> = synchronized(lock) {
    val key = r.headers["idempotency-key"]
    writes += Write(r.method, r.path, r.obj(), key)
    if (!perms.manage) return@synchronized 403 to err("forbidden")
    if (key.isNullOrBlank()) return@synchronized 400 to err("idempotency_key_required")
    keys[key]?.let { return@synchronized 200 to json.encodeToString(PoOrderDto(orders.getValue(it), replayed = true)) }
    val id = fresh.firstOrNull { it !in orders } ?: return@synchronized 500 to err("no_fresh_id")
    val b = r.obj()
    val o = shape(
      PurchaseOrderDto(
        id = id, no = ++no, supplierId = b["supplierId"]!!.jsonPrimitive.content, supplierName = b["supplierName"]?.jsonPrimitive?.content.orEmpty(),
        state = "draft", lines = lines(b), note = b["note"]?.jsonPrimitive?.content.orEmpty(),
        expectedDate = b["expectedDate"]?.jsonPrimitive?.content, version = 1, createdAt = tick(),
      ),
    )
    orders[id] = o
    keys[key] = id
    201 to json.encodeToString(PoOrderDto(o, changed = true))
  }

  private fun edit(id: String, r: TestHttpServer.Request): Pair<Int, String> = act(id, r, manage = true) { o ->
    val b = r.obj()
    if (o.state != "draft") conflict("not_draft")
    else if (b["version"]?.jsonPrimitive?.longOrNull?.toInt() != o.version) conflict("version_conflict")
    else ok(o.copy(lines = lines(b), note = b["note"]?.jsonPrimitive?.content.orEmpty(), version = o.version + 1))
  }

  private fun lines(b: JsonObject): List<PoLineDto> = (b["lines"] as JsonArray).mapIndexed { i, e ->
    val l = e.jsonObject
    PoLineDto(
      id = l["id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: "l${i + 1}", productId = l["productId"]!!.jsonPrimitive.content,
      name = l["name"]?.jsonPrimitive?.content.orEmpty(), unit = l["unit"]?.jsonPrimitive?.content.orEmpty(),
      qtyMilli = l["qtyMilli"]!!.jsonPrimitive.long, priceMinor = l["priceMinor"]!!.jsonPrimitive.long,
    )
  }

  private fun receipt(o: PurchaseOrderDto, r: TestHttpServer.Request): Pair<Int, String> {
    val b = r.obj()
    val rid = b["id"]!!.jsonPrimitive.content
    o.receipts.firstOrNull { it.id == rid }?.let { return 200 to json.encodeToString(PoReceivedDto(o, it, replayed = true)) }
    if (o.state != "sent") return conflict("not_receivable")
    val byLine = o.lines.associateBy { it.id }
    val prog = o.progress.associateBy { it.lineId }
    val got = (b["lines"] as JsonArray).map { e ->
      val l = e.jsonObject
      PoReceiptLineDto(l["lineId"]!!.jsonPrimitive.content, l["receivedMilli"]!!.jsonPrimitive.long, l["damagedMilli"]!!.jsonPrimitive.long, l["priceMinor"]!!.jsonPrimitive.long)
    }
    val posted = got.map { g ->
      val line = byLine.getValue(g.lineId)
      val accepted = g.receivedMilli - g.damagedMilli
      val left = prog[g.lineId]?.remainingMilli ?: line.qtyMilli
      PoPostedDto(
        g.lineId, left, g.receivedMilli, g.damagedMilli, accepted, maxOf(0L, left - accepted), maxOf(0L, accepted - left),
        g.priceMinor, accepted * g.priceMinor / 1000,
      )
    }
    val rec = PoReceiptDto(rid, o.id, got, posted, posted.sumOf { it.totalMinor }, note = b["note"]?.jsonPrimitive?.content.orEmpty(), createdBy = "آزمون", createdAt = tick())
    val next = shape(o.copy(receipts = o.receipts + rec, receiptCount = o.receiptCount + 1, firstReceiptAt = o.firstReceiptAt ?: rec.createdAt))
    orders[o.id] = next
    return 201 to json.encodeToString(PoReceivedDto(next, rec))
  }

  private fun bill(o: PurchaseOrderDto, r: TestHttpServer.Request): Pair<Int, String> {
    val b = r.obj()
    val bid = b["id"]!!.jsonPrimitive.content
    o.bills.firstOrNull { it.id == bid }?.let { return 200 to json.encodeToString(PoBilledDto(o, it, replayed = true)) }
    if (o.state == "draft") return conflict("po_not_sent")
    val billNo = b["billNo"]!!.jsonPrimitive.content
    if (o.bills.any { it.active && it.billNo == billNo }) return conflict("bill_no_taken")
    val lines = (b["lines"] as JsonArray).map { e ->
      val l = e.jsonObject
      PoBillLineDto(l["lineId"]?.jsonPrimitive?.content, l["productId"]!!.jsonPrimitive.content, l["qtyMilli"]!!.jsonPrimitive.long, l["totalMinor"]!!.jsonPrimitive.long)
    }
    val bill = PoBillDto(bid, o.id, billNo, b["billDate"]?.jsonPrimitive?.content, lines, lines.sumOf { it.totalMinor }, createdBy = "آزمون", createdAt = tick())
    val next = shape(o.copy(bills = o.bills + bill))
    orders[o.id] = next
    routeVoid(o.id, bid)
    return 201 to json.encodeToString(PoBilledDto(next, bill, changed = true))
  }

  /** مسیرِ «باطل» ِ هر فاکتور (همان شناسه‌ای که ساخته شد یا از آغاز بود) */
  private fun routeVoid(id: String, billId: String) {
    server.on("$ROOT/$id/bills/$billId/void") { r ->
      act(id, r, manage = true) { o ->
        val why = r.str("reason")
        val b = o.bills.firstOrNull { it.id == billId } ?: return@act 404 to err("not_found")
        when {
          why.isBlank() -> 400 to err("reason_required")
          !b.active -> 200 to json.encodeToString(PoBilledDto(o, b, replayed = true))
          else -> {
            val done = b.copy(voidedAt = tick(), voidReason = why)
            val next = shape(o.copy(bills = o.bills.map { if (it.id == billId) done else it }))
            orders[o.id] = next
            200 to json.encodeToString(PoBilledDto(next, done, changed = true))
          }
        }
      }
    }
  }

  /** پیشرفت، جمع‌ها، وضعیتِ نمایشی و کارهای مجاز از ردیف‌ها و رسیدها — همان `purchase-order-core.js` ‏`status`/`progress` */
  private fun shape(o: PurchaseOrderDto): PurchaseOrderDto {
    o.bills.forEach { routeVoid(o.id, it.id) }
    val rec = o.receipts.flatMap { it.posted }.groupBy { it.lineId }
    val progress = o.lines.map { l ->
      val ps = rec[l.id].orEmpty()
      val received = ps.sumOf { it.receivedMilli }
      val damaged = ps.sumOf { it.damagedMilli }
      val accepted = ps.sumOf { it.acceptedMilli }
      PoProgressDto(l.id, l.productId, l.qtyMilli, received, damaged, accepted, maxOf(0L, l.qtyMilli - accepted), maxOf(0L, accepted - l.qtyMilli))
    }
    val ordered = o.lines.sumOf { it.qtyMilli * it.priceMinor / 1000 }
    val accepted = o.lines.zip(progress).sumOf { (l, p) -> minOf(p.acceptedMilli, l.qtyMilli) * l.priceMinor / 1000 }
    val status = when {
      o.state == "draft" -> "draft"
      o.state == "cancelled" -> "cancelled"
      progress.isNotEmpty() && progress.all { it.remainingMilli == 0L } -> "complete"
      progress.any { it.acceptedMilli > 0 } -> "partial"
      else -> "sent"
    }
    val allowed = when (status) {
      "draft" -> listOf("edit", "send", "cancel")
      "sent", "partial" -> listOf("receive", "cancel")
      else -> emptyList()
    }
    return o.copy(
      status = status, allowed = allowed, progress = progress, totals = PoTotalsDto(ordered, accepted, ordered - accepted),
      billCount = o.bills.count { it.active }, updatedAt = maxOf(o.updatedAt, o.createdAt),
    )
  }
}
