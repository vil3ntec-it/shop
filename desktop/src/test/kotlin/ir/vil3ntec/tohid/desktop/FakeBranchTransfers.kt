package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.core.net.TestHttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 *  سرورِ ساختگیِ `/api/shop/catalog/branch-transfers` با حالت — همان قاعده‌های `server/src/lib/branch-transfers.js` تا جایی که
 *  صفحه‌ها می‌بینند (این دستگاه = شعبهٔ اصلی): دریافتِ پایانی/ناقص، رد، ارسال و لغوِ پیش‌نویس/فرستاده، کلیدِ یکتا (همان کلید ⇒
 *  همان پاسخ) و هر نوشتن در [writes].
 */
internal class FakeBranchTransfers(private val server: TestHttpServer) {
  companion object {
    const val ROOT = "/api/v1/shop/catalog/branch-transfers"
    const val A = "brn_aaaaaaaaaaaaaaaaaaaaaaaa"
    const val B = "brn_bbbbbbbbbbbbbbbbbbbbbbbb"
    fun id(n: Int) = "bxf_" + n.toString().padStart(24, '0')
  }

  class Write(val method: String, val path: String, val body: JsonObject, val key: String?)

  data class T(
    val id: String, val out: Boolean, val status: String, val lines: List<Triple<String, Long, Long?>>, val note: String = "",
    val reason: String = "", val sentAt: Long? = 1_763_000_000_000, val returned: Boolean = false,
  )

  private val lock = Any()
  private val ts = LinkedHashMap<String, T>()
  private val keys = HashMap<String, String>()
  private val writes = ArrayList<Write>()
  private var next = 100

  fun writes(): List<Write> = synchronized(lock) { writes.toList() }
  fun writes(suffix: String): List<Write> = writes().filter { it.path.endsWith(suffix) }
  fun get(id: String): T? = synchronized(lock) { ts[id] }

  fun seed(vararg t: T) = synchronized(lock) { t.forEach { ts[it.id] = it; routes(it.id) } }

  init {
    server.on("/api/v1/shop/catalog/config") { 200 to """{"enabled":true,"permissions":{"move":true,"view":true}}""" }
    server.on("$ROOT/destinations") { 200 to buildJsonObject {
      put("branch", "")
      put("destinations", buildJsonArray {
        add(buildJsonObject { put("id", A); put("name", "الف"); put("main", false) })
        add(buildJsonObject { put("id", B); put("name", "ب"); put("main", false) })
      })
    }.toString() }
    server.on(ROOT) { r ->
      if (r.method == "POST") create(r) else 200 to buildJsonObject {
        put("branch", "")
        put("transfers", buildJsonArray { synchronized(lock) { ts.values.reversed().forEach { add(view(it)) } } })
      }.toString()
    }
    for (n in 100..110) routes(id(n))
  }

  private fun view(t: T): JsonObject = buildJsonObject {
    val open = t.status == "in_transit" || t.status == "partial"
    put("id", t.id)
    put("direction", if (t.out) "out" else "in")
    put("fromBranch", buildJsonObject { if (t.out) { put("id", ""); put("name", ""); put("main", true) } else { put("id", A); put("name", "الف"); put("main", false) } })
    put("toBranch", buildJsonObject { if (t.out) { put("id", B); put("name", "ب"); put("main", false) } else { put("id", ""); put("name", ""); put("main", true) } })
    put("from", "shop_floor"); put("to", "warehouse"); put("status", t.status)
    put("lines", buildJsonArray {
      t.lines.forEach { (pid, sent, got) ->
        add(buildJsonObject {
          put("productId", pid); put("sentMilli", sent); put("receivedMilli", got)
          put("pendingMilli", if (open) sent - (got ?: 0) else 0)
          put("shortMilli", if (t.status == "received") sent - (got ?: 0) else 0)
          put("returnedMilli", if (t.status == "rejected" || (t.status == "cancelled" && t.sentAt != null)) sent - (got ?: 0) else 0)
        })
      }
    })
    put("note", t.note); put("reason", t.reason); put("createdBy", "usr_1"); put("createdAt", 1_763_000_000_000)
    put("sentBy", if (t.sentAt != null) "usr_1" else ""); put("sentAt", t.sentAt)
    put("closedBy", ""); put("closedAt", null as Long?)
  }

  private fun answer(t: T): Pair<Int, String> = 200 to buildJsonObject { put("transfer", view(t)); put("steps", buildJsonArray { }) }.toString()

  private fun body(r: TestHttpServer.Request): JsonObject = runCatching { Json.parseToJsonElement(r.body).jsonObject }.getOrDefault(JsonObject(emptyMap()))

