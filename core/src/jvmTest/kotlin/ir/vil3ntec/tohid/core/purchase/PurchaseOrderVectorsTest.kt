package ir.vil3ntec.tohid.core.purchase

import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore.Bill
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore.Failure
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore.Line
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore.Order
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore.Outcome
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore.Receipt
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore.ReceiptLine
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore.ReceiveResult
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore.Tolerance
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/*
 *  همان بردارهای سرور (`core/test-vectors/purchase-orders-v1.json`، `server/test/purchase-order-core.test.js`)
 *  روی هستهٔ Kotlin. core وابستگیِ JSON ندارد، پس یک خوانندهٔ کوچکِ JSON همین‌جاست (فقط آزمون).
 */
class PurchaseOrderVectorsTest {

  private val v: Map<String, Any?> by lazy {
    val f = listOf(File("test-vectors/purchase-orders-v1.json"), File("core/test-vectors/purchase-orders-v1.json")).first { it.exists() }
    @Suppress("UNCHECKED_CAST")
    MiniJson(f.readText()).parse() as Map<String, Any?>
  }

  @Suppress("UNCHECKED_CAST") private fun obj(x: Any?) = x as Map<String, Any?>
  @Suppress("UNCHECKED_CAST") private fun arr(x: Any?) = x as List<Any?>
  private fun long(x: Any?) = x as Long
  private fun str(x: Any?) = x as String

  private fun order(x: Any?): Order = obj(x).let { o ->
    Order(str(o["supplierId"]), str(o["state"]), arr(o["lines"]).map { l ->
      obj(l).let { Line(str(it["id"]), str(it["productId"]), long(it["qtyMilli"]), long(it["priceMinor"])) }
    })
  }

  private fun receipt(x: Any?): Receipt = obj(x).let { r ->
    Receipt(str(r["id"]), arr(r["lines"]).map { l ->
      obj(l).let { ReceiptLine(str(it["lineId"]), long(it["receivedMilli"]), long(it["damagedMilli"]), long(it["priceMinor"])) }
    })
  }

  private fun failure(x: Any?): Failure? = (x as Map<*, *>?)?.let {
    Failure(it["code"] as String, it["lineId"] as String?, (it["index"] as Long).toInt())
  }

  private fun outcomeJson(r: Outcome): Map<String, Any?> = when (r) {
    is Outcome.Changed -> mapOf("changed" to r.state)
    Outcome.Unchanged -> mapOf("unchanged" to true)
    is Outcome.Failed -> mapOf("failure" to failureJson(r.failure))
  }

  private fun failureJson(f: Failure) = mapOf("code" to f.code, "lineId" to f.lineId, "index" to f.index.toLong())

  private fun progressJson(p: PurchaseOrderCore.Progress) = mapOf(
    "lineId" to p.lineId, "productId" to p.productId, "orderedMilli" to p.orderedMilli, "receivedMilli" to p.receivedMilli,
    "damagedMilli" to p.damagedMilli, "acceptedMilli" to p.acceptedMilli, "remainingMilli" to p.remainingMilli, "overMilli" to p.overMilli,
  )

  private fun postedJson(p: PurchaseOrderCore.Posted) = mapOf(
    "lineId" to p.lineId, "expectedMilli" to p.expectedMilli, "receivedMilli" to p.receivedMilli, "damagedMilli" to p.damagedMilli,
    "acceptedMilli" to p.acceptedMilli, "shortMilli" to p.shortMilli, "overMilli" to p.overMilli, "priceMinor" to p.priceMinor,
    "totalMinor" to p.totalMinor,
  )

  @Test fun mulDivRound() {
    val rows = arr(v["mulDivRound"])
    assertTrue(rows.size >= 15)
    for (row in rows) {
      val (a, b, c, want) = arr(row)
      if (want == "overflow") assertFailsWith<ArithmeticException>("$a×$b/$c") { PurchaseOrderCore.mulDivRound(long(a), long(b), long(c)) }
      else assertEquals(long(want), PurchaseOrderCore.mulDivRound(long(a), long(b), long(c)), "$a×$b/$c")
    }
  }

  @Test fun checkOrder() {
    for (c in arr(v["checkOrder"]).map(::obj)) {
      assertEquals(failure(c["failure"]), PurchaseOrderCore.checkOrder(order(c["order"])), str(c["name"]))
    }
  }