  /** همان کلید ⇒ همان پاسخ */
  private fun keyed(r: TestHttpServer.Request, act: () -> Pair<Int, String>): Pair<Int, String> = synchronized(lock) {
    val key = r.headers["idempotency-key"]
    writes += Write(r.method, r.path, body(r), key)
    if (key != null) keys[key]?.let { return@synchronized 200 to it }
    val out = act()
    if (out.first in 200..299 && key != null) keys[key] = out.second
    out
  }

  private fun create(r: TestHttpServer.Request): Pair<Int, String> = keyed(r) {
    val b = body(r)
    val lines = b["lines"]!!.jsonArray.map { val o = it.jsonObject; Triple(o["productId"]!!.jsonPrimitive.content, o["qtyMilli"]!!.jsonPrimitive.longOrNull ?: 0L, null as Long?) }
    val draft = b["draft"]?.jsonPrimitive?.booleanOrNull == true
    val t = T(id(next++), out = true, status = if (draft) "draft" else "in_transit", lines = lines, note = b["note"]?.jsonPrimitive?.content.orEmpty(),
      sentAt = if (draft) null else 1_763_000_100_000)
    ts[t.id] = t
    201 to buildJsonObject { put("transfer", view(t)); put("replayed", false) }.toString()
  }

  private fun routes(id: String) {
    server.on("$ROOT/$id") { r -> if (r.method == "PUT") keyed(r) { edit(id, r) } else synchronized(lock) { ts[id] }?.let(::answer) ?: (404 to """{"error":{"code":"transfer_not_found","message":"انتقال پیدا نشد"}}""") }
    server.on("$ROOT/$id/receive") { r -> keyed(r) { receive(id, r) } }
    server.on("$ROOT/$id/reject") { r -> keyed(r) { close(id, "rejected", body(r)["reason"]?.jsonPrimitive?.content.orEmpty()) } }
    server.on("$ROOT/$id/send") { r -> keyed(r) { val t = ts[id]!!; if (t.status != "draft") 409 to err("transfer_bad_state") else keep(t.copy(status = "in_transit", sentAt = 1_763_000_200_000)) } }
    server.on("$ROOT/$id/cancel") { r ->
      keyed(r) {
        val t = ts[id]!!
        val why = body(r)["reason"]?.jsonPrimitive?.content.orEmpty()
        when {
          t.status != "draft" && t.status != "in_transit" -> 409 to err("transfer_closed")
          t.status == "in_transit" && why.isBlank() -> 400 to err("reason_required")
          else -> keep(t.copy(status = "cancelled", reason = why))
        }
      }
    }
  }

  private fun err(code: String) = """{"error":{"code":"$code","message":"$code"}}"""
  private fun keep(t: T): Pair<Int, String> { ts[t.id] = t; return answer(t) }

  private fun edit(id: String, r: TestHttpServer.Request): Pair<Int, String> {
    val t = ts[id]!!
    if (t.status != "draft") return 409 to err("transfer_bad_state")
    val b = body(r)
    val lines = b["lines"]!!.jsonArray.map { val o = it.jsonObject; Triple(o["productId"]!!.jsonPrimitive.content, o["qtyMilli"]!!.jsonPrimitive.longOrNull ?: 0L, null as Long?) }
    return keep(t.copy(lines = lines, note = b["note"]?.jsonPrimitive?.content.orEmpty()))
  }

  private fun receive(id: String, r: TestHttpServer.Request): Pair<Int, String> {
    val t = ts[id]!!
    if (t.status != "in_transit" && t.status != "partial") return 409 to err("transfer_closed")
    val b = body(r)
    val final = b["final"]?.jsonPrimitive?.booleanOrNull ?: true
    val given = b["lines"]?.jsonArray.orEmpty().associate { val o = it.jsonObject; o["productId"]!!.jsonPrimitive.content to (o["receivedMilli"]!!.jsonPrimitive.longOrNull ?: 0L) }
    val lines = t.lines.map { (pid, sent, got) -> val left = sent - (got ?: 0); Triple(pid, sent, (got ?: 0) + (given[pid] ?: if (final) left else 0)) }
    val all = lines.all { it.third == it.second }
    return keep(t.copy(status = if (final || all) "received" else "partial", lines = lines))
  }

  private fun close(id: String, status: String, why: String): Pair<Int, String> {
    val t = ts[id]!!
    if (t.status != "in_transit" && t.status != "partial") return 409 to err("transfer_closed")
    if (why.isBlank()) return 400 to err("reason_required")
    return keep(t.copy(status = status, reason = why, lines = t.lines.map { (p, s, g) -> Triple(p, s, g ?: 0) }))
  }
}