  @Test fun flows() {
    var steps = 0
    for (f in arr(v["flows"]).map(::obj)) {
      var o = order(f["order"])
      val receipts = ArrayList<Receipt>()
      arr(f["steps"]).map(::obj).forEachIndexed { i, st ->
        val at = "${f["name"]} — گامِ ${i + 1} (${st["do"]})"
        steps++
        when (st["do"]) {
          "status" -> assertEquals(st["expect"], PurchaseOrderCore.status(o, receipts).wire, at)
          "allowed" -> assertEquals(st["expect"], PurchaseOrderCore.allowed(PurchaseOrderCore.status(o, receipts))
            .sortedBy { it.ordinal }.map { it.wire }, at)
          "progress" -> assertEquals(st["expect"], PurchaseOrderCore.progress(o, receipts).map(::progressJson), at)
          "totals" -> assertEquals(st["expect"], PurchaseOrderCore.totals(o, receipts).let {
            mapOf("orderedMinor" to it.orderedMinor, "acceptedMinor" to it.acceptedMinor, "remainingMinor" to it.remainingMinor)
          }, at)
          "send", "cancel", "edit" -> {
            val r = when (st["do"]) {
              "send" -> PurchaseOrderCore.send(o, receipts)
              "cancel" -> PurchaseOrderCore.cancel(o, receipts, st["reason"] as String?)
              else -> PurchaseOrderCore.edit(o, receipts, order(st["next"]))
            }
            assertEquals(st["expect"], outcomeJson(r), at)
            if (r is Outcome.Changed) o = if (st["do"] == "edit") order(st["next"]) else o.copy(state = r.state)
          }
          "receive" -> {
            val r = PurchaseOrderCore.receive(o, receipts, receipt(st["receipt"]), st["allowOver"] == true)
            val want = obj(st["expect"])
            when {
              "failure" in want -> assertEquals(want["failure"], failureJson(assertIs<ReceiveResult.Failed>(r, at).failure), at)
              "duplicate" in want -> assertEquals(want["duplicate"], assertIs<ReceiveResult.Duplicate>(r, at).receipt.id, at)
              else -> {
                val ok = assertIs<ReceiveResult.Ok>(r, "$at: $r")
                assertEquals(want["ok"], mapOf("lines" to ok.lines.map(::postedJson), "totalMinor" to ok.totalMinor, "status" to ok.status.wire), at)
                receipts += ok.receipt
              }
            }
          }
          else -> fail("گامِ ناشناخته ${st["do"]}")
        }
      }
    }
    assertTrue(steps >= 60, "فقط $steps گام")
  }

  @Test fun match() {
    for (c in arr(v["match"]).map(::obj)) {
      val t = obj(c["tolerance"])
      val m = PurchaseOrderCore.match(
        order(c["order"]), arr(c["receipts"]).map(::receipt),
        arr(c["bills"]).map(::obj).map { Bill(it["lineId"] as String?, str(it["productId"]), long(it["qtyMilli"]), long(it["totalMinor"])) },
        Tolerance(long(t["qtyMilli"]), long(t["priceBp"]), long(t["absMinor"])),
      )
      val got = mapOf(
        "matched" to m.matched, "orderedMinor" to m.orderedMinor, "acceptedMinor" to m.acceptedMinor, "billedMinor" to m.billedMinor,
        "lines" to m.lines.map {
          mapOf(
            "lineId" to it.lineId, "productId" to it.productId, "orderedMilli" to it.orderedMilli, "acceptedMilli" to it.acceptedMilli,
            "billedMilli" to it.billedMilli, "orderPriceMinor" to it.orderPriceMinor, "expectedMinor" to it.expectedMinor,
            "billedMinor" to it.billedMinor, "issues" to it.issues.map { i -> i.wire },
          )
        },
      )
      assertEquals(c["expect"], got, str(c["name"]))
    }
  }
}

/** خوانندهٔ کوچکِ JSON برای آزمون: شیء ⇒ LinkedHashMap، آرایه ⇒ List، عددِ صحیح ⇒ Long، رشته، Boolean، null */
internal class MiniJson(private val s: String) {
  private var i = 0

  fun parse(): Any? = value().also { ws(); require(i == s.length) { "JSON: نویسهٔ اضافه در $i" } }

  private fun ws() { while (i < s.length && s[i] in " \t\r\n") i++ }

  private fun value(): Any? {
    ws()
    return when (val c = s[i]) {
      '{' -> obj()
      '[' -> arr()
      '"' -> str()
      't' -> lit("true", true)
      'f' -> lit("false", false)
      'n' -> lit("null", null)
      else -> if (c == '-' || c.isDigit()) num() else error("JSON: نویسهٔ ناشناخته '$c' در $i")
    }
  }

  private fun lit(word: String, v: Any?): Any? {
    require(s.startsWith(word, i)) { "JSON: $word در $i" }
    i += word.length
    return v
  }

  private fun num(): Long {
    val start = i
    if (s[i] == '-') i++
    while (i < s.length && s[i].isDigit()) i++
    require(i >= s.length || s[i] !in ".eE") { "JSON: فقط عددِ صحیح در بردارها ($start)" }
    return s.substring(start, i).toLong()
  }

  private fun str(): String {
    i++
    val b = StringBuilder()
    while (s[i] != '"') {
      if (s[i] == '\\') {
        i++
        when (val e = s[i]) {
          'n' -> b.append('\n'); 't' -> b.append('\t'); 'r' -> b.append('\r'); 'b' -> b.append('\b'); 'f' -> b.append('\u000C')
          'u' -> { b.append(s.substring(i + 1, i + 5).toInt(16).toChar()); i += 4 }
          else -> b.append(e)
        }
      } else b.append(s[i])
      i++
    }
    i++
    return b.toString()
  }

  private fun arr(): List<Any?> {
    i++
    val out = ArrayList<Any?>()
    ws()
    if (s[i] == ']') { i++; return out }
    while (true) {
      out += value(); ws()
      if (s[i] == ',') { i++; continue }
      require(s[i] == ']') { "JSON: ] در $i" }
      i++
      return out
    }
  }

  private fun obj(): Map<String, Any?> {
    i++
    val out = LinkedHashMap<String, Any?>()
    ws()
    if (s[i] == '}') { i++; return out }
    while (true) {
      ws()
      val k = str(); ws()
      require(s[i] == ':') { "JSON: : در $i" }
      i++
      out[k] = value(); ws()
      if (s[i] == ',') { i++; continue }
      require(s[i] == '}') { "JSON: } در $i" }
      i++
      return out
    }
  }
}
